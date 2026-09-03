package com.jace.phonelending.consumer;

import android.Manifest;
import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;

public final class PolicyController {
    private final Context context;
    private final DevicePolicyManager dpm;
    private final ComponentName admin;

    public PolicyController(Context context) {
        this.context = context.getApplicationContext();
        this.dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        this.admin = new ComponentName(context, AdminReceiver.class);
    }

    public boolean isDeviceOwner() {
        return dpm != null && dpm.isDeviceOwnerApp(context.getPackageName());
    }

    public ComponentName adminComponent() { return admin; }

    public void grantRequiredNotificationPermissionIfPossible() {
        if (Build.VERSION.SDK_INT < 33 || !isDeviceOwner()) return;
        try {
            dpm.setPermissionGrantState(admin, context.getPackageName(), Manifest.permission.POST_NOTIFICATIONS,
                    DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED);
        } catch (Throwable ignored) {}
    }

    public void configureLockTaskAllowlist() {
        if (!isDeviceOwner()) return;
        try {
            dpm.setLockTaskPackages(admin, new String[]{context.getPackageName()});
            if (Build.VERSION.SDK_INT >= 28) {
                dpm.setLockTaskFeatures(admin, DevicePolicyManager.LOCK_TASK_FEATURE_NONE);
            }
        } catch (Throwable ignored) {}
    }

    public void applyLockedHome() {
        if (!isDeviceOwner()) return;
        configureLockTaskAllowlist();
        try {
            IntentFilter filter = new IntentFilter(Intent.ACTION_MAIN);
            filter.addCategory(Intent.CATEGORY_HOME);
            filter.addCategory(Intent.CATEGORY_DEFAULT);
            ComponentName activity = new ComponentName(context, RestrictedActivity.class);
            dpm.addPersistentPreferredActivity(admin, filter, activity);
        } catch (Throwable ignored) {}
    }

    public void clearLockedHome() {
        if (!isDeviceOwner()) return;
        try {
            dpm.clearPackagePersistentPreferredActivities(admin, context.getPackageName());
        } catch (Throwable ignored) {}
    }

    public void enterLockTask(Activity activity) {
        if (!isDeviceOwner()) return;
        configureLockTaskAllowlist();
        try {
            if (dpm.isLockTaskPermitted(context.getPackageName())) activity.startLockTask();
        } catch (Throwable ignored) {}
    }

    public void exitLockTask(Activity activity) {
        try { activity.stopLockTask(); } catch (Throwable ignored) {}
    }

    public void applyRestrictedAndBringToFront() {
        if (!isDeviceOwner()) return;
        applyLockedHome();
        try {
            Intent i = new Intent(context, RestrictedActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            context.startActivity(i);
        } catch (Throwable ignored) {}
    }

    public void applyActiveAndOpenHome() {
        clearLockedHome();
        try {
            Intent home = new Intent(Intent.ACTION_MAIN);
            home.addCategory(Intent.CATEGORY_HOME);
            home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(home);
        } catch (Throwable ignored) {}
    }

    public boolean notificationPermissionGranted() {
        if (Build.VERSION.SDK_INT < 33) return true;
        return context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }
}
