#!/usr/bin/env bash
set -euo pipefail
AAPT2="$ANDROID_HOME/build-tools/36.0.0/aapt2"
HOST_APK="$(find apps/host/app/build/outputs/apk/debug -type f -name '*.apk' | head -n 1)"
RENTAL_APK="$(find apps/consumer/app/build/outputs/apk/fieldTest -type f -name '*.apk' | head -n 1)"
test "$($AAPT2 dump packagename "$HOST_APK")" = 'com.jace.phonelending.host.dev'
test "$($AAPT2 dump packagename "$RENTAL_APK")" = 'com.jace.phonelending.consumer.field'
$AAPT2 dump badging "$HOST_APK" | grep -q "versionCode='9' versionName='0.5.5-dev'"
$AAPT2 dump badging "$RENTAL_APK" | grep -q "versionCode='11' versionName='0.5.5-field'"

grep -q '0.5.5-field' apps/consumer/app/src/main/java/com/jace/phonelending/consumer/PolicyController.java
grep -q 'PURPOSE_DECRYPT' apps/consumer/app/src/main/java/com/jace/phonelending/consumer/ConsumerIdentity.java
grep -q 'DIGEST_NONE' apps/consumer/app/src/main/java/com/jace/phonelending/consumer/ConsumerIdentity.java
grep -q 'ENCRYPTION_PADDING_RSA_PKCS1' apps/consumer/app/src/main/java/com/jace/phonelending/consumer/ConsumerIdentity.java
grep -q 'for (PairingPayload.Endpoint e : payload.endpoints)' apps/host/app/src/main/java/com/jace/phonelending/host/ConsumerClient.java
grep -q 'NsdDiscovery.resolve(context, payload.serviceName, 1800L)' apps/host/app/src/main/java/com/jace/phonelending/host/ConsumerClient.java

test -f apps/host/app/src/main/java/com/jace/phonelending/host/RentalRatePreset.java
grep -q 'showDashboard' apps/host/app/src/main/java/com/jace/phonelending/host/MainActivity.java
grep -q 'showDevices' apps/host/app/src/main/java/com/jace/phonelending/host/MainActivity.java
grep -q 'showSettings' apps/host/app/src/main/java/com/jace/phonelending/host/MainActivity.java
grep -q 'pagerNavigation' apps/host/app/src/main/java/com/jace/phonelending/host/MainActivity.java
grep -q 'MotionEvent' apps/host/app/src/main/java/com/jace/phonelending/host/MainActivity.java
grep -q 'THEME_LIGHT' apps/host/app/src/main/java/com/jace/phonelending/host/HostPrefs.java
grep -q 'THEME_DARK' apps/host/app/src/main/java/com/jace/phonelending/host/HostPrefs.java
grep -q 'THEME_SYSTEM' apps/host/app/src/main/java/com/jace/phonelending/host/HostPrefs.java
test -f apps/host/app/src/main/res/values-night/colors.xml
grep -q 'androidx.appcompat:appcompat:1.8.0' apps/host/app/build.gradle

grep -q '00:00:00' apps/consumer/app/src/main/java/com/jace/phonelending/consumer/MainActivity.java
grep -q '00:00:00' apps/consumer/app/src/main/java/com/jace/phonelending/consumer/ConsumerService.java
grep -q 'setVisibility(Notification.VISIBILITY_PUBLIC)' apps/consumer/app/src/main/java/com/jace/phonelending/consumer/ConsumerService.java
grep -q 'PAIRING_SUCCESS' apps/consumer/app/src/main/java/com/jace/phonelending/consumer/ConsumerService.java
grep -q 'SESSION_STARTED' apps/consumer/app/src/main/java/com/jace/phonelending/consumer/ConsumerService.java
grep -q 'SESSION_EXPIRED' apps/consumer/app/src/main/java/com/jace/phonelending/consumer/ConsumerService.java
test -f contracts/PhoneLending_Host_UX_Rate_Presets_Notification_Addendum_v0.5.5.md
grep -q 'automatic TTL' contracts/PhoneLending_Host_UX_Rate_Presets_Notification_Addendum_v0.5.5.md
grep -q 'does not create per-device `.log` files' services/control-service/README.md

python3 - <<'PY'
from pathlib import Path
import re
s=Path('apps/consumer/app/src/main/java/com/jace/phonelending/consumer/SessionStore.java').read_text()
allowed={'UNPROVISIONED','AVAILABLE_LOCKED','ACTIVE','EXPIRED_LOCKED','ADMIN_MAINTENANCE','RECOVERY_LOCKED'}
names=[n for n,v in re.findall(r'public static final String ([A-Z_]+) = "([A-Z_]+)";',s) if n==v and n in allowed]
assert len(names)==6 and set(names)==allowed, names
c=Path('apps/host/app/src/main/java/com/jace/phonelending/host/ConsumerClient.java').read_text()
assert c.index('for (PairingPayload.Endpoint e : payload.endpoints)') < c.index('NsdDiscovery.resolve(context, payload.serviceName, 1800L)')
hp=Path('apps/host/app/src/main/java/com/jace/phonelending/host/HostPrefs.java').read_text()
for duration,price in [(60,5),(120,10),(240,20),(600,50),(1200,100)]:
    assert f'new RentalRatePreset({duration}, {price})' in hp, (duration, price)
assert 'saveRatePreset' in hp and 'resetRatePresets' in hp
rental=Path('apps/consumer/app/src/main/java/com/jace/phonelending/consumer/ConsumerService.java').read_text()
assert 'pricePesos' not in rental and 'RentalRatePreset' not in rental
PY

echo 'PASS: v0.5.5 Host UX, presets, renter notification, diagnostics and authority boundaries validated.'
