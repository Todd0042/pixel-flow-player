#!/usr/bin/env bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"

echo "Checking connected devices..."
adb devices

APK="$DIR/app/build/outputs/apk/debug/app-debug.apk"

if [ ! -f "$APK" ]; then
    echo "Building APK..."
    ./gradlew assembleDebug
fi

echo "Installing $APK..."
adb install -r "$APK"
echo "Installation complete! Launching Pixel Flow on device..."
adb shell monkey -p com.pixelflow.player -c android.intent.category.LAUNCHER 1
