#!/bin/bash
#
# Build the buy flavor in release and install it on the one connected device.
#
# Refuses to guess: no device or more than one, and the script stops before it
# builds anything.
#
# R8 is off: the APK is release-signed but not minified or obfuscated, so it
# builds faster and its stack traces are readable. That makes it a sideload
# build, not what Play gets — pass --minify to reproduce the shipping APK.
#
# Usage: ./install-buy-release.sh [--no-clean] [--minify]
#
#   --no-clean   Skip the clean, reusing whatever is already built. Faster, and
#                enough for a code-only change; a clean build is the default
#                because stale generated sources (Compose resource collectors,
#                BuildKonfig) survive an incremental one and have shipped wrong
#                output before.
#   --minify     Build with R8 minification and resource shrinking on, as the
#                Play release does.
set -u

cd "$(dirname "$0")"

APP_ID="com.together.buy"
APK="androidApp/build/outputs/apk/buy/release/androidApp-buy-release.apk"
SIGNING_PROPS="/home/mandroid/Videos/AA_FILES/bodenkunde_signing"

CLEAN=1
MINIFY=0
for arg in "$@"; do
    case "$arg" in
        --no-clean) CLEAN=0 ;;
        --minify) MINIFY=1 ;;
        -h|--help)
            sed -n '2,22p' "$0" | sed 's/^# \{0,1\}//'
            exit 0
            ;;
        *)
            echo "Unknown argument: $arg" >&2
            echo "Usage: $(basename "$0") [--no-clean] [--minify]" >&2
            exit 1
            ;;
    esac
done

# The release build type reads this property; absent, it minifies as it always has.
GRADLE_ARGS=()
if [ "$MINIFY" -eq 0 ]; then
    GRADLE_ARGS+=("-Pnewverse.skipMinify=true")
fi

# --- Pick the device ------------------------------------------------------

if ! command -v adb >/dev/null 2>&1; then
    echo "adb is not on the PATH. Install the platform tools or add them to PATH." >&2
    exit 1
fi

adb start-server >/dev/null 2>&1

# Everything adb lists that is ready to take an install.
mapfile -t DEVICES < <(adb devices | awk '$2 == "device" { print $1 }')
# Listed but unusable (unauthorized, offline, no permissions) — worth naming, because
# the usual cause of "no device" is a phone waiting for the USB-debugging prompt.
mapfile -t UNUSABLE < <(adb devices | awk 'NR > 1 && NF == 2 && $2 != "device" { print $1 " (" $2 ")" }')

if [ "${#DEVICES[@]}" -eq 0 ]; then
    echo "No device connected — nothing to install on." >&2
    if [ "${#UNUSABLE[@]}" -gt 0 ]; then
        echo "adb does see:" >&2
        printf '  %s\n' "${UNUSABLE[@]}" >&2
        echo "Accept the USB-debugging prompt on the phone and run this again." >&2
    fi
    exit 1
fi

if [ "${#DEVICES[@]}" -gt 1 ]; then
    echo "${#DEVICES[@]} devices connected — unplug all but one, or install by hand:" >&2
    printf '  %s\n' "${DEVICES[@]}" >&2
    exit 1
fi

SERIAL="${DEVICES[0]}"
MODEL=$(adb -s "$SERIAL" shell getprop ro.product.model 2>/dev/null | tr -d '\r')
ANDROID=$(adb -s "$SERIAL" shell getprop ro.build.version.release 2>/dev/null | tr -d '\r')
SDK=$(adb -s "$SERIAL" shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r')
echo "Device: ${MODEL:-unknown} ($SERIAL), Android ${ANDROID:-?} / SDK ${SDK:-?}"

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
    # Its own invocation rather than "gradlew clean assembleBuyRelease": in one
    # invocation clean races the tasks that read what it is deleting.
    if ! ./gradlew clean; then
        echo "Clean failed — nothing installed." >&2
        exit 1
    fi
fi

echo
if [ "$MINIFY" -eq 1 ]; then
    echo "=== Building buy release (minified, as Play gets it) ==="
else
    echo "=== Building buy release (no minify, no obfuscation) ==="
fi
if ! ./gradlew "${GRADLE_ARGS[@]}" :androidApp:assembleBuyRelease; then
    echo "Build failed — nothing installed." >&2
    exit 1
fi

if [ ! -f "$APK" ]; then
    echo "Build reported success but $APK is missing." >&2
    exit 1
fi

# --- Install -------------------------------------------------------------

echo
echo "=== Installing $APK ==="
INSTALL_LOG=$(adb -s "$SERIAL" install -r "$APK" 2>&1)
echo "$INSTALL_LOG"

if echo "$INSTALL_LOG" | grep -q "Success"; then
    echo
    echo "Installed $APK on ${MODEL:-$SERIAL}."
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
