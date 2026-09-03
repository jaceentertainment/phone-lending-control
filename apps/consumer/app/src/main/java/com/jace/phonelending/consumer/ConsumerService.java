package com.jace.phonelending.consumer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.util.Base64;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;

public class ConsumerService extends Service {
    public static final String SERVICE_TYPE = "_phonelending._tcp.";
    public static final int PROTOCOL_VERSION = 2;

    private static final String STATUS_CHANNEL = "rental_status";
    private static final String WARNING_CHANNEL = "rental_warning";
    private static final int STATUS_NOTIFICATION = 1001;
    private static final int WARNING_NOTIFICATION = 1002;
    private static final String CAPABILITIES = "status,start,extend,end,prepare,maintenance,relock";

    private static volatile int advertisedPort = 0;
    private static volatile String advertisedServiceName = "";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newCachedThreadPool();
    private final SecureRandom random = new SecureRandom();
    private SessionStore sessions;
    private PairingManager pairing;
    private PolicyController policy;
    private SharedPreferences replayPrefs;
    private final ArrayDeque<String> recentCommandIds = new ArrayDeque<>();
    private final Set<String> recentCommandSet = new HashSet<>();
    private volatile SSLServerSocket serverSocket;
    private NsdManager nsdManager;
    private NsdManager.RegistrationListener registrationListener;
    private boolean nsdRegistered;
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

    public static int advertisedPort() { return advertisedPort; }
    public static String advertisedServiceName() { return advertisedServiceName; }

    @Override
    public void onCreate() {
        super.onCreate();
        sessions = new SessionStore(this);
        pairing = new PairingManager(this);
        policy = new PolicyController(this);
        replayPrefs = createDeviceProtectedStorageContext().getSharedPreferences("replay_cache_v2", MODE_PRIVATE);
        loadReplayCache();
        createChannels();
        startForeground(STATUS_NOTIFICATION, buildStatusNotification());
        policy.grantRequiredNotificationPermissionIfPossible();
        io.execute(this::runSecureServer);
        handler.post(tick);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) { return START_STICKY; }
    @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        unregisterNsd();
        try { if (serverSocket != null) serverSocket.close(); } catch (Exception ignored) {}
        advertisedPort = 0;
        advertisedServiceName = "";
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
            b.setContentText("Business provisioning required");
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

    private void runSecureServer() {
        try {
            SSLServerSocket ss = (SSLServerSocket) pairing.serverSslContext().getServerSocketFactory().createServerSocket(0);
            ss.setNeedClientAuth(false);
            serverSocket = ss;
            advertisedPort = ss.getLocalPort();
            registerNsd(advertisedPort);
            while (!Thread.currentThread().isInterrupted()) {
                Socket socket = ss.accept();
                io.execute(() -> handleClient(socket));
            }
        } catch (Exception e) {
            advertisedPort = 0;
            advertisedServiceName = "";
        }
    }

