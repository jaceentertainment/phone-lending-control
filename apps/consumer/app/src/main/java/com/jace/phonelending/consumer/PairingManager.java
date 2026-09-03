package com.jace.phonelending.consumer;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Base64;

import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.List;
import java.util.Locale;

public final class PairingManager {
    public static final int PROTOCOL_VERSION = 2;
    private static final long PAIR_TTL_MS = 120_000L;

    private final Context context;
    private final SharedPreferences prefs;
    private final SecureRandom random = new SecureRandom();
    private ConsumerIdentity identity;

    public PairingManager(Context context) {
        this.context = context.createDeviceProtectedStorageContext();
        this.prefs = this.context.getSharedPreferences("pairing_v2", Context.MODE_PRIVATE);
        ensureEnrollmentId();
    }

    public static final class PairingSession {
        public final String id;
        public final String token;
        public final long remainingSeconds;
        PairingSession(String id, String token, long remainingSeconds) {
            this.id = id;
            this.token = token;
            this.remainingSeconds = remainingSeconds;
        }
    }

    public static final class PairResult {
        public final boolean ok;
        public final String error;
        public final int authorizationRevision;
        private PairResult(boolean ok, String error, int authorizationRevision) {
            this.ok = ok;
            this.error = error;
            this.authorizationRevision = authorizationRevision;
        }
        public static PairResult ok(int revision) { return new PairResult(true, "", revision); }
        public static PairResult fail(String error) { return new PairResult(false, error, 0); }
    }

    private void ensureEnrollmentId() {
        if (!prefs.contains("deviceId")) {
            byte[] id = new byte[5];
            random.nextBytes(id);
            StringBuilder sb = new StringBuilder("PL-");
            for (byte b : id) sb.append(String.format(Locale.US, "%02X", b & 0xff));
            prefs.edit().putString("deviceId", sb.toString()).commit();
        }
    }

    private synchronized ConsumerIdentity identity() throws Exception {
        if (identity == null) identity = new ConsumerIdentity();
        return identity;
    }

    public String getDeviceId() { return prefs.getString("deviceId", "PL-UNKNOWN"); }
    public boolean isPaired() { return prefs.getBoolean("paired", false); }
    public String getHostId() { return prefs.getString("hostId", ""); }
    public String getHostPublicKeyBase64() { return prefs.getString("hostPublicKey", ""); }
    public int getAuthorizationRevision() { return prefs.getInt("authRevision", 0); }

    public String consumerFingerprint() {
        try { return identity().fingerprint(); }
        catch (Exception e) { return ""; }
    }

    public String consumerPublicKeyBase64() {
        try { return identity().publicKeyBase64(); }
        catch (Exception e) { return ""; }
    }

    public String signAsConsumer(String data) throws Exception { return identity().sign(data); }
    public javax.net.ssl.SSLContext serverSslContext() throws Exception { return identity().serverSslContext(); }

    public synchronized PairingSession getOrCreatePairingSession() {
        if (isPaired()) return null;
        long now = SystemClock.elapsedRealtime();
        int boot = readBootCount();
        String sid = prefs.getString("pairSessionId", "");
        String token = prefs.getString("pairToken", "");
        long expires = prefs.getLong("pairExpiresElapsed", 0L);
        int storedBoot = prefs.getInt("pairBootCount", -2);
        if (sid.isEmpty() || token.isEmpty() || now >= expires || storedBoot != boot) {
            sid = randomToken(16);
            token = randomToken(32);
            expires = now + PAIR_TTL_MS;
            prefs.edit()
                    .putString("pairSessionId", sid)
                    .putString("pairToken", token)
                    .putLong("pairExpiresElapsed", expires)
                    .putInt("pairBootCount", boot)
                    .commit();
        }
        return new PairingSession(sid, token, Math.max(0L, (expires - now + 999L) / 1000L));
    }

    public synchronized void rotatePairingSession() {
        prefs.edit()
                .remove("pairSessionId")
                .remove("pairToken")
                .remove("pairExpiresElapsed")
                .remove("pairBootCount")
                .commit();
    }

    public synchronized long pairingSessionRemainingSeconds() {
        if (isPaired()) return 0L;
        PairingSession session = getOrCreatePairingSession();
        return session == null ? 0L : session.remainingSeconds;
    }

    public synchronized String buildPairingUri(String serviceName, List<String> endpointHints) {
        PairingSession s = getOrCreatePairingSession();
        String fingerprint = consumerFingerprint();
        if (s == null || serviceName == null || serviceName.isEmpty() || fingerprint.isEmpty()) return "";
        Uri.Builder b = new Uri.Builder()
                .scheme("phonelending")
                .authority("pair")
                .appendQueryParameter("pv", String.valueOf(PROTOCOL_VERSION))
                .appendQueryParameter("eid", getDeviceId())
                .appendQueryParameter("sid", s.id)
                .appendQueryParameter("tok", s.token)
                .appendQueryParameter("pkh", fingerprint)
                .appendQueryParameter("svc", serviceName)
                .appendQueryParameter("ttl", String.valueOf(s.remainingSeconds));
        if (endpointHints != null) {
            for (String ep : endpointHints) if (ep != null && !ep.isEmpty()) b.appendQueryParameter("ep", ep);
        }
        return b.build().toString();
    }

