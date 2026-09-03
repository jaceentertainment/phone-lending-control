package com.jace.phonelending.host;

import android.net.Uri;

import java.util.ArrayList;
import java.util.List;

public final class PairingPayload {
    public static final int PROTOCOL_VERSION = 2;

    public int protocol;
    public String deviceId;
    public String sessionId;
    public String token;
    public String consumerFingerprint;
    public String serviceName;
    public long ttlSeconds;
    public final List<Endpoint> endpoints = new ArrayList<>();

    public static final class Endpoint {
        public final String host;
        public final int port;
        public Endpoint(String host, int port) { this.host = host; this.port = port; }
    }

    public static PairingPayload parse(String raw) throws Exception {
        Uri uri = Uri.parse(raw == null ? "" : raw.trim());
        if (!"phonelending".equalsIgnoreCase(uri.getScheme()) || !"pair".equalsIgnoreCase(uri.getHost()))
            throw new IllegalArgumentException("Not a PhoneLending pairing QR");

        PairingPayload p = new PairingPayload();
        p.protocol = Integer.parseInt(require(uri, "pv"));
        p.deviceId = require(uri, "eid");
        p.sessionId = require(uri, "sid");
        p.token = require(uri, "tok");
        p.consumerFingerprint = require(uri, "pkh");
        p.serviceName = require(uri, "svc");
        try { p.ttlSeconds = Long.parseLong(uri.getQueryParameter("ttl")); }
        catch (Exception e) { p.ttlSeconds = 0L; }

        for (String ep : uri.getQueryParameters("ep")) {
            int split = ep.lastIndexOf(':');
            if (split <= 0 || split >= ep.length() - 1) continue;
            try {
                String host = ep.substring(0, split).trim();
                int port = Integer.parseInt(ep.substring(split + 1));
                if (!host.isEmpty() && port > 0 && port <= 65535) p.endpoints.add(new Endpoint(host, port));
            } catch (Exception ignored) {}
        }

        if (p.protocol != PROTOCOL_VERSION) throw new IllegalArgumentException("Unsupported pairing protocol v" + p.protocol);
        return p;
    }

    private static String require(Uri uri, String key) {
        String value = uri.getQueryParameter(key);
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException("Pairing QR missing " + key);
        return value.trim();
    }
}
