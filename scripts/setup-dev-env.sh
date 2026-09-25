#!/usr/bin/env bash
# Sets up a terminal-only Android build environment for this repo on Ubuntu:
# JDK 17 (apt), Android SDK command-line tools, platform 34 and build-tools 34.
# Safe to re-run. Only the apt step needs sudo.
set -euo pipefail

SDK_DIR="${ANDROID_HOME:-$HOME/Android/Sdk}"
REPO_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SDK_PACKAGES=("platforms;android-34" "build-tools;34.0.0")

echo "==> Installing JDK 17"
if ! dpkg -s openjdk-17-jdk-headless >/dev/null 2>&1; then
    sudo apt-get update
    sudo apt-get install -y openjdk-17-jdk-headless unzip curl
fi
JAVA_HOME_17="$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")"
export JAVA_HOME="$JAVA_HOME_17"
"$JAVA_HOME/bin/java" -version

echo "==> Installing Android command-line tools into $SDK_DIR"
if [ ! -x "$SDK_DIR/cmdline-tools/latest/bin/sdkmanager" ]; then
    zip_name="$(curl -fsSL https://dl.google.com/android/repository/repository2-3.xml \
        | grep -o 'commandlinetools-linux-[0-9]*_latest.zip' | sort -t- -k3 -n | tail -1)"
    echo "    downloading $zip_name"
    tmp="$(mktemp -d)"
    curl -fL "https://dl.google.com/android/repository/$zip_name" -o "$tmp/cmdline-tools.zip"
    unzip -q "$tmp/cmdline-tools.zip" -d "$tmp"
    mkdir -p "$SDK_DIR/cmdline-tools"
    rm -rf "$SDK_DIR/cmdline-tools/latest"
    mv "$tmp/cmdline-tools" "$SDK_DIR/cmdline-tools/latest"
    rm -rf "$tmp"
fi
SDKMANAGER="$SDK_DIR/cmdline-tools/latest/bin/sdkmanager"

echo "==> Accepting SDK licenses"
yes | "$SDKMANAGER" --sdk_root="$SDK_DIR" --licenses >/dev/null || true

echo "==> Installing SDK packages: ${SDK_PACKAGES[*]}"
"$SDKMANAGER" --sdk_root="$SDK_DIR" "${SDK_PACKAGES[@]}"

echo "==> Writing $REPO_DIR/local.properties"
echo "sdk.dir=$SDK_DIR" > "$REPO_DIR/local.properties"

echo "==> Adding JAVA_HOME / ANDROID_HOME to ~/.zshrc"
MARKER="# android-dev-env (bridge-launcher setup)"
if ! grep -qF "$MARKER" "$HOME/.zshrc" 2>/dev/null; then
    cat >> "$HOME/.zshrc" <<EOF

$MARKER
export JAVA_HOME="$JAVA_HOME_17"
export ANDROID_HOME="$SDK_DIR"
export PATH="\$JAVA_HOME/bin:\$ANDROID_HOME/cmdline-tools/latest/bin:\$PATH"
EOF
fi

echo
echo "Done. Open a new shell (or 'source ~/.zshrc'), then run: ./gradlew assembleDebug"
