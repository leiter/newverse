#!/bin/bash
#
# Device selection shared by the four install scripts in the project root
# (install-buy-release.sh, install-sell-release.sh, reinstall-buy-debug.sh,
# reinstall-sell-debug.sh). Source it, then call pick_device.
#
#   source "$(dirname "$0")/scripts/pick-device.sh"
#   pick_device "$DEVICE"      # "" means: ANDROID_SERIAL, else the only device,
#                              # else ask (or refuse when not on a terminal)
#
# On success it sets SERIAL, DEVICE_MODEL, DEVICE_ANDROID and DEVICE_SDK, and
# exports ANDROID_SERIAL — which both adb and the Android Gradle plugin honour,
# so `./gradlew installBuyDebug` lands on the same phone the script named
# instead of on every attached one.
#
# The point of the whole file: with two phones plugged in, each script can be
# aimed at one of them (buy on one, sell on the other) rather than refusing to
# guess. A selector may be the full serial, a unique piece of the serial or of
# the model name, or the 1-based number from --list.

# All attached devices that are ready to take an install.
_pd_ready_devices() {
    adb devices | awk '$2 == "device" { print $1 }'
}

# Attached but unusable (unauthorized, offline, no permissions). Named in errors
# because the usual cause of "no device" is a phone still waiting for the
# USB-debugging prompt.
_pd_unusable_devices() {
    adb devices | awk 'NR > 1 && NF == 2 && $2 != "device" { print $1 " (" $2 ")" }'
}

_pd_model_of() {
    adb -s "$1" shell getprop ro.product.model 2>/dev/null | tr -d '\r'
}

# Prints the attached devices, numbered, as `--list` and the pick prompt show
# them. The numbers are positions in this listing and nothing more: they shift
# as phones come and go, so a script invocation worth repeating should name a
# serial.
list_devices() {
    local serials=() i=1
    mapfile -t serials < <(_pd_ready_devices)

    if [ "${#serials[@]}" -eq 0 ]; then
        echo "No device attached."
    else
        for s in "${serials[@]}"; do
            printf '  %d) %-24s %s\n' "$i" "$s" "$(_pd_model_of "$s")"
            i=$((i + 1))
        done
    fi

    local unusable=()
    mapfile -t unusable < <(_pd_unusable_devices)
    if [ "${#unusable[@]}" -gt 0 ]; then
        echo "Attached but not usable:"
        printf '     %s\n' "${unusable[@]}"
    fi
}

# Resolves $1 against the attached devices. Echoes the matching serial, or an
# explanation to stderr and returns non-zero.
_pd_resolve() {
    local want="$1"
    shift
    local serials=("$@")

    # An exact serial always wins, so a serial that happens to look like an
    # index or to be a prefix of another cannot be mistaken for something else.
    for s in "${serials[@]}"; do
        if [ "$s" = "$want" ]; then
            echo "$s"
            return 0
        fi
    done

    # A small number is the position from --list.
    if [[ "$want" =~ ^[0-9]{1,2}$ ]]; then
        if [ "$want" -ge 1 ] && [ "$want" -le "${#serials[@]}" ]; then
            echo "${serials[$((want - 1))]}"
            return 0
        fi
        echo "No device number $want — there are ${#serials[@]} attached:" >&2
        list_devices >&2
        return 1
    fi

    # Otherwise a piece of the serial or of the model name, which must pick out
    # exactly one device.
    local matches=() lower_want
    lower_want=$(printf '%s' "$want" | tr '[:upper:]' '[:lower:]')
    for s in "${serials[@]}"; do
        local haystack
        haystack=$(printf '%s %s' "$s" "$(_pd_model_of "$s")" | tr '[:upper:]' '[:lower:]')
        case "$haystack" in
            *"$lower_want"*) matches+=("$s") ;;
        esac
    done

    if [ "${#matches[@]}" -eq 1 ]; then
        echo "${matches[0]}"
        return 0
    fi

    if [ "${#matches[@]}" -eq 0 ]; then
        echo "No attached device matches '$want'." >&2
    else
        echo "'$want' matches more than one attached device:" >&2
        printf '     %s\n' "${matches[@]}" >&2
        echo "Give the full serial." >&2
    fi
    list_devices >&2
    return 1
}

# Asks which device to use. Only called with a terminal on stdin.
_pd_ask() {
    local serials=("$@")
    local reply resolved

    echo "More than one device attached:" >&2
    list_devices >&2

    while true; do
        printf 'Install on which one? [1-%d, serial, or q to quit] ' "${#serials[@]}" >&2
        if ! read -r reply; then
            echo >&2
            echo "No answer — nothing installed." >&2
            return 1
        fi
        case "$reply" in
            q|Q|quit|exit)
                echo "Cancelled — nothing installed." >&2
                return 1
                ;;
            "") continue ;;
        esac
        if resolved=$(_pd_resolve "$reply" "${serials[@]}" 2>/dev/null); then
            echo "$resolved"
            return 0
        fi
        echo "Not one of the devices above." >&2
    done
}

# The entry point. $1 is the selector from the command line, which may be empty.
pick_device() {
    local want="${1:-}"

    if ! command -v adb >/dev/null 2>&1; then
        echo "adb is not on the PATH. Install the platform tools or add them to PATH." >&2
        return 1
    fi

    adb start-server >/dev/null 2>&1

    # An explicit flag beats the environment, so one shell can hold
    # ANDROID_SERIAL for most runs and a single invocation can still go
    # elsewhere.
    if [ -z "$want" ]; then
        want="${ANDROID_SERIAL:-}"
    fi

    local serials=()
    mapfile -t serials < <(_pd_ready_devices)

    if [ "${#serials[@]}" -eq 0 ]; then
        echo "No device attached. Plug one in or start an emulator." >&2
        local unusable=()
        mapfile -t unusable < <(_pd_unusable_devices)
        if [ "${#unusable[@]}" -gt 0 ]; then
            echo "adb does see:" >&2
            printf '  %s\n' "${unusable[@]}" >&2
            echo "Accept the USB-debugging prompt on the phone and run this again." >&2
        fi
        return 1
    fi

    if [ -n "$want" ]; then
        SERIAL=$(_pd_resolve "$want" "${serials[@]}") || return 1
    elif [ "${#serials[@]}" -eq 1 ]; then
        SERIAL="${serials[0]}"
    elif [ -t 0 ]; then
        SERIAL=$(_pd_ask "${serials[@]}") || return 1
    else
        # No terminal to ask at: name the devices and the flag instead of
        # picking one of two phones at random.
        echo "${#serials[@]} devices attached and no device given:" >&2
        list_devices >&2
        echo "Pass --device <serial|number|model> (or set ANDROID_SERIAL)." >&2
        return 1
    fi

    export ANDROID_SERIAL="$SERIAL"
    DEVICE_MODEL=$(_pd_model_of "$SERIAL")
    DEVICE_ANDROID=$(adb -s "$SERIAL" shell getprop ro.build.version.release 2>/dev/null | tr -d '\r')
    DEVICE_SDK=$(adb -s "$SERIAL" shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r')
    return 0
}
