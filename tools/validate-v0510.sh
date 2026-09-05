#!/usr/bin/env bash
set -euo pipefail
MAIN='apps/host/app/src/main/java/com/jace/phonelending/host/MainActivity.java'
DESIGN='apps/host/app/src/main/java/com/jace/phonelending/host/HostDesign.java'
COLORS='apps/host/app/src/main/res/values/colors.xml'
GRADLE='apps/host/app/build.gradle'
grep -q "versionCode 13" "$GRADLE"
grep -q "versionName '0.5.10'" "$GRADLE"
grep -q 'renderFleetOverviewPanel' "$MAIN"
grep -q 'fleetMetricTile' "$MAIN"
grep -q 'SELECTED RENTAL PHONE' "$MAIN"
grep -q 'settingsGroupCard' "$MAIN"
grep -q 'settingsGroupRow' "$MAIN"
grep -q 'pl_primary_soft' "$MAIN"
grep -q 'setElevation(dp(3))' "$MAIN"
grep -q 'TEXT_PAGE_TITLE = 28' "$DESIGN"
grep -q 'APP_BAR_HEIGHT = 64' "$DESIGN"
grep -q 'BOTTOM_NAV_HEIGHT = 64' "$DESIGN"
grep -q 'pl_surface_elevated' "$COLORS"
grep -q 'pl_primary_soft' "$COLORS"
# Protected state vocabulary remains present and no seventh canonical state is introduced here.
for state in UNPROVISIONED AVAILABLE_LOCKED ACTIVE EXPIRED_LOCKED ADMIN_MAINTENANCE RECOVERY_LOCKED; do grep -Rq "$state" apps shared; done
# Rental v0.5.8 remains the preserved field build.
grep -q "versionCode 13" apps/consumer/app/build.gradle
grep -q "versionName '0.5.8'" apps/consumer/app/build.gradle
echo 'v0.5.10 Host visual renovation acceptance: PASS'
