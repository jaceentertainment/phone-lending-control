#!/usr/bin/env bash
set -euo pipefail

HOST_MAIN='apps/host/app/src/main/java/com/jace/phonelending/host/MainActivity.java'
HOST_PREFS='apps/host/app/src/main/java/com/jace/phonelending/host/HostPrefs.java'
HOST_QR='apps/host/app/src/main/java/com/jace/phonelending/host/ManagementQrPayload.java'
RENTAL_MAIN='apps/consumer/app/src/main/java/com/jace/phonelending/consumer/MainActivity.java'
RENTAL_QR='apps/consumer/app/src/main/java/com/jace/phonelending/consumer/ManagementQrPayload.java'
HOST_GRADLE='apps/host/app/build.gradle'
RENTAL_GRADLE='apps/consumer/app/build.gradle'
DESIGN_MASTER='Contracts/PhoneLending_Owner_Design_Master_v0.7.0.md'

# Version lineage. Host and Rental remain independently versioned.
grep -q "versionCode 15" "$HOST_GRADLE"
grep -q "versionName '0.7.0'" "$HOST_GRADLE"
grep -q "versionCode 14" "$RENTAL_GRADLE"
grep -q "versionName '0.5.9'" "$RENTAL_GRADLE"

# Main shell/navigation architecture: exactly two top-level pages, Settings|Dashboard.
grep -q "implementation 'androidx.viewpager2:viewpager2:1.1.0'" "$HOST_GRADLE"
grep -q 'class MainPagerAdapter' "$HOST_MAIN"
grep -q 'getItemCount() { return 2; }' "$HOST_MAIN"
grep -q 'position == 0 ? buildSettingsMainPage() : buildDashboardMainPage()' "$HOST_MAIN"
grep -q 'mainPageIndex == 1 ? "○    ●" : "●    ○"' "$HOST_MAIN"
! grep -q 'addBottomNavItem.*Devices' "$HOST_MAIN"

# Dashboard routes directly to status-specific lists, not a Devices landing page.
for filter in ACTIVE READY EXPIRED ATTENTION; do
  grep -q "\"$filter\"" "$HOST_MAIN"
done
grep -q 'tile.setOnClickListener(v -> navigateDevicesFilter(filter))' "$HOST_MAIN"
grep -q 'screenMode = "device_list"' "$HOST_MAIN"
grep -q 'deviceListTitle(deviceFilter)' "$HOST_MAIN"

# Dashboard immediate actions use distinct Pair vs Add-Time QR purposes.
grep -q 'Pair Rental Phone.*launchQrScanner("PAIR")' "$HOST_MAIN"
grep -q 'Add Time using QR.*launchQrScanner("ADD_TIME")' "$HOST_MAIN"
grep -q 'R.color.pl_primary' "$HOST_MAIN"
grep -q 'R.color.pl_success' "$HOST_MAIN"

# Back behavior / contextual submenus.
grep -q 'if (!navStack.isEmpty())' "$HOST_MAIN"
grep -q 'if (isMainPagerScreen() && !"dashboard".equals(screenMode))' "$HOST_MAIN"
grep -q '"device_list".equals(nav.screen)' "$HOST_MAIN"
grep -q '"rental_extend".equals(nav.screen)' "$HOST_MAIN"
grep -q '"settings_rental".equals(nav.screen)' "$HOST_MAIN"

# Manual Add Time and QR Add Time converge on the same chooser.
grep -q 'navigateRentalChooser(d, true)' "$HOST_MAIN"
grep -q 'showRentalChooser(device, true)' "$HOST_MAIN"
grep -q 'String command = addTime ? "EXTEND" : "START"' "$HOST_MAIN"

# Management QR is identification-only; Host reconciles authoritative state/session first.
test -f "$HOST_QR"
test -f "$RENTAL_QR"
grep -q 'PURPOSE_ADD_TIME = "add_time"' "$HOST_QR"
grep -q 'PURPOSE_ADD_TIME = "add_time"' "$RENTAL_QR"
grep -q 'client.command(this, prefs.getHostId(), device, "STATUS", "")' "$HOST_MAIN"
grep -q 'payload.sessionId.equals(device.sessionId)' "$HOST_MAIN"
grep -q '"ACTIVE".equals(device.state)' "$HOST_MAIN"
grep -q 'Show QR to Add Time' "$RENTAL_MAIN"
grep -q 'renderAddTimeQr' "$RENTAL_MAIN"
# No key/token/secret parameter may be added to the management QR format.
! grep -Eqi 'appendQueryParameter\("(tok|token|secret|key|pkh|private|signature)' "$HOST_QR" "$RENTAL_QR"

# Pricing model and exact Host-only math.
grep -q 'DEFAULT_MINUTES_PER_PESO = 12' "$HOST_PREFS"
grep -q 'DEFAULT_CUSTOM_DURATION_MINUTES = 12' "$HOST_PREFS"
for value in '60, 500' '120, 1000' '240, 2000' '600, 5000' '1200, 10000'; do grep -q "RentalRatePreset($value)" "$HOST_PREFS"; done
grep -q 'Math.multiplyExact(minutes, 100L)' "$HOST_MAIN"
grep -q 'String.valueOf(minutes \* 60L)' "$HOST_MAIN"
# Rental protocol stays duration-only: no price fields cross into Rental/shared protocol.
! grep -Rq 'priceCentavos\|minutesPerPeso\|pricePerBillingUnitCentavos' apps/consumer

# Settings hierarchy and preset editor remain routed through submenus.
grep -q 'settingsLandingRow(content, "Rental"' "$HOST_MAIN"
grep -q 'showSettingsRental()' "$HOST_MAIN"
grep -q 'showSettingsPresets()' "$HOST_MAIN"
grep -q 'showPresetEditor' "$HOST_MAIN"
grep -q 'Edit Rental Button' "$HOST_MAIN"
grep -q 'Restore defaults' "$HOST_MAIN"

# Friendly names are first-class; legacy technical-name records are normalized.
grep -q 'normalizeFriendlyDeviceNames' "$HOST_MAIN"
grep -q 'nextDefaultDeviceName' "$HOST_MAIN"
grep -q 'Device details  ›' "$HOST_MAIN"

# Frozen canonical state vocabulary remains intact; Attention is metadata/UI, not a seventh canonical state.
for state in UNPROVISIONED AVAILABLE_LOCKED ACTIVE EXPIRED_LOCKED ADMIN_MAINTENANCE RECOVERY_LOCKED; do grep -Rq "$state" apps; done
! grep -Rq 'ATTENTION_LOCKED\|OFFLINE_LOCKED' apps

# Existing command security boundary remains present.
grep -q 'client.command(this, prefs.getHostId()' "$HOST_MAIN"
grep -q 'accepted' apps/host/app/src/main/java/com/jace/phonelending/host/ConsumerClient.java
! grep -Rq 'androidx.compose' apps/host apps/consumer

# Owner-directed design authority record must travel with the candidate.
test -f "$DESIGN_MASTER"
grep -q 'Do not visually re-audit' "$DESIGN_MASTER"

echo 'v0.7.0 code/architecture conformance: PASS (physical visual acceptance not asserted)'
