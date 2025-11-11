#!/bin/bash

# Google Sign-In Test Script
# Monitors logs for Google Sign-In flow

echo "======================================"
echo "Google Sign-In Test Monitor"
echo "======================================"
echo ""
echo "Installing APK..."
adb install -r androidApp/build/outputs/apk/buy/debug/androidApp-buy-debug.apk

echo ""
echo "Clearing logcat..."
adb logcat -c

echo ""
echo "======================================"
echo "INSTRUCTIONS:"
echo "1. Open the app on your device"
echo "2. Navigate to Login screen"
echo "3. Click 'Sign in with Google' button"
echo "======================================"
echo ""
echo "Monitoring logs (press Ctrl+C to stop)..."
echo ""

# Monitor relevant logs
adb logcat -s MainActivity:D GoogleSignInHelper:D System.out:I | grep --line-buffered -E "🔐|🔍|GoogleSignIn|triggerGoogleSignIn"
