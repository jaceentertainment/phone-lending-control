package com.jace.phonelending.consumer;

import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class CryptoUtils {
    private CryptoUtils() {}

    public static byte[] sha256(String value) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return md.digest(value.getBytes(StandardCharsets.UTF_8));
    }

    public static String hmacBase64(byte[] key, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return Base64.encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP);
    }

    public static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
