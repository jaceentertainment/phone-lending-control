#!/usr/bin/env bash
set -euo pipefail
MAIN='apps/host/app/src/main/java/com/jace/phonelending/host/MainActivity.java'
DESIGN='apps/host/app/src/main/java/com/jace/phonelending/host/HostDesign.java'
STATE='apps/host/app/src/main/java/com/jace/phonelending/host/HostUiState.java'
PREFS='apps/host/app/src/main/java/com/jace/phonelending/host/HostPrefs.java'
HOST_GRADLE='apps/host/app/build.gradle'
RENTAL_GRADLE='apps/consumer/app/build.gradle'

grep -q "versionCode 14" "$HOST_GRADLE"
grep -q "versionName '0.6.0'" "$HOST_GRADLE"
grep -q "versionCode 13" "$RENTAL_GRADLE"
grep -q "versionName '0.5.8'" "$RENTAL_GRADLE"

grep -q 'topAppBar("Dashboard", "PhoneLending Host")' "$MAIN"
grep -q 'BOTTOM_NAV_HEIGHT' "$MAIN"
grep -q 'systemBarInsets' "$MAIN"
grep -q 'setContentDescription("Dashboard")' "$MAIN"
grep -q 'setContentDescription("Devices")' "$MAIN"
grep -q 'setContentDescription("Settings")' "$MAIN"
grep -q 'dot.setBackground' "$MAIN"
! grep -q 'View devices  ›' "$MAIN"

grep -q 'TEXT_PAGE_TITLE = 22' "$DESIGN"
grep -q 'TEXT_BODY = 14' "$DESIGN"
grep -q 'TOUCH_TARGET = 48' "$DESIGN"
grep -q 'APP_BAR_HEIGHT = 58' "$DESIGN"
grep -q 'BOTTOM_NAV_HEIGHT = 58' "$DESIGN"

test -f "$STATE"
grep -q 'class Dashboard' "$STATE"
grep -q 'class Devices' "$STATE"
grep -q 'class Settings' "$STATE"
grep -q 'buildDevicesUiState' "$MAIN"

grep -q 'sectionTitle("Fleet")' "$MAIN"
grep -q 'Pair Rental Phone' "$MAIN"
grep -q 'sectionTitle("Start Rental")' "$MAIN"
grep -q 'dashboardSelectedPresetIndex' "$MAIN"
grep -q 'renderSelectedPresetSummary' "$MAIN"
grep -q 'primaryButton(summary, "Start Rental"' "$MAIN"

grep -q 'addFilterChip(row, "OFFLINE", "Offline")' "$MAIN"
grep -q 'addDeviceListRow' "$MAIN"
grep -q 'isOfflineMetadata' "$MAIN"
grep -q 'promptRenameDevice' "$MAIN"
grep -q 'nextDefaultDeviceName' "$MAIN"

for label in 'Appearance' 'Rental' 'Device Management' 'Support' 'Advanced' 'About'; do grep -q "$label" "$MAIN"; done
grep -q 'topAppBar("Rental Presets", "Settings")' "$MAIN"
grep -q 'Preset .* of 5' "$MAIN" || grep -q 'Preset " + (index + 1) + " of 5' "$MAIN"
grep -q 'Restore custom rental defaults' "$MAIN"
grep -q 'resetCustomRentalSettings' "$PREFS"

grep -q 'DEFAULT_MINUTES_PER_PESO = 12' "$PREFS"
grep -q 'DEFAULT_CUSTOM_DURATION_MINUTES = 12' "$PREFS"
! grep -Rq 'priceCentavos\|billingUnitMinutes\|pricePerUnitCentavos' apps/consumer shared

for state in UNPROVISIONED AVAILABLE_LOCKED ACTIVE EXPIRED_LOCKED ADMIN_MAINTENANCE RECOVERY_LOCKED; do grep -Rq "$state" apps shared; done
! grep -Rq 'ATTENTION_LOCKED\|OFFLINE_LOCKED' apps shared
! grep -Rq 'androidx.compose' apps/host

echo 'v0.6.0 Professional Host UX blueprint acceptance: PASS'
