#!/usr/bin/env bash
set -euo pipefail
AAPT2="$ANDROID_HOME/build-tools/36.0.0/aapt2"
HOST_APK="$(find apps/host/app/build/outputs/apk/debug -type f -name '*.apk' | head -n 1)"
RENTAL_APK="$(find apps/consumer/app/build/outputs/apk/fieldTest -type f -name '*.apk' | head -n 1)"

test "$($AAPT2 dump packagename "$HOST_APK")" = 'com.jace.phonelending.host.dev'
test "$($AAPT2 dump packagename "$RENTAL_APK")" = 'com.jace.phonelending.consumer.field'
$AAPT2 dump badging "$HOST_APK" | grep -q "versionCode='11' versionName='0.5.7-dev'"
$AAPT2 dump badging "$RENTAL_APK" | grep -q "versionCode='13' versionName='0.5.8-field'"

test -f contracts/PhoneLending_Host_Professional_UX_Correction_Addendum_v0.5.7.md
test -f contracts/PhoneLending_Rental_UX_Expiry_Refinement_Addendum_v0.5.8.md

python3 - <<'PY'
from pathlib import Path
import re

host=Path('apps/host/app/src/main/java/com/jace/phonelending/host/MainActivity.java').read_text()
main=Path('apps/consumer/app/src/main/java/com/jace/phonelending/consumer/MainActivity.java').read_text()
service=Path('apps/consumer/app/src/main/java/com/jace/phonelending/consumer/ConsumerService.java').read_text()
policy=Path('apps/consumer/app/src/main/java/com/jace/phonelending/consumer/PolicyController.java').read_text()
build=Path('apps/consumer/app/build.gradle').read_text()
contract=Path('contracts/PhoneLending_Rental_UX_Expiry_Refinement_Addendum_v0.5.8.md').read_text()
sessions=Path('apps/consumer/app/src/main/java/com/jace/phonelending/consumer/SessionStore.java').read_text()

for x in ['WindowCompat.setDecorFitsSystemWindows(getWindow(), false)','renderDashboardStartRental','rental_start_picker','Technical details']:
    assert x in host, x
prefs=Path('apps/host/app/src/main/java/com/jace/phonelending/host/HostPrefs.java').read_text()
assert 'v057CustomDurationMigrated' in prefs

allowed={'UNPROVISIONED','AVAILABLE_LOCKED','ACTIVE','EXPIRED_LOCKED','ADMIN_MAINTENANCE','RECOVERY_LOCKED'}
pairs=re.findall(r'public static final String ([A-Z_]+) = "([A-Z_]+)";', sessions)
names=[n for n,v in pairs if n==v and n in allowed]
assert len(names)==6 and set(names)==allowed, names

assert "versionCode 13" in build and "versionName '0.5.8'" in build
assert '"0.5.8-field".equals(versionName)' in policy
qual=policy[policy.index('public boolean isTimerQualificationBuild()'):policy.index('public boolean softLockPermissionGranted()')]
assert '"0.5.8-field".equals(versionName)' in qual
restricted=policy[policy.index('public void applyRestrictedAndBringToFront()'):policy.index('public void bringRentalStatusToFrontBestEffort()')]
assert 'if (isTimerQualificationBuild())' in restricted and 'return;' in restricted

for method in ['renderReady','renderActive','renderExpired','renderNeedsAttention']:
    start=main.index('private void '+method+'()')
    end=main.find('\n    private void ', start+1)
    block=main[start:end if end != -1 else len(main)]
    assert block.count('addVerticalFlexSpacer();') == 2, (method, block.count('addVerticalFlexSpacer();'))
for method in ['renderOverlaySetupStep','renderPairingSetupStep']:
    start=main.index('private void '+method+'()')
    end=main.find('\n    private void ', start+1)
    block=main[start:end if end != -1 else len(main)]
    assert 'addVerticalFlexSpacer();' not in block, method

assert '.setOngoing(SessionStore.ACTIVE.equals(state))' in service
assert 'statusNotificationPresent()' in service
assert 'nextStatusNotificationHealthCheckElapsed = nowElapsed + 5000L' in service
assert 'TIMER_NOTIFICATION_RESTORED' in service
assert '.setContentTitle("Rental ended")' in service and '.setContentText("00:00:00")' in service

assert 'boolean requestExpiryUiHandoff = expired && policy.isTimerQualificationBuild();' in service
assert service.count('policy.bringRentalStatusToFrontBestEffort();') == 1
handoff=policy[policy.index('public void bringRentalStatusToFrontBestEffort()'):policy.index('public void applyActiveAndOpenHome()')]
assert 'MainActivity.class' in handoff
assert 'while (' not in handoff and 'postDelayed' not in handoff
assert 'SessionStore' not in handoff
assert 'one best-effort request' in contract.lower()
assert 'not called a lock' in contract.lower()

consumer=''.join(p.read_text(errors='ignore') for p in Path('apps/consumer/app/src/main').rglob('*') if p.is_file())
assert 'RentalRatePreset' not in consumer and 'priceCentavos' not in consumer and 'minutesPerPeso' not in consumer
PY

echo 'PASS: v0.5.8 Rental UX/expiry refinement, Host preservation, and protected boundaries validated.'
