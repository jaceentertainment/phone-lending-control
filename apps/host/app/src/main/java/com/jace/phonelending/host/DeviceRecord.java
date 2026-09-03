package com.jace.phonelending.host;

import android.util.Base64;
import org.json.JSONObject;

public final class DeviceRecord {
    public String deviceId;
    public String alias;
    public String ip;
    public int port = 42424;
    public byte[] sharedKey;
    public String state = "UNKNOWN";
    public long remainingSeconds = 0L;
    public long lastSyncEpoch = 0L;
    public int protocolVersion = 1;
    public String lastMessage = "";

    public JSONObject toJson() throws Exception {
        JSONObject o = new JSONObject();
        o.put("deviceId", deviceId);
        o.put("alias", alias);
        o.put("ip", ip);
        o.put("port", port);
        o.put("sharedKey", Base64.encodeToString(sharedKey, Base64.NO_WRAP));
        o.put("state", state);
        o.put("remaining", remainingSeconds);
        o.put("lastSync", lastSyncEpoch);
        o.put("protocol", protocolVersion);
        o.put("lastMessage", lastMessage);
        return o;
    }

    public static DeviceRecord fromJson(JSONObject o) throws Exception {
        DeviceRecord r = new DeviceRecord();
        r.deviceId = o.optString("deviceId", "");
        r.alias = o.optString("alias", r.deviceId);
        r.ip = o.optString("ip", "");
        r.port = o.optInt("port", 42424);
        r.sharedKey = Base64.decode(o.optString("sharedKey", ""), Base64.DEFAULT);
        r.state = o.optString("state", "UNKNOWN");
        r.remainingSeconds = o.optLong("remaining", 0L);
        r.lastSyncEpoch = o.optLong("lastSync", 0L);
        r.protocolVersion = o.optInt("protocol", 1);
        r.lastMessage = o.optString("lastMessage", "");
        return r;
    }
}
