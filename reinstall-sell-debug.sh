#!/bin/bash
#
# Clean-build the sell flavor in debug and reinstall it, clearing the launcher
# caches on the way so a changed app icon actually shows up.
#
# The sell counterpart of reinstall-buy-debug.sh, same steps and same options.
set -u

cd "$(dirname "$0")"

# The real applicationId: base com.together plus the flavor suffix (see
# doc/guides/build-flavors.md). Not com.together.newverse.sell — that name would
# make the uninstall below silently remove nothing, and a debug install over a
# release-signed build then fails with INSTALL_FAILED_UPDATE_INCOMPATIBLE.
APP_ID="com.together.sell"
APK="androidApp/build/outputs/apk/sell/debug/androidApp-sell-debug.apk"

# Gradle's installSellDebug targets every attached device, and a bare `adb` command
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
if ! ./gradlew clean :androidApp:assembleSellDebug; then
  echo "" >&2
  echo "Build failed -- nothing installed." >&2
  exit 1
fi

echo ""
echo "=== Installing new app ==="
if ! ./gradlew :androidApp:installSellDebug; then
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
# Newest build-tools that is actually installed, rather than one pinned version:
# the buy script hardcodes 36.0.0-rc1 and silently prints nothing once that is
# replaced by a newer release.
AAPT2=$(find "${ANDROID_HOME:-$HOME/Android/Sdk}/build-tools" -maxdepth 2 -name aapt2 -type f 2>/dev/null | sort -V | tail -1)
if [ -n "$AAPT2" ] && [ -f "$APK" ]; then
  echo "Icon configuration from APK:"
  "$AAPT2" dump badging "$APK" | grep "application-icon-640" || echo "  (no application-icon-640 entry found)"
else
  echo "Skipped: aapt2 not found under ${ANDROID_HOME:-$HOME/Android/Sdk}/build-tools, or $APK is missing."
fi

echo ""
echo "=== Done! ==="
echo "The app icon should now be visible on your device."
echo ""
echo "If you still don't see the correct icon:"
echo "  1. Restart your device: adb reboot"
echo "  2. Check that Android version is 8.0+ for adaptive icons"
echo "  3. Try a different launcher app"
echo "  4. Long-press home screen > Remove app > Re-add from app drawer"
