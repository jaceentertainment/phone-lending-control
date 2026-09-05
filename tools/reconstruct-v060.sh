#!/usr/bin/env bash
set -euo pipefail

bash tools/reconstruct-v0510.sh

find apps/consumer -type f -print0 | sort -z | xargs -0 sha256sum > /tmp/consumer-before-v060.sha256

EXPECTED_B64='2d088849795e9d227db67b7848c8167b43a1a13904ed57433a5f959578fac48a'
EXPECTED_PATCH='0f89e367335fe4df3771f8fe17e2737caa977c7f1f978211ddac8c736abcf333'
printf '%s  %s\n' "$EXPECTED_B64" tools/v0.6.0-host-professional-ux.patch.gz.b64 | sha256sum -c -
base64 -d tools/v0.6.0-host-professional-ux.patch.gz.b64 | gzip -d > /tmp/v060-host.patch
printf '%s  %s\n' "$EXPECTED_PATCH" /tmp/v060-host.patch | sha256sum -c -
patch -p1 < /tmp/v060-host.patch

find apps/consumer -type f -print0 | sort -z | xargs -0 sha256sum > /tmp/consumer-after-v060.sha256
cmp /tmp/consumer-before-v060.sha256 /tmp/consumer-after-v060.sha256

grep -q "versionCode 14" apps/host/app/build.gradle
grep -q "versionName '0.6.0'" apps/host/app/build.gradle
grep -q "versionCode 13" apps/consumer/app/build.gradle
grep -q "versionName '0.5.8'" apps/consumer/app/build.gradle

echo 'v0.6.0 reconstruction: PASS (Host blueprint delta applied; Rental unchanged)'
