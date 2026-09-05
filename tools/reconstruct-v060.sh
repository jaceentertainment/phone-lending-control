#!/usr/bin/env bash
set -euo pipefail

bash tools/reconstruct-v0510.sh

find apps/consumer -type f -print0 | sort -z | xargs -0 sha256sum > /tmp/consumer-before-v060.sha256

PARTS=(
  tools/v0.6.0-host-professional-ux.patch.gz.b64.part-00
  tools/v0.6.0-host-professional-ux.patch.gz.b64.part-01
  tools/v0.6.0-host-professional-ux.patch.gz.b64.part-02
  tools/v0.6.0-host-professional-ux.patch.gz.b64.part-03
  tools/v0.6.0-host-professional-ux.patch.gz.b64.part-04
)
EXPECTED_PARTS=(
  752f907e2f23de2665816c7bba4f3ce2ca776031891a8917a7134540b78804ed
  9aec59829932b51521382093ba6070e786da10f1ca5f5b2e5b1d6747977196a9
  a96f72427cb0eb13ac3d6469f8448b0ab81ffb28eb16975bd722a8bc195c3fff
  246683fe72a77e64e60070c51fc171adb69ddddf24127da2a8cfffd9ed782498
  e1fe3957ccf198125bd54a454ee1ce5eb5c0c555a71ed282fc10c2b2266c2993
)
for i in "${!PARTS[@]}"; do
  tr -d '\r\n\t ' < "${PARTS[$i]}" > "/tmp/v060-part-$i.normalized"
  printf '%s  %s\n' "${EXPECTED_PARTS[$i]}" "/tmp/v060-part-$i.normalized" | sha256sum -c -
done
cat /tmp/v060-part-{0..4}.normalized > /tmp/v060-host.patch.gz.b64.normalized

EXPECTED_B64_NORMALIZED='5fda69dda003f8d38486af1694145facfc0e6046ce36a8aaf011439bfdfe9043'
EXPECTED_PATCH='1ff351447db02ff4a5b5ffffa8b7c205cc75c16fabd13214c0770acf9e21776b'
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
