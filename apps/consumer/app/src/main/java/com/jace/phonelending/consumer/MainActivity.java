package com.jace.phonelending.consumer;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
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
    private LinearLayout root;
    private int hiddenTapCount = 0;
    private long firstHiddenTapAt = 0L;
    private String lastRenderedState = "";
    private boolean lastRenderedUnrestricted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sessions = new SessionStore(this);
        pairing = new PairingManager(this);
        policy = new PolicyController(this);
        ConsumerService.start(this);
        requestNotificationPermissionIfNeeded();
        render();
        handler.post(uiTick);
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        String state = sessions.getState();
        if (isRestricted(state) || (SessionStore.ADMIN_MAINTENANCE.equals(state) && !sessions.isDevUnrestricted())) {
            return;
        }
        super.onBackPressed();
    }

    private final Runnable uiTick = new Runnable() {
        @Override public void run() {
            sessions.reconcileMaintenance();
            sessions.expireIfNeeded();
            String state = sessions.getState();
            boolean unrestricted = sessions.isDevUnrestricted();
            if (!state.equals(lastRenderedState) || unrestricted != lastRenderedUnrestricted
                    || SessionStore.ACTIVE.equals(state) || SessionStore.ADMIN_MAINTENANCE.equals(state)) {
                render();
            }
            handler.postDelayed(this, 1000L);
        }
    };

    private void render() {
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
        title("PHONE LENDING — DEVELOPMENT DEVICE");
        warning("NOT FOR CUSTOMER RENTAL");
        body("This Consumer build is intentionally recoverable while Device Owner and kiosk behavior are under development.");
        spacer(16);
        labelValue("Device", pairing.getDeviceId());
        labelValue("Local IP", localIp());
        labelValue("Control port", String.valueOf(ConsumerService.PORT));
        labelValue("Pairing code", pairing.getPairCode());
        labelValue("Development recovery PIN", pairing.getDevPin());
        spacer(18);
        section("Provisioning");
        mono("1. Install with ADB:\n   adb install -t PhoneLending-Consumer-v0.3.0-vc1-dev.apk\n\n2. Set Device Owner:\n   adb shell dpm set-device-owner com.jace.phonelending.consumer.dev/com.jace.phonelending.consumer.AdminReceiver");
        body("Record the development recovery PIN before provisioning. ADB removal and physical reset remain deliberate Batch-1 owner escape paths.");
        button("REFRESH STATUS", v -> render());
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
        title("OWNER MAINTENANCE — DEVELOPMENT");
        warning("NOT FOR CUSTOMER RENTAL");
        labelValue("Canonical state", SessionStore.ADMIN_MAINTENANCE);
        labelValue("Maintenance remaining", formatDuration(sessions.maintenanceRemainingSeconds()));
        labelValue("Device Owner", policy.isDeviceOwner() ? "ACTIVE" : "MISSING");
        labelValue("Timer service", "STARTED / RECONCILING");
        labelValue("Pairing", pairing.isPaired() ? "PAIRED" : "NOT PAIRED");
        labelValue("Notification", policy.notificationPermissionGranted() ? "READY" : "PERMISSION MISSING");

        if (unrestricted) {
            warning("UNRESTRICTED TEST ACCESS IS TEMPORARY");
            body("The underlying rental state has not changed. This maintenance escape will automatically close and reconcile.");
            button("RELOCK NOW", v -> {
                sessions.exitMaintenance();
                policy.applyRestrictedAndBringToFront();
                render();
            });
            button("OPEN ANDROID HOME", v -> policy.applyActiveAndOpenHome());
        } else {
            policy.applyLockedHome();
            policy.enterLockTask(this);
            body("Developer authentication opens only this constrained diagnostic environment. Full kiosk exit requires a second explicit action.");
            button("REAPPLY CURRENT POLICY", v -> {
                policy.applyLockedHome();
                policy.enterLockTask(this);
                Toast.makeText(this, "Restricted policy reapplied", Toast.LENGTH_SHORT).show();
            });
            button("ADVANCED: TEMPORARILY EXIT KIOSK (2 MIN)", v -> confirmTemporaryExit());
            button("EXIT OWNER MAINTENANCE", v -> {
                sessions.exitMaintenance();
                render();
            });
        }
        buildFooter();
    }

    private void confirmTemporaryExit() {
        new AlertDialog.Builder(this)
                .setTitle("Unrestricted development access")
                .setMessage("This is a high-risk development action. Kiosk restrictions will be relaxed for up to 2 minutes, then the Consumer will reconcile the authoritative state.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Continue", (d, w) -> {
                    sessions.setDevUnrestricted(120L);
                    policy.clearLockedHome();
                    policy.exitLockTask(this);
                    policy.applyActiveAndOpenHome();
                }).show();
    }

    private void buildFooter() {
        TextView footer = text("Build v0.3.0-dev", 13, Typeface.NORMAL);
        footer.setPadding(0, dp(28), 0, dp(10));
        footer.setOnClickListener(v -> onHiddenBuildTap());
        root.addView(footer);
    }

    private void onHiddenBuildTap() {
        long now = System.currentTimeMillis();
        if (firstHiddenTapAt == 0L || now - firstHiddenTapAt > 5000L) {
            firstHiddenTapAt = now;
            hiddenTapCount = 0;
        }
        hiddenTapCount++;
        if (hiddenTapCount >= 7) {
            hiddenTapCount = 0;
            firstHiddenTapAt = 0L;
            showDeveloperGate();
        }
    }

    private void showDeveloperGate() {
        long cooldownMs = pairing.devCooldownRemainingMs();
        if (cooldownMs > 0L) {
            Toast.makeText(this, "Developer Access cooling down: " + ((cooldownMs + 999L) / 1000L) + " sec", Toast.LENGTH_LONG).show();
            return;
        }
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setHint("8-digit development PIN");
        new AlertDialog.Builder(this)
                .setTitle("Developer Access")
                .setMessage("Developer Mode is locked. The hidden gesture does not unlock the device.")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Authenticate", (d, w) -> {
                    if (pairing.verifyDevPin(input.getText().toString().trim())) {
                        sessions.enterMaintenance(600L);
                        render();
                    } else {
                        long left = pairing.devCooldownRemainingMs();
                        String msg = left > 0L ? "Too many failures. Developer Access locked for 60 seconds." : "Authentication failed";
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                    }
                }).show();
    }

    private void requestNotificationPermissionIfNeeded() {
        policy.grantRequiredNotificationPermissionIfPossible();
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 7001);
        }
    }

    private boolean isRestricted(String state) {
        return SessionStore.AVAILABLE_LOCKED.equals(state)
                || SessionStore.EXPIRED_LOCKED.equals(state)
                || SessionStore.RECOVERY_LOCKED.equals(state);
    }

    private void title(String value) {
        TextView t = text(value, 24, Typeface.BOLD);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, 0, 0, dp(14));
        root.addView(t);
    }

    private void section(String value) {
        TextView t = text(value, 14, Typeface.BOLD);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(8), 0, dp(4));
        root.addView(t);
    }

    private void warning(String value) {
        TextView t = text(value, 18, Typeface.BOLD);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(12), dp(12), dp(12), dp(12));
        root.addView(t, matchWrap());
    }

    private void success(String value) {
        TextView t = text(value, 17, Typeface.BOLD);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(8), 0, dp(8));
        root.addView(t);
    }

    private void bigTime(String value) {
        TextView t = text(value, 46, Typeface.BOLD);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(6), 0, dp(14));
        root.addView(t);
    }

    private void body(String value) {
        TextView t = text(value, 16, Typeface.NORMAL);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(8), 0, dp(12));
        root.addView(t);
    }

    private void labelValue(String label, String value) {
        TextView t = text(label + ":  " + value, 15, Typeface.NORMAL);
        t.setPadding(0, dp(5), 0, dp(5));
        root.addView(t, matchWrap());
    }

    private void mono(String value) {
        TextView t = text(value, 13, Typeface.MONOSPACE.getStyle());
        t.setTypeface(Typeface.MONOSPACE);
        t.setTextIsSelectable(true);
        t.setPadding(dp(10), dp(10), dp(10), dp(10));
        root.addView(t, matchWrap());
    }

    private Button button(String label, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setOnClickListener(listener);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        lp.setMargins(0, dp(7), 0, dp(7));
        root.addView(b, lp);
        return b;
    }

    private TextView text(String value, int sizeSp, int style) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sizeSp);
        t.setTextColor(0xFF111111);
        t.setTypeface(Typeface.DEFAULT, style);
        return t;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private void spacer(int dp) {
        View v = new View(this);
        root.addView(v, new LinearLayout.LayoutParams(1, dp(dp)));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static String formatDuration(long totalSeconds) {
        long h = totalSeconds / 3600;
        long m = (totalSeconds % 3600) / 60;
        long s = totalSeconds % 60;
        return String.format(Locale.US, "%02d:%02d:%02d", h, m, s);
    }

    private String localIp() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                for (java.net.InetAddress address : Collections.list(ni.getInetAddresses())) {
                    if (!address.isLoopbackAddress() && address instanceof Inet4Address && address.isSiteLocalAddress()) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {}
        return "Unavailable";
    }
}
