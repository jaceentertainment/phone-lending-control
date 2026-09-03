package com.jace.phonelending.consumer;

import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.security.SecureRandom;

/** FIELD-TEST SOURCE SET ONLY. Never production. */
public final class DebugRecoveryHooks {
    private static final String PREFS = "field_recovery";
    private static final String PIN = "fieldPin";
    private static final String FAILURES = "fieldFailures";
    private static final String COOLDOWN = "fieldCooldownUntil";
    private static final String PROVISIONING_ACK = "provisioningPinAcknowledged";

    private DebugRecoveryHooks() {}

    public static void augmentUnprovisioned(MainActivity activity) {
        String pin = ensurePin(activity);
        activity.debugWarning("FIELD-TEST BUILD — NOT PRODUCTION");
        activity.debugLabelValue("Current-install recovery PIN", pin);
        activity.debugBody("Normal APK installation is supported. If this exact installation is provisioned without a factory reset, this PIN remains valid. If you factory-reset for QR managed provisioning, Android erases this installation and this PIN. PhoneLending will generate and show a NEW post-reset recovery PIN during managed provisioning before restricted kiosk policy is applied.");
    }

    /**
     * Intercepts admin-integrated provisioning only for the field-test source set.
     * The credential is generated after the managed APK is installed on the
     * freshly reset device, then shown before restricted HOME is committed.
     */
    public static boolean showProvisioningRecoveryGate(
            PolicyComplianceActivity activity,
            Runnable onConfirmed) {
        SharedPreferences p = prefs(activity);
        if (p.getBoolean(PROVISIONING_ACK, false)) return false;

        String pin = ensurePin(activity);
        int density = Math.round(activity.getResources().getDisplayMetrics().density);
        int side = 24 * density;

        ScrollView scroll = new ScrollView(activity);
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(side, 28 * density, side, 28 * density);
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = text(activity, "PhoneLending Owner Setup", 24, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrap());

        TextView warning = text(activity, "FIELD-TEST RECOVERY CREDENTIAL", 18, Typeface.BOLD);
        warning.setGravity(Gravity.CENTER);
        warning.setPadding(0, 22 * density, 0, 12 * density);
        root.addView(warning, matchWrap());

        TextView body = text(activity,
                "Record this NEW recovery PIN now. It belongs to the post-reset managed installation. PhoneLending will not apply its restricted HOME/kiosk policy until you acknowledge this step.",
                16,
                Typeface.NORMAL);
        body.setGravity(Gravity.CENTER);
        body.setPadding(0, 8 * density, 0, 16 * density);
        root.addView(body, matchWrap());

        TextView pinView = text(activity, pin, 34, Typeface.BOLD);
        pinView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        pinView.setTextIsSelectable(true);
        pinView.setGravity(Gravity.CENTER);
        pinView.setPadding(12 * density, 18 * density, 12 * density, 18 * density);
        root.addView(pinView, matchWrap());

        TextView fallback = text(activity,
                "Independent field-test fallback: a factory reset remains available if software recovery fails. This PIN is test-only and is not the production recovery design.",
                14,
                Typeface.NORMAL);
        fallback.setGravity(Gravity.CENTER);
        fallback.setPadding(0, 14 * density, 0, 20 * density);
        root.addView(fallback, matchWrap());

        Button confirm = new Button(activity);
        confirm.setText("I HAVE RECORDED THIS PIN");
        confirm.setAllCaps(false);
        confirm.setTextSize(15);
        confirm.setOnClickListener(v -> new AlertDialog.Builder(activity)
                .setTitle("Continue managed setup?")
                .setMessage("After continuing, PhoneLending will enter its managed restricted setup state. Keep the recovery PIN somewhere available to the owner/technician.")
                .setNegativeButton("Go back", null)
                .setPositiveButton("Continue", (d, w) -> {
                    p.edit().putBoolean(PROVISIONING_ACK, true).commit();
                    onConfirmed.run();
                })
                .show());
        LinearLayout.LayoutParams buttonLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                56 * density);
        buttonLp.setMargins(0, 8 * density, 0, 8 * density);
        root.addView(confirm, buttonLp);

        activity.setContentView(scroll);
        return true;
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
            prefs(activity).edit().putBoolean(PROVISIONING_ACK, false).commit();
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

    private static TextView text(Context context, String value, int sizeSp, int style) {
        TextView t = new TextView(context);
        t.setText(value);
        t.setTextSize(sizeSp);
        t.setTextColor(0xFF111111);
        t.setTypeface(Typeface.DEFAULT, style);
        return t;
    }

    private static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}
