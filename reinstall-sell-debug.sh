#!/bin/bash
#
# Clean-build the sell flavor in debug and reinstall it, clearing the launcher
# caches on the way so a changed app icon actually shows up.
#
# The sell counterpart of reinstall-buy-debug.sh, same steps and same options.
#
# With two phones attached, say which one: --device takes a serial, a unique
# piece of the serial or model name, or the number from --list. With one phone
# attached it is used; with several and no --device the script asks, and refuses
# rather than guessing when there is no terminal to ask at. So the seller app
# can go on one phone while reinstall-buy-debug.sh puts the buyer app on the
# other.
#
# Usage: ./reinstall-sell-debug.sh [--device <serial>]
#        ./reinstall-sell-debug.sh --list
#
#   -s, --device <d>  Which attached device to install on: full serial, a unique
#                     substring of the serial or of the model name, or the number
#                     --list prints. Also read from $ANDROID_SERIAL, which this
#                     flag overrides.
#   -l, --list        List the attached devices and exit, building nothing.
set -u

cd "$(dirname "$0")"

source "./scripts/pick-device.sh"

# The real applicationId: base com.together plus the flavor suffix (see
# doc/guides/build-flavors.md). Not com.together.newverse.sell — that name would
# make the uninstall below silently remove nothing, and a debug install over a
# release-signed build then fails with INSTALL_FAILED_UPDATE_INCOMPATIBLE.
APP_ID="com.together.sell"
APK="androidApp/build/outputs/apk/sell/debug/androidApp-sell-debug.apk"

DEVICE=""
while [ "$#" -gt 0 ]; do
    case "$1" in
        -s|--device|-d)
            if [ "$#" -lt 2 ]; then
                echo "$1 needs a device: a serial, a unique part of one, or a number from --list." >&2
                exit 1
            fi
            DEVICE="$2"
            shift
            ;;
        -s=*|--device=*|-d=*) DEVICE="${1#*=}" ;;
        -l|--list)
            list_devices
            exit 0
            ;;
        -h|--help)
            # The header block above, rather than a line range that goes stale
            # the first time a comment is added to it.
            sed -n '2,/^set -u/p' "$0" | sed '/^set -u/d' | sed 's/^# \{0,1\}//'
            exit 0
            ;;
        *)
            echo "Unknown argument: $1" >&2
            echo "Usage: $(basename "$0") [--device <serial>]" >&2
            exit 1
            ;;
    esac
    shift
done

# Gradle's installSellDebug targets every attached device, and a bare `adb` command
# refuses to run at all with more than one. Pick one and hold it for the whole run:
# pick_device exports ANDROID_SERIAL, which both adb and the Android Gradle plugin
# honour.
pick_device "$DEVICE" || exit 1

echo "=== Device Info ==="
echo "Device: $SERIAL (${DEVICE_MODEL:-unknown})"
echo "Android Version: ${DEVICE_ANDROID:-?} (SDK ${DEVICE_SDK:-?})"
echo "Note: Adaptive icons require Android 8.0+ (SDK 26+)"

echo ""
echo "=== Uninstalling old app ==="
# Uninstall rather than install over the top: the previous build may be signed with
# the release key, and Android rejects a signature change on update.
if [ -n "$(adb -s "$SERIAL" shell pm path "$APP_ID" | tr -d '\r')" ]; then
  adb -s "$SERIAL" uninstall "$APP_ID"
else
  echo "$APP_ID not installed on $SERIAL; nothing to remove."
fi

echo ""
echo "=== Clearing launcher cache (multiple launchers) ==="
# Try multiple common launchers
adb -s "$SERIAL" shell pm clear com.android.launcher3 2>/dev/null && echo "Cleared com.android.launcher3"
adb -s "$SERIAL" shell pm clear com.google.android.apps.nexuslauncher 2>/dev/null && echo "Cleared Pixel Launcher"
adb -s "$SERIAL" shell pm clear com.sec.android.app.launcher 2>/dev/null && echo "Cleared Samsung Launcher"
adb -s "$SERIAL" shell pm clear com.miui.home 2>/dev/null && echo "Cleared MIUI Launcher"
adb -s "$SERIAL" shell pm clear com.huawei.android.launcher 2>/dev/null && echo "Cleared Huawei Launcher"

echo ""
echo "=== Clearing package manager cache ==="
adb -s "$SERIAL" shell pm clear com.android.packageinstaller 2>/dev/null

echo ""
echo "=== Building fresh APK ==="
if ! ./gradlew clean :androidApp:assembleSellDebug; then
  echo "" >&2
  echo "Build failed -- nothing installed." >&2
  exit 1
fi

echo ""
echo "=== Installing new app on ${DEVICE_MODEL:-$SERIAL} ($SERIAL) ==="
if ! ./gradlew :androidApp:installSellDebug; then
  echo "" >&2
  echo "Install failed -- stopping before the icon check below reports success." >&2
  exit 1
fi

echo ""
echo "=== Killing launcher to force refresh ==="
adb -s "$SERIAL" shell am force-stop com.android.launcher3 2>/dev/null
adb -s "$SERIAL" shell am force-stop com.google.android.apps.nexuslauncher 2>/dev/null
adb -s "$SERIAL" shell am force-stop com.sec.android.app.launcher 2>/dev/null
adb -s "$SERIAL" shell am force-stop com.miui.home 2>/dev/null

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
echo "  1. Restart your device: adb -s $SERIAL reboot"
echo "  2. Check that Android version is 8.0+ for adaptive icons"
echo "  3. Try a different launcher app"
echo "  4. Long-press home screen > Remove app > Re-add from app drawer"
