#!/usr/bin/env bash
set -euo pipefail

# Reconstruct the accepted v0.6.0 source baseline first.
bash tools/reconstruct-v060.sh

# Preserve every Rental source/build file except the three v0.7.0 owner-approved changes:
# build.gradle version bump, MainActivity Add-Time QR UI, and the new identification-only QR payload class.
find apps/consumer -type f \
  ! -path 'apps/consumer/app/build.gradle' \
  ! -path 'apps/consumer/app/src/main/java/com/jace/phonelending/consumer/MainActivity.java' \
  ! -path 'apps/consumer/app/src/main/java/com/jace/phonelending/consumer/ManagementQrPayload.java' \
  -print0 | sort -z | xargs -0 sha256sum > /tmp/rental-protected-before-v070.sha256

PARTS=(
  tools/v0.7.0-owner-design.patch.gz.b64.part-00
  tools/v0.7.0-owner-design.patch.gz.b64.part-01
  tools/v0.7.0-owner-design.patch.gz.b64.part-02
  tools/v0.7.0-owner-design.patch.gz.b64.part-03
  tools/v0.7.0-owner-design.patch.gz.b64.part-04
  tools/v0.7.0-owner-design.patch.gz.b64.part-05
)
EXPECTED_PARTS=(
  a145cdd7e15264ea4728ee71f7b18a24ee4ee33550e651bc8c03f70733d6b65b
  cddaa814fcb00dfc87f39b9dc34045b8f052d1aade80279b3c45618b5351c117
  28e14d07ec43f6e8a6459b020ea34b607c696c27fba1b3215c66541c32262b64
  4a5e5c010e11b2e2223614fb950f37c55336537955ea8cfeb4a0c3bdc2055272
  47e9dcfd363996349b6bac3f341b6e202c8685172592b5cbb001b7537835c7ed
  0241719c85ef8fc391fb86d2f154e698bcf611e07a4c54cb5a57d7b10b1339ca
)

for i in "${!PARTS[@]}"; do
  tr -d '\r\n\t ' < "${PARTS[$i]}" > "/tmp/v070-part-$i.normalized"
  printf '%s  %s\n' "${EXPECTED_PARTS[$i]}" "/tmp/v070-part-$i.normalized" | sha256sum -c -
done
cat /tmp/v070-part-{0..5}.normalized > /tmp/v070-owner-design.patch.gz.b64.normalized

EXPECTED_B64_NORMALIZED='656ae0e659353fc56bca93a1691f39f8e156ee9f171e391ffb4f9dba4651a73c'
EXPECTED_PATCH='22d9d550f1ab3f1f04fdc3dbecbf6591c16a100ec2469c21acf4cf46606ec71e'
printf '%s  %s\n' "$EXPECTED_B64_NORMALIZED" /tmp/v070-owner-design.patch.gz.b64.normalized | sha256sum -c -
base64 -d /tmp/v070-owner-design.patch.gz.b64.normalized | gzip -d > /tmp/v070-owner-design.patch
printf '%s  %s\n' "$EXPECTED_PATCH" /tmp/v070-owner-design.patch | sha256sum -c -
patch -p1 < /tmp/v070-owner-design.patch

find apps/consumer -type f \
  ! -path 'apps/consumer/app/build.gradle' \
  ! -path 'apps/consumer/app/src/main/java/com/jace/phonelending/consumer/MainActivity.java' \
  ! -path 'apps/consumer/app/src/main/java/com/jace/phonelending/consumer/ManagementQrPayload.java' \
  -print0 | sort -z | xargs -0 sha256sum > /tmp/rental-protected-after-v070.sha256
cmp /tmp/rental-protected-before-v070.sha256 /tmp/rental-protected-after-v070.sha256

grep -q "versionCode 15" apps/host/app/build.gradle
grep -q "versionName '0.7.0'" apps/host/app/build.gradle
grep -q "versionCode 14" apps/consumer/app/build.gradle
grep -q "versionName '0.5.9'" apps/consumer/app/build.gradle
test -f apps/host/app/src/main/java/com/jace/phonelending/host/ManagementQrPayload.java
test -f apps/consumer/app/src/main/java/com/jace/phonelending/consumer/ManagementQrPayload.java
test -f Contracts/PhoneLending_Owner_Design_Master_v0.7.0.md

echo 'v0.7.0 reconstruction: PASS (owner design delta applied; Rental changes limited to approved Add-Time QR surface)'
