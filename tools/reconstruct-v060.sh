#!/usr/bin/env bash
set -euo pipefail

bash tools/reconstruct-v0510.sh

find apps/consumer -type f -print0 | sort -z | xargs -0 sha256sum > /tmp/consumer-before-v060.sha256

EXPECTED_B64_NORMALIZED='5fda69dda003f8d38486af1694145facfc0e6046ce36a8aaf011439bfdfe9043'
EXPECTED_PATCH='1ff351447db02ff4a5b5ffffa8b7c205cc75c16fabd13214c0770acf9e21776b'
tr -d '\r\n\t ' < tools/v0.6.0-host-professional-ux.patch.gz.b64 > /tmp/v060-host.patch.gz.b64.normalized
printf '%s  %s\n' "$EXPECTED_B64_NORMALIZED" /tmp/v060-host.patch.gz.b64.normalized | sha256sum -c -
base64 -d /tmp/v060-host.patch.gz.b64.normalized | gzip -d > /tmp/v060-host.patch
printf '%s  %s\n' "$EXPECTED_PATCH" /tmp/v060-host.patch | sha256sum -c -
patch -p1 < /tmp/v060-host.patch

find apps/consumer -type f -print0 | sort -z | xargs -0 sha256sum > /tmp/consumer-after-v060.sha256
cmp /tmp/consumer-before-v060.sha256 /tmp/consumer-after-v060.sha256

grep -q "versionCode 14" apps/host/app/build.gradle
grep -q "versionName '0.6.0'" apps/host/app/build.gradle
grep -q "versionCode 13" apps/consumer/app/build.gradle
grep -q "versionName '0.5.8'" apps/consumer/app/build.gradle

echo 'v0.6.0 reconstruction: PASS (Host blueprint delta applied; Rental unchanged)'
