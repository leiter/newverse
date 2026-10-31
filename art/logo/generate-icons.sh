#!/usr/bin/env bash
#
# Regenerates every launcher / store icon from the vector masters in this folder.
#
# Everything is rasterised straight out of mark.svg by Inkscape at the exact
# target pixel size, so no output is ever an upscale of a smaller bitmap.
#
#   ./art/logo/generate-icons.sh
#
# Requires: inkscape, ImageMagick (convert).
# See README.md for the geometry rules this encodes.

set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
MARK="$HERE/mark.svg"
WORK="$(mktemp -d "${TMPDIR:-/tmp}/bodenicons.XXXXXX")"
# Inkscape ships as a snap here and cannot read /tmp, so stage under the repo.
STAGE="$ROOT/tmp/icon-stage"
trap 'rm -rf "$WORK" "$STAGE"' EXIT
mkdir -p "$STAGE"

command -v inkscape >/dev/null || { echo "inkscape not found" >&2; exit 1; }
command -v convert  >/dev/null || { echo "ImageMagick 'convert' not found" >&2; exit 1; }

# ---------------------------------------------------------------- brand colours
BUY_BG="#FA9C4D"    # BodenOrange
BUY_FG="#000000"    # the logo's own black
SELL_BG="#0A6308"   # BodenGreen, the sell flavor's flavor_primary
SELL_FG="#F6F2E8"   # BodenPaper

# ------------------------------------------------------------------- geometry
# Mark aspect is 965.764 : 539.847 (1.789:1), measured from mark.svg.
#
# ADAPTIVE_FRAC: the adaptive-icon foreground canvas is 108dp and only a 66dp
# centred circle is guaranteed to survive every launcher mask. 60/108 is the
# largest width at which no ink at all falls outside that circle (measured).
ADAPTIVE_FRAC=0.5556   # 60dp of 108dp
LEGACY_FRAC=0.70       # pre-API-26 square icon, fully visible, so it can breathe
ROUND_FRAC=0.66        # pre-API-26 circular icon
STORE_FRAC=0.62        # Play / App Store tile

# render_mark <width_px> <colour> <out.png>   -- transparent, trimmed to the art
render_mark() {
    local w="$1" colour="$2" out="$3"
    local cache="$STAGE/mark_${w}.png"
    [ -f "$cache" ] || inkscape --export-type=png --export-width="$w" \
        --export-filename="$cache" "$MARK" >/dev/null 2>&1
    # art is pure black on transparent; -colorize recolours RGB and keeps alpha
    convert "$cache" -fill "$colour" -colorize 100 PNG32:"$out"
}

