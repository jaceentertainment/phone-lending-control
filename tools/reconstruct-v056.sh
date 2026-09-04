#!/usr/bin/env bash
set -euo pipefail

cat tools/v0.5.2-professional-ui.patch.gz.b64.part-* > /tmp/v052.patch.gz.b64
echo '025bee9ee89255810ff313b25c6f7e64649727b73336e757b3023a03f18989ae  /tmp/v052.patch.gz.b64' | sha256sum -c -
base64 -d /tmp/v052.patch.gz.b64 | gzip -d > /tmp/v052.patch
echo 'baa3b11b1af7e00f43540b0c1975f90e9a6f6f320984da72ec7a5022dbf5573c  /tmp/v052.patch' | sha256sum -c -

echo '8f146648fed89d24e91d76f5ac439bd927617033e87e632a9cc167083698c306  tools/v0.5.3-timer-qualification.patch.gz.b64' | sha256sum -c -
base64 -d tools/v0.5.3-timer-qualification.patch.gz.b64 | gzip -d > /tmp/v053.patch
echo '12c998cf9440377b8b1b5bd01d187b04b5e7f1a87bf660791e29de2caed5276f  /tmp/v053.patch' | sha256sum -c -

cat tools/v0.5.4-phase1-android.patch.gz.b64.part-* > /tmp/v054.patch.gz.b64
echo '410835611498f381cd00651a52d233ef6e2c3bce72955a07496618b9293dee10  /tmp/v054.patch.gz.b64' | sha256sum -c -
base64 -d /tmp/v054.patch.gz.b64 | gzip -d > /tmp/v054.patch
echo '81f25cd8db4ee81075aa492ec88db6835170c67f3066fff1ad81e0e57a6c859d  /tmp/v054.patch' | sha256sum -c -

cat tools/v0.5.5-host-ux-notification.patch.gz.b64.part-* > /tmp/v055.patch.gz.b64
echo '23806583f53dc316f3ecd7a49d668fa0fd04871037bb253d99ee11383df1232d  /tmp/v055.patch.gz.b64' | sha256sum -c -
base64 -d /tmp/v055.patch.gz.b64 | gzip -d > /tmp/v055.patch
echo '791044bca5b4487f9e4a6b7a094e0fb86ac2ef7aa654bc6df8c0435ff1867fdf  /tmp/v055.patch' | sha256sum -c -
echo '55b78a10eb49bf6db6486b2504326c0d9e3e4c793d412cfd651c7942aa7a1a0d  tools/v0.5.5-compile-fix.patch' | sha256sum -c -
echo '732dd1d205f622a69760a255e46b37a70ad4119d231e3b304a866ff426400048  tools/v0.5.5-consumer-context-fix.patch' | sha256sum -c -

# v0.5.6 transport is intentionally split for GitHub Contents transport safety.
# The decoded source patch hash is authoritative; base64 terminal-newline
# normalization is semantically irrelevant and therefore is not a release gate.
printf "v0.5.6 transport fragments as stored in GitHub:\n"
wc -c tools/v0.5.6-professional-ux.patch.gz.b64.part-*
sha256sum tools/v0.5.6-professional-ux.patch.gz.b64.part-*
cat tools/v0.5.6-professional-ux.patch.gz.b64.part-* > /tmp/v056.patch.gz.b64
printf "reconstructed wrapper: "
wc -c /tmp/v056.patch.gz.b64
sha256sum /tmp/v056.patch.gz.b64
base64 -d /tmp/v056.patch.gz.b64 | gzip -d > /tmp/v056.patch
echo 'fc5b0096f4e8c79d83e1a2ad09f47c2ba4f4fed42dd3eeffa336a6ebd10b5548  /tmp/v056.patch' | sha256sum -c -

base64 -d tools/v0.5.1-guided-onboarding.patch.gz.b64 | gzip -d > /tmp/v051.patch
for p in \
  tools/v0.5.0-consumer-service.patch \
  tools/v0.5.0-host-main.patch \
  /tmp/v051.patch \
  /tmp/v052.patch \
  /tmp/v053.patch \
  /tmp/v054.patch \
  /tmp/v055.patch \
  tools/v0.5.5-compile-fix.patch \
  tools/v0.5.5-consumer-context-fix.patch \
  /tmp/v056.patch; do
  git apply --check "$p"
  git apply "$p"
done
