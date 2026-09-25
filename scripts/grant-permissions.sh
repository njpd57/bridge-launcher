#!/usr/bin/env bash
# Grants Bridge the permissions that Android only allows granting over adb.
# Needed once per install: they survive `adb install -r`, but are lost on uninstall.
# With several devices connected, pick one with ANDROID_SERIAL=<serial>.
set -euo pipefail

REPO_DIR="$(cd "$(dirname "$0")/.." && pwd)"
PACKAGE="$(grep -oP 'applicationId\s+"\K[^"]+' "$REPO_DIR/app/build.gradle")"
PERMISSIONS=(
    android.permission.WRITE_SECURE_SETTINGS # set system night mode
)

if ! adb shell pm path "$PACKAGE" >/dev/null 2>&1; then
    echo "error: $PACKAGE is not installed on the device" >&2
    exit 1
fi

for perm in "${PERMISSIONS[@]}"; do
    echo "==> Granting $perm to $PACKAGE"
    adb shell pm grant "$PACKAGE" "$perm"
done

echo
echo "Done. Go back to the home screen so Bridge re-checks its permissions."
