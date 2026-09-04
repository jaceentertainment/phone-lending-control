package com.jace.phonelending.consumer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        SessionStore sessions = new SessionStore(context);
        sessions.reconcileMaintenance();
        sessions.expireIfNeeded();
        sessions.reconcileSoftLockLeaseAfterBoot();
        ConsumerService.start(context);
    }
}