# compose <canvas_px> <frac> <bg|none> <fg> <shape:square|round|rounded> <out>
compose() {
    local c="$1" frac="$2" bg="$3" fg="$4" shape="$5" out="$6"
    local aw; aw=$(awk "BEGIN{printf \"%d\", $c*$frac}")
    render_mark "$aw" "$fg" "$WORK/art.png"
    convert "$WORK/art.png" -background none -gravity center -extent "${c}x${c}" \
        PNG32:"$WORK/centred.png"

    if [ "$bg" = "none" ]; then
        cp "$WORK/centred.png" "$WORK/flat.png"
    else
        convert -size "${c}x${c}" xc:"$bg" "$WORK/centred.png" \
            -compose over -composite PNG32:"$WORK/flat.png"
    fi

    case "$shape" in
        square) cp "$WORK/flat.png" "$WORK/shaped.png" ;;
        round)
            local r=$(( c / 2 ))
            convert -size "${c}x${c}" xc:none -fill white \
                -draw "circle $r,$r $r,0" PNG32:"$WORK/mask.png"
            convert "$WORK/flat.png" "$WORK/mask.png" -alpha off \
                -compose CopyOpacity -composite PNG32:"$WORK/shaped.png" ;;
        rounded)
            local rad; rad=$(awk "BEGIN{printf \"%d\", $c*0.22}")
            convert -size "${c}x${c}" xc:none -fill white \
                -draw "roundrectangle 0,0 $((c-1)),$((c-1)) $rad,$rad" PNG32:"$WORK/mask.png"
            convert "$WORK/flat.png" "$WORK/mask.png" -alpha off \
                -compose CopyOpacity -composite PNG32:"$WORK/shaped.png" ;;
    esac

    mkdir -p "$(dirname "$out")"
    convert "$WORK/shaped.png" -strip PNG32:"$out"
    case "$out" in "$WORK"/*) ;; *) echo "  $(printf '%-72s' "${out#$ROOT/}") ${c}x${c}" ;; esac
}

# ------------------------------------------------------------------- Android
# density -> (adaptive foreground canvas = 108dp, legacy icon canvas = 48dp)
DENSITIES="mdpi:108:48 hdpi:162:72 xhdpi:216:96 xxhdpi:324:144 xxxhdpi:432:192"

android_flavor() {
    local flavor="$1" bg="$2" fg="$3"
    local res="$ROOT/androidApp/src/$flavor/res"
    echo "android/$flavor"
    for d in $DENSITIES; do
        local dpi=${d%%:*} rest=${d#*:}
        local fgc=${rest%%:*} legacy=${rest#*:}
        # adaptive foreground: transparent, background comes from ic_vegi_background
        compose "$fgc"    "$ADAPTIVE_FRAC" none "$fg" square \
            "$res/drawable-$dpi/ic_launcher_foreground.png"
        # legacy icons for API < 26
        compose "$legacy" "$LEGACY_FRAC" "$bg" "$fg" rounded \
            "$res/mipmap-$dpi/ic_launcher.png"
        compose "$legacy" "$ROUND_FRAC"  "$bg" "$fg" round \
            "$res/mipmap-$dpi/ic_launcher_round.png"
    done
}

android_flavor main "$BUY_BG"  "$BUY_FG"
android_flavor buy  "$BUY_BG"  "$BUY_FG"
android_flavor sell "$SELL_BG" "$SELL_FG"

# ic_vegi is the historical name for the same legacy icon; keep it in step.
echo "android/main ic_vegi aliases"
for d in $DENSITIES; do
    dpi=${d%%:*}
    for flavor in main buy; do
        src="$ROOT/androidApp/src/$flavor/res/mipmap-$dpi/ic_launcher.png"
        dst="$ROOT/androidApp/src/$flavor/res/mipmap-$dpi/ic_vegi.png"
        [ -f "$src" ] && [ -e "$dst" ] && cp "$src" "$dst" && \
            echo "  $(printf '%-72s' "${dst#$ROOT/}") copy of ic_launcher"
    done
done

# ----------------------------------------------------------------------- iOS
# App Store / springboard icons must be opaque with no alpha and no rounding;
# iOS applies its own mask.
echo "ios"
IOS="$ROOT/iosApp/iosApp/Assets.xcassets/AppIcon.appiconset"
ios_icon() { # <px> <filename>
    compose "$1" "$STORE_FRAC" "$BUY_BG" "$BUY_FG" square "$WORK/ios.png"
    convert "$WORK/ios.png" -background "$BUY_BG" -alpha remove -alpha off \
        -strip PNG24:"$IOS/$2"
    echo "  $(printf '%-72s' "AppIcon.appiconset/$2") $1x$1"
}
ios_icon 40   Icon-20@2x.png
ios_icon 60   Icon-20@3x.png
ios_icon 58   Icon-29@2x.png
ios_icon 87   Icon-29@3x.png
ios_icon 80   Icon-40@2x.png
ios_icon 120  Icon-40@3x.png
ios_icon 120  Icon-60@2x.png
ios_icon 180  Icon-60@3x.png
ios_icon 20   Icon-20.png
ios_icon 40   Icon-20-ipad@2x.png
ios_icon 29   Icon-29.png
ios_icon 58   Icon-29-ipad@2x.png
ios_icon 40   Icon-40.png
ios_icon 80   Icon-40-ipad@2x.png
ios_icon 152  Icon-76@2x.png
ios_icon 167  Icon-83.5@2x.png
ios_icon 1024 Icon-1024.png

# ------------------------------------------------------- store + in-app assets
echo "store / shared"
# Play Store listing icon: 512x512, 32-bit PNG.
compose 512 "$STORE_FRAC" "$BUY_BG" "$BUY_FG" rounded \
    "$ROOT/androidApp/fastlane/metadata/android/de-DE/images/icon.png"
# Shown on the About screen; 512 so it stays sharp on tablets.
compose 512 "$STORE_FRAC" "$BUY_BG" "$BUY_FG" rounded \
    "$ROOT/shared/src/commonMain/composeResources/drawable/app_icon.png"
# The bare mark for the splash screen, transparent and trimmed to the art. The
# fill baked in here is irrelevant: the splash tints it from the running colour
# scheme, so one file serves the light and the dark theme and both flavors.
# 1024 wide covers the 220dp it is drawn at, at xxxhdpi, without an upscale.
render_mark 1024 "$BUY_FG" \
    "$ROOT/shared/src/commonMain/composeResources/drawable/logo_mark.png"

echo
echo "done."
