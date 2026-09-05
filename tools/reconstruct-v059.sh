#!/usr/bin/env bash
set -euo pipefail

bash tools/reconstruct-v058.sh

rm -rf /tmp/v059-consumer-before
cp -a apps/consumer /tmp/v059-consumer-before

printf '%s  %s\n' 'ddd66222ebd809522d41efc749498ea45cfdcb2009c48e1a9b0cbb0bffe7f747' 'tools/v0.5.9-combined-host-renovation.patch.gz.b64' | sha256sum -c -
base64 -d tools/v0.5.9-combined-host-renovation.patch.gz.b64 > /tmp/v059.patch.gz
gzip -dc /tmp/v059.patch.gz > /tmp/v059.patch
printf '%s  %s\n' '5da33f2b4b4988f7fcf7692a93d7bc3a0825df2aab593dc4ef280297fc7ca434' '/tmp/v059.patch' | sha256sum -c -
git apply --check /tmp/v059.patch
git apply /tmp/v059.patch

diff -qr /tmp/v059-consumer-before apps/consumer
