package com.jace.phonelending.host;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
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
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private HostPrefs prefs;
    private final ConsumerClient client = new ConsumerClient();
    private final ExecutorService io = Executors.newFixedThreadPool(4);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private List<DeviceRecord> devices = new ArrayList<>();
    private boolean unlocked = false;
    private LinearLayout root;
    private String screenMode = "lock";
    private DeviceRecord currentDevice = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
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
        title("PHONE LENDING — HOST DEV");
        warning("DEVELOPMENT BUILD — NOT FOR PRODUCTION RENTAL");
        body("Create an operator PIN. Host controls never become authoritative until the Consumer acknowledges a signed command.");
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
        title("PHONE LENDING");
        section("OPERATOR ACCESS");
        EditText pin = input("Operator PIN", true);
        button("UNLOCK HOST", v -> {
            if (prefs.verifyPin(pin.getText().toString().trim())) {
                unlocked = true;
                showDashboard(true);
            } else {
                Toast.makeText(this, "Incorrect operator PIN", Toast.LENGTH_SHORT).show();
            }
        });
        body("Host ID: " + prefs.getHostId());
    }

    private void showDashboard(boolean refreshNow) {
        if (!unlocked) return;
        screenMode = "dashboard";
        currentDevice = null;
        page();
        title("PHONE LENDING — HOST DEV");
        warning("DEVELOPMENT FLEET ONLY");
        int active = 0, ready = 0, expired = 0, attention = 0;
        for (DeviceRecord d : devices) {
            if ("ACTIVE".equals(d.state)) active++;
            else if ("AVAILABLE_LOCKED".equals(d.state)) ready++;
            else if ("EXPIRED_LOCKED".equals(d.state)) expired++;
            else if ("RECOVERY_LOCKED".equals(d.state)) attention++;
        }
        body(devices.size() + " devices   |   " + active + " active   |   " + ready + " ready   |   " + expired + " expired   |   " + attention + " attention");
        button("PAIR RENTAL DEVICE", v -> showPairDialog());
        button("REFRESH ALL", v -> refreshAll());
        for (DeviceRecord d : devices) renderCard(d);
        if (devices.isEmpty()) body("No rental devices paired yet. Pair a provisioned Consumer on the same local network.");
        button("LOCK HOST", v -> showUnlock());
        if (refreshNow) refreshAll();
    }

    private void renderCard(DeviceRecord d) {
        section(d.alias + "  (" + d.deviceId + ")");
        String timer = displayTimer(d);
        labelValue("Status", d.state);
        labelValue("Remaining", timer);
        labelValue("Endpoint", d.ip + ":" + d.port);
        labelValue("Last confirmed", d.lastSyncEpoch == 0 ? "never" : ageText(d.lastSyncEpoch));
        if (!d.lastMessage.isEmpty()) labelValue("Last result", d.lastMessage);
        button("OPEN DEVICE", v -> showDevice(d));
    }

    private void showDevice(DeviceRecord d) {
        if (!unlocked) return;
        screenMode = "device";
        currentDevice = d;
        page();
        title(d.alias);
        body(d.deviceId);
        labelValue("State", d.state);
        labelValue("Remaining", displayTimer(d));
        labelValue("Last confirmed", d.lastSyncEpoch == 0 ? "never" : ageText(d.lastSyncEpoch));
        labelValue("Protocol", "v" + d.protocolVersion);
        labelValue("Endpoint", d.ip + ":" + d.port);
        button("REFRESH STATUS", v -> request(d, "STATUS", "", "Refreshing status..."));

        if ("AVAILABLE_LOCKED".equals(d.state)) {
            section("START TEST RENTAL");
            button("15 MINUTES", v -> request(d, "START", "900", "Starting rental..."));
            button("30 MINUTES", v -> request(d, "START", "1800", "Starting rental..."));
            button("1 HOUR", v -> request(d, "START", "3600", "Starting rental..."));
            button("CUSTOM DURATION", v -> customStart(d));
        } else if ("ACTIVE".equals(d.state)) {
            section("ACTIVE RENTAL");
            button("EXTEND +15 MIN", v -> request(d, "EXTEND", "900", "Requesting extension..."));
            button("EXTEND +30 MIN", v -> request(d, "EXTEND", "1800", "Requesting extension..."));
            button("END RENTAL NOW", v -> confirmEnd(d));
        } else if ("EXPIRED_LOCKED".equals(d.state)) {
            body("This development batch does not claim renter-data turnover is complete. PREPARE only resets the test session state.");
            button("PREPARE TEST DEVICE", v -> request(d, "PREPARE", "", "Preparing test state..."));
            button("OWNER MAINTENANCE", v -> request(d, "MAINTENANCE", "", "Requesting maintenance..."));
        } else if ("RECOVERY_LOCKED".equals(d.state)) {
            warning("OWNER ATTENTION REQUIRED");
            button("OWNER MAINTENANCE", v -> request(d, "MAINTENANCE", "", "Requesting recovery maintenance..."));
        } else if ("ADMIN_MAINTENANCE".equals(d.state)) {
            button("END MAINTENANCE / RELOCK", v -> request(d, "RELOCK", "", "Relocking..."));
        }

        button("BACK TO DASHBOARD", v -> showDashboard(false));
    }

    private void showPairDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        EditText alias = dialogInput("Alias (example: Phone #07)", false);
        EditText ip = dialogInput("Consumer IP address", false);
        EditText code = dialogInput("12-digit pairing code", true);
        box.addView(alias); box.addView(ip); box.addView(code);
        new AlertDialog.Builder(this)
                .setTitle("Pair rental device")
                .setMessage("Use the IP and pairing code shown on the business-owned Consumer development phone.")
                .setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Pair", (dialog, which) -> {
                    String ipValue = ip.getText().toString().trim();
                    String codeValue = code.getText().toString().trim();
                    String aliasValue = alias.getText().toString().trim();
                    Toast.makeText(this, "Pairing request sent...", Toast.LENGTH_SHORT).show();
                    io.execute(() -> {
                        ConsumerClient.PairResult r = client.pair(prefs.getHostId(), aliasValue, ipValue, 42424, codeValue);
                        handler.post(() -> {
                            if (r.ok) {
                                devices.add(r.device);
                                prefs.saveDevices(devices);
                                Toast.makeText(this, "Consumer acknowledged pairing", Toast.LENGTH_SHORT).show();
                                showDashboard(false);
                            } else {
                                Toast.makeText(this, "Pairing failed: " + r.error, Toast.LENGTH_LONG).show();
                            }
                        });
                    });
                }).show();
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
                    } catch (Exception e) {
                        Toast.makeText(this, "Enter 1-1440 minutes", Toast.LENGTH_SHORT).show();
                    }
                }).show();
    }

    private void confirmEnd(DeviceRecord d) {
        new AlertDialog.Builder(this)
                .setTitle("End rental now?")
                .setMessage("The Host will request immediate expiration. The UI will not claim success until the Consumer acknowledges EXPIRED_LOCKED.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("End rental", (x, y) -> request(d, "END", "", "Ending and locking..."))
                .show();
    }

    private void request(DeviceRecord d, String command, String payload, String pendingText) {
        Toast.makeText(this, pendingText, Toast.LENGTH_SHORT).show();
        io.execute(() -> {
            ConsumerClient.CommandResult r = client.command(d, command, payload);
            handler.post(() -> {
                if (r.transportOk && r.accepted) {
                    d.state = r.state;
                    d.remainingSeconds = r.remaining;
                    d.lastSyncEpoch = System.currentTimeMillis();
                    d.lastMessage = "ACK " + command + ": " + r.message;
                    prefs.saveDevices(devices);
                    Toast.makeText(this, "Consumer acknowledged: " + r.state, Toast.LENGTH_SHORT).show();
                } else if (r.transportOk) {
                    d.lastMessage = "REJECTED " + command + ": " + r.message;
                    prefs.saveDevices(devices);
                    Toast.makeText(this, "Consumer rejected command: " + r.message, Toast.LENGTH_LONG).show();
                } else {
                    d.lastMessage = "OFFLINE: " + r.message;
                    prefs.saveDevices(devices);
                    Toast.makeText(this, "Consumer unreachable. Host state is stale.", Toast.LENGTH_LONG).show();
                }
                showDevice(d);
            });
        });
    }

    private void refreshAll() {
        if (devices.isEmpty()) return;
        for (DeviceRecord d : devices) {
            io.execute(() -> {
                ConsumerClient.CommandResult r = client.command(d, "STATUS", "");
                handler.post(() -> {
                    if (r.transportOk && r.accepted) {
                        d.state = r.state;
                        d.remainingSeconds = r.remaining;
                        d.lastSyncEpoch = System.currentTimeMillis();
                        d.lastMessage = "status confirmed";
                    } else {
                        d.lastMessage = "offline / stale";
                    }
                    prefs.saveDevices(devices);
                    if ("dashboard".equals(screenMode)) showDashboard(false);
                });
            });
        }
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

    private void title(String value) {
        TextView t = text(value, 24, Typeface.BOLD);
        t.setGravity(Gravity.CENTER); t.setPadding(0, 0, 0, dp(12)); root.addView(t);
    }
    private void section(String value) {
        TextView t = text(value, 17, Typeface.BOLD);
        t.setPadding(0, dp(16), 0, dp(6)); root.addView(t, matchWrap());
    }
    private void warning(String value) {
        TextView t = text(value, 16, Typeface.BOLD);
        t.setGravity(Gravity.CENTER); t.setPadding(0, dp(8), 0, dp(8)); root.addView(t);
    }
    private void body(String value) {
        TextView t = text(value, 15, Typeface.NORMAL);
        t.setGravity(Gravity.CENTER); t.setPadding(0, dp(8), 0, dp(10)); root.addView(t);
    }
    private void labelValue(String label, String value) {
        TextView t = text(label + ":  " + value, 15, Typeface.NORMAL);
        t.setPadding(0, dp(4), 0, dp(4)); root.addView(t, matchWrap());
    }
    private Button button(String label, View.OnClickListener listener) {
        Button b = new Button(this); b.setText(label); b.setAllCaps(false); b.setTextSize(15); b.setOnClickListener(listener);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)); lp.setMargins(0, dp(6), 0, dp(6)); root.addView(b, lp); return b;
    }
    private EditText input(String hint, boolean numeric) {
        EditText e = new EditText(this); e.setHint(hint); if (numeric) e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        root.addView(e, matchWrap()); return e;
    }
    private EditText dialogInput(String hint, boolean numeric) {
        EditText e = new EditText(this); e.setHint(hint); e.setSingleLine(true); if (numeric) e.setInputType(InputType.TYPE_CLASS_NUMBER); return e;
    }
    private TextView text(String value, int size, int style) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(0xFF111111); t.setTypeface(Typeface.DEFAULT, style); return t;
    }
    private LinearLayout.LayoutParams matchWrap() { return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private static String formatDuration(long sec) { long h=sec/3600, m=(sec%3600)/60, s=sec%60; return String.format(Locale.US, "%02d:%02d:%02d", h,m,s); }
}
