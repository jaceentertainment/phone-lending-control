package com.jace.phonelending.host;

import android.content.Context;
import android.util.Base64;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public final class ConsumerClient {
    private static final int PROTOCOL_VERSION = 2;
    private final SecureRandom random = new SecureRandom();

    public static final class PairResult {
        public boolean ok;
        public String error = "";
        public String stage = "";
        public DeviceRecord device;
    }

    public static final class CommandResult {
        public boolean transportOk;
        public boolean accepted;
        public String commandId = "";
        public String state = "UNKNOWN";
        public long remaining;
        public String sessionId = "";
        public String message = "";
        public String ip = "";
        public int port;
    }

    private static final class ExchangeResult {
        String response;
        String ip;
        int port;
    }

    public PairResult pair(Context context, String hostId, PairingPayload payload, String alias) {
        PairResult result = new PairResult();
        try {
            result.stage = "DISCOVERY";
            List<PairingPayload.Endpoint> candidates = pairingCandidates(context, payload);
            if (candidates.isEmpty()) {
                result.error = "Rental phone not found on the local network";
                return result;
            }

            HostIdentity identity = new HostIdentity();
            String hostPublicKey = identity.publicKeyBase64();
            String hostNonce = randomToken(16);
            String canonical = pairCanonical(payload.protocol, payload.sessionId, payload.token, hostId, hostPublicKey, hostNonce);

            JSONObject request = new JSONObject();
            request.put("type", "PAIR_INIT");
            request.put("protocol", payload.protocol);
            request.put("sessionId", payload.sessionId);
            request.put("token", payload.token);
            request.put("hostId", hostId);
            request.put("hostPublicKey", hostPublicKey);
            request.put("hostNonce", hostNonce);
            request.put("signature", identity.sign(canonical));

            Exception last = null;
            for (PairingPayload.Endpoint endpoint : candidates) {
                try {
                    result.stage = "TLS_IDENTITY";
                    String responseText = exchangePinned(endpoint.host, endpoint.port, payload.consumerFingerprint, request.toString());
                    JSONObject response = new JSONObject(responseText);
                    if ("ERROR".equals(response.optString("type"))) {
                        result.stage = "PAIRING_REJECTED";
                        result.error = response.optString("message", "pairing rejected");
                        return result;
                    }
                    if (!"PAIR_ACK".equals(response.optString("type"))) throw new IllegalStateException("Unexpected pairing response");

                    result.stage = "PAIR_ACK_VERIFY";
                    if (response.optInt("protocol", 0) != PROTOCOL_VERSION) throw new SecurityException("Protocol mismatch");
                    if (!hostId.equals(response.optString("hostId"))) throw new SecurityException("Host identity mismatch");
                    if (!hostNonce.equals(response.optString("hostNonce"))) throw new SecurityException("Pairing nonce mismatch");
                    if (!payload.deviceId.equals(response.optString("deviceId"))) throw new SecurityException("Device identity mismatch");

                    String consumerPublicKey = response.optString("consumerPublicKey", "");
                    String actualFingerprint = HostIdentity.fingerprint(consumerPublicKey);
                    if (!constantTime(payload.consumerFingerprint, actualFingerprint)) throw new SecurityException("Consumer fingerprint mismatch");

                    String consumerNonce = response.optString("consumerNonce", "");
                    int revision = response.optInt("authorizationRevision", 0);
                    String state = response.optString("state", "UNKNOWN");
                    long remaining = response.optLong("remaining", 0L);
                    String sessionId = response.optString("sessionId", "");
                    String capabilities = response.optString("capabilities", "");
                    String ackCanonical = pairAckCanonical(hostId, hostNonce, consumerNonce, revision,
                            state, remaining, sessionId, consumerPublicKey, capabilities);
                    if (!HostIdentity.verify(consumerPublicKey, ackCanonical, response.optString("signature", "")))
                        throw new SecurityException("Invalid Consumer pairing acknowledgement");

                    DeviceRecord d = new DeviceRecord();
                    d.deviceId = payload.deviceId;
                    d.alias = alias == null || alias.trim().isEmpty() ? payload.deviceId : alias.trim();
                    d.ip = endpoint.host;
                    d.port = endpoint.port;
                    d.serviceName = payload.serviceName;
                    d.consumerFingerprint = payload.consumerFingerprint;
                    d.consumerPublicKey = consumerPublicKey;
                    d.state = state;
                    d.remainingSeconds = remaining;
                    d.sessionId = sessionId;
                    d.protocolVersion = PROTOCOL_VERSION;
                    d.capabilities = capabilities;
                    d.authorizationRevision = revision;
                    d.lastSyncEpoch = System.currentTimeMillis();
                    d.lastMessage = "paired / Consumer ACK verified";
                    result.ok = true;
                    result.stage = "COMPLETE";
                    result.device = d;
                    return result;
                } catch (Exception e) {
                    last = e;
                }
            }
            result.error = last == null ? "Unable to connect" : last.getClass().getSimpleName() + ": " + safeMessage(last);
        } catch (Exception e) {
            result.error = e.getClass().getSimpleName() + ": " + safeMessage(e);
        }
        return result;
    }

    public CommandResult command(Context context, String hostId, DeviceRecord device, String command, String payload) {
        CommandResult result = new CommandResult();
        result.commandId = UUID.randomUUID().toString();
        if (!device.isV2Trusted()) {
            result.message = "re_pair_required";
            return result;
        }
        try {
            HostIdentity identity = new HostIdentity();
            long issuedAt = System.currentTimeMillis();
            String nonce = randomToken(16);
            String sessionId = ("EXTEND".equals(command) || "END".equals(command)) ? device.sessionId : "";
            String canonical = commandCanonical(PROTOCOL_VERSION, result.commandId, hostId, device.deviceId,
                    sessionId, issuedAt, nonce, command, payload);

            JSONObject request = new JSONObject();
            request.put("type", "CMD");
            request.put("protocol", PROTOCOL_VERSION);
            request.put("commandId", result.commandId);
            request.put("hostId", hostId);
            request.put("target", device.deviceId);
            request.put("sessionId", sessionId);
            request.put("issuedAt", issuedAt);
            request.put("nonce", nonce);
            request.put("command", command);
            request.put("payload", payload == null ? "" : payload);
            request.put("signature", identity.sign(canonical));

            ExchangeResult exchange = exchangeDevice(context, device, request.toString());
            result.transportOk = true;
            result.ip = exchange.ip;
            result.port = exchange.port;

            JSONObject response = new JSONObject(exchange.response);
            if (!"ACK".equals(response.optString("type"))) {
                result.accepted = false;
                result.message = response.optString("message", "unexpected_response");
                return result;
            }
            if (!result.commandId.equals(response.optString("commandId"))) throw new SecurityException("ACK command mismatch");
            if (!nonce.equals(response.optString("nonce"))) throw new SecurityException("ACK nonce mismatch");

            boolean accepted = response.optBoolean("accepted", false);
            String state = response.optString("state", "UNKNOWN");
            long remaining = response.optLong("remaining", 0L);
            String responseSessionId = response.optString("sessionId", "");
            String message = response.optString("message", "");
            String ackCanonical = ackCanonical(result.commandId, nonce, accepted, state, remaining, responseSessionId, message);
            if (!HostIdentity.verify(device.consumerPublicKey, ackCanonical, response.optString("signature", "")))
                throw new SecurityException("Invalid Consumer acknowledgement signature");

            result.accepted = accepted;
            result.state = state;
            result.remaining = remaining;
            result.sessionId = responseSessionId;
            result.message = message;
        } catch (Exception e) {
            result.transportOk = false;
            result.accepted = false;
            result.message = e.getClass().getSimpleName() + ": " + safeMessage(e);
        }
        return result;
    }

    private ExchangeResult exchangeDevice(Context context, DeviceRecord device, String line) throws Exception {
        Exception first = null;
        if (device.ip != null && !device.ip.isEmpty() && device.port > 0) {
            try {
                ExchangeResult r = new ExchangeResult();
                r.response = exchangePinned(device.ip, device.port, device.consumerFingerprint, line);
                r.ip = device.ip;
                r.port = device.port;
                return r;
            } catch (Exception e) { first = e; }
        }

        PairingPayload.Endpoint discovered = NsdDiscovery.resolve(context, device.serviceName, 3500L);
        if (discovered != null) {
            ExchangeResult r = new ExchangeResult();
            r.response = exchangePinned(discovered.host, discovered.port, device.consumerFingerprint, line);
            r.ip = discovered.host;
            r.port = discovered.port;
            return r;
        }
        if (first != null) throw first;
        throw new IllegalStateException("Consumer service not discovered");
    }

    private List<PairingPayload.Endpoint> pairingCandidates(Context context, PairingPayload payload) {
        ArrayList<PairingPayload.Endpoint> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        PairingPayload.Endpoint discovered = NsdDiscovery.resolve(context, payload.serviceName, 4500L);
        if (discovered != null) addCandidate(result, seen, discovered);
        for (PairingPayload.Endpoint e : payload.endpoints) addCandidate(result, seen, e);
        return result;
    }

    private void addCandidate(List<PairingPayload.Endpoint> result, Set<String> seen, PairingPayload.Endpoint e) {
        String key = e.host + ":" + e.port;
        if (seen.add(key)) result.add(e);
    }

    private String exchangePinned(String ip, int port, String expectedFingerprint, String line) throws Exception {
        X509TrustManager trust = new X509TrustManager() {
            @Override public void checkClientTrusted(X509Certificate[] chain, String authType) throws java.security.cert.CertificateException {
                throw new java.security.cert.CertificateException("Client trust not used");
            }

            @Override public void checkServerTrusted(X509Certificate[] chain, String authType) throws java.security.cert.CertificateException {
                if (chain == null || chain.length == 0) throw new java.security.cert.CertificateException("Missing Consumer certificate");
                try {
                    byte[] digest = MessageDigest.getInstance("SHA-256").digest(chain[0].getPublicKey().getEncoded());
                    String actual = Base64.encodeToString(digest, Base64.NO_WRAP | Base64.URL_SAFE | Base64.NO_PADDING);
                    if (!constantTime(expectedFingerprint, actual)) throw new java.security.cert.CertificateException("Consumer identity mismatch");
                } catch (java.security.cert.CertificateException e) {
                    throw e;
                } catch (Exception e) {
                    throw new java.security.cert.CertificateException(e);
                }
            }

            @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
        };

        SSLContext ssl = SSLContext.getInstance("TLS");
        ssl.init(null, new TrustManager[]{trust}, new SecureRandom());
        try (Socket raw = new Socket()) {
            raw.connect(new InetSocketAddress(ip, port), 4500);
            try (SSLSocket socket = (SSLSocket) ssl.getSocketFactory().createSocket(raw, ip, port, true)) {
                socket.setSoTimeout(7000);
                socket.startHandshake();
                BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                out.write(line);
                out.write("\n");
                out.flush();
                String response = in.readLine();
                if (response == null) throw new IllegalStateException("No response from Consumer");
                return response.trim();
            }
        }
    }

    private String randomToken(int bytes) {
        byte[] value = new byte[bytes];
        random.nextBytes(value);
        return Base64.encodeToString(value, Base64.NO_WRAP | Base64.URL_SAFE | Base64.NO_PADDING);
    }

    private static boolean constantTime(String a, String b) {
        if (a == null || b == null) return false;
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private static String pairCanonical(int protocol, String sessionId, String token, String hostId,
                                        String hostPublicKeyBase64, String hostNonce) {
        return "PAIR_INIT|" + protocol + "|" + safe(sessionId) + "|" + safe(token) + "|" + safe(hostId)
                + "|" + safe(hostPublicKeyBase64) + "|" + safe(hostNonce);
    }

    private static String pairAckCanonical(String hostId, String hostNonce, String consumerNonce, int revision,
                                           String state, long remaining, String sessionId, String consumerPublicKey,
                                           String capabilities) {
        return "PAIR_ACK|" + PROTOCOL_VERSION + "|" + safe(hostId) + "|" + safe(hostNonce) + "|"
                + safe(consumerNonce) + "|" + revision + "|" + safe(state) + "|" + remaining + "|"
                + safe(sessionId) + "|" + safe(consumerPublicKey) + "|" + safe(capabilities);
    }

    private static String commandCanonical(int protocol, String commandId, String hostId, String target,
                                           String sessionId, long issuedAt, String nonce, String command, String payload) {
        return "CMD|" + protocol + "|" + safe(commandId) + "|" + safe(hostId) + "|" + safe(target)
                + "|" + safe(sessionId) + "|" + issuedAt + "|" + safe(nonce) + "|" + safe(command) + "|" + safe(payload);
    }

    private static String ackCanonical(String commandId, String nonce, boolean accepted, String state,
                                       long remaining, String sessionId, String message) {
        return "ACK|" + PROTOCOL_VERSION + "|" + safe(commandId) + "|" + safe(nonce) + "|" + accepted
                + "|" + safe(state) + "|" + remaining + "|" + safe(sessionId) + "|" + safe(message);
    }

    private static String safe(String s) { return s == null ? "" : s; }
    private static String safeMessage(Exception e) { return e.getMessage() == null ? "unknown" : e.getMessage(); }
}
