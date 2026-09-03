package com.jace.phonelending.consumer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ConsumerService extends Service {
    public static final int PORT = 42424;
    private static final String STATUS_CHANNEL = "rental_status";
    private static final String WARNING_CHANNEL = "rental_warning";
    private static final int STATUS_NOTIFICATION = 1001;
    private static final int WARNING_NOTIFICATION = 1002;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newCachedThreadPool();
    private SessionStore sessions;
    private PairingManager pairing;
    private PolicyController policy;
    private SharedPreferences replayPrefs;
    private final ArrayDeque<String> recentCommandIds = new ArrayDeque<>();
    private final Set<String> recentCommandSet = new HashSet<>();
    private volatile ServerSocket serverSocket;
    private String warningSession = "";
    private boolean warned60;
    private boolean warned30;
    private boolean warned10;
    private boolean lastDevUnrestricted = false;

    public static void start(Context context) {
        Intent i = new Intent(context, ConsumerService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i);
            else context.startService(i);
        } catch (Throwable ignored) {}
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sessions = new SessionStore(this);
        pairing = new PairingManager(this);
        policy = new PolicyController(this);
        replayPrefs = createDeviceProtectedStorageContext().getSharedPreferences("replay_cache", MODE_PRIVATE);
        loadReplayCache();
        createChannels();
        startForeground(STATUS_NOTIFICATION, buildStatusNotification());
        policy.grantRequiredNotificationPermissionIfPossible();
        io.execute(this::runServer);
        handler.post(tick);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        try { if (serverSocket != null) serverSocket.close(); } catch (Exception ignored) {}
        io.shutdownNow();
        super.onDestroy();
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            try {
                if (policy.isDeviceOwner()) sessions.initializeProvisioned();
                sessions.reconcileMaintenance();
                String stateBefore = sessions.getState();
                boolean expired = sessions.expireIfNeeded();
                String state = sessions.getState();

                if (expired || (!state.equals(stateBefore) && isRestricted(state))) {
                    policy.applyRestrictedAndBringToFront();
                } else if (isRestricted(state)) {
                    policy.applyLockedHome();
                } else if (SessionStore.ACTIVE.equals(state)) {
                    policy.clearLockedHome();
                } else if (SessionStore.ADMIN_MAINTENANCE.equals(state)) {
                    boolean unrestricted = sessions.isDevUnrestricted();
                    if (unrestricted) {
                        policy.clearLockedHome();
                    } else {
                        policy.applyLockedHome();
                        if (lastDevUnrestricted || !SessionStore.ADMIN_MAINTENANCE.equals(stateBefore)) {
                            policy.applyRestrictedAndBringToFront();
                        }
                    }
                    lastDevUnrestricted = unrestricted;
                } else {
                    lastDevUnrestricted = false;
                }

                handleWarnings(state);
                NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                nm.notify(STATUS_NOTIFICATION, buildStatusNotification());
            } catch (Throwable t) {
                sessions.markRecoveryLocked("service_tick_failure");
                policy.applyRestrictedAndBringToFront();
            }
            handler.postDelayed(this, 1000L);
        }
    };

    private boolean isRestricted(String state) {
        return SessionStore.AVAILABLE_LOCKED.equals(state)
                || SessionStore.EXPIRED_LOCKED.equals(state)
                || SessionStore.RECOVERY_LOCKED.equals(state);
    }

    private void createChannels() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel status = new NotificationChannel(STATUS_CHANNEL, "Rental status", NotificationManager.IMPORTANCE_LOW);
        status.setDescription("Persistent remaining-time display for an active rental.");
        status.setSound(null, null);
        status.enableVibration(false);
        nm.createNotificationChannel(status);

        NotificationChannel warning = new NotificationChannel(WARNING_CHANNEL, "Rental warnings", NotificationManager.IMPORTANCE_HIGH);
        warning.setDescription("Time-ending warnings for the renter.");
        warning.enableVibration(true);
        nm.createNotificationChannel(warning);
    }

    private Notification buildStatusNotification() {
        String state = sessions.getState();
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, STATUS_CHANNEL)
                : new Notification.Builder(this);
        b.setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("Phone Rental")
                .setContentIntent(pi)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_SERVICE);

        if (SessionStore.ACTIVE.equals(state)) {
            long remaining = sessions.remainingSeconds();
            long endEpoch = sessions.getEndEpoch();
            b.setContentText("Time remaining: " + formatDuration(remaining));
            if (endEpoch > 0L) {
                b.setWhen(endEpoch).setUsesChronometer(true);
                if (Build.VERSION.SDK_INT >= 24) b.setChronometerCountDown(true);
            }
        } else if (SessionStore.ADMIN_MAINTENANCE.equals(state)) {
            b.setContentText("Owner maintenance active — " + formatDuration(sessions.maintenanceRemainingSeconds()));
        } else if (SessionStore.UNPROVISIONED.equals(state)) {
            b.setContentText("Development setup required");
        } else {
            b.setContentText("Rental device locked — " + state);
        }
        return b.build();
    }

    private void handleWarnings(String state) {
        if (!SessionStore.ACTIVE.equals(state)) {
            warningSession = "";
            warned60 = warned30 = warned10 = false;
            return;
        }
        String sid = sessions.getSessionId();
        if (!sid.equals(warningSession)) {
            warningSession = sid;
            warned60 = warned30 = warned10 = false;
        }
        long rem = sessions.remainingSeconds();
        if (rem <= 60 && rem > 0 && !warned60) {
            warned60 = true;
            showWarning("Rental ending soon", "Less than 1 minute remaining — " + formatDuration(rem));
        } else if (rem <= 30 && rem > 0 && !warned30) {
            warned30 = true;
            showWarning("30 seconds remaining", "Please finish what you're doing.");
        } else if (rem <= 10 && rem > 0 && !warned10) {
            warned10 = true;
            showWarning("Rental ending", formatDuration(rem) + " remaining");
        }
    }

    private void showWarning(String title, String text) {
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, WARNING_CHANNEL)
                : new Notification.Builder(this);
        b.setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_ALARM)
                .setPriority(Notification.PRIORITY_HIGH);
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(WARNING_NOTIFICATION, b.build());
        vibrateOnce();
    }

    private void vibrateOnce() {
        try {
            Vibrator vibrator;
            if (Build.VERSION.SDK_INT >= 31) {
                VibratorManager vm = (VibratorManager) getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                vibrator = vm.getDefaultVibrator();
            } else {
                vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            }
            if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createOneShot(350L, VibrationEffect.DEFAULT_AMPLITUDE));
            else vibrator.vibrate(350L);
        } catch (Throwable ignored) {}
    }

    private void bringMainActivity() {
        try {
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(i);
        } catch (Throwable ignored) {}
    }

    private void runServer() {
        try (ServerSocket ss = new ServerSocket(PORT)) {
            serverSocket = ss;
            while (!Thread.currentThread().isInterrupted()) {
                Socket socket = ss.accept();
                io.execute(() -> handleClient(socket));
            }
        } catch (Exception ignored) {
        }
    }

    private void handleClient(Socket socket) {
        try (Socket s = socket;
             BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8))) {
            s.setSoTimeout(7000);
            String line = in.readLine();
            String response = processLine(line == null ? "" : line.trim());
            out.write(response);
            out.write("\n");
            out.flush();
        } catch (Exception ignored) {}
    }

    private synchronized String processLine(String line) {
        try {
            String[] p = line.split("\\|", -1);
            if (p.length >= 3 && "PAIR".equals(p[0])) {
                String hostId = p[1];
                String code = p[2];
                if (pairing.isPaired()) return "ERROR|already_paired";
                if (!pairing.pair(hostId, code)) return "ERROR|pairing_rejected";
                return "PAIRED|" + pairing.getDeviceId() + "|" + sessions.getState() + "|" + sessions.remainingSeconds() + "|1";
            }
            if (p.length != 6 || !"CMD".equals(p[0])) return "ERROR|bad_format";
            if (!pairing.isPaired()) return "ERROR|not_paired";
            String commandId = p[1];
            long timestamp = Long.parseLong(p[2]);
            String command = p[3];
            String payload = p[4];
            String signature = p[5];
            long now = System.currentTimeMillis();
            if (Math.abs(now - timestamp) > 300_000L) return ack(commandId, false, "stale_command");
            if (recentCommandSet.contains(commandId)) return ack(commandId, false, "replay_rejected");
            byte[] key = pairing.getSharedKey();
            if (key == null) return ack(commandId, false, "missing_key");
            String signed = commandId + "|" + timestamp + "|" + command + "|" + payload;
            String expected = CryptoUtils.hmacBase64(key, signed);
            if (!CryptoUtils.constantTimeEquals(expected, signature)) return ack(commandId, false, "bad_signature");
            rememberCommand(commandId);

            boolean ok = executeCommand(command, payload);
            return ack(commandId, ok, ok ? "ok" : "invalid_state_or_payload");
        } catch (Exception e) {
            return "ERROR|exception";
        }
    }

    private boolean executeCommand(String command, String payload) {
        String state = sessions.getState();
        switch (command) {
            case "STATUS":
                return true;
            case "START": {
                if (!SessionStore.AVAILABLE_LOCKED.equals(state)) return false;
                long seconds = parsePositive(payload, 24 * 3600L);
                if (seconds <= 0) return false;
                sessions.startSession(seconds);
                policy.clearLockedHome();
                bringMainActivity();
                return true;
            }
            case "EXTEND": {
                if (!SessionStore.ACTIVE.equals(state)) return false;
                long seconds = parsePositive(payload, 24 * 3600L);
                if (seconds <= 0) return false;
                sessions.extendSession(seconds);
                return true;
            }
            case "END":
                if (!SessionStore.ACTIVE.equals(state)) return false;
                sessions.endSession();
                policy.applyRestrictedAndBringToFront();
                return true;
            case "PREPARE":
                if (!SessionStore.EXPIRED_LOCKED.equals(state)) return false;
                sessions.prepareAvailable();
                policy.applyRestrictedAndBringToFront();
                return true;
            case "MAINTENANCE":
                if (SessionStore.ACTIVE.equals(state)) return false;
                sessions.enterMaintenance(600L);
                policy.applyRestrictedAndBringToFront();
                return true;
            case "RELOCK":
                if (!SessionStore.ADMIN_MAINTENANCE.equals(state)) return false;
                sessions.exitMaintenance();
                policy.applyRestrictedAndBringToFront();
                return true;
            default:
                return false;
        }
    }

    private long parsePositive(String value, long max) {
        try {
            long v = Long.parseLong(value);
            return v > 0 && v <= max ? v : -1L;
        } catch (Exception e) { return -1L; }
    }

    private String ack(String id, boolean ok, String message) {
        return "ACK|" + id + "|" + (ok ? "OK" : "REJECTED") + "|" + sessions.getState() + "|" + sessions.remainingSeconds() + "|" + message;
    }

    private void loadReplayCache() {
        String raw = replayPrefs.getString("ids", "");
        if (raw.isEmpty()) return;
        for (String id : raw.split(",")) if (!id.isEmpty()) {
            recentCommandIds.add(id);
            recentCommandSet.add(id);
        }
    }

    private void rememberCommand(String id) {
        recentCommandIds.addLast(id);
        recentCommandSet.add(id);
        while (recentCommandIds.size() > 50) {
            String removed = recentCommandIds.removeFirst();
            recentCommandSet.remove(removed);
        }
        StringBuilder sb = new StringBuilder();
        for (String x : recentCommandIds) {
            if (sb.length() > 0) sb.append(',');
            sb.append(x);
        }
        replayPrefs.edit().putString("ids", sb.toString()).apply();
    }

    private static String formatDuration(long totalSeconds) {
        long h = totalSeconds / 3600;
        long m = (totalSeconds % 3600) / 60;
        long s = totalSeconds % 60;
        return String.format(java.util.Locale.US, "%02d:%02d:%02d", h, m, s);
    }
}
