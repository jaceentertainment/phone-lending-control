package com.jace.phonelending.host;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class HostPrefs {
    private final SharedPreferences prefs;
    public HostPrefs(Context context) { prefs = context.getSharedPreferences("host_prefs", Context.MODE_PRIVATE); }

    public String getHostId() {
        String id = prefs.getString("hostId", "");
        if (id.isEmpty()) {
            id = "HOST-" + UUID.randomUUID();
            prefs.edit().putString("hostId", id).commit();
        }
        return id;
    }

    public boolean hasOperatorPin() { return prefs.contains("pinHash"); }

    public void setOperatorPin(String pin) {
        String salt = CryptoUtils.randomHex(16);
        prefs.edit().putString("pinSalt", salt).putString("pinHash", CryptoUtils.pinHash(salt, pin)).commit();
    }

    public boolean verifyPin(String pin) {
        String salt = prefs.getString("pinSalt", "");
        String expected = prefs.getString("pinHash", "");
        return !expected.isEmpty() && expected.equals(CryptoUtils.pinHash(salt, pin));
    }

    public List<DeviceRecord> loadDevices() {
        ArrayList<DeviceRecord> result = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs.getString("devices", "[]"));
            for (int i = 0; i < arr.length(); i++) result.add(DeviceRecord.fromJson(arr.getJSONObject(i)));
        } catch (Exception ignored) {}
        return result;
    }

    public void saveDevices(List<DeviceRecord> devices) {
        JSONArray arr = new JSONArray();
        try {
            for (DeviceRecord r : devices) arr.put(r.toJson());
            prefs.edit().putString("devices", arr.toString()).commit();
        } catch (Exception ignored) {}
    }
}
