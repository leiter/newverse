#!/bin/bash

# Upload App Store Metadata Script
# Run this from your terminal to upload metadata to App Store Connect

set -e  # Exit on error

echo "🚀 Uploading metadata to App Store Connect..."
echo ""

# Navigate to iosApp directory
cd "$(dirname "$0")"

# Set environment variables for non-interactive mode
export CI=true
export FASTLANE_SKIP_UPDATE_CHECK=1
export FASTLANE_HIDE_TIMESTAMP=1
export FASTLANE_DISABLE_COLORS=0
export FASTLANE_SKIP_DOCS_UPDATE_CHECK=1

# Run fastlane metadata upload
echo "📝 Running fastlane deliver_metadata..."
bundle exec fastlane deliver_metadata

echo ""
echo "✅ Metadata upload complete!"
echo ""
echo "Next steps:"
echo "1. Check App Store Connect: https://appstoreconnect.apple.com"
echo "2. Verify metadata appears correctly"
echo "3. Add screenshots if needed"
echo "4. Submit for review when ready"
