package com.jace.phonelending.consumer;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

/** Android 12+ admin-integrated provisioning mode selector. */
public final class GetProvisioningModeActivity extends Activity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT < 29) {
            setResult(RESULT_CANCELED);
            finish();
            return;
        }
        Intent result = new Intent();
        result.putExtra(DevicePolicyManager.EXTRA_PROVISIONING_MODE,
                DevicePolicyManager.PROVISIONING_MODE_FULLY_MANAGED_DEVICE);
        result.putExtra(DevicePolicyManager.EXTRA_PROVISIONING_SKIP_EDUCATION_SCREENS, true);
        setResult(RESULT_OK, result);
        finish();
    }
}
