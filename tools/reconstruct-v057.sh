#!/usr/bin/env bash
set -euo pipefail

bash tools/reconstruct-v056.sh

rm -rf /tmp/v057-consumer-before
cp -a apps/consumer /tmp/v057-consumer-before

printf '%s  %s\n' 'da54efe6c6cf824608d54a7ae58ea669f132ef168281f9ecec70750400dbdb5d' 'tools/v0.5.7-host-professional-redesign.patch.gz.b64.part-00' | sha256sum -c -
printf '%s  %s\n' '395463558e4c14a74efde38200c142e7575eacf73ebb583bbfc9430ccc9b7493' 'tools/v0.5.7-host-professional-redesign.patch.gz.b64.part-01' | sha256sum -c -
printf '%s  %s\n' 'e38708c7a6018d6e8182a15b8efa0d159be95ab034cda65d414b92c221fa8cf0' 'tools/v0.5.7-host-professional-redesign.patch.gz.b64.part-02' | sha256sum -c -
printf '%s  %s\n' 'c25ba4f05e21a9871c98f7d01a6e3dbd2081f9102a210b5ac2de40a53ab3591b' 'tools/v0.5.7-host-professional-redesign.patch.gz.b64.part-03a' | sha256sum -c -
printf '%s  %s\n' '5b39e85a9cac80d13f2004daebfa60dcddc11460c0cbb0491fe14b92fcc489bd' 'tools/v0.5.7-host-professional-redesign.patch.gz.b64.part-03b' | sha256sum -c -
printf '%s  %s\n' '979d8b9031ea6a96213051a6277944d23c49a5fca0f9f42cda4b535d984c50be' 'tools/v0.5.7-host-professional-redesign.patch.gz.b64.part-03c' | sha256sum -c -
printf '%s  %s\n' '90db5df16e8eccce87be15be7dd5c0f6e1f0b45ea719c5746d32506ab979d006' 'tools/v0.5.7-host-professional-redesign.patch.gz.b64.part-04' | sha256sum -c -

cat tools/v0.5.7-host-professional-redesign.patch.gz.b64.part-00 \
    tools/v0.5.7-host-professional-redesign.patch.gz.b64.part-01 \
    tools/v0.5.7-host-professional-redesign.patch.gz.b64.part-02 \
    tools/v0.5.7-host-professional-redesign.patch.gz.b64.part-03a \
    tools/v0.5.7-host-professional-redesign.patch.gz.b64.part-03b \
    tools/v0.5.7-host-professional-redesign.patch.gz.b64.part-03c \
    tools/v0.5.7-host-professional-redesign.patch.gz.b64.part-04 > /tmp/v057.patch.gz.b64
printf '%s  %s\n' 'bfa3c2ba14099591b39330927fce958a0d35a61e277291d9d064e676b14598a7' '/tmp/v057.patch.gz.b64' | sha256sum -c -
base64 -d /tmp/v057.patch.gz.b64 > /tmp/v057.patch.gz
gzip -dc /tmp/v057.patch.gz > /tmp/v057.patch
printf '%s  %s\n' '2544b5ad9a229ec6d3368ce1b31b8cd45bb49abd57fda7c35b4bfbda8de2a518' '/tmp/v057.patch' | sha256sum -c -

git apply --check /tmp/v057.patch
git apply /tmp/v057.patch

diff -qr /tmp/v057-consumer-before apps/consumer
