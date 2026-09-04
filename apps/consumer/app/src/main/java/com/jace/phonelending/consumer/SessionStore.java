package com.jace.phonelending.consumer;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.provider.Settings;

import java.util.UUID;

public final class SessionStore {
    public static final String UNPROVISIONED = "UNPROVISIONED";
    public static final String AVAILABLE_LOCKED = "AVAILABLE_LOCKED";
    public static final String ACTIVE = "ACTIVE";
    public static final String EXPIRED_LOCKED = "EXPIRED_LOCKED";
    public static final String ADMIN_MAINTENANCE = "ADMIN_MAINTENANCE";
    public static final String RECOVERY_LOCKED = "RECOVERY_LOCKED";

    public static final long DEV_SOFT_LOCK_LEASE_MS = 5L * 60L * 1000L;

    private final Context context;
    private final SharedPreferences prefs;

    public SessionStore(Context context) {
        this.context = context.createDeviceProtectedStorageContext();
        this.prefs = this.context.getSharedPreferences("session_state", Context.MODE_PRIVATE);
    }

    public synchronized String getState() {
        return prefs.getString("state", UNPROVISIONED);
    }

    public synchronized void setState(String state) {
        prefs.edit().putString("state", state).commit();
    }

    /** Early-development setup: overlay permission, not Device Owner, is the readiness gate. */
    public synchronized void initializeDevelopment() {
        if (UNPROVISIONED.equals(getState())) {
            prefs.edit().putString("state", AVAILABLE_LOCKED).commit();
        }
    }

    /** Retained for source compatibility with the later managed-device milestone. */
    public synchronized void initializeProvisioned() {
        initializeDevelopment();
    }

    public synchronized void startSession(long durationSeconds) {
        long nowEpoch = System.currentTimeMillis();
        long nowElapsed = SystemClock.elapsedRealtime();
        int boot = readBootCount();
        prefs.edit()
                .putString("state", ACTIVE)
                .putString("sessionId", UUID.randomUUID().toString())
                .putLong("durationSec", durationSeconds)
                .putLong("startEpoch", nowEpoch)
                .putLong("endEpoch", nowEpoch + durationSeconds * 1000L)
                .putLong("startElapsed", nowElapsed)
                .putLong("endElapsed", nowElapsed + durationSeconds * 1000L)
                .putInt("bootCount", boot)
                .putLong("lastObservedEpoch", nowEpoch)
                .putString("maintenanceUnderlying", "")
                .commit();
        releaseSoftLockLease();
    }

    public synchronized void extendSession(long additionalSeconds) {
        if (!ACTIVE.equals(getState())) return;
        long nowEpoch = System.currentTimeMillis();
        long endEpoch = prefs.getLong("endEpoch", nowEpoch);
        long endElapsed = prefs.getLong("endElapsed", SystemClock.elapsedRealtime());
        prefs.edit()
                .putLong("endEpoch", Math.max(nowEpoch, endEpoch) + additionalSeconds * 1000L)
                .putLong("endElapsed", Math.max(SystemClock.elapsedRealtime(), endElapsed) + additionalSeconds * 1000L)
                .commit();
    }

    public synchronized long remainingSeconds() {
        String state = getState();
        if (ADMIN_MAINTENANCE.equals(state)) {
            String underlying = prefs.getString("maintenanceUnderlying", AVAILABLE_LOCKED);
            if (!ACTIVE.equals(underlying)) return 0L;
        } else if (!ACTIVE.equals(state)) {
            return 0L;
        }

        long nowEpoch = System.currentTimeMillis();
        long lastObserved = prefs.getLong("lastObservedEpoch", nowEpoch);
        if (nowEpoch + 120_000L < lastObserved) {
            markRecoveryLocked("clock_rollback");
            return 0L;
        }

        long remainingMs;
        int storedBoot = prefs.getInt("bootCount", -1);
        int currentBoot = readBootCount();
        if (storedBoot >= 0 && currentBoot == storedBoot) {
            remainingMs = prefs.getLong("endElapsed", SystemClock.elapsedRealtime()) - SystemClock.elapsedRealtime();
        } else {
            remainingMs = prefs.getLong("endEpoch", nowEpoch) - nowEpoch;
        }

        prefs.edit().putLong("lastObservedEpoch", nowEpoch).apply();
        return Math.max(0L, (remainingMs + 999L) / 1000L);
    }

