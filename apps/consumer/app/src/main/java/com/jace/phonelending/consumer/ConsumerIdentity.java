package com.jace.phonelending.consumer;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.math.BigInteger;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.util.Date;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.security.auth.x500.X500Principal;

public final class ConsumerIdentity {
    private static final String STORE = "AndroidKeyStore";
    private static final String ALIAS = "phonelending_consumer_identity_v2";
    private static final long DAY_MS = 86_400_000L;

    public ConsumerIdentity() throws Exception {
        ensureKey();
    }

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
                .setCertificateSubject(new X500Principal("CN=PhoneLending Consumer"))
                .setCertificateSerialNumber(new BigInteger(63, new SecureRandom()).add(BigInteger.ONE))
                .setCertificateNotBefore(new Date(now - DAY_MS))
                .setCertificateNotAfter(new Date(now + (3650L * DAY_MS)))
                .build();
        generator.initialize(spec);
        generator.generateKeyPair();
    }

    public PublicKey publicKey() throws Exception {
        return keyStore().getCertificate(ALIAS).getPublicKey();
    }

    public String publicKeyBase64() throws Exception {
        return Base64.encodeToString(publicKey().getEncoded(), Base64.NO_WRAP | Base64.URL_SAFE);
    }

    public String fingerprint() throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(publicKey().getEncoded());
        return Base64.encodeToString(digest, Base64.NO_WRAP | Base64.URL_SAFE | Base64.NO_PADDING);
    }

    public String sign(String data) throws Exception {
        PrivateKey key = (PrivateKey) keyStore().getKey(ALIAS, null);
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(key);
        signature.update(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return Base64.encodeToString(signature.sign(), Base64.NO_WRAP | Base64.URL_SAFE | Base64.NO_PADDING);
    }

    public SSLContext serverSslContext() throws Exception {
        KeyStore ks = keyStore();
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, null);
        SSLContext ssl = SSLContext.getInstance("TLS");
        ssl.init(kmf.getKeyManagers(), null, new SecureRandom());
        return ssl;
    }

    private static KeyStore keyStore() throws Exception {
        KeyStore ks = KeyStore.getInstance(STORE);
        ks.load(null);
        return ks;
    }
}
