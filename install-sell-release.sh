#!/bin/bash
#
# Build the sell flavor in release and install it on one connected device.
#
# The sell counterpart of install-buy-release.sh. Both flavors share the release
# signing config and the newverse.skipMinify property, so the two scripts differ
# only in the flavor they build and the applicationId they install.
#
# With two phones attached, say which one: --device takes a serial, a unique
# piece of the serial or model name, or the number from --list. With one phone
# attached it is installed on that one; with several and no --device the script
# asks, and refuses rather than guessing when there is no terminal to ask at.
# So the seller app can go on one phone while install-buy-release.sh puts the
# buyer app on the other.
#
# R8 is off: the APK is release-signed but not minified or obfuscated, so it
# builds faster and its stack traces are readable. That makes it a sideload
# build, not what Play gets — pass --minify to reproduce the shipping APK.
#
# Usage: ./install-sell-release.sh [--device <serial>] [--no-clean] [--minify]
#        ./install-sell-release.sh --list
#
#   -s, --device <d>  Which attached device to install on: full serial, a unique
#                     substring of the serial or of the model name, or the number
#                     --list prints. Also read from $ANDROID_SERIAL, which this
#                     flag overrides.
#   -l, --list        List the attached devices and exit, building nothing.
#   --no-clean        Skip the clean, reusing whatever is already built. Faster, and
#                     enough for a code-only change; a clean build is the default
#                     because stale generated sources (Compose resource collectors,
#                     BuildKonfig) survive an incremental one and have shipped wrong
#                     output before.
#   --minify          Build with R8 minification and resource shrinking on, as the
#                     Play release does.
set -u

cd "$(dirname "$0")"

source "./scripts/pick-device.sh"

APP_ID="com.together.sell"
APK="androidApp/build/outputs/apk/sell/release/androidApp-sell-release.apk"
SIGNING_PROPS="/home/mandroid/Videos/AA_FILES/bodenkunde_signing"

CLEAN=1
MINIFY=0
DEVICE=""
while [ "$#" -gt 0 ]; do
    case "$1" in
        --no-clean) CLEAN=0 ;;
        --minify) MINIFY=1 ;;
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
            echo "Usage: $(basename "$0") [--device <serial>] [--no-clean] [--minify]" >&2
            exit 1
            ;;
    esac
    shift
done

# The release build type reads this property; absent, it minifies as it always has.
GRADLE_ARGS=()
if [ "$MINIFY" -eq 0 ]; then
    GRADLE_ARGS+=("-Pnewverse.skipMinify=true")
fi

# --- Pick the device ------------------------------------------------------

pick_device "$DEVICE" || exit 1
echo "Device: ${DEVICE_MODEL:-unknown} ($SERIAL), Android ${DEVICE_ANDROID:-?} / SDK ${DEVICE_SDK:-?}"

# --- Build ---------------------------------------------------------------

# The release build is signed from a keystore outside the repo. Without it the
# build still produces an unsigned APK that no device will take, so say so here
# rather than at the install.
if [ ! -f "$SIGNING_PROPS" ]; then
    echo "Release signing properties not found at $SIGNING_PROPS." >&2
    echo "The release APK cannot be signed without them." >&2
    exit 1
fi

if [ "$CLEAN" -eq 1 ]; then
    echo
    echo "=== Cleaning ==="
    # Its own invocation rather than "gradlew clean assembleSellRelease": in one
    # invocation clean races the tasks that read what it is deleting.
    if ! ./gradlew clean; then
        echo "Clean failed — nothing installed." >&2
        exit 1
    fi
fi

echo
if [ "$MINIFY" -eq 1 ]; then
    echo "=== Building sell release (minified, as Play gets it) ==="
else
    echo "=== Building sell release (no minify, no obfuscation) ==="
fi
if ! ./gradlew "${GRADLE_ARGS[@]}" :androidApp:assembleSellRelease; then
    echo "Build failed — nothing installed." >&2
    exit 1
fi

if [ ! -f "$APK" ]; then
    echo "Build reported success but $APK is missing." >&2
    exit 1
fi

# --- Install -------------------------------------------------------------

echo
echo "=== Installing $APK on ${DEVICE_MODEL:-$SERIAL} ($SERIAL) ==="
INSTALL_LOG=$(adb -s "$SERIAL" install -r "$APK" 2>&1)
echo "$INSTALL_LOG"

if echo "$INSTALL_LOG" | grep -q "Success"; then
    echo
    echo "Installed $APK on ${DEVICE_MODEL:-$SERIAL}."
    exit 0
fi

# A debug build of the same flavor is already there: same applicationId, different
# signing key, and the platform will not swap one for the other in place.
if echo "$INSTALL_LOG" | grep -qE "INSTALL_FAILED_UPDATE_INCOMPATIBLE|signatures do not match"; then
    echo
    echo "A differently signed $APP_ID is already installed (most likely the debug build)." >&2
    echo "Remove it and run this again — it takes that app's data with it:" >&2
    echo "  adb -s $SERIAL uninstall $APP_ID" >&2
fi

exit 1