    public synchronized boolean expireIfNeeded() {
        if (ACTIVE.equals(getState()) && remainingSeconds() <= 0L) {
            prefs.edit().putString("state", EXPIRED_LOCKED).commit();
            armSoftLockLease();
            return true;
        }
        if (ADMIN_MAINTENANCE.equals(getState())) {
            String underlying = prefs.getString("maintenanceUnderlying", AVAILABLE_LOCKED);
            if (ACTIVE.equals(underlying) && remainingSeconds() <= 0L) {
                prefs.edit().putString("maintenanceUnderlying", EXPIRED_LOCKED).commit();
            }
        }
        return false;
    }

    public synchronized void endSession() {
        prefs.edit().putString("state", EXPIRED_LOCKED).commit();
        armSoftLockLease();
    }

    public synchronized void prepareAvailable() {
        prefs.edit()
                .putString("state", AVAILABLE_LOCKED)
                .remove("sessionId")
                .remove("durationSec")
                .remove("startEpoch")
                .remove("endEpoch")
                .remove("startElapsed")
                .remove("endElapsed")
                .remove("bootCount")
                .commit();
        armSoftLockLease();
    }

    public synchronized void enterMaintenance(long seconds) {
        String current = getState();
        if (ADMIN_MAINTENANCE.equals(current)) return;
        prefs.edit()
                .putString("maintenanceUnderlying", current)
                .putString("state", ADMIN_MAINTENANCE)
                .putLong("maintenanceEndEpoch", System.currentTimeMillis() + seconds * 1000L)
                .putBoolean("devUnrestricted", false)
                .commit();
        releaseSoftLockLease();
    }

    public synchronized void setDevUnrestricted(long seconds) {
        if (!ADMIN_MAINTENANCE.equals(getState())) return;
        prefs.edit()
                .putBoolean("devUnrestricted", true)
                .putLong("devUnrestrictedEnd", System.currentTimeMillis() + seconds * 1000L)
                .commit();
    }

    public synchronized boolean isDevUnrestricted() {
        if (!ADMIN_MAINTENANCE.equals(getState())) return false;
        boolean enabled = prefs.getBoolean("devUnrestricted", false);
        long end = prefs.getLong("devUnrestrictedEnd", 0L);
        if (enabled && System.currentTimeMillis() >= end) {
            prefs.edit().putBoolean("devUnrestricted", false).commit();
            return false;
        }
        return enabled;
    }

    public synchronized long maintenanceRemainingSeconds() {
        if (!ADMIN_MAINTENANCE.equals(getState())) return 0L;
        return Math.max(0L, (prefs.getLong("maintenanceEndEpoch", 0L) - System.currentTimeMillis() + 999L) / 1000L);
    }

    public synchronized void reconcileMaintenance() {
        if (!ADMIN_MAINTENANCE.equals(getState())) return;
        if (maintenanceRemainingSeconds() > 0L) {
            isDevUnrestricted();
            return;
        }
        exitMaintenance();
    }

    public synchronized void exitMaintenance() {
        if (!ADMIN_MAINTENANCE.equals(getState())) return;
        String underlying = prefs.getString("maintenanceUnderlying", AVAILABLE_LOCKED);
        if (ACTIVE.equals(underlying)) {
            long rem = remainingSecondsForUnderlying();
            underlying = rem > 0L ? ACTIVE : EXPIRED_LOCKED;
        }
        prefs.edit()
                .putString("state", underlying)
                .putString("maintenanceUnderlying", "")
                .putBoolean("devUnrestricted", false)
                .remove("maintenanceEndEpoch")
                .remove("devUnrestrictedEnd")
                .commit();
        if (AVAILABLE_LOCKED.equals(underlying) || EXPIRED_LOCKED.equals(underlying) || RECOVERY_LOCKED.equals(underlying)) {
            armSoftLockLease();
        }
    }

