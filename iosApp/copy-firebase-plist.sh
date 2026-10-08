#!/bin/bash
#
# Copy the GoogleService-Info plist matching this build's configuration into the app
# bundle, as the "Copy Firebase Plist" build phase.
#
# The plist chooses the Firebase project, so it has to follow BOTH axes:
#
#     Debug-Buy    -> GoogleService-Info-Debug-Buy.plist     (fire-one-58ddc)
#     Release-Buy  -> GoogleService-Info-Release-Buy.plist   (bodenschaetze-a988e)
#     Debug-Sell   -> GoogleService-Info-Debug-Sell.plist    (fire-one-58ddc)
#     Release-Sell -> GoogleService-Info-Release-Sell.plist  (not set up yet)
#
# The previous version matched only *Buy* / *Sell*. "Release-Buy" contains "Buy", so it
# took the first branch and every build -- release included -- shipped the development
# project. This version fails the build instead of guessing, because silently talking to
# the development backend from a release build is far worse than not building.
#
set -euo pipefail

echo "Firebase config: resolving plist for configuration '${CONFIGURATION}'"

case "${CONFIGURATION}" in
  Debug-Buy)    FIREBASE_PLIST="GoogleService-Info-Debug-Buy.plist"   ;;
  Release-Buy)  FIREBASE_PLIST="GoogleService-Info-Release-Buy.plist" ;;
  Debug-Sell)   FIREBASE_PLIST="GoogleService-Info-Debug-Sell.plist"  ;;
  Release-Sell) FIREBASE_PLIST="GoogleService-Info-Release-Sell.plist";;
  *)
    echo "error: unknown build configuration '${CONFIGURATION}'." >&2
    echo "       Expected one of Debug-Buy, Release-Buy, Debug-Sell, Release-Sell." >&2
    echo "       Add a case here and a matching plist rather than defaulting to one," >&2
    echo "       or the new configuration silently inherits another project's backend." >&2
    exit 1
    ;;
esac

SOURCE_PATH="${PROJECT_DIR}/iosApp/${FIREBASE_PLIST}"
DEST_PATH="${BUILT_PRODUCTS_DIR}/${PRODUCT_NAME}.app/GoogleService-Info.plist"

if [ ! -f "${SOURCE_PATH}" ]; then
  echo "error: ${FIREBASE_PLIST} not found at ${SOURCE_PATH}" >&2
  echo "       Download it from the Firebase console for the project this" >&2
  echo "       configuration targets, and add it to iosApp/iosApp/." >&2
  echo "       See doc/build-identity-and-firebase-wiring.md." >&2
  exit 1
fi

# The Google Sign-In callback URL scheme lives in Info.plist, which is processed in a
# later build phase, so it cannot be patched from here. It reads the build setting
# GOOGLE_REVERSED_CLIENT_ID instead -- which means that value and the plist are two
# copies of one fact. Verify they agree, so they cannot drift the way the hardcoded
# web client id on Android did.
PLIST_REVERSED_ID="$(/usr/libexec/PlistBuddy -c 'Print :REVERSED_CLIENT_ID' "${SOURCE_PATH}" 2>/dev/null || true)"
if [ -z "${PLIST_REVERSED_ID}" ]; then
  echo "error: ${FIREBASE_PLIST} has no REVERSED_CLIENT_ID; Google Sign-In cannot work." >&2
  exit 1
fi
if [ "${GOOGLE_REVERSED_CLIENT_ID:-}" != "${PLIST_REVERSED_ID}" ]; then
  echo "error: GOOGLE_REVERSED_CLIENT_ID does not match ${FIREBASE_PLIST}." >&2
  echo "       build setting: ${GOOGLE_REVERSED_CLIENT_ID:-<unset>}" >&2
  echo "       plist:         ${PLIST_REVERSED_ID}" >&2
  echo "       Update the build setting for '${CONFIGURATION}' in the Xcode project." >&2
  echo "       Google Sign-In would otherwise fail: the redirect comes back on a URL" >&2
  echo "       scheme the app does not declare." >&2
  exit 1
fi

cp "${SOURCE_PATH}" "${DEST_PATH}"
echo "Firebase config: copied ${FIREBASE_PLIST} ($(/usr/libexec/PlistBuddy -c 'Print :PROJECT_ID' "${SOURCE_PATH}"))"
