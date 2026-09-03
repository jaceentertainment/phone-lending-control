package com.jace.phonelending.consumer;

import android.Manifest;
import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.Locale;

public class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SessionStore sessions;
    private PairingManager pairing;
    private PolicyController policy;
    LinearLayout root;
    private String lastRenderedState = "";
    private boolean lastRenderedUnrestricted = false;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sessions = new SessionStore(this);
        pairing = new PairingManager(this);
        policy = new PolicyController(this);
        ConsumerService.start(this);
        requestNotificationPermissionIfNeeded();
        render();
        handler.post(uiTick);
    }

    @Override protected void onResume() { super.onResume(); render(); }
    @Override protected void onDestroy() { handler.removeCallbacksAndMessages(null); super.onDestroy(); }

    @Override public void onBackPressed() {
        String state = sessions.getState();
        if (isRestricted(state) || (SessionStore.ADMIN_MAINTENANCE.equals(state) && !sessions.isDevUnrestricted())) return;
        super.onBackPressed();
    }

    private final Runnable uiTick = new Runnable() {
        @Override public void run() {
            sessions.reconcileMaintenance();
            sessions.expireIfNeeded();
            String state = sessions.getState();
            boolean unrestricted = sessions.isDevUnrestricted();
            if (!state.equals(lastRenderedState) || unrestricted != lastRenderedUnrestricted
                    || SessionStore.ACTIVE.equals(state) || SessionStore.ADMIN_MAINTENANCE.equals(state)) render();
            handler.postDelayed(this, 1000L);
        }
    };

    void render() {
        String state;
        if (!policy.isDeviceOwner()) {
            state = SessionStore.UNPROVISIONED;
            sessions.setState(SessionStore.UNPROVISIONED);
        } else {
            sessions.initializeProvisioned();
            sessions.expireIfNeeded();
            state = sessions.getState();
        }
        lastRenderedState = state;
        lastRenderedUnrestricted = sessions.isDevUnrestricted();

        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(28), dp(22), dp(28));
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        scroll.addView(root, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);

        if (SessionStore.UNPROVISIONED.equals(state)) renderUnprovisioned();
        else if (SessionStore.ACTIVE.equals(state)) renderActive();
        else if (SessionStore.ADMIN_MAINTENANCE.equals(state)) renderMaintenance();
        else if (SessionStore.RECOVERY_LOCKED.equals(state)) renderRecoveryLocked();
        else renderRestricted(state);
    }

    private void renderUnprovisioned() {
        policy.clearLockedHome();
        title("PHONE LENDING");
        warning(isDebuggable() ? "DEVELOPMENT DEVICE — NOT FOR CUSTOMER RENTAL" : "BUSINESS PROVISIONING REQUIRED");
        body("This business-owned phone must be provisioned by an authorized operator before rental use.");
        spacer(16);
        labelValue("Device", pairing.getDeviceId());
        labelValue("Local IP", localIp());
        labelValue("Control port", String.valueOf(ConsumerService.PORT));
        labelValue("Pairing code", pairing.getPairCode());
        invokeDebugHook("augmentUnprovisioned", null);
        button("REFRESH STATUS", v -> render());
        buildFooter();
    }

    private void renderActive() {
        policy.clearLockedHome();
        policy.exitLockTask(this);
        title("PHONE RENTAL");
        section("TIME REMAINING");
        bigTime(formatDuration(sessions.remainingSeconds()));
        success("SESSION ACTIVE");
        body("The persistent notification shows the same Consumer-authoritative timer. Ask the rental operator if you need more time.");
        labelValue("Device", pairing.getDeviceId());
        labelValue("Connection", localIp() + ":" + ConsumerService.PORT);
        button("RETURN TO PHONE", v -> policy.applyActiveAndOpenHome());
        buildFooter();
    }

    private void renderRestricted(String state) {
        policy.applyLockedHome();
        title("PHONE RENTAL");
        if (SessionStore.AVAILABLE_LOCKED.equals(state)) {
            success("DEVICE READY");
            bigTime("00:00:00");
            body(pairing.isPaired() ? "Waiting for the rental operator to start a session." : "Waiting for business pairing and rental setup.");
        } else {
            warning("RENTAL TIME EXPIRED");
            bigTime("00:00:00");
            body("Your rental session has ended. Please return this business-owned phone to the rental operator.");
        }
        labelValue("Device", pairing.getDeviceId());
        labelValue("Local IP", localIp());
        if (!pairing.isPaired()) labelValue("Pairing code", pairing.getPairCode());
        buildFooter();
        policy.enterLockTask(this);
    }

    private void renderRecoveryLocked() {
        policy.applyLockedHome();
        title("PHONE LENDING");
        warning("DEVICE NEEDS OWNER ATTENTION");
        bigTime("00:00:00");
        body("Rental access is unavailable because the Consumer could not prove session integrity. Please return the device to the operator.");
        labelValue("Device", pairing.getDeviceId());
        String reason = sessions.getLockReason();
        if (!reason.isEmpty()) labelValue("Recovery reason", reason);
        buildFooter();
        policy.enterLockTask(this);
    }

    private void renderMaintenance() {
        boolean unrestricted = sessions.isDevUnrestricted();
        title("OWNER MAINTENANCE");
        labelValue("Canonical state", SessionStore.ADMIN_MAINTENANCE);
        labelValue("Maintenance remaining", formatDuration(sessions.maintenanceRemainingSeconds()));
        labelValue("Device Owner", policy.isDeviceOwner() ? "ACTIVE" : "MISSING");
        labelValue("Timer service", "STARTED / RECONCILING");
        labelValue("Pairing", pairing.isPaired() ? "PAIRED" : "NOT PAIRED");
        labelValue("Notification", policy.notificationPermissionGranted() ? "READY" : "PERMISSION MISSING");

        if (unrestricted) {
            warning("TEMPORARY OWNER ACCESS ACTIVE");
            body("The underlying rental state has not changed. This maintenance lease will automatically close and reconcile.");
            button("RELOCK NOW", v -> {
                sessions.exitMaintenance();
                policy.applyRestrictedAndBringToFront();
                render();
            });
            button("OPEN ANDROID HOME", v -> policy.applyActiveAndOpenHome());
        } else {
            policy.applyLockedHome();
            policy.enterLockTask(this);
            body("Maintenance is constrained by default. Privileged escape actions require an authorized build-specific recovery mechanism.");
            button("REAPPLY CURRENT POLICY", v -> {
                policy.applyLockedHome();
                policy.enterLockTask(this);
                Toast.makeText(this, "Restricted policy reapplied", Toast.LENGTH_SHORT).show();
            });
            button("EXIT OWNER MAINTENANCE", v -> { sessions.exitMaintenance(); render(); });
        }
        invokeDebugHook("augmentMaintenance", null);
        buildFooter();
    }

    private void buildFooter() {
        TextView footer = text("Build " + versionName(), 13, Typeface.NORMAL);
        footer.setPadding(0, dp(28), 0, dp(10));
        root.addView(footer);
        invokeDebugHook("attachFooter", footer);
    }

    private void invokeDebugHook(String method, TextView footer) {
        if (!isDebuggable()) return;
        try {
            Class<?> clazz = Class.forName("com.jace.phonelending.consumer.DebugRecoveryHooks");
            if (footer == null) clazz.getMethod(method, MainActivity.class).invoke(null, this);
            else clazz.getMethod(method, MainActivity.class, TextView.class).invoke(null, this, footer);
        } catch (Throwable ignored) {}
    }

    SessionStore sessions() { return sessions; }
    PairingManager pairing() { return pairing; }
    PolicyController policy() { return policy; }
    void debugLabelValue(String label, String value) { labelValue(label, value); }
    void debugBody(String value) { body(value); }
    void debugWarning(String value) { warning(value); }
    void debugMono(String value) { mono(value); }
    Button debugButton(String label, View.OnClickListener listener) { return button(label, listener); }

    private void requestNotificationPermissionIfNeeded() {
        policy.grantRequiredNotificationPermissionIfPossible();
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 7001);
    }

    private boolean isDebuggable() { return (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0; }
    private boolean isRestricted(String state) {
        return SessionStore.AVAILABLE_LOCKED.equals(state) || SessionStore.EXPIRED_LOCKED.equals(state) || SessionStore.RECOVERY_LOCKED.equals(state);
    }

    private void title(String value) { TextView t=text(value,24,Typeface.BOLD); t.setGravity(Gravity.CENTER); t.setPadding(0,0,0,dp(14)); root.addView(t); }
    private void section(String value) { TextView t=text(value,14,Typeface.BOLD); t.setGravity(Gravity.CENTER); t.setPadding(0,dp(8),0,dp(4)); root.addView(t); }
    private void warning(String value) { TextView t=text(value,18,Typeface.BOLD); t.setGravity(Gravity.CENTER); t.setPadding(dp(12),dp(12),dp(12),dp(12)); root.addView(t,matchWrap()); }
    private void success(String value) { TextView t=text(value,17,Typeface.BOLD); t.setGravity(Gravity.CENTER); t.setPadding(0,dp(8),0,dp(8)); root.addView(t); }
    private void bigTime(String value) { TextView t=text(value,46,Typeface.BOLD); t.setGravity(Gravity.CENTER); t.setPadding(0,dp(6),0,dp(14)); root.addView(t); }
    private void body(String value) { TextView t=text(value,16,Typeface.NORMAL); t.setGravity(Gravity.CENTER); t.setPadding(0,dp(8),0,dp(12)); root.addView(t); }
    private void labelValue(String label,String value) { TextView t=text(label+":  "+value,15,Typeface.NORMAL); t.setPadding(0,dp(5),0,dp(5)); root.addView(t,matchWrap()); }
    private void mono(String value) { TextView t=text(value,13,Typeface.NORMAL); t.setTypeface(Typeface.MONOSPACE); t.setTextIsSelectable(true); t.setPadding(dp(10),dp(10),dp(10),dp(10)); root.addView(t,matchWrap()); }
    private Button button(String label,View.OnClickListener listener) { Button b=new Button(this); b.setText(label); b.setAllCaps(false); b.setTextSize(15); b.setOnClickListener(listener); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(52)); lp.setMargins(0,dp(7),0,dp(7)); root.addView(b,lp); return b; }
    private TextView text(String value,int sizeSp,int style) { TextView t=new TextView(this); t.setText(value); t.setTextSize(sizeSp); t.setTextColor(0xFF111111); t.setTypeface(Typeface.DEFAULT,style); return t; }
    private LinearLayout.LayoutParams matchWrap() { return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT); }
    private void spacer(int valueDp) { View v=new View(this); root.addView(v,new LinearLayout.LayoutParams(1,dp(valueDp))); }
    private int dp(int value) { return Math.round(value*getResources().getDisplayMetrics().density); }
    private static String formatDuration(long totalSeconds) { long h=totalSeconds/3600,m=(totalSeconds%3600)/60,s=totalSeconds%60; return String.format(Locale.US,"%02d:%02d:%02d",h,m,s); }
    private String versionName() { try { return getPackageManager().getPackageInfo(getPackageName(),0).versionName; } catch(Exception e) { return "unknown"; } }
    private String localIp() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces()))
                for (java.net.InetAddress address : Collections.list(ni.getInetAddresses()))
                    if (!address.isLoopbackAddress() && address instanceof Inet4Address && address.isSiteLocalAddress()) return address.getHostAddress();
        } catch (Exception ignored) {}
        return "Unavailable";
    }
}