    private void registerNsd(int port) {
        try {
            nsdManager = (NsdManager) getSystemService(Context.NSD_SERVICE);
            if (nsdManager == null) return;
            NsdServiceInfo info = new NsdServiceInfo();
            info.setServiceName("PhoneLending-" + pairing.getDeviceId().replace("PL-", ""));
            info.setServiceType(SERVICE_TYPE);
            info.setPort(port);
            if (Build.VERSION.SDK_INT >= 21) {
                info.setAttribute("eid", pairing.getDeviceId());
                info.setAttribute("pv", String.valueOf(PROTOCOL_VERSION));
            }
            registrationListener = new NsdManager.RegistrationListener() {
                @Override public void onServiceRegistered(NsdServiceInfo serviceInfo) {
                    advertisedServiceName = serviceInfo.getServiceName();
                    nsdRegistered = true;
                }
                @Override public void onRegistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
                    advertisedServiceName = "";
                    nsdRegistered = false;
                }
                @Override public void onServiceUnregistered(NsdServiceInfo serviceInfo) {
                    advertisedServiceName = "";
                    nsdRegistered = false;
                }
                @Override public void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {}
            };
            nsdManager.registerService(info, NsdManager.PROTOCOL_DNS_SD, registrationListener);
        } catch (Throwable ignored) {
            advertisedServiceName = "";
        }
    }

    private void unregisterNsd() {
        try {
            if (nsdRegistered && nsdManager != null && registrationListener != null) {
                nsdManager.unregisterService(registrationListener);
            }
        } catch (Throwable ignored) {}
        nsdRegistered = false;
    }

    private void handleClient(Socket socket) {
        try (SSLSocket s = (SSLSocket) socket;
             BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8))) {
            s.setSoTimeout(7000);
            s.startHandshake();
            String line = in.readLine();
            String response = processLine(line == null ? "" : line.trim());
            out.write(response);
            out.write("\n");
            out.flush();
        } catch (Exception ignored) {}
    }

    private synchronized String processLine(String line) throws Exception {
        try {
            JSONObject request = new JSONObject(line);
            String type = request.optString("type", "");
            if ("PAIR_INIT".equals(type)) return processPair(request).toString();
            if ("CMD".equals(type)) return processCommand(request).toString();
            return error("bad_type").toString();
        } catch (Exception e) {
            return error("bad_format").toString();
        }
    }

    private JSONObject processPair(JSONObject request) throws Exception {
        if (!policy.isDeviceOwner()) return error("not_provisioned");
        if (SessionStore.ACTIVE.equals(sessions.getState())) return error("pairing_not_allowed_active");

        int protocol = request.optInt("protocol", 0);
        String sid = request.optString("sessionId", "");
        String token = request.optString("token", "");
        String hostId = request.optString("hostId", "");
        String hostPublicKey = request.optString("hostPublicKey", "");
        String hostNonce = request.optString("hostNonce", "");
        String signature = request.optString("signature", "");

        PairingManager.PairResult result = pairing.pair(protocol, sid, token, hostId, hostPublicKey, hostNonce, signature);
        if (!result.ok) return error(result.error);

        String consumerNonce = randomToken(16);
        String state = sessions.getState();
        long remaining = sessions.remainingSeconds();
        String sessionId = sessions.getSessionId();
        String publicKey = pairing.consumerPublicKeyBase64();
        String canonical = pairAckCanonical(hostId, hostNonce, consumerNonce, result.authorizationRevision,
                state, remaining, sessionId, publicKey);
        String ackSignature = pairing.signAsConsumer(canonical);

        JSONObject ack = new JSONObject();
        ack.put("type", "PAIR_ACK");
        ack.put("protocol", PROTOCOL_VERSION);
        ack.put("deviceId", pairing.getDeviceId());
        ack.put("hostId", hostId);
        ack.put("hostNonce", hostNonce);
        ack.put("consumerNonce", consumerNonce);
        ack.put("authorizationRevision", result.authorizationRevision);
        ack.put("state", state);
        ack.put("remaining", remaining);
        ack.put("sessionId", sessionId);
        ack.put("capabilities", CAPABILITIES);
        ack.put("consumerPublicKey", publicKey);
        ack.put("signature", ackSignature);
        handler.post(() -> policy.applyRestrictedAndBringToFront());
        return ack;
    }

    private JSONObject processCommand(JSONObject request) throws Exception {
        if (!pairing.isPaired()) return error("not_paired");
        int protocol = request.optInt("protocol", 0);
        if (protocol != PROTOCOL_VERSION) return commandError(request, "protocol_mismatch");

        String commandId = request.optString("commandId", "");
        String hostId = request.optString("hostId", "");
        String target = request.optString("target", "");
        String sessionId = request.optString("sessionId", "");
        long issuedAt = request.optLong("issuedAt", 0L);
        String nonce = request.optString("nonce", "");
        String command = request.optString("command", "");
        String payload = request.optString("payload", "");
        String signature = request.optString("signature", "");

        if (!pairing.getHostId().equals(hostId)) return commandError(request, "wrong_host");
        if (!pairing.getDeviceId().equals(target)) return commandError(request, "wrong_target");
        if (commandId.isEmpty() || nonce.isEmpty()) return commandError(request, "missing_command_identity");
        long now = System.currentTimeMillis();
        if (Math.abs(now - issuedAt) > 300_000L) return commandError(request, "stale_command");
        if (recentCommandSet.contains(commandId)) return commandError(request, "replay_rejected");

        String canonical = commandCanonical(protocol, commandId, hostId, target, sessionId, issuedAt, nonce, command, payload);
        if (!pairing.verifyHostSignature(canonical, signature)) return commandError(request, "bad_signature");
        if (("EXTEND".equals(command) || "END".equals(command)) && !sessions.getSessionId().equals(sessionId))
            return commandError(request, "session_mismatch");

        rememberCommand(commandId);
        boolean ok = executeCommand(command, payload);
        return commandAck(commandId, nonce, ok, ok ? "ok" : "invalid_state_or_payload");
    }

    private boolean executeCommand(String command, String payload) {
        String state = sessions.getState();
        switch (command) {
            case "STATUS":
                return true;
            case "START": {
                if (!SessionStore.AVAILABLE_LOCKED.equals(state) || !pairing.isPaired()) return false;
                long seconds = parsePositive(payload, 24 * 3600L);
                if (seconds <= 0) return false;
                sessions.startSession(seconds);
                policy.applyActiveAndOpenHome();
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

    private JSONObject commandAck(String commandId, String nonce, boolean ok, String message) throws Exception {
        String state = sessions.getState();
        long remaining = sessions.remainingSeconds();
        String sessionId = sessions.getSessionId();
        String canonical = ackCanonical(commandId, nonce, ok, state, remaining, sessionId, message);
        JSONObject ack = new JSONObject();
        ack.put("type", "ACK");
        ack.put("protocol", PROTOCOL_VERSION);
        ack.put("commandId", commandId);
        ack.put("accepted", ok);
        ack.put("state", state);
        ack.put("remaining", remaining);
        ack.put("sessionId", sessionId);
        ack.put("message", message);
        ack.put("nonce", nonce);
        ack.put("signature", pairing.signAsConsumer(canonical));
        return ack;
    }

    private JSONObject commandError(JSONObject request, String message) throws Exception {
        String commandId = request.optString("commandId", "");
        String nonce = request.optString("nonce", "");
        return commandAck(commandId, nonce, false, message);
    }

    private JSONObject error(String message) throws Exception {
        JSONObject o = new JSONObject();
        o.put("type", "ERROR");
        o.put("protocol", PROTOCOL_VERSION);
        o.put("message", message);
        return o;
    }

    public static String commandCanonical(int protocol, String commandId, String hostId, String target,
                                          String sessionId, long issuedAt, String nonce, String command, String payload) {
        return "CMD|" + protocol + "|" + safe(commandId) + "|" + safe(hostId) + "|" + safe(target)
                + "|" + safe(sessionId) + "|" + issuedAt + "|" + safe(nonce) + "|" + safe(command) + "|" + safe(payload);
    }

    public static String ackCanonical(String commandId, String nonce, boolean accepted, String state,
                                      long remaining, String sessionId, String message) {
        return "ACK|" + PROTOCOL_VERSION + "|" + safe(commandId) + "|" + safe(nonce) + "|" + accepted
                + "|" + safe(state) + "|" + remaining + "|" + safe(sessionId) + "|" + safe(message);
    }

    public static String pairAckCanonical(String hostId, String hostNonce, String consumerNonce, int revision,
                                          String state, long remaining, String sessionId, String consumerPublicKey) {
        return "PAIR_ACK|" + PROTOCOL_VERSION + "|" + safe(hostId) + "|" + safe(hostNonce) + "|"
                + safe(consumerNonce) + "|" + revision + "|" + safe(state) + "|" + remaining + "|"
                + safe(sessionId) + "|" + safe(consumerPublicKey) + "|" + CAPABILITIES;
    }

    private long parsePositive(String value, long max) {
        try {
            long v = Long.parseLong(value);
            return v > 0 && v <= max ? v : -1L;
        } catch (Exception e) { return -1L; }
    }

    private String randomToken(int bytes) {
        byte[] value = new byte[bytes];
        random.nextBytes(value);
        return Base64.encodeToString(value, Base64.NO_WRAP | Base64.URL_SAFE | Base64.NO_PADDING);
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
        while (recentCommandIds.size() > 100) {
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

    private static String safe(String s) { return s == null ? "" : s; }
}
