package com.jace.phonelending.host;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Date;

import javax.security.auth.x500.X500Principal;

public final class HostIdentity {
    private static final String STORE = "AndroidKeyStore";
    private static final String ALIAS = "phonelending_host_identity_v2";
    private static final long DAY_MS = 86_400_000L;

    public HostIdentity() throws Exception { ensureKey(); }

    private void ensureKey() throws Exception {
        KeyStore ks = keyStore();
        if (ks.containsAlias(ALIAS)) return;
        long now = System.currentTimeMillis();
        KeyPairGenerator generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, STORE);
        KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                .setKeySize(2048)
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                .setCertificateSubject(new X500Principal("CN=PhoneLending Host"))
                .setCertificateSerialNumber(new BigInteger(63, new SecureRandom()).add(BigInteger.ONE))
                .setCertificateNotBefore(new Date(now - DAY_MS))
                .setCertificateNotAfter(new Date(now + (3650L * DAY_MS)))
                .build();
        generator.initialize(spec);
        generator.generateKeyPair();
    }

    public String publicKeyBase64() throws Exception {
        return Base64.encodeToString(keyStore().getCertificate(ALIAS).getPublicKey().getEncoded(), Base64.NO_WRAP | Base64.URL_SAFE);
    }

    public String sign(String data) throws Exception {
        PrivateKey key = (PrivateKey) keyStore().getKey(ALIAS, null);
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(key);
        signature.update(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return Base64.encodeToString(signature.sign(), Base64.NO_WRAP | Base64.URL_SAFE | Base64.NO_PADDING);
    }

    public static boolean verify(String publicKeyBase64, String data, String encodedSignature) {
        try {
            PublicKey key = decode(publicKeyBase64);
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(key);
            signature.update(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return signature.verify(Base64.decode(encodedSignature, Base64.URL_SAFE | Base64.NO_WRAP));
        } catch (Exception e) {
            return false;
        }
    }

    public static String fingerprint(String publicKeyBase64) throws Exception {
        byte[] publicBytes = Base64.decode(publicKeyBase64, Base64.URL_SAFE | Base64.NO_WRAP);
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(publicBytes);
        return Base64.encodeToString(digest, Base64.NO_WRAP | Base64.URL_SAFE | Base64.NO_PADDING);
    }

    private static PublicKey decode(String encoded) throws Exception {
        byte[] bytes = Base64.decode(encoded, Base64.URL_SAFE | Base64.NO_WRAP);
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(bytes));
    }

    private static KeyStore keyStore() throws Exception {
        KeyStore ks = KeyStore.getInstance(STORE);
        ks.load(null);
        return ks;
    }
}
