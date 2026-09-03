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
        if (!prefs.contains("pairCode") && !prefs.getBoolean("paired", false)) {
            prefs.edit().putString("pairCode", randomDigits(12)).commit();
        }
    }

    private String randomDigits(int len) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) sb.append(random.nextInt(10));
        return sb.toString();
    }

    public String getDeviceId() { return prefs.getString("deviceId", "PL-UNKNOWN"); }
    public String getPairCode() { return prefs.getString("pairCode", ""); }
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

    public byte[] getSharedKey() {
        String value = prefs.getString("sharedKey", "");
        if (value.isEmpty()) return null;
        return Base64.decode(value, Base64.DEFAULT);
    }
}
