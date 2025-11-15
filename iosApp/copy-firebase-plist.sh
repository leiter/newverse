#!/bin/bash

# Script to copy the correct GoogleService-Info.plist based on the build configuration
# This script should be run as a Build Phase in Xcode

echo "🔥 Firebase Config: Copying GoogleService-Info.plist for configuration: ${CONFIGURATION}"

# Determine which plist to use based on configuration name
if [[ "${CONFIGURATION}" == *"Buy"* ]]; then
    FIREBASE_PLIST="GoogleService-Info-Buy.plist"
    echo "📦 Using Buy variant configuration"
elif [[ "${CONFIGURATION}" == *"Sell"* ]]; then
    FIREBASE_PLIST="GoogleService-Info-Sell.plist"
    echo "📦 Using Sell variant configuration"
else
    echo "⚠️  Unknown configuration: ${CONFIGURATION}"
    echo "⚠️  Defaulting to Buy variant"
    FIREBASE_PLIST="GoogleService-Info-Buy.plist"
fi

# Source and destination paths
SOURCE_PATH="${PROJECT_DIR}/iosApp/${FIREBASE_PLIST}"
DEST_PATH="${BUILT_PRODUCTS_DIR}/${PRODUCT_NAME}.app/GoogleService-Info.plist"

# Check if source file exists
if [ ! -f "$SOURCE_PATH" ]; then
    echo "❌ ERROR: ${FIREBASE_PLIST} not found at ${SOURCE_PATH}"
    exit 1
fi

# Copy the correct plist to the app bundle
echo "📋 Copying ${FIREBASE_PLIST} to app bundle..."
cp "$SOURCE_PATH" "$DEST_PATH"

if [ $? -eq 0 ]; then
    echo "✅ Successfully copied ${FIREBASE_PLIST}"
else
    echo "❌ Failed to copy ${FIREBASE_PLIST}"
    exit 1
fi

# Verify the file was copied
if [ -f "$DEST_PATH" ]; then
    echo "✅ GoogleService-Info.plist is in the app bundle"
else
    echo "❌ GoogleService-Info.plist is NOT in the app bundle"
    exit 1
fi
