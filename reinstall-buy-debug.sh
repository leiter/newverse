#!/bin/bash

echo "=== Device Info ==="
ANDROID_VERSION=$(adb shell getprop ro.build.version.release)
SDK_VERSION=$(adb shell getprop ro.build.version.sdk)
echo "Android Version: $ANDROID_VERSION (SDK $SDK_VERSION)"
echo "Note: Adaptive icons require Android 8.0+ (SDK 26+)"

echo ""
echo "=== Uninstalling old app ==="
adb uninstall com.together.newverse.buy

echo ""
echo "=== Clearing launcher cache (multiple launchers) ==="
# Try multiple common launchers
adb shell pm clear com.android.launcher3 2>/dev/null && echo "Cleared com.android.launcher3"
adb shell pm clear com.google.android.apps.nexuslauncher 2>/dev/null && echo "Cleared Pixel Launcher"
adb shell pm clear com.sec.android.app.launcher 2>/dev/null && echo "Cleared Samsung Launcher"
adb shell pm clear com.miui.home 2>/dev/null && echo "Cleared MIUI Launcher"
adb shell pm clear com.huawei.android.launcher 2>/dev/null && echo "Cleared Huawei Launcher"

echo ""
echo "=== Clearing package manager cache ==="
adb shell pm clear com.android.packageinstaller 2>/dev/null

echo ""
echo "=== Building fresh APK ==="
cd "$(dirname "$0")"
./gradlew clean :androidApp:assembleBuyDebug

echo ""
echo "=== Installing new app ==="
./gradlew :androidApp:installBuyDebug

echo ""
echo "=== Killing launcher to force refresh ==="
adb shell am force-stop com.android.launcher3 2>/dev/null
adb shell am force-stop com.google.android.apps.nexuslauncher 2>/dev/null
adb shell am force-stop com.sec.android.app.launcher 2>/dev/null
adb shell am force-stop com.miui.home 2>/dev/null

echo ""
echo "=== Verifying icon in APK ==="
echo "Icon configuration from APK:"
~/Android/Sdk/build-tools/36.0.0-rc1/aapt2 dump badging androidApp/build/outputs/apk/buy/debug/androidApp-buy-debug.apk | grep "application-icon-640"

echo ""
echo "=== Done! ==="
echo "The app icon should now be visible on your device."
echo ""
echo "If you still don't see the correct icon:"
echo "  1. Restart your device: adb reboot"
echo "  2. Check that Android version is 8.0+ for adaptive icons"
echo "  3. Try a different launcher app"
echo "  4. Long-press home screen > Remove app > Re-add from app drawer"
