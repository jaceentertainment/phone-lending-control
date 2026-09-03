package com.jace.phonelending.host;

import org.json.JSONObject;

public final class DeviceRecord {
    public String deviceId = "";
    public String alias = "";
    public String ip = "";
    public int port = 0;
    public String serviceName = "";
    public String consumerFingerprint = "";
    public String consumerPublicKey = "";
    public String state = "UNKNOWN";
    public long remainingSeconds = 0L;
    public long lastSyncEpoch = 0L;
    public int protocolVersion = 0;
    public String capabilities = "";
    public String sessionId = "";
    public int authorizationRevision = 0;
    public String lastMessage = "";

    public boolean isV2Trusted() {
        return protocolVersion == 2
                && !deviceId.isEmpty()
                && !consumerFingerprint.isEmpty()
                && !consumerPublicKey.isEmpty()
                && !serviceName.isEmpty();
    }

    public JSONObject toJson() throws Exception {
        JSONObject o = new JSONObject();
        o.put("deviceId", deviceId);
        o.put("alias", alias);
        o.put("ip", ip);
        o.put("port", port);
        o.put("serviceName", serviceName);
        o.put("consumerFingerprint", consumerFingerprint);
        o.put("consumerPublicKey", consumerPublicKey);
        o.put("state", state);
        o.put("remaining", remainingSeconds);
        o.put("lastSync", lastSyncEpoch);
        o.put("protocol", protocolVersion);
        o.put("capabilities", capabilities);
        o.put("sessionId", sessionId);
        o.put("authorizationRevision", authorizationRevision);
        o.put("lastMessage", lastMessage);
        return o;
    }

    public static DeviceRecord fromJson(JSONObject o) {
        DeviceRecord r = new DeviceRecord();
        r.deviceId = o.optString("deviceId", "");
        r.alias = o.optString("alias", r.deviceId);
        r.ip = o.optString("ip", "");
        r.port = o.optInt("port", 0);
        r.serviceName = o.optString("serviceName", "");
        r.consumerFingerprint = o.optString("consumerFingerprint", "");
        r.consumerPublicKey = o.optString("consumerPublicKey", "");
        r.state = o.optString("state", "UNKNOWN");
        r.remainingSeconds = o.optLong("remaining", 0L);
        r.lastSyncEpoch = o.optLong("lastSync", 0L);
        r.protocolVersion = o.optInt("protocol", 0);
        r.capabilities = o.optString("capabilities", "");
        r.sessionId = o.optString("sessionId", "");
        r.authorizationRevision = o.optInt("authorizationRevision", 0);
        r.lastMessage = o.optString("lastMessage", "");
        return r;
    }
}
