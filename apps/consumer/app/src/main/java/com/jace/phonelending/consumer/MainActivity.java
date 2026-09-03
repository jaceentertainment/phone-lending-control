package com.jace.phonelending.consumer;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
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

import java.util.Locale;

public class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SessionStore sessions;
    private PairingManager pairing;
    private PolicyController policy;
    private LinearLayout root;

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

    private final Runnable uiTick = new Runnable() {
        @Override public void run() {
            if (policy.isDeviceOwner()) {
                sessions.initializeProvisioned();
                sessions.reconcileMaintenance();
                sessions.expireIfNeeded();
                if (!SessionStore.ACTIVE.equals(sessions.getState())) {
                    openRestricted();
                    return;
                }
                renderActive();
            }
            handler.postDelayed(this, 1000L);
        }
    };

    void render() {
        if (!policy.isDeviceOwner()) {
            sessions.setState(SessionStore.UNPROVISIONED);
            renderUnprovisioned();
            return;
        }
        sessions.initializeProvisioned();
        sessions.expireIfNeeded();
        if (!SessionStore.ACTIVE.equals(sessions.getState())) {
            openRestricted();
            return;
        }
        renderActive();
    }

    private void renderUnprovisioned() {
        policy.clearLockedHome();
        page();
        title("PhoneLending Rental");
        warning(isDebuggable() ? "DEVELOPMENT DEVICE — NOT FOR CUSTOMER RENTAL" : "BUSINESS SETUP REQUIRED");
        section("DEVICE SETUP REQUIRED");
        body("The app is installed, but this phone is not yet under PhoneLending management. Installation alone does not lock the phone.");
        labelValue("Device", pairing.getDeviceId());
        labelValue("Management", "NOT CONFIGURED");
        labelValue("Host pairing", "DISABLED UNTIL MANAGEMENT IS ACTIVE");
        body("An authorized owner/technician must provision this business-owned phone as Device Owner before Host pairing becomes available.");
        invokeDebugUnprovisionedHook();
        button("REFRESH SETUP STATUS", v -> render());
        buildFooter();
    }

    private void renderActive() {
        policy.clearLockedHome();
        page();
        title("PhoneLending Rental");
        section("TIME REMAINING");
        bigTime(formatDuration(sessions.remainingSeconds()));
        success("SESSION ACTIVE");
        body("Your rental is active. The Consumer on this phone owns the authoritative timer and will continue counting down even if the Host is offline.");
        labelValue("Device", pairing.getDeviceId());
        labelValue("Host", pairing.isPaired() ? "PAIRED" : "NOT PAIRED");
        button("RETURN TO PHONE", v -> policy.applyActiveAndOpenHome());
        buildFooter();
    }

    private void openRestricted() {
        policy.applyLockedHome();
        Intent i = new Intent(this, RestrictedActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i);
        finish();
    }

    private void requestNotificationPermissionIfNeeded() {
        policy.grantRequiredNotificationPermissionIfPossible();
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 7001);
    }

    private void invokeDebugUnprovisionedHook() {
        if (!isDebuggable()) return;
        try {
            Class<?> clazz = Class.forName("com.jace.phonelending.consumer.DebugRecoveryHooks");
            clazz.getMethod("augmentUnprovisioned", MainActivity.class).invoke(null, this);
        } catch (Throwable ignored) {}
    }

    private void buildFooter() {
        TextView footer = text("Build " + versionName(), 13, Typeface.NORMAL);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(28), 0, dp(10));
        root.addView(footer, matchWrap());
    }

    SessionStore sessions() { return sessions; }
    PairingManager pairing() { return pairing; }
    PolicyController policy() { return policy; }
    void debugLabelValue(String label, String value) { labelValue(label, value); }
    void debugBody(String value) { body(value); }
    void debugWarning(String value) { warning(value); }
    void debugMono(String value) { mono(value); }
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
    private void success(String value) { TextView t=text(value,20,Typeface.BOLD); t.setGravity(Gravity.CENTER); t.setPadding(0,dp(8),0,dp(8)); root.addView(t); }
    private void bigTime(String value) { TextView t=text(value,46,Typeface.BOLD); t.setGravity(Gravity.CENTER); t.setPadding(0,dp(6),0,dp(14)); root.addView(t); }
    private void body(String value) { TextView t=text(value,16,Typeface.NORMAL); t.setGravity(Gravity.CENTER); t.setPadding(0,dp(8),0,dp(12)); root.addView(t); }
    private void labelValue(String label,String value) { TextView t=text(label+":  "+value,15,Typeface.NORMAL); t.setPadding(0,dp(5),0,dp(5)); root.addView(t,matchWrap()); }
    private void mono(String value) { TextView t=text(value,13,Typeface.NORMAL); t.setTypeface(Typeface.MONOSPACE); t.setTextIsSelectable(true); t.setPadding(dp(10),dp(10),dp(10),dp(10)); root.addView(t,matchWrap()); }
    private Button button(String label,View.OnClickListener listener) { Button b=new Button(this); b.setText(label); b.setAllCaps(false); b.setTextSize(15); b.setOnClickListener(listener); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(52)); lp.setMargins(0,dp(7),0,dp(7)); root.addView(b,lp); return b; }
    private TextView text(String value,int sizeSp,int style) { TextView t=new TextView(this); t.setText(value); t.setTextSize(sizeSp); t.setTextColor(0xFF111111); t.setTypeface(Typeface.DEFAULT,style); return t; }
    private LinearLayout.LayoutParams matchWrap() { return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT); }
    private int dp(int value) { return Math.round(value*getResources().getDisplayMetrics().density); }
    private static String formatDuration(long totalSeconds) { long h=totalSeconds/3600,m=(totalSeconds%3600)/60,s=totalSeconds%60; return String.format(Locale.US,"%02d:%02d:%02d",h,m,s); }
    private String versionName() { try { return getPackageManager().getPackageInfo(getPackageName(),0).versionName; } catch(Exception e) { return "unknown"; } }
    private boolean isDebuggable() { return (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0; }
}
