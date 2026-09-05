#!/usr/bin/env bash
set -euo pipefail
AAPT2="$ANDROID_HOME/build-tools/36.0.0/aapt2"
HOST_APK="$(find apps/host/app/build/outputs/apk/debug -type f -name '*.apk' | head -n 1)"
RENTAL_APK="$(find apps/consumer/app/build/outputs/apk/fieldTest -type f -name '*.apk' | head -n 1)"

test "$($AAPT2 dump packagename "$HOST_APK")" = 'com.jace.phonelending.host.dev'
test "$($AAPT2 dump packagename "$RENTAL_APK")" = 'com.jace.phonelending.consumer.field'
$AAPT2 dump badging "$HOST_APK" | grep -q "versionCode='12' versionName='0.5.9-dev'"
$AAPT2 dump badging "$RENTAL_APK" | grep -q "versionCode='13' versionName='0.5.8-field'"

test -f contracts/PhoneLending_Host_Professional_UX_Correction_Addendum_v0.5.7.md
test -f contracts/PhoneLending_Rental_UX_Expiry_Refinement_Addendum_v0.5.8.md
test -f contracts/PhoneLending_Combined_Host_Renovation_Addendum_v0.5.9.md

python3 - <<'PY2'
from pathlib import Path
import re
host=Path('apps/host/app/src/main/java/com/jace/phonelending/host/MainActivity.java').read_text()
design=Path('apps/host/app/src/main/java/com/jace/phonelending/host/HostDesign.java').read_text()
prefs=Path('apps/host/app/src/main/java/com/jace/phonelending/host/HostPrefs.java').read_text()
main=Path('apps/consumer/app/src/main/java/com/jace/phonelending/consumer/MainActivity.java').read_text()
service=Path('apps/consumer/app/src/main/java/com/jace/phonelending/consumer/ConsumerService.java').read_text()
policy=Path('apps/consumer/app/src/main/java/com/jace/phonelending/consumer/PolicyController.java').read_text()
sessions=Path('apps/consumer/app/src/main/java/com/jace/phonelending/consumer/SessionStore.java').read_text()
contract=Path('contracts/PhoneLending_Combined_Host_Renovation_Addendum_v0.5.9.md').read_text()

allowed={'UNPROVISIONED','AVAILABLE_LOCKED','ACTIVE','EXPIRED_LOCKED','ADMIN_MAINTENANCE','RECOVERY_LOCKED'}
pairs=re.findall(r'public static final String ([A-Z_]+) = "([A-Z_]+)";', sessions)
names=[n for n,v in pairs if n==v and n in allowed]
assert len(names)==6 and set(names)==allowed,names

# Existing Host professional shell/architecture stays intact.
for x in ['WindowCompat.setDecorFitsSystemWindows(getWindow(), false)','WindowInsetsCompat.Type.systemBars()','WindowInsetsCompat.Type.displayCutout()','buildBottomNavigation','rental_start_picker','registerDefaultNetworkCallback','Technical details']:
    assert x in host,x
for x in ['PAGE_MARGIN = 18','BOTTOM_NAV_HEIGHT = 56','TEXT_PAGE_TITLE = 24','TEXT_BODY = 15']:
    assert x in design,x
assert 'v057CustomDurationMigrated' in prefs and 'return 12;' in prefs

# Dashboard is still exactly the intended three operator sections and has explicit target hierarchy.
dash=host[host.index('private void showDashboard'):host.index('private void showReadyDevicePicker')]
for x in ['Fleet overview','Pair a device','Start a rental','renderSelectedRentalTarget','Choose rental phone','Quick presets']:
    assert x in dash,x
assert 'dashboardActionCard("Pair Rental Phone"' in host
assert 'Choose the exact Rental phone before selecting time or price.' in host
assert 'RENTAL PHONE' in host

# Device inventory/detail polish and duplicate START control removal.
assert 'Search name or device ID' in host
assert 'd.deviceId.toLowerCase(Locale.US).contains(q)' in host
show_device=host[host.index('private void showDevice(DeviceRecord d)'):host.index('private void launchQrScanner()')]
assert 'renderRatePresetButtons(d)' not in show_device
assert 'renderCustomRentalControl(d)' not in show_device
assert 'openDashboardForRental(d)' in show_device
assert 'Refresh status' in show_device and 'Technical details' in show_device
assert 'showDeviceTechnicalDetails' in host

# Rental confirmation and pricing stay plain-language Host metadata only.
confirm=host[host.index('private void confirmStart(DeviceRecord d, long seconds, String label, Long displayedPriceCentavos)'):host.index('private void renderRatePresetButtons')]
for x in ['Rental phone: ','Duration: ','Price: ','Time starts only after the Rental phone confirms the request.','Confirm rental']:
    assert x in confirm,x
assert 'String.valueOf(seconds)' in confirm
assert 'priceCentavos' not in ''.join(p.read_text(errors='ignore') for p in Path('apps/consumer/app/src/main').rglob('*') if p.is_file())

# Preset editor and custom-rate UI now visually preview what the operator will use.
assert 'BUTTON PREVIEW' in host
assert 'RentalRatePreset.formatPesoWhole(cents)' in host
assert 'Minutes for ₱1' in host and '₱1 gives " + prefs.getMinutesPerPeso() + " minutes' in host
assert 'Tap the time to enter Hours + Minutes' in host
assert 'Price  •  ' in host and 'Time  •  ' in host

# Page-visible single-device reconciliation supplements the existing event-first fleet refresh.
assert 'private void refreshDeviceStatus(DeviceRecord d)' in host
assert 'navigateDevice(DeviceRecord d) { pushCurrentNavigation(); showDevice(d); refreshDeviceStatus(d); }' in host
assert 'client.command(this, prefs.getHostId(), d, "STATUS", "")' in host

# v0.5.8 Rental refinement remains present and no enforcement-strengthening is introduced here.
assert '"0.5.8-field".equals(versionName)' in policy
assert '.setOngoing(SessionStore.ACTIVE.equals(state))' in service
assert 'nextStatusNotificationHealthCheckElapsed = nowElapsed + 5000L' in service
assert service.count('policy.bringRentalStatusToFrontBestEffort();') == 1
for method in ['renderReady','renderActive','renderExpired','renderNeedsAttention']:
    start=main.index('private void '+method+'()')
    end=main.find('\n    private void ', start+1)
    block=main[start:end if end != -1 else len(main)]
    assert block.count('addVerticalFlexSpacer();') == 2, method

# Contract boundaries explicitly preserve authority/recovery semantics.
for phrase in ['Rental remains authoritative','six canonical states','Physical lock enforcement remains NOT QUALIFIED','no merge to `main`']:
    assert phrase.lower() in contract.lower(), phrase
PY2

echo 'PASS: v0.5.9 combined Host renovation with preserved v0.5.8 Rental refinement validated.'
