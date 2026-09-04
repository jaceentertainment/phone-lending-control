#!/usr/bin/env bash
set -euo pipefail
AAPT2="$ANDROID_HOME/build-tools/36.0.0/aapt2"
HOST_APK="$(find apps/host/app/build/outputs/apk/debug -type f -name '*.apk' | head -n 1)"
RENTAL_APK="$(find apps/consumer/app/build/outputs/apk/fieldTest -type f -name '*.apk' | head -n 1)"

test "$($AAPT2 dump packagename "$HOST_APK")" = 'com.jace.phonelending.host.dev'
test "$($AAPT2 dump packagename "$RENTAL_APK")" = 'com.jace.phonelending.consumer.field'
$AAPT2 dump badging "$HOST_APK" | grep -q "versionCode='10' versionName='0.5.6-dev'"
$AAPT2 dump badging "$RENTAL_APK" | grep -q "versionCode='12' versionName='0.5.6-field'"

# Preserve the physically proven v0.5.4 transport/pairing lane.
grep -q '0.5.6-field' apps/consumer/app/src/main/java/com/jace/phonelending/consumer/PolicyController.java
grep -q 'PURPOSE_DECRYPT' apps/consumer/app/src/main/java/com/jace/phonelending/consumer/ConsumerIdentity.java
grep -q 'DIGEST_NONE' apps/consumer/app/src/main/java/com/jace/phonelending/consumer/ConsumerIdentity.java
grep -q 'ENCRYPTION_PADDING_RSA_PKCS1' apps/consumer/app/src/main/java/com/jace/phonelending/consumer/ConsumerIdentity.java
grep -q 'for (PairingPayload.Endpoint e : payload.endpoints)' apps/host/app/src/main/java/com/jace/phonelending/host/ConsumerClient.java
grep -q 'NsdDiscovery.resolve(context, payload.serviceName, 1800L)' apps/host/app/src/main/java/com/jace/phonelending/host/ConsumerClient.java

# Host professional shell/navigation/state model.
grep -q 'ArrayDeque<NavSnapshot>' apps/host/app/src/main/java/com/jace/phonelending/host/MainActivity.java
grep -q 'buildBottomNavigation' apps/host/app/src/main/java/com/jace/phonelending/host/MainActivity.java
grep -q 'WindowInsets.Type.systemBars' apps/host/app/src/main/java/com/jace/phonelending/host/MainActivity.java
grep -q 'Pair Rental Phone' apps/host/app/src/main/java/com/jace/phonelending/host/MainActivity.java
grep -q 'renderDeviceFilterChips' apps/host/app/src/main/java/com/jace/phonelending/host/MainActivity.java
grep -q 'showPresetEditor' apps/host/app/src/main/java/com/jace/phonelending/host/MainActivity.java
grep -q 'showSettingsCustomRental' apps/host/app/src/main/java/com/jace/phonelending/host/MainActivity.java
grep -q 'smartRefreshDelayMs' apps/host/app/src/main/java/com/jace/phonelending/host/MainActivity.java
grep -q '15_000L' apps/host/app/src/main/java/com/jace/phonelending/host/MainActivity.java
grep -q '30_000L' apps/host/app/src/main/java/com/jace/phonelending/host/MainActivity.java
grep -q '60_000L' apps/host/app/src/main/java/com/jace/phonelending/host/MainActivity.java
grep -q '120_000L' apps/host/app/src/main/java/com/jace/phonelending/host/MainActivity.java
for icon in ic_nav_home.xml ic_nav_devices.xml ic_nav_settings.xml; do test -f "apps/host/app/src/main/res/drawable/$icon"; done

# Host-only money model and v0.5.5 preset migration.
grep -q 'priceCentavos' apps/host/app/src/main/java/com/jace/phonelending/host/RentalRatePreset.java
grep -q 'pricePesos' apps/host/app/src/main/java/com/jace/phonelending/host/RentalRatePreset.java
grep -q 'BigDecimal' apps/host/app/src/main/java/com/jace/phonelending/host/RentalRatePreset.java
grep -q 'BigDecimal' apps/host/app/src/main/java/com/jace/phonelending/host/MainActivity.java
grep -q 'billingUnitMinutes' apps/host/app/src/main/java/com/jace/phonelending/host/HostPrefs.java
grep -q 'pricePerBillingUnitCentavos' apps/host/app/src/main/java/com/jace/phonelending/host/HostPrefs.java
grep -q 'INPUT_TIME' apps/host/app/src/main/java/com/jace/phonelending/host/HostPrefs.java
grep -q 'INPUT_AMOUNT' apps/host/app/src/main/java/com/jace/phonelending/host/HostPrefs.java
grep -q 'RoundingMode.UNNECESSARY' apps/host/app/src/main/java/com/jace/phonelending/host/MainActivity.java
grep -q 'cents%per!=0' apps/host/app/src/main/java/com/jace/phonelending/host/MainActivity.java

