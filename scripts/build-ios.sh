#!/bin/bash

set -eo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
IOS_DIR="$PROJECT_DIR/iosApp"

SCHEME="${1:-iosApp-Buy}"

# Derive flavor (Buy/Sell) from scheme name
if [[ "$SCHEME" == *"Sell"* ]]; then
  FLAVOR="Sell"
else
  FLAVOR="Buy"
fi

CONFIGURATION="Release-$FLAVOR"
ARCHIVE_PATH="$PROJECT_DIR/build/ios/$SCHEME.xcarchive"
EXPORT_PATH="$PROJECT_DIR/build/ios/export-$FLAVOR"
EXPORT_OPTIONS="$IOS_DIR/exportOptions.plist"
INFO_PLIST="$IOS_DIR/iosApp/Info.plist"

cd "$PROJECT_DIR"

echo "=== Incrementing build number ==="
CURRENT_BUILD=$(/usr/libexec/PlistBuddy -c "Print CFBundleVersion" "$INFO_PLIST")
NEW_BUILD=$((CURRENT_BUILD + 1))
/usr/libexec/PlistBuddy -c "Set CFBundleVersion $NEW_BUILD" "$INFO_PLIST"
echo "Build number: $CURRENT_BUILD → $NEW_BUILD"

echo ""
echo "=== Building shared module for iOS ==="
echo "Project:       $PROJECT_DIR"
echo "Scheme:        $SCHEME"
echo "Configuration: $CONFIGURATION"
echo ""

echo "--- Compiling shared framework for iosArm64 (Release) ---"
./gradlew :shared:linkReleaseFrameworkIosArm64

echo ""
echo "=== Archiving $SCHEME ==="

xcodebuild archive \
  -workspace "$IOS_DIR/iosApp.xcworkspace" \
  -scheme "$SCHEME" \
  -configuration "$CONFIGURATION" \
  -destination "generic/platform=iOS" \
  -archivePath "$ARCHIVE_PATH" \
  CODE_SIGN_STYLE=Manual

echo ""
echo "=== Exporting IPA ==="

xcodebuild -exportArchive \
  -archivePath "$ARCHIVE_PATH" \
  -exportPath "$EXPORT_PATH" \
  -exportOptionsPlist "$EXPORT_OPTIONS"

echo ""
echo "=== Done ==="
echo "Archive: $ARCHIVE_PATH"
echo "IPA:     $EXPORT_PATH"


# run ./scripts/build-ios.sh 2>&1 | grep -E "(=== |--- |Build number|ARCHIVE SUCCEEDED|EXPORT SUCCEEDED|error:|Error