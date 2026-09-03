package com.jace.phonelending.consumer;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

import java.util.ArrayList;

/** Android 12+ admin-integrated provisioning mode selector. */
public final class GetProvisioningModeActivity extends Activity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT < 29) {
            setResult(RESULT_CANCELED);
            finish();
            return;
        }

        if (Build.VERSION.SDK_INT >= 31) {
            ArrayList<Integer> allowed = getIntent().getIntegerArrayListExtra(
                    DevicePolicyManager.EXTRA_PROVISIONING_ALLOWED_PROVISIONING_MODES);
            if (allowed != null && !allowed.contains(DevicePolicyManager.PROVISIONING_MODE_FULLY_MANAGED_DEVICE)) {
                setResult(RESULT_CANCELED);
                finish();
                return;
            }
        }

        Intent result = new Intent();
        result.putExtra(DevicePolicyManager.EXTRA_PROVISIONING_MODE,
                DevicePolicyManager.PROVISIONING_MODE_FULLY_MANAGED_DEVICE);
        // Do not skip Android's provisioning education screens. PhoneLending's
        // field recovery acknowledgment is an additional owner-only gate, not
        // a replacement for platform disclosure/education.
        setResult(RESULT_OK, result);
        finish();
    }
}