# Rental presentation and single-countdown notification.
grep -q 'accessibleRemainingTime' apps/consumer/app/src/main/java/com/jace/phonelending/consumer/MainActivity.java
grep -q '54, Typeface.BOLD' apps/consumer/app/src/main/java/com/jace/phonelending/consumer/MainActivity.java
grep -q '00:00:00' apps/consumer/app/src/main/java/com/jace/phonelending/consumer/MainActivity.java
grep -q 'RemoteViews' apps/consumer/app/src/main/java/com/jace/phonelending/consumer/ConsumerService.java
grep -q 'DecoratedCustomViewStyle' apps/consumer/app/src/main/java/com/jace/phonelending/consumer/ConsumerService.java
grep -q 'setChronometerCountDown' apps/consumer/app/src/main/java/com/jace/phonelending/consumer/ConsumerService.java
grep -q 'warned300' apps/consumer/app/src/main/java/com/jace/phonelending/consumer/ConsumerService.java
grep -q 'warned60' apps/consumer/app/src/main/java/com/jace/phonelending/consumer/ConsumerService.java
grep -q 'lastNotificationEndEpoch' apps/consumer/app/src/main/java/com/jace/phonelending/consumer/ConsumerService.java
test -f apps/consumer/app/src/main/res/layout/notification_rental_active.xml
test -f apps/consumer/app/src/main/res/layout/notification_rental_active_big.xml
test -f apps/consumer/app/src/main/res/values-night/colors.xml

test -f contracts/PhoneLending_Professional_UX_Architecture_Addendum_v0.5.6.md
grep -q 'exactly six canonical states' contracts/PhoneLending_Professional_UX_Architecture_Addendum_v0.5.6.md
grep -q 'server-assisted Recovery remains contractually defined but not implemented' contracts/PhoneLending_Professional_UX_Architecture_Addendum_v0.5.6.md

python3 - <<'PY'
from pathlib import Path
import re

# Exactly six canonical states remain defined.
s = Path('apps/consumer/app/src/main/java/com/jace/phonelending/consumer/SessionStore.java').read_text()
allowed = {'UNPROVISIONED','AVAILABLE_LOCKED','ACTIVE','EXPIRED_LOCKED','ADMIN_MAINTENANCE','RECOVERY_LOCKED'}
pairs = re.findall(r'public static final String ([A-Z_]+) = "([A-Z_]+)";', s)
names = [n for n,v in pairs if n == v and n in allowed]
assert len(names) == 6 and set(names) == allowed, names

# Direct QR endpoint remains before NSD fallback.
c = Path('apps/host/app/src/main/java/com/jace/phonelending/host/ConsumerClient.java').read_text()
assert c.index('for (PairingPayload.Endpoint e : payload.endpoints)') < c.index('NsdDiscovery.resolve(context, payload.serviceName, 1800L)')

# Five fixed, independently configurable defaults use integer centavos.
hp = Path('apps/host/app/src/main/java/com/jace/phonelending/host/HostPrefs.java').read_text()
for duration, cents in [(60,500),(120,1000),(240,2000),(600,5000),(1200,10000)]:
    assert f'new RentalRatePreset({duration}, {cents})' in hp, (duration, cents)
assert 'for (int i = 0; i < 5; i++)' in hp
assert 'THEME_SYSTEM' in hp and 'prefs.getString("themeMode", THEME_SYSTEM)' in hp

# Dashboard owns pairing; Devices inventory does not embed pairing actions.
host = Path('apps/host/app/src/main/java/com/jace/phonelending/host/MainActivity.java').read_text()
start = host.index('private void showDevices(')
end = host.find('\n    private ', start + 20)
devices_method = host[start:end]
assert 'launchQrScanner' not in devices_method and 'Pair Rental Phone' not in devices_method
assert 'request(d, "START", String.valueOf(seconds)' in host

# Price/business metadata never moves into Rental implementation.
consumer_tree = ''.join(p.read_text(errors='ignore') for p in Path('apps/consumer/app/src/main').rglob('*') if p.is_file())
assert 'priceCentavos' not in consumer_tree
assert 'RentalRatePreset' not in consumer_tree

# Each custom notification view contains one, and only one, visible countdown widget.
for path in ['apps/consumer/app/src/main/res/layout/notification_rental_active.xml',
             'apps/consumer/app/src/main/res/layout/notification_rental_active_big.xml']:
    x = Path(path).read_text()
    assert x.count('<Chronometer') == 1, (path, x.count('<Chronometer'))

# Status notification refresh is gated by state/deadline changes rather than every service tick.
rental = Path('apps/consumer/app/src/main/java/com/jace/phonelending/consumer/ConsumerService.java').read_text()
gate = 'if (!state.equals(lastNotificationState) || notificationEnd != lastNotificationEndEpoch)'
assert gate in rental
assert rental.index(gate) < rental.index('nm.notify(STATUS_NOTIFICATION, buildStatusNotification())')
assert 'rem <= 300' in rental and 'rem <= 60' in rental
identifiers = set(re.findall(r'\bwarned\d+\b', rental))
assert 'warned300' in identifiers and 'warned60' in identifiers, identifiers
assert 'warned30' not in identifiers and 'warned10' not in identifiers, identifiers
PY

echo 'PASS: v0.5.6 professional Host/Rental UX, pricing, notification, pairing and authority boundaries validated.'
