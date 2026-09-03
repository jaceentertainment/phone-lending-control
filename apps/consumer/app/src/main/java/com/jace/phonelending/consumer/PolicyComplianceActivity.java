package com.jace.phonelending.consumer;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;

/** Final policy reconciliation step for admin-integrated provisioning. */
public final class PolicyComplianceActivity extends Activity {
    private PolicyController policy;
    private SessionStore sessions;
    private boolean completing;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        policy = new PolicyController(this);
        sessions = new SessionStore(this);

        if (!policy.isDeviceOwner()) {
            setResult(RESULT_CANCELED);
            finish();
            return;
        }

        sessions.initializeProvisioned();
        sessions.expireIfNeeded();

        // Field-test builds must expose the recovery credential created AFTER
        // a factory reset / managed download before restricted HOME is applied.
        // The hook lives only in test source sets; production has no such class.
        if (invokeTestProvisioningRecoveryGate()) return;

        completeProvisioning();
    }

    @Override public void onBackPressed() {
        // Provisioning compliance is owner setup, not a renter escape route.
        // The platform can cancel provisioning outside this activity if needed.
    }

    public void completeProvisioning() {
        if (completing || isFinishing()) return;
        completing = true;

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

    private boolean invokeTestProvisioningRecoveryGate() {
        if ((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) == 0) return false;
        try {
            Class<?> clazz = Class.forName("com.jace.phonelending.consumer.DebugRecoveryHooks");
            Object result = clazz.getMethod(
                            "showProvisioningRecoveryGate",
                            PolicyComplianceActivity.class,
                            Runnable.class)
                    .invoke(null, this, (Runnable) this::completeProvisioning);
            return Boolean.TRUE.equals(result);
        } catch (NoSuchMethodException ignored) {
            return false;
        } catch (Throwable failure) {
            setResult(RESULT_CANCELED);
            finish();
            return true;
        }
    }
}
