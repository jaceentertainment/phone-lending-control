package com.jace.phonelending.consumer;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.InputType;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.security.SecureRandom;

/** DEBUG SOURCE SET ONLY. This class must never be present in a production/release Consumer APK. */
public final class DebugRecoveryHooks {
    private static final String PREFS = "debug_recovery";
    private static final String PIN = "devPin";
    private static final String FAILURES = "devFailures";
    private static final String COOLDOWN = "devCooldownUntil";

    private DebugRecoveryHooks() {}

    public static void augmentUnprovisioned(MainActivity activity) {
        String pin = ensurePin(activity);
        activity.debugWarning("DEVELOPMENT RECOVERY ENABLED");
        activity.debugLabelValue("Development recovery PIN", pin);
        activity.debugMono(
                "Install with ADB:\n" +
                "adb install -t PhoneLending-Consumer-v0.4.0-vc2-dev.apk\n\n" +
                "Set Device Owner on a clean test phone:\n" +
                "adb shell dpm set-device-owner com.jace.phonelending.consumer.dev/com.jace.phonelending.consumer.AdminReceiver\n\n" +
                "Out-of-band development recovery:\n" +
                "adb shell dpm remove-active-admin com.jace.phonelending.consumer.dev/com.jace.phonelending.consumer.AdminReceiver");
        activity.debugBody("Record this device-specific development PIN before kiosk testing. ADB and physical recovery remain independent owner escape paths.");
    }

    public static void attachFooter(RestrictedActivity activity, TextView footer) {
        final int[] taps = {0};
        final long[] firstTap = {0L};
        footer.setOnClickListener(v -> {
            long now = System.currentTimeMillis();
            if (firstTap[0] == 0L || now - firstTap[0] > 5000L) {
                firstTap[0] = now;
                taps[0] = 0;
            }
            taps[0]++;
            if (taps[0] >= 7) {
                taps[0] = 0;
                firstTap[0] = 0L;
                showGate(activity);
            }
        });
    }

    public static void augmentMaintenance(RestrictedActivity activity) {
        if (activity.sessions().isDevUnrestricted()) {
            activity.debugWarning("DEVELOPMENT UNRESTRICTED TEST LEASE");
            return;
        }
        activity.debugWarning("DEVELOPMENT DIAGNOSTICS");
        activity.debugButton("ADVANCED: TEMPORARILY EXIT KIOSK (2 MIN)", v ->
                new AlertDialog.Builder(activity)
                        .setTitle("Unrestricted development access")
                        .setMessage("High-risk development action. Kiosk restrictions will be relaxed for up to two minutes, then the Consumer must reconcile its authoritative state.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Continue", (d, w) -> {
                            activity.sessions().setDevUnrestricted(120L);
                            activity.policy().clearLockedHome();
                            activity.policy().exitLockTask(activity);
                            activity.policy().applyActiveAndOpenHome();
                        }).show());

        activity.debugButton("RESET HOST PAIRING (DEV)", v ->
                new AlertDialog.Builder(activity)
                        .setTitle("Reset Host pairing?")
                        .setMessage("Development owner action only. The Consumer will forget the current Host and generate a new one-time QR after maintenance ends. Rental time is not changed.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Reset pairing", (d, w) -> {
                            activity.pairing().resetPairing();
                            Toast.makeText(activity, "Host trust reset", Toast.LENGTH_SHORT).show();
                            activity.render();
                        }).show());
    }

    private static void showGate(RestrictedActivity activity) {
        long cooldown = cooldownRemaining(activity);
        if (cooldown > 0L) {
            Toast.makeText(activity, "Developer Access cooling down: " + ((cooldown + 999L) / 1000L) + " sec", Toast.LENGTH_LONG).show();
            return;
        }
        EditText input = new EditText(activity);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setHint("8-digit development PIN");
        new AlertDialog.Builder(activity)
                .setTitle("Developer Access")
                .setMessage("Seven taps reveal only this authenticated owner gate. They do not unlock the rental phone or change rental time.")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Authenticate", (d, w) -> {
                    if (verifyPin(activity, input.getText().toString().trim())) {
                        activity.sessions().enterMaintenance(600L);
                        activity.render();
                    } else {
                        long left = cooldownRemaining(activity);
                        String msg = left > 0L ? "Too many failures. Developer Access locked for 60 seconds." : "Authentication failed";
                        Toast.makeText(activity, msg, Toast.LENGTH_LONG).show();
                    }
                }).show();
    }

    private static SharedPreferences prefs(Context context) {
        return context.createDeviceProtectedStorageContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String ensurePin(Context context) {
        SharedPreferences p = prefs(context);
        String pin = p.getString(PIN, "");
        if (!pin.isEmpty()) return pin;
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) sb.append(random.nextInt(10));
        pin = sb.toString();
        p.edit().putString(PIN, pin).commit();
        return pin;
    }

    private static long cooldownRemaining(Context context) {
        return Math.max(0L, prefs(context).getLong(COOLDOWN, 0L) - System.currentTimeMillis());
    }

    private static boolean verifyPin(Context context, String candidate) {
        SharedPreferences p = prefs(context);
        if (cooldownRemaining(context) > 0L) return false;
        boolean ok = ensurePin(context).equals(candidate == null ? "" : candidate);
        if (ok) {
            p.edit().putInt(FAILURES, 0).remove(COOLDOWN).commit();
            return true;
        }
        int failures = p.getInt(FAILURES, 0) + 1;
        SharedPreferences.Editor e = p.edit().putInt(FAILURES, failures);
        if (failures >= 5) e.putInt(FAILURES, 0).putLong(COOLDOWN, System.currentTimeMillis() + 60_000L);
        e.commit();
        return false;
    }
}
