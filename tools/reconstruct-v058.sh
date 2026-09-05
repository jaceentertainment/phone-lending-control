#!/usr/bin/env bash
set -euo pipefail

bash tools/reconstruct-v057.sh

rm -rf /tmp/v058-host-before
cp -a apps/host /tmp/v058-host-before

printf '%s  %s\n' '0266d44aed33b8dc0e3f83b5d31f52bca2f25592b5645c032c60d47833685b0e' 'tools/v0.5.8-rental-ux-expiry.patch' | sha256sum -c -
git apply --check tools/v0.5.8-rental-ux-expiry.patch
git apply tools/v0.5.8-rental-ux-expiry.patch

diff -qr /tmp/v058-host-before apps/host
