#!/usr/bin/env bash
set -euo pipefail
# v0.5.9 reconstruction expects the original historical trigger file content.
printf '# Batch 1 CI\n\nThis branch is the isolated PhoneLending v0.3.0 development build branch.\n' > BUILD_TRIGGER.md
bash tools/reconstruct-v059.sh
find apps/consumer -type f -print0 | sort -z | xargs -0 sha256sum > /tmp/consumer-before-v0510.sha256
base64 -d tools/v0.5.10-host-visual-renovation.patch.gz.b64 | gzip -d > /tmp/v0510-host.patch
patch -p1 < /tmp/v0510-host.patch
find apps/consumer -type f -print0 | sort -z | xargs -0 sha256sum > /tmp/consumer-after-v0510.sha256
cmp /tmp/consumer-before-v0510.sha256 /tmp/consumer-after-v0510.sha256
