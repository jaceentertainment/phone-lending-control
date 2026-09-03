package com.jace.phonelending.consumer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        PolicyController policy = new PolicyController(context);
        SessionStore sessions = new SessionStore(context);
        if (policy.isDeviceOwner()) {
            sessions.initializeProvisioned();
            if (SessionStore.ADMIN_MAINTENANCE.equals(sessions.getState())) {
                sessions.exitMaintenance();
            }
            sessions.expireIfNeeded();
            if (SessionStore.ACTIVE.equals(sessions.getState())) {
                policy.clearLockedHome();
            } else {
                policy.applyRestrictedAndBringToFront();
            }
        }
        ConsumerService.start(context);
    }
}
