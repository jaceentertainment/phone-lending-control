package com.jace.phonelending.host;

import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class CryptoUtils {
    private CryptoUtils() {}

    public static byte[] sha256(String value) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
    }

    public static String hmacBase64(byte[] key, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return Base64.encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP);
    }

    public static String randomHex(int bytes) {
        byte[] b = new byte[bytes];
        new SecureRandom().nextBytes(b);
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format(java.util.Locale.US, "%02x", x & 0xff));
        return sb.toString();
    }

    public static String pinHash(String salt, String pin) {
        try {
            return Base64.encodeToString(sha256(salt + "|" + pin), Base64.NO_WRAP);
        } catch (Exception e) { return ""; }
    }
}
