package com.jace.phonelending.consumer;

import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.InputType;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.security.SecureRandom;

/** FIELD-TEST SOURCE SET ONLY. Never production. */
public final class DebugRecoveryHooks {
    private static final String PREFS = "field_recovery";
    private static final String PIN = "fieldPin";
    private static final String FAILURES = "fieldFailures";
    private static final String COOLDOWN = "fieldCooldownUntil";

    private DebugRecoveryHooks() {}

    public static void augmentUnprovisioned(MainActivity activity) {
        String pin = ensurePin(activity);
        activity.debugWarning("FIELD-TEST BUILD — NOT PRODUCTION");
        activity.debugLabelValue("Field recovery PIN", pin);
        activity.debugBody("Normal APK installation is supported. Full Device Owner / locking behavior still requires Android managed provisioning on a fresh or factory-reset test phone. Record this device-specific PIN before managed-device testing.");
    }

    public static void attachFooter(MainActivity activity, TextView footer) {
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

    public static void augmentMaintenance(MainActivity activity) {
        if (activity.sessions().isDevUnrestricted()) {
            activity.debugWarning("FIELD-TEST TEMPORARY OWNER ACCESS ACTIVE");
            return;
        }

        activity.debugWarning("FIELD-TEST OWNER RECOVERY");
        activity.debugButton("TEMPORARILY EXIT KIOSK (2 MIN)", v ->
                new AlertDialog.Builder(activity)
                        .setTitle("Temporary field-test access")
                        .setMessage("Kiosk restrictions will be relaxed for up to two minutes. The authoritative rental state is not changed and will be reconciled automatically.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Continue", (d, w) -> {
                            activity.sessions().setDevUnrestricted(120L);
                            activity.policy().clearLockedHome();
                            activity.policy().exitLockTask(activity);
                            activity.policy().applyActiveAndOpenHome();
                        }).show());

        if (activity.policy().isDeviceOwner()) {
            activity.debugButton("FIELD TEST: REMOVE DEVICE OWNER", v ->
                    new AlertDialog.Builder(activity)
                            .setTitle("Remove field-test Device Owner?")
                            .setMessage("Testing-only recovery. This ends PhoneLending management on this phone, clears pairing, and cannot be undone without provisioning the device again. Some Android policies may require a factory reset to fully clear.")
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton("Remove Device Owner", (d, w) -> clearFieldOwner(activity))
                            .show());
        }
    }

    @SuppressWarnings("deprecation")
    private static void clearFieldOwner(MainActivity activity) {
        try {
            activity.policy().clearLockedHome();
            activity.policy().exitLockTask(activity);
            activity.pairing().resetPairing();
            activity.sessions().setState(SessionStore.UNPROVISIONED);
            DevicePolicyManager dpm = (DevicePolicyManager) activity.getSystemService(Context.DEVICE_POLICY_SERVICE);
            if (dpm == null || !dpm.isDeviceOwnerApp(activity.getPackageName())) {
                Toast.makeText(activity, "Field Device Owner is not active", Toast.LENGTH_LONG).show();
                activity.render();
                return;
            }
            dpm.clearDeviceOwnerApp(activity.getPackageName());
            Toast.makeText(activity, "Field Device Owner removed", Toast.LENGTH_LONG).show();
            activity.render();
        } catch (Throwable t) {
            Toast.makeText(activity, "Could not remove Device Owner. Use factory reset as the independent field-test recovery path.", Toast.LENGTH_LONG).show();
        }
    }

    private static void showGate(MainActivity activity) {
        long cooldown = cooldownRemaining(activity);
        if (cooldown > 0L) {
            Toast.makeText(activity, "Owner recovery cooling down: " + ((cooldown + 999L) / 1000L) + " sec", Toast.LENGTH_LONG).show();
            return;
        }
        EditText input = new EditText(activity);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setHint("8-digit field recovery PIN");
        new AlertDialog.Builder(activity)
                .setTitle("Field-Test Owner Recovery")
                .setMessage("Seven taps reveal only this authenticated gate; they do not unlock the device.")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Authenticate", (d, w) -> {
                    if (verifyPin(activity, input.getText().toString().trim())) {
                        activity.sessions().enterMaintenance(600L);
                        activity.render();
                    } else {
                        long left = cooldownRemaining(activity);
                        String msg = left > 0L ? "Too many failures. Owner recovery locked for 60 seconds." : "Authentication failed";
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
