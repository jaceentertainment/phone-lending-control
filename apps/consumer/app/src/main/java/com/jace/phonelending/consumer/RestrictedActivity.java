package com.jace.phonelending.consumer;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;

public class RestrictedActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SessionStore sessions;
    private PairingManager pairing;
    private PolicyController policy;
    private LinearLayout root;
    private String lastSignature = "";

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sessions = new SessionStore(this);
        pairing = new PairingManager(this);
        policy = new PolicyController(this);
        ConsumerService.start(this);
        render();
        handler.post(uiTick);
    }

    @Override protected void onResume() { super.onResume(); render(); }
    @Override protected void onDestroy() { handler.removeCallbacksAndMessages(null); super.onDestroy(); }

    @Override public void onBackPressed() {
        if (SessionStore.ADMIN_MAINTENANCE.equals(sessions.getState()) && sessions.isDevUnrestricted()) super.onBackPressed();
    }

    private final Runnable uiTick = new Runnable() {
        @Override public void run() {
            sessions.reconcileMaintenance();
            sessions.expireIfNeeded();
            String state = sessions.getState();
            if (SessionStore.ACTIVE.equals(state)) {
                policy.exitLockTask(RestrictedActivity.this);
                policy.applyActiveAndOpenHome();
                finish();
                return;
            }
            String signature = state + "|" + pairing.isPaired() + "|" + pairing.pairingSessionRemainingSeconds()
                    + "|" + sessions.isDevUnrestricted() + "|" + ConsumerService.advertisedPort()
                    + "|" + ConsumerService.advertisedServiceName();
            if (!signature.equals(lastSignature)) render();
            handler.postDelayed(this, 1000L);
        }
    };

    void render() {
        if (!policy.isDeviceOwner()) {
            policy.exitLockTask(this);
            startActivity(new android.content.Intent(this, MainActivity.class));
            finish();
            return;
        }

        sessions.initializeProvisioned();
        sessions.expireIfNeeded();
        String state = sessions.getState();
        if (SessionStore.ACTIVE.equals(state)) {
            policy.exitLockTask(this);
            policy.applyActiveAndOpenHome();
            finish();
            return;
        }

        lastSignature = state + "|" + pairing.isPaired() + "|" + pairing.pairingSessionRemainingSeconds()
                + "|" + sessions.isDevUnrestricted() + "|" + ConsumerService.advertisedPort()
                + "|" + ConsumerService.advertisedServiceName();

        page();
        if (SessionStore.AVAILABLE_LOCKED.equals(state)) renderAvailable();
        else if (SessionStore.EXPIRED_LOCKED.equals(state)) renderExpired();
        else if (SessionStore.RECOVERY_LOCKED.equals(state)) renderRecoveryLocked();
        else if (SessionStore.ADMIN_MAINTENANCE.equals(state)) renderMaintenance();
        else renderRecoveryUnknown(state);

        buildFooter();
        if (!SessionStore.ADMIN_MAINTENANCE.equals(state) || !sessions.isDevUnrestricted()) {
            policy.applyLockedHome();
            policy.enterLockTask(this);
        }
    }

    private void renderAvailable() {
        title("PhoneLending Rental");
        if (!pairing.isPaired()) {
            warning("PAIR WITH HOST");
            body("This business-owned rental phone is managed and waiting for its authorized Host.");
            int port = ConsumerService.advertisedPort();
            String service = ConsumerService.advertisedServiceName();
            if (port <= 0 || service.isEmpty()) {
                section("Preparing secure pairing...");
                body("Keep both phones on the same local network. The QR code will appear automatically when the secure local service is ready.");
            } else {
                List<String> hints = NetworkUtils.localEndpointHints(port);
                String payload = pairing.buildPairingUri(service, hints);
                try {
                    ImageView qr = new ImageView(this);
                    int size = dp(280);
                    qr.setImageBitmap(QrCodeRenderer.render(payload, size));
                    qr.setAdjustViewBounds(true);
                    root.addView(qr, new LinearLayout.LayoutParams(size, size));
                } catch (Exception e) {
                    warning("QR generation failed");
                }
                body("Open PhoneLending Host → Add Rental Device → Scan QR.");
                labelValue("QR expires in", formatDuration(pairing.pairingSessionRemainingSeconds()));
            }
            labelValue("Device", pairing.getDeviceId());
            labelValue("Management", "ACTIVE");
            labelValue("Host", "NOT PAIRED");
        } else {
            success("READY");
            bigTime("00:00:00");
            body("This rental phone is paired and waiting for the operator to start a rental session.");
            labelValue("Device", pairing.getDeviceId());
            labelValue("Management", "ACTIVE");
            labelValue("Host", "PAIRED");
        }
    }

    private void renderExpired() {
        title("PhoneLending Rental");
        warning("RENTAL TIME EXPIRED");
        bigTime("00:00:00");
        body("Your rental session has ended. Please return this business-owned phone to the rental operator.");
        labelValue("Device", pairing.getDeviceId());
    }

    private void renderRecoveryLocked() {
        title("PhoneLending Rental");
        warning("DEVICE NEEDS OWNER ATTENTION");
        bigTime("00:00:00");
        body("Rental access is unavailable because the device could not prove management/session integrity. Please return it to the operator.");
        labelValue("Device", pairing.getDeviceId());
        String reason = sessions.getLockReason();
        if (!reason.isEmpty()) labelValue("Recovery reason", reason);
    }

    private void renderMaintenance() {
        boolean unrestricted = sessions.isDevUnrestricted();
        title("OWNER MAINTENANCE");
        labelValue("Canonical state", SessionStore.ADMIN_MAINTENANCE);
        labelValue("Maintenance remaining", formatDuration(sessions.maintenanceRemainingSeconds()));
        labelValue("Device Owner", policy.isDeviceOwner() ? "ACTIVE" : "MISSING");
        labelValue("Pairing", pairing.isPaired() ? "PAIRED" : "NOT PAIRED");
        labelValue("Notification", policy.notificationPermissionGranted() ? "READY" : "PERMISSION MISSING");

        if (unrestricted) {
            warning("TEMPORARY OWNER ACCESS ACTIVE");
            body("The rental state underneath maintenance has not changed. This development lease automatically expires and reconciles.");
            button("RELOCK NOW", v -> {
                sessions.exitMaintenance();
                policy.applyRestrictedAndBringToFront();
                render();
            });
            button("OPEN ANDROID HOME", v -> policy.applyActiveAndOpenHome());
        } else {
            body("Maintenance is constrained by default. High-risk escape actions require the authenticated development recovery gate.");
            button("REAPPLY RESTRICTED POLICY", v -> {
                policy.applyLockedHome();
                policy.enterLockTask(this);
                Toast.makeText(this, "Restricted policy reapplied", Toast.LENGTH_SHORT).show();
            });
            button("EXIT OWNER MAINTENANCE", v -> { sessions.exitMaintenance(); render(); });
        }
        invokeDebugMaintenanceHook();
    }

    private void renderRecoveryUnknown(String state) {
        sessions.markRecoveryLocked("unexpected_state:" + state);
        renderRecoveryLocked();
    }

    private void buildFooter() {
        TextView footer = text("Build " + versionName(), 13, Typeface.NORMAL);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(28), 0, dp(10));
        root.addView(footer, matchWrap());
        invokeDebugFooterHook(footer);
    }

    private void invokeDebugFooterHook(TextView footer) {
        if (!isDebuggable()) return;
        try {
            Class<?> clazz = Class.forName("com.jace.phonelending.consumer.DebugRecoveryHooks");
            clazz.getMethod("attachFooter", RestrictedActivity.class, TextView.class).invoke(null, this, footer);
        } catch (Throwable ignored) {}
    }

    private void invokeDebugMaintenanceHook() {
        if (!isDebuggable()) return;
        try {
            Class<?> clazz = Class.forName("com.jace.phonelending.consumer.DebugRecoveryHooks");
            clazz.getMethod("augmentMaintenance", RestrictedActivity.class).invoke(null, this);
        } catch (Throwable ignored) {}
    }

    SessionStore sessions() { return sessions; }
    PairingManager pairing() { return pairing; }
    PolicyController policy() { return policy; }
    void debugLabelValue(String label, String value) { labelValue(label, value); }
    void debugBody(String value) { body(value); }
    void debugWarning(String value) { warning(value); }
    Button debugButton(String label, View.OnClickListener listener) { return button(label, listener); }

    private void page() {
        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(28), dp(22), dp(28));
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        scroll.addView(root, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);
    }

    private void title(String value) { TextView t=text(value,24,Typeface.BOLD); t.setGravity(Gravity.CENTER); t.setPadding(0,0,0,dp(14)); root.addView(t); }
    private void section(String value) { TextView t=text(value,16,Typeface.BOLD); t.setGravity(Gravity.CENTER); t.setPadding(0,dp(8),0,dp(8)); root.addView(t); }
    private void warning(String value) { TextView t=text(value,18,Typeface.BOLD); t.setGravity(Gravity.CENTER); t.setPadding(dp(12),dp(12),dp(12),dp(12)); root.addView(t,matchWrap()); }
    private void success(String value) { TextView t=text(value,22,Typeface.BOLD); t.setGravity(Gravity.CENTER); t.setPadding(0,dp(12),0,dp(12)); root.addView(t); }
    private void bigTime(String value) { TextView t=text(value,46,Typeface.BOLD); t.setGravity(Gravity.CENTER); t.setPadding(0,dp(6),0,dp(14)); root.addView(t); }
    private void body(String value) { TextView t=text(value,16,Typeface.NORMAL); t.setGravity(Gravity.CENTER); t.setPadding(0,dp(8),0,dp(12)); root.addView(t); }
    private void labelValue(String label,String value) { TextView t=text(label+":  "+value,15,Typeface.NORMAL); t.setPadding(0,dp(5),0,dp(5)); root.addView(t,matchWrap()); }
    private Button button(String label,View.OnClickListener listener) { Button b=new Button(this); b.setText(label); b.setAllCaps(false); b.setTextSize(15); b.setOnClickListener(listener); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(52)); lp.setMargins(0,dp(7),0,dp(7)); root.addView(b,lp); return b; }
    private TextView text(String value,int sizeSp,int style) { TextView t=new TextView(this); t.setText(value); t.setTextSize(sizeSp); t.setTextColor(0xFF111111); t.setTypeface(Typeface.DEFAULT,style); return t; }
    private LinearLayout.LayoutParams matchWrap() { return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT); }
    private int dp(int value) { return Math.round(value*getResources().getDisplayMetrics().density); }
    private static String formatDuration(long totalSeconds) { long h=totalSeconds/3600,m=(totalSeconds%3600)/60,s=totalSeconds%60; return String.format(Locale.US,"%02d:%02d:%02d",h,m,s); }
    private String versionName() { try { return getPackageManager().getPackageInfo(getPackageName(),0).versionName; } catch(Exception e) { return "unknown"; } }
    private boolean isDebuggable() { return (getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0; }
}
