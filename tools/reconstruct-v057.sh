#!/usr/bin/env bash
set -euo pipefail

bash tools/reconstruct-v056.sh

rm -rf /tmp/v057-consumer-before
cp -a apps/consumer /tmp/v057-consumer-before

printf '%s  %s\n' 'f4ea7a9dc93f7cae61073e86852e6661942dac26254ad009ed7b866538307fac' 'tools/v0.5.7-host-professional-redesign.patch.gz' | sha256sum -c -
gzip -d -c tools/v0.5.7-host-professional-redesign.patch.gz > /tmp/v057.patch
printf '%s  %s\n' '2544b5ad9a229ec6d3368ce1b31b8cd45bb49abd57fda7c35b4bfbda8de2a518' '/tmp/v057.patch' | sha256sum -c -
git apply --check /tmp/v057.patch
git apply /tmp/v057.patch

diff -qr /tmp/v057-consumer-before apps/consumer