    private long remainingSecondsForUnderlying() {
        long nowEpoch = System.currentTimeMillis();
        int storedBoot = prefs.getInt("bootCount", -1);
        int currentBoot = readBootCount();
        long ms;
        if (storedBoot >= 0 && currentBoot == storedBoot) {
            ms = prefs.getLong("endElapsed", SystemClock.elapsedRealtime()) - SystemClock.elapsedRealtime();
        } else {
            ms = prefs.getLong("endEpoch", nowEpoch) - nowEpoch;
        }
        return Math.max(0L, (ms + 999L) / 1000L);
    }

    public synchronized void markRecoveryLocked(String reason) {
        prefs.edit().putString("state", RECOVERY_LOCKED).putString("lockReason", reason).commit();
        armSoftLockLease();
    }

    public synchronized String getLockReason() {
        return prefs.getString("lockReason", "");
    }

    public synchronized long getEndEpoch() {
        return prefs.getLong("endEpoch", 0L);
    }

    public synchronized String getSessionId() {
        return prefs.getString("sessionId", "");
    }

    // -------------------------------------------------------------------------
    // Early-development soft-lock lease. This is presentation enforcement only;
    // it never changes canonical rental state and is NOT production security.
    // -------------------------------------------------------------------------

    public synchronized void armSoftLockLease() {
        long nextGeneration = prefs.getLong("softLockGeneration", 0L) + 1L;
        long now = SystemClock.elapsedRealtime();
        prefs.edit()
                .putLong("softLockGeneration", nextGeneration)
                .putLong("softLockEndElapsed", now + DEV_SOFT_LOCK_LEASE_MS)
                .putInt("softLockBootCount", readBootCount())
                .commit();
    }

    public synchronized void releaseSoftLockLease() {
        long generation = prefs.getLong("softLockGeneration", 0L);
        if (generation <= 0L) return;
        prefs.edit().putLong("softLockReleasedGeneration", generation).commit();
    }

    public synchronized boolean softLockLeaseActive() {
        long generation = prefs.getLong("softLockGeneration", 0L);
        if (generation <= 0L) return false;
        if (prefs.getLong("softLockReleasedGeneration", -1L) == generation) return false;
        reconcileSoftLockLeaseAfterBoot();
        long end = prefs.getLong("softLockEndElapsed", 0L);
        if (SystemClock.elapsedRealtime() >= end) {
            releaseSoftLockLease();
            return false;
        }
        return true;
    }

    public synchronized long softLockRemainingMs() {
        if (!softLockLeaseActive()) return 0L;
        return Math.max(0L, prefs.getLong("softLockEndElapsed", 0L) - SystemClock.elapsedRealtime());
    }

    public synchronized void reconcileSoftLockLeaseAfterBoot() {
        long generation = prefs.getLong("softLockGeneration", 0L);
        if (generation <= 0L) return;
        if (prefs.getLong("softLockReleasedGeneration", -1L) == generation) return;

        int storedBoot = prefs.getInt("softLockBootCount", -1);
        int currentBoot = readBootCount();
        long now = SystemClock.elapsedRealtime();
        long end = prefs.getLong("softLockEndElapsed", 0L);

        boolean bootChanged = currentBoot >= 0 && storedBoot >= 0 && currentBoot != storedBoot;
        boolean elapsedLooksReset = end - now > DEV_SOFT_LOCK_LEASE_MS;
        if (bootChanged || elapsedLooksReset) {
            prefs.edit()
                    .putLong("softLockEndElapsed", now + DEV_SOFT_LOCK_LEASE_MS)
                    .putInt("softLockBootCount", currentBoot)
                    .commit();
        }
    }

    private int readBootCount() {
        try {
            return Settings.Global.getInt(context.getContentResolver(), Settings.Global.BOOT_COUNT);
        } catch (Exception e) {
            return -1;
        }
    }
}
