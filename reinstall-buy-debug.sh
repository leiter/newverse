#!/bin/bash
#
# Clean-build the buy flavor in debug and reinstall it, clearing the launcher
# caches on the way so a changed app icon actually shows up.
#
# With two phones attached, say which one: --device takes a serial, a unique
# piece of the serial or model name, or the number from --list. With one phone
# attached it is used; with several and no --device the script asks, and refuses
# rather than guessing when there is no terminal to ask at.
#
# Usage: ./reinstall-buy-debug.sh [--device <serial>]
#        ./reinstall-buy-debug.sh --list
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
# doc/guides/build-flavors.md). It is NOT com.together.newverse.buy -- that name
# was wrong here for as long as this script existed, so the uninstall below always
# silently removed nothing, and a debug install over a release-signed build then
# failed with INSTALL_FAILED_UPDATE_INCOMPATIBLE.
APP_ID="com.together.buy"

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

# Gradle's installBuyDebug targets every attached device, and a bare `adb` command
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
./gradlew clean :androidApp:assembleBuyDebug

echo ""
echo "=== Installing new app on ${DEVICE_MODEL:-$SERIAL} ($SERIAL) ==="
if ! ./gradlew :androidApp:installBuyDebug; then
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
echo "Icon configuration from APK:"
~/Android/Sdk/build-tools/36.0.0-rc1/aapt2 dump badging androidApp/build/outputs/apk/buy/debug/androidApp-buy-debug.apk | grep "application-icon-640"

echo ""
echo "=== Done! ==="
echo "The app icon should now be visible on your device."
echo ""
echo "If you still don't see the correct icon:"
echo "  1. Restart your device: adb -s $SERIAL reboot"
echo "  2. Check that Android version is 8.0+ for adaptive icons"
echo "  3. Try a different launcher app"
echo "  4. Long-press home screen > Remove app > Re-add from app drawer"
