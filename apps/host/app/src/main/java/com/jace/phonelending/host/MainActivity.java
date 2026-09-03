package com.jace.phonelending.host;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int REQUEST_SCAN_QR = 7201;
    private static final int REQUEST_LOCAL_NETWORK = 7202;

    private HostPrefs prefs;
    private final ConsumerClient client = new ConsumerClient();
    private final ExecutorService io = Executors.newFixedThreadPool(4);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private List<DeviceRecord> devices = new ArrayList<>();
    private boolean unlocked = false;
    private LinearLayout root;
    private String screenMode = "lock";
    private DeviceRecord currentDevice = null;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new HostPrefs(this);
        devices = prefs.loadDevices();
        if (!prefs.hasOperatorPin()) showCreatePin();
        else showUnlock();
        handler.post(clockTick);
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        io.shutdownNow();
        super.onDestroy();
    }

    private final Runnable clockTick = new Runnable() {
        @Override public void run() {
            if (unlocked && "dashboard".equals(screenMode)) showDashboard(false);
            else if (unlocked && "device".equals(screenMode) && currentDevice != null) showDevice(currentDevice);
            handler.postDelayed(this, 1000L);
        }
    };

    private void showCreatePin() {
        unlocked = false;
        screenMode = "setup";
        page();
        title("PhoneLending Host");
        warning("DEVELOPMENT BUILD — NOT FOR PRODUCTION RENTAL");
        body("Create an operator PIN. Host controls are requests only; the rental phone remains authoritative until it signs and acknowledges a command.");
        EditText pin = input("Create 6+ digit operator PIN", true);
        EditText confirm = input("Confirm operator PIN", true);
        button("CREATE OPERATOR PIN", v -> {
            String a = pin.getText().toString().trim();
            String b = confirm.getText().toString().trim();
            if (a.length() < 6 || !a.equals(b)) {
                Toast.makeText(this, "PINs must match and be at least 6 digits", Toast.LENGTH_SHORT).show();
                return;
            }
            prefs.setOperatorPin(a);
            showUnlock();
        });
    }

    private void showUnlock() {
        unlocked = false;
        screenMode = "lock";
        currentDevice = null;
        page();
        title("PhoneLending Host");
        section("OPERATOR ACCESS");
        EditText pin = input("Operator PIN", true);
        button("UNLOCK HOST", v -> {
            if (prefs.verifyPin(pin.getText().toString().trim())) {
                unlocked = true;
                showDashboard(true);
            } else Toast.makeText(this, "Incorrect operator PIN", Toast.LENGTH_SHORT).show();
        });
        body("Host ID: " + prefs.getHostId());
    }

    private void showDashboard(boolean refreshNow) {
        if (!unlocked) return;
        screenMode = "dashboard";
        currentDevice = null;
        page();
        title("PhoneLending Host");
        warning("DEVELOPMENT FLEET ONLY");

        int active = 0, ready = 0, expired = 0, attention = 0, repair = 0;
        for (DeviceRecord d : devices) {
            if (!d.isV2Trusted()) repair++;
            else if ("ACTIVE".equals(d.state)) active++;
            else if ("AVAILABLE_LOCKED".equals(d.state)) ready++;
            else if ("EXPIRED_LOCKED".equals(d.state)) expired++;
            else if ("RECOVERY_LOCKED".equals(d.state)) attention++;
        }
        body(devices.size() + " devices   |   " + active + " active   |   " + ready + " ready   |   "
                + expired + " expired   |   " + attention + " attention" + (repair > 0 ? "   |   " + repair + " re-pair" : ""));

        button("ADD RENTAL DEVICE — SCAN QR", v -> launchQrScanner());
        button("REFRESH ALL", v -> refreshAll());
        for (DeviceRecord d : devices) renderCard(d);
        if (devices.isEmpty()) body("No rental devices paired yet. Provision a Consumer as Device Owner, then scan the QR shown on that rental phone.");
        button("ADVANCED / TROUBLESHOOTING", v -> showAdvancedPairing());
        button("LOCK HOST", v -> showUnlock());
        if (refreshNow) refreshAll();
    }

    private void renderCard(DeviceRecord d) {
        section((d.alias == null || d.alias.isEmpty() ? d.deviceId : d.alias) + "  (" + d.deviceId + ")");
        if (!d.isV2Trusted()) {
            warning("RE-PAIR REQUIRED");
            body("This is a legacy Batch-1 record and is not trusted by QR protocol v2.");
        } else {
            labelValue("Status", d.state);
            labelValue("Remaining", displayTimer(d));
            labelValue("Last confirmed", d.lastSyncEpoch == 0 ? "never" : ageText(d.lastSyncEpoch));
            labelValue("Connection", d.ip.isEmpty() ? "discover on demand" : d.ip + ":" + d.port + " (last known)");
        }
        button("OPEN DEVICE", v -> showDevice(d));
    }

    private void showDevice(DeviceRecord d) {
        if (!unlocked) return;
        screenMode = "device";
        currentDevice = d;
        page();
        title(d.alias == null || d.alias.isEmpty() ? d.deviceId : d.alias);
        body(d.deviceId);

        if (!d.isV2Trusted()) {
            warning("RE-PAIR REQUIRED");
            body("This record came from the older manual pairing system. For safety it cannot send v0.4 management commands.");
            button("FORGET LOCAL RECORD", v -> forgetDevice(d));
            button("BACK TO DASHBOARD", v -> showDashboard(false));
            return;
        }

        labelValue("State", d.state);
        labelValue("Remaining", displayTimer(d));
        labelValue("Last confirmed", d.lastSyncEpoch == 0 ? "never" : ageText(d.lastSyncEpoch));
        labelValue("Protocol", "v" + d.protocolVersion);
        labelValue("Service", d.serviceName);
        labelValue("Endpoint", d.ip.isEmpty() ? "discover on demand" : d.ip + ":" + d.port + " (last known)");
        if (!d.lastMessage.isEmpty()) labelValue("Last result", d.lastMessage);

        button("REFRESH STATUS", v -> request(d, "STATUS", "", "Refreshing status..."));

        if ("AVAILABLE_LOCKED".equals(d.state)) {
            section("START TEST RENTAL");
            if (supports(d, "start")) {
                button("15 MINUTES", v -> request(d, "START", "900", "Starting rental..."));
                button("30 MINUTES", v -> request(d, "START", "1800", "Starting rental..."));
                button("1 HOUR", v -> request(d, "START", "3600", "Starting rental..."));
                button("CUSTOM DURATION", v -> customStart(d));
            } else body("START is not supported by this Consumer version.");
        } else if ("ACTIVE".equals(d.state)) {
            section("ACTIVE RENTAL");
            if (supports(d, "extend")) {
                button("EXTEND +15 MIN", v -> request(d, "EXTEND", "900", "Requesting extension..."));
                button("EXTEND +30 MIN", v -> request(d, "EXTEND", "1800", "Requesting extension..."));
            }
            if (supports(d, "end")) button("END & LOCK NOW", v -> confirmEnd(d));
        } else if ("EXPIRED_LOCKED".equals(d.state)) {
            body("This development build does not claim renter-data turnover is production-complete. PREPARE only resets the test-session state.");
            if (supports(d, "prepare")) button("PREPARE TEST DEVICE", v -> request(d, "PREPARE", "", "Preparing test state..."));
            if (supports(d, "maintenance")) button("OWNER MAINTENANCE", v -> request(d, "MAINTENANCE", "", "Requesting maintenance..."));
        } else if ("RECOVERY_LOCKED".equals(d.state)) {
            warning("OWNER ATTENTION REQUIRED");
            if (supports(d, "maintenance")) button("OWNER MAINTENANCE", v -> request(d, "MAINTENANCE", "", "Requesting recovery maintenance..."));
        } else if ("ADMIN_MAINTENANCE".equals(d.state)) {
            if (supports(d, "relock")) button("END MAINTENANCE / RELOCK", v -> request(d, "RELOCK", "", "Relocking..."));
        }
        button("BACK TO DASHBOARD", v -> showDashboard(false));
    }

    private void launchQrScanner() {
        if (Build.VERSION.SDK_INT >= 37 && checkSelfPermission("android.permission.ACCESS_LOCAL_NETWORK") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.ACCESS_LOCAL_NETWORK"}, REQUEST_LOCAL_NETWORK);
        }
        startActivityForResult(new Intent(this, QrScannerActivity.class), REQUEST_SCAN_QR);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SCAN_QR && resultCode == RESULT_OK && data != null) {
            String raw = data.getStringExtra(QrScannerActivity.EXTRA_PAYLOAD);
            pairScannedPayload(raw, null);
        }
    }

    private void pairScannedPayload(String raw, String endpointOverride) {
        if (!unlocked) return;
        PairingPayload payload;
        try {
            payload = PairingPayload.parse(raw);
            if (endpointOverride != null && !endpointOverride.trim().isEmpty()) {
                PairingPayload.Endpoint ep = parseEndpoint(endpointOverride.trim());
                payload.endpoints.add(0, ep);
            }
        } catch (Exception e) {
            new AlertDialog.Builder(this).setTitle("Invalid pairing QR").setMessage(e.getMessage()).setPositiveButton("OK", null).show();
            return;
        }

        screenMode = "pairing";
        page();
        title("Pairing Rental Device");
        section(payload.deviceId);
        body("Finding the rental phone, verifying its cryptographic identity, and waiting for the Consumer to acknowledge Host trust...");
        labelValue("Status", "PAIRING / PENDING CONSUMER ACK");

        io.execute(() -> {
            ConsumerClient.PairResult result = client.pair(this, prefs.getHostId(), payload, payload.deviceId);
            handler.post(() -> {
                if (result.ok) {
                    removeRecordWithId(result.device.deviceId);
                    devices.add(result.device);
                    prefs.saveDevices(devices);
                    promptAlias(result.device);
                } else {
                    new AlertDialog.Builder(this)
                            .setTitle("Pairing failed")
                            .setMessage("Stage: " + result.stage + "\n\n" + result.error
                                    + "\n\nMake sure both phones are on the same local network and the Consumer QR is still current.")
                            .setPositiveButton("BACK", (d, w) -> showDashboard(false))
                            .show();
                }
            });
        });
    }

    private void promptAlias(DeviceRecord device) {
        EditText alias = dialogInput("Device name (example: Phone #07)", false);
        alias.setText(device.deviceId);
        new AlertDialog.Builder(this)
                .setTitle("Device paired")
                .setMessage("Consumer identity and acknowledgement verified. Give this rental phone a friendly name.")
                .setView(alias)
                .setCancelable(false)
                .setPositiveButton("SAVE", (d, w) -> {
                    String value = alias.getText().toString().trim();
                    device.alias = value.isEmpty() ? device.deviceId : value;
                    prefs.saveDevices(devices);
                    Toast.makeText(this, "Rental device paired", Toast.LENGTH_SHORT).show();
                    showDevice(device);
                }).show();
    }

    private void showAdvancedPairing() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        EditText payload = dialogInput("PhoneLending QR payload", false);
        EditText endpoint = dialogInput("Optional IP:port override", false);
        box.addView(payload);
        box.addView(endpoint);
        new AlertDialog.Builder(this)
                .setTitle("Advanced pairing")
                .setMessage("Troubleshooting only. Security is NOT bypassed: a valid one-time QR token and Consumer fingerprint are still required.")
                .setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("PAIR", (d, w) -> pairScannedPayload(payload.getText().toString().trim(), endpoint.getText().toString().trim()))
                .show();
    }

    private PairingPayload.Endpoint parseEndpoint(String raw) {
        int split = raw.lastIndexOf(':');
        if (split <= 0) throw new IllegalArgumentException("Endpoint must be IP:port");
        int port = Integer.parseInt(raw.substring(split + 1));
        if (port < 1 || port > 65535) throw new IllegalArgumentException("Invalid port");
        return new PairingPayload.Endpoint(raw.substring(0, split).trim(), port);
    }

    private void customStart(DeviceRecord d) {
        EditText minutes = dialogInput("Minutes (1-1440)", true);
        new AlertDialog.Builder(this)
                .setTitle("Custom test rental")
                .setView(minutes)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Start", (x, y) -> {
                    try {
                        long m = Long.parseLong(minutes.getText().toString().trim());
                        if (m < 1 || m > 1440) throw new NumberFormatException();
                        request(d, "START", String.valueOf(m * 60L), "Starting rental...");
                    } catch (Exception e) { Toast.makeText(this, "Enter 1-1440 minutes", Toast.LENGTH_SHORT).show(); }
                }).show();
    }

    private void confirmEnd(DeviceRecord d) {
        new AlertDialog.Builder(this)
                .setTitle("End and lock rental now?")
                .setMessage("The Host will request immediate expiration. Success will only be shown after the Consumer signs and acknowledges the resulting state.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("END & LOCK", (x, y) -> request(d, "END", "", "Ending and locking..."))
                .show();
    }

    private void request(DeviceRecord d, String command, String payload, String pendingText) {
        d.lastMessage = "PENDING " + command;
        prefs.saveDevices(devices);
        Toast.makeText(this, pendingText, Toast.LENGTH_SHORT).show();
        showDevice(d);
        io.execute(() -> {
            ConsumerClient.CommandResult r = client.command(this, prefs.getHostId(), d, command, payload);
            handler.post(() -> {
                if (r.transportOk) {
                    d.state = r.state;
                    d.remainingSeconds = r.remaining;
                    d.sessionId = r.sessionId;
                    d.lastSyncEpoch = System.currentTimeMillis();
                    if (!r.ip.isEmpty()) { d.ip = r.ip; d.port = r.port; }
                    d.lastMessage = (r.accepted ? "ACK " : "REJECTED ") + command + ": " + r.message;
                    Toast.makeText(this, r.accepted ? "Consumer acknowledged: " + r.state : "Consumer rejected: " + r.message,
                            r.accepted ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG).show();
                } else {
                    d.lastMessage = "OUTCOME UNKNOWN / OFFLINE: " + r.message;
                    Toast.makeText(this, "Consumer unreachable. Command outcome is unknown; authoritative state will be reconciled on reconnect.", Toast.LENGTH_LONG).show();
                }
                prefs.saveDevices(devices);
                showDevice(d);
            });
        });
    }

    private void refreshAll() {
        if (devices.isEmpty()) return;
        for (DeviceRecord d : devices) {
            if (!d.isV2Trusted()) continue;
            io.execute(() -> {
                ConsumerClient.CommandResult r = client.command(this, prefs.getHostId(), d, "STATUS", "");
                handler.post(() -> {
                    if (r.transportOk) {
                        d.state = r.state;
                        d.remainingSeconds = r.remaining;
                        d.sessionId = r.sessionId;
                        d.lastSyncEpoch = System.currentTimeMillis();
                        if (!r.ip.isEmpty()) { d.ip = r.ip; d.port = r.port; }
                        d.lastMessage = "status confirmed / signed ACK";
                    } else d.lastMessage = "offline / stale";
                    prefs.saveDevices(devices);
                    if ("dashboard".equals(screenMode)) showDashboard(false);
                });
            });
        }
    }

    private boolean supports(DeviceRecord d, String capability) {
        if (d.capabilities == null) return false;
        for (String x : d.capabilities.split(",")) if (capability.equals(x.trim())) return true;
        return false;
    }

    private void forgetDevice(DeviceRecord d) {
        new AlertDialog.Builder(this)
                .setTitle("Forget local record?")
                .setMessage("This only removes the record from this Host. It does not remotely change Consumer trust or rental state.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("FORGET", (x, y) -> {
                    devices.remove(d);
                    prefs.saveDevices(devices);
                    showDashboard(false);
                }).show();
    }

    private void removeRecordWithId(String deviceId) {
        Iterator<DeviceRecord> it = devices.iterator();
        while (it.hasNext()) if (deviceId.equals(it.next().deviceId)) it.remove();
    }

    private String displayTimer(DeviceRecord d) {
        if (!"ACTIVE".equals(d.state)) return formatDuration(d.remainingSeconds);
        if (d.lastSyncEpoch <= 0L) return "unknown";
        long elapsed = Math.max(0L, (System.currentTimeMillis() - d.lastSyncEpoch) / 1000L);
        long estimated = Math.max(0L, d.remainingSeconds - elapsed);
        boolean stale = System.currentTimeMillis() - d.lastSyncEpoch > 10_000L;
        return (stale ? "~" : "") + formatDuration(estimated) + (stale ? " estimated" : "");
    }

    private String ageText(long epoch) {
        long s = Math.max(0L, (System.currentTimeMillis() - epoch) / 1000L);
        if (s < 2) return "just now";
        if (s < 60) return s + " sec ago";
        return (s / 60) + " min ago";
    }

    private void page() {
        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(26), dp(20), dp(30));
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        scroll.addView(root, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);
    }

    private void title(String value) { TextView t=text(value,24,Typeface.BOLD); t.setGravity(Gravity.CENTER); t.setPadding(0,0,0,dp(12)); root.addView(t); }
    private void section(String value) { TextView t=text(value,17,Typeface.BOLD); t.setPadding(0,dp(16),0,dp(6)); root.addView(t,matchWrap()); }
    private void warning(String value) { TextView t=text(value,16,Typeface.BOLD); t.setGravity(Gravity.CENTER); t.setPadding(0,dp(8),0,dp(8)); root.addView(t); }
    private void body(String value) { TextView t=text(value,15,Typeface.NORMAL); t.setGravity(Gravity.CENTER); t.setPadding(0,dp(8),0,dp(10)); root.addView(t); }
    private void labelValue(String label,String value) { TextView t=text(label+":  "+value,15,Typeface.NORMAL); t.setPadding(0,dp(4),0,dp(4)); root.addView(t,matchWrap()); }
    private Button button(String label,View.OnClickListener listener) { Button b=new Button(this); b.setText(label); b.setAllCaps(false); b.setTextSize(15); b.setOnClickListener(listener); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(52)); lp.setMargins(0,dp(6),0,dp(6)); root.addView(b,lp); return b; }
    private EditText input(String hint,boolean numeric) { EditText e=new EditText(this); e.setHint(hint); if(numeric)e.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD); root.addView(e,matchWrap()); return e; }
    private EditText dialogInput(String hint,boolean numeric) { EditText e=new EditText(this); e.setHint(hint); e.setSingleLine(true); if(numeric)e.setInputType(InputType.TYPE_CLASS_NUMBER); return e; }
    private TextView text(String value,int size,int style) { TextView t=new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(0xFF111111); t.setTypeface(Typeface.DEFAULT,style); return t; }
    private LinearLayout.LayoutParams matchWrap() { return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT); }
    private int dp(int v) { return Math.round(v*getResources().getDisplayMetrics().density); }
    private static String formatDuration(long sec) { long h=sec/3600,m=(sec%3600)/60,s=sec%60; return String.format(Locale.US,"%02d:%02d:%02d",h,m,s); }
}