    public synchronized PairResult pair(
            int protocol,
            String sessionId,
            String token,
            String hostId,
            String hostPublicKeyBase64,
            String hostNonce,
            String signatureBase64) {
        if (protocol != PROTOCOL_VERSION) return PairResult.fail("protocol_mismatch");
        if (hostId == null || hostId.isEmpty() || hostPublicKeyBase64 == null || hostPublicKeyBase64.isEmpty())
            return PairResult.fail("host_identity_missing");

        try {
            String canonical = pairCanonical(protocol, sessionId, token, hostId, hostPublicKeyBase64, hostNonce);
            PublicKey hostKey = decodeRsaPublicKey(hostPublicKeyBase64);
            if (!verify(hostKey, canonical, signatureBase64)) return PairResult.fail("host_signature_invalid");

            if (isPaired()) {
                boolean exactRetry = constantTime(getHostId(), hostId)
                        && constantTime(getHostPublicKeyBase64(), hostPublicKeyBase64)
                        && constantTime(prefs.getString("lastPairSessionId", ""), sessionId);
                return exactRetry ? PairResult.ok(getAuthorizationRevision()) : PairResult.fail("already_paired");
            }

            PairingSession current = getOrCreatePairingSession();
            if (current == null) return PairResult.fail("pairing_unavailable");
            if (!constantTime(current.id, sessionId)) return PairResult.fail("pairing_session_mismatch");
            if (!constantTime(current.token, token)) return PairResult.fail("pairing_token_invalid");
            if (current.remainingSeconds <= 0L) return PairResult.fail("pairing_token_expired");

            int revision = Math.max(1, prefs.getInt("authRevision", 0) + 1);
            prefs.edit()
                    .putBoolean("paired", true)
                    .putString("hostId", hostId)
                    .putString("hostPublicKey", hostPublicKeyBase64)
                    .putString("lastPairSessionId", sessionId)
                    .putInt("authRevision", revision)
                    .remove("pairSessionId")
                    .remove("pairToken")
                    .remove("pairExpiresElapsed")
                    .remove("pairBootCount")
                    .commit();
            return PairResult.ok(revision);
        } catch (Exception e) {
            return PairResult.fail("host_identity_invalid");
        }
    }

    public boolean verifyHostSignature(String canonical, String signatureBase64) {
        try {
            String key = getHostPublicKeyBase64();
            return !key.isEmpty() && verify(decodeRsaPublicKey(key), canonical, signatureBase64);
        } catch (Exception e) {
            return false;
        }
    }

    public synchronized void resetPairing() {
        prefs.edit()
                .putBoolean("paired", false)
                .remove("hostId")
                .remove("hostPublicKey")
                .remove("authRevision")
                .remove("lastPairSessionId")
                .remove("pairSessionId")
                .remove("pairToken")
                .remove("pairExpiresElapsed")
                .remove("pairBootCount")
                .commit();
    }

    public static String pairCanonical(int protocol, String sessionId, String token, String hostId,
                                       String hostPublicKeyBase64, String hostNonce) {
        return "PAIR_INIT|" + protocol + "|" + safe(sessionId) + "|" + safe(token) + "|" + safe(hostId)
                + "|" + safe(hostPublicKeyBase64) + "|" + safe(hostNonce);
    }

    private static PublicKey decodeRsaPublicKey(String encoded) throws Exception {
        byte[] bytes = Base64.decode(encoded, Base64.URL_SAFE | Base64.NO_WRAP);
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(bytes));
    }

    private static boolean verify(PublicKey key, String data, String encodedSignature) throws Exception {
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initVerify(key);
        signature.update(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        byte[] sig = Base64.decode(encodedSignature, Base64.URL_SAFE | Base64.NO_WRAP);
        return signature.verify(sig);
    }

    private String randomToken(int bytes) {
        byte[] value = new byte[bytes];
        random.nextBytes(value);
        return Base64.encodeToString(value, Base64.NO_WRAP | Base64.URL_SAFE | Base64.NO_PADDING);
    }

    private boolean constantTime(String a, String b) {
        if (a == null || b == null) return false;
        return MessageDigest.isEqual(
                a.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                b.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private int readBootCount() {
        try { return Settings.Global.getInt(context.getContentResolver(), Settings.Global.BOOT_COUNT); }
        catch (Exception e) { return -1; }
    }

    private static String safe(String s) { return s == null ? "" : s; }
}
