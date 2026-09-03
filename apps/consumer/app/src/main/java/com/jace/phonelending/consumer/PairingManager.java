package com.jace.phonelending.consumer;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.util.Base64;

import java.security.SecureRandom;

public final class PairingManager {
    private final Context context;
    private final SharedPreferences prefs;
    private final SecureRandom random = new SecureRandom();

    public PairingManager(Context context) {
        this.context = context.createDeviceProtectedStorageContext();
        this.prefs = this.context.getSharedPreferences("pairing", Context.MODE_PRIVATE);
        ensureIdentity();
    }

    private void ensureIdentity() {
        if (!prefs.contains("deviceId")) {
            String androidId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
            String suffix = androidId == null ? randomDigits(6) : androidId.substring(Math.max(0, androidId.length() - Math.min(6, androidId.length()))).toUpperCase();
            prefs.edit().putString("deviceId", "PL-" + suffix).commit();
        }
        if (!prefs.contains("pairCode")) {
            prefs.edit().putString("pairCode", randomDigits(12)).commit();
        }
        if (!prefs.contains("devPin")) {
            prefs.edit().putString("devPin", randomDigits(8)).commit();
        }
    }

    private String randomDigits(int len) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) sb.append(random.nextInt(10));
        return sb.toString();
    }

    public String getDeviceId() { return prefs.getString("deviceId", "PL-UNKNOWN"); }
    public String getPairCode() { return prefs.getString("pairCode", ""); }
    public String getDevPin() { return prefs.getString("devPin", ""); }
    public boolean isPaired() { return prefs.getBoolean("paired", false); }
    public String getHostId() { return prefs.getString("hostId", ""); }

    public synchronized boolean pair(String hostId, String code) {
        if (hostId == null || code == null || !code.equals(getPairCode())) return false;
        try {
            byte[] key = CryptoUtils.sha256(getDeviceId() + "|" + code + "|" + hostId);
            prefs.edit()
                    .putBoolean("paired", true)
                    .putString("hostId", hostId)
                    .putString("sharedKey", Base64.encodeToString(key, Base64.NO_WRAP))
                    .remove("pairCode")
                    .commit();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public synchronized void resetPairing() {
        prefs.edit().clear().commit();
        ensureIdentity();
    }

    public synchronized long devCooldownRemainingMs() {
        long until = prefs.getLong("devCooldownUntil", 0L);
        return Math.max(0L, until - System.currentTimeMillis());
    }

    public synchronized boolean verifyDevPin(String candidate) {
        long cooldown = devCooldownRemainingMs();
        if (cooldown > 0L) return false;
        boolean ok = getDevPin().equals(candidate == null ? "" : candidate.trim());
        if (ok) {
            prefs.edit().putInt("devFailures", 0).remove("devCooldownUntil").commit();
            return true;
        }
        int failures = prefs.getInt("devFailures", 0) + 1;
        SharedPreferences.Editor e = prefs.edit().putInt("devFailures", failures);
        if (failures >= 5) {
            e.putInt("devFailures", 0).putLong("devCooldownUntil", System.currentTimeMillis() + 60_000L);
        }
        e.commit();
        return false;
    }

    public byte[] getSharedKey() {
        String value = prefs.getString("sharedKey", "");
        if (value.isEmpty()) return null;
        return Base64.decode(value, Base64.DEFAULT);
    }
}
