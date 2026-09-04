#!/usr/bin/env bash
set -euo pipefail
AAPT2="$ANDROID_HOME/build-tools/36.0.0/aapt2"
HOST_APK="$(find apps/host/app/build/outputs/apk/debug -type f -name '*.apk' | head -n 1)"
RENTAL_APK="$(find apps/consumer/app/build/outputs/apk/fieldTest -type f -name '*.apk' | head -n 1)"

test "$($AAPT2 dump packagename "$HOST_APK")" = 'com.jace.phonelending.host.dev'
test "$($AAPT2 dump packagename "$RENTAL_APK")" = 'com.jace.phonelending.consumer.field'
$AAPT2 dump badging "$HOST_APK" | grep -q "versionCode='11' versionName='0.5.7-dev'"
$AAPT2 dump badging "$RENTAL_APK" | grep -q "versionCode='12' versionName='0.5.6-field'"

test -f contracts/PhoneLending_Host_Professional_UX_Correction_Addendum_v0.5.7.md
test -f apps/host/app/src/main/java/com/jace/phonelending/host/HostDesign.java

python3 - <<'PY'
from pathlib import Path
import re
host=Path('apps/host/app/src/main/java/com/jace/phonelending/host/MainActivity.java').read_text()
prefs=Path('apps/host/app/src/main/java/com/jace/phonelending/host/HostPrefs.java').read_text()
design=Path('apps/host/app/src/main/java/com/jace/phonelending/host/HostDesign.java').read_text()
contract=Path('contracts/PhoneLending_Host_Professional_UX_Correction_Addendum_v0.5.7.md').read_text()

# Exact protected state model.
s=Path('apps/consumer/app/src/main/java/com/jace/phonelending/consumer/SessionStore.java').read_text()
allowed={'UNPROVISIONED','AVAILABLE_LOCKED','ACTIVE','EXPIRED_LOCKED','ADMIN_MAINTENANCE','RECOVERY_LOCKED'}
pairs=re.findall(r'public static final String ([A-Z_]+) = "([A-Z_]+)";',s)
names=[n for n,v in pairs if n==v and n in allowed]
assert len(names)==6 and set(names)==allowed,names

# System bars / fixed shell.
for x in ['WindowCompat.setDecorFitsSystemWindows(getWindow(), false)','WindowInsetsCompat.Type.systemBars()','WindowInsetsCompat.Type.displayCutout()','appBarHost','bottomSystemHost','R.color.pl_surface']:
    assert x in host,x
assert host.index('shell.addView(appBarHost') < host.index('shell.addView(scroll') < host.index('shell.addView(bottomSystemHost')
assert 'bottomSystemHost.setPadding(bars.left, 0, bars.right, bars.bottom)' in host
assert 'appBarHost.setPadding(bars.left + dp(HostDesign.PAGE_MARGIN), bars.top' in host

# Back semantics and origin stack.
assert 'if (isMainPagerScreen() && !"dashboard".equals(screenMode))' in host
assert 'navStack.clear();\n        if (clamped == 0)' in host
assert 'pushCurrentNavigation();\n        deviceFilter = filter' in host
assert 'rental_start_picker' in host

# Bottom navigation: semantic icons + compact page indicator, no repeated visible labels.
nav=host[host.index('private LinearLayout buildBottomNavigation()'):host.index('private void renderDeviceFilterChips()')]
assert 'item.setContentDescription(label)' in nav
assert 'TextView name' not in nav and 'text(label' not in nav
assert 'indicator' in nav and 'ImageView icon' in nav
assert 'addPagerItem' not in host

# Dashboard requirements.
dash=host[host.index('private void showDashboard'):host.index('private void showDevices')]
for x in ['Fleet overview','Pair a device','Start a rental','renderDashboardStartRental']:
    assert x in dash,x
assert 'Active rentals' not in dash
assert 'showReadyDevicePicker' in host

# Devices remains inventory only.
dev=host[host.index('private void showDevices'):host.index('private void showSettings')]
assert 'Pair Rental Phone' not in dev and 'launchQrScanner' not in dev
for x in ['ALL','ACTIVE','READY','EXPIRED','ATTENTION']:
    assert x in host

# Settings grouping / subpages.
settings=host[host.index('private void showSettings()'):host.index('private void mainHeading')]
for x in ['Appearance','Rental','Device management','Support','Advanced','About']:
    assert f'sectionTitle("{x}")' in settings,x
for x in ['settings_devices','settings_advanced','settings_support','settings_about','settings_presets','settings_custom','settings_appearance']:
    assert x in host,x

# Plain language first; technical detail remains secondary.
pair=host[host.index('private void pairScannedPayload'):host.index('private void promptAlias')]
assert 'Stage: ' not in pair
assert 'Technical details' in pair
assert 'private void showPairingTechnicalDetails' in host

# Typography / spacing tokens: semantic text may not use arbitrary literal sizes.
for x in ['TEXT_PAGE_TITLE','TEXT_SECTION_TITLE','TEXT_BODY','TEXT_SECONDARY','TEXT_LABEL','TEXT_METADATA','TEXT_SUMMARY','TEXT_DISPLAY','TOUCH_TARGET','PAGE_MARGIN','APP_BAR_HEIGHT','BOTTOM_NAV_HEIGHT']:
    assert x in design,x
literal=set(re.findall(r'text\([^\n]*?,\s*(\d+)\s*,\s*Typeface',host))
assert literal.issubset({'24','28'}),literal

# Whole-peso custom model, default ₱1 = 12 min, and upgrade migration from v0.5.6's saved 60-minute start.
assert 'getMinutesPerPeso' in prefs and 'return 12;' in prefs
assert 'v057CustomDurationMigrated' in prefs
assert 'prefs.edit().putInt("customDurationMinutes", unit)' in prefs
assert 'prefs.getInt("customDurationMinutes", unit)' in prefs
assert 'parseWholePesos' in host and 'parseWholePesoCentavos' in host
assert '₱1 gives " + unit + " minutes' in host
for call in re.findall(r'labeledInput\([^\n]+',host):
    assert 'false, true' not in call,call
assert 'TYPE_NUMBER_FLAG_DECIMAL' in host  # legacy helper capability may remain, but v0.5.7 pricing UI does not use it.

# Five configurable defaults stay Host-only; whitespace is not part of the contract.
for duration,cents in [(60,500),(120,1000),(240,2000),(600,5000),(1200,10000)]:
    assert re.search(r'new\s+RentalRatePreset\(\s*%d\s*,\s*%d\s*\)' % (duration,cents), prefs), (duration,cents)
consumer=''.join(p.read_text(errors='ignore') for p in Path('apps/consumer/app/src/main').rglob('*') if p.is_file())
assert 'RentalRatePreset' not in consumer and 'priceCentavos' not in consumer and 'minutesPerPeso' not in consumer

# Event-first reconciliation triggers, with adaptive fallback retained.
assert 'registerDefaultNetworkCallback' in host
assert 'refreshAfterEvent' in host
assert 'showDashboard(true)' in host and 'showDevices(true)' in host
assert 'smartRefreshDelayMs' in host

# Contract itself protects Rental and recovery/enforcement boundaries.
assert 'No file under `apps/consumer/` may change' in contract
assert 'Physical lock enforcement remains NOT QUALIFIED' in contract
assert 'server-assisted recovery remains contractually defined but not implemented' in contract.lower()
PY

echo 'PASS: v0.5.7 Host professional redesign and Rental-preservation boundaries validated.'
