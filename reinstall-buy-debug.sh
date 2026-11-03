#!/bin/bash
set -u

cd "$(dirname "$0")"

# The real applicationId: base com.together plus the flavor suffix (see
# doc/guides/build-flavors.md). It is NOT com.together.newverse.buy -- that name
# was wrong here for as long as this script existed, so the uninstall below always
# silently removed nothing, and a debug install over a release-signed build then
# failed with INSTALL_FAILED_UPDATE_INCOMPATIBLE.
APP_ID="com.together.buy"

# Gradle's installBuyDebug targets every attached device, and a bare `adb` command
# refuses to run at all with more than one. Pick one and hold it for the whole run:
# ANDROID_SERIAL is honoured by both adb and the Android Gradle plugin.
if [ -z "${ANDROID_SERIAL:-}" ]; then
  mapfile -t DEVICES < <(adb devices | awk '/\tdevice$/ {print $1}')
  if [ "${#DEVICES[@]}" -eq 0 ]; then
    echo "No device attached. Plug one in or start an emulator." >&2
    exit 1
  elif [ "${#DEVICES[@]}" -gt 1 ]; then
    echo "More than one device attached:" >&2
    for d in "${DEVICES[@]}"; do
      echo "  $d  ($(adb -s "$d" shell getprop ro.product.model | tr -d '\r'))" >&2
    done
    echo "Choose one:  ANDROID_SERIAL=<serial> $0" >&2
    exit 1
  fi
  export ANDROID_SERIAL="${DEVICES[0]}"
fi

echo "=== Device Info ==="
echo "Device: $ANDROID_SERIAL ($(adb shell getprop ro.product.model | tr -d '\r'))"
ANDROID_VERSION=$(adb shell getprop ro.build.version.release | tr -d '\r')
SDK_VERSION=$(adb shell getprop ro.build.version.sdk | tr -d '\r')
echo "Android Version: $ANDROID_VERSION (SDK $SDK_VERSION)"
echo "Note: Adaptive icons require Android 8.0+ (SDK 26+)"

echo ""
echo "=== Uninstalling old app ==="
# Uninstall rather than install over the top: the previous build may be signed with
# the release key, and Android rejects a signature change on update.
if [ -n "$(adb shell pm path "$APP_ID" | tr -d '\r')" ]; then
  adb uninstall "$APP_ID"
else
  echo "$APP_ID not installed on $ANDROID_SERIAL; nothing to remove."
fi

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
./gradlew clean :androidApp:assembleBuyDebug

echo ""
echo "=== Installing new app ==="
if ! ./gradlew :androidApp:installBuyDebug; then
  echo "" >&2
  echo "Install failed -- stopping before the icon check below reports success." >&2
  exit 1
fi

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
