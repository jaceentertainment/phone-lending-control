package com.jace.phonelending.consumer;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/** Final policy reconciliation step for admin-integrated provisioning. */
public final class PolicyComplianceActivity extends Activity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PolicyController policy = new PolicyController(this);
        SessionStore sessions = new SessionStore(this);

        if (!policy.isDeviceOwner()) {
            setResult(RESULT_CANCELED);
            finish();
            return;
        }

        sessions.initializeProvisioned();
        sessions.expireIfNeeded();
        if (SessionStore.ACTIVE.equals(sessions.getState())) {
            policy.clearLockedHome();
        } else {
            policy.applyLockedHome();
        }

        setResult(RESULT_OK, new Intent());
        finish();
    }
}
