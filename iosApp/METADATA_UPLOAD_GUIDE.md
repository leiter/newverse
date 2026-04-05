# Metadata Upload Guide for Newverse iOS App

This guide provides multiple methods to upload your app metadata to App Store Connect.

---

## ✅ Quick Start (Recommended Method)

### Option 1: Run the Upload Script from Your Terminal

```bash
cd /Users/user289697/Documents/newverse/iosApp
./upload-metadata.sh
```

This script sets all necessary environment variables and runs fastlane in the correct mode.

---

## 🛠️ Manual Methods

### Option 2: Direct Fastlane Command

From your Mac's Terminal app (not through Claude):

```bash
cd /Users/user289697/Documents/newverse/iosApp
bundle exec fastlane deliver_metadata
```

This works because your terminal is interactive and can handle prompts.

---

### Option 3: Upload via App Store Connect Web Interface (Most Reliable)

1. Open [App Store Connect](https://appstoreconnect.apple.com)
2. Sign in with: **markro77@arcor.de**
3. Navigate to **My Apps** → **Newverse** (App ID: 6757431931)
4. Select version **1.0**
5. Use the metadata from `APP_STORE_METADATA.md` to fill in:
   - App Information → Name, Subtitle, Privacy Policy
   - Localizations → German (Primary), English
   - Version Information → What's New

All the text is ready in `APP_STORE_METADATA.md` for easy copy/paste.

---

## 🔧 What I Fixed

### 1. Updated Fastfile

Modified the `deliver_metadata` lane in `fastlane/Fastfile` to include explicit parameters:

```ruby
lane :deliver_metadata do
  deliver(
    api_key: api_key,
    app_identifier: "com.together.buy",
    skip_binary_upload: true,
    skip_screenshots: true,
    force: true,
    precheck_include_in_app_purchases: false,
    submit_for_review: false,
    automatic_release: false,
    run_precheck_before_submit: false,
    metadata_path: "./fastlane/metadata",
    overwrite_screenshots: false
  )
end
```

These explicit parameters help fastlane run in non-interactive environments.

### 2. Created Upload Script

Created `upload-metadata.sh` which sets required environment variables:
- `CI=true` - Enables CI mode
- `FASTLANE_SKIP_UPDATE_CHECK=1` - Skips version checks
- `FASTLANE_HIDE_TIMESTAMP=1` - Cleaner output
- `FASTLANE_SKIP_DOCS_UPDATE_CHECK=1` - Skips doc checks

---

## 📋 Metadata Files Location

All metadata files are in:
```
iosApp/fastlane/metadata/
├── de-DE/           # German (Primary)
│   ├── name.txt
│   ├── description.txt
│   ├── keywords.txt
│   ├── promotional_text.txt
│   └── release_notes.txt
└── en-US/           # English
    ├── name.txt
    ├── description.txt
    ├── keywords.txt
    ├── promotional_text.txt
    └── release_notes.txt
```

---

## 🚫 Why It Doesn't Work Through Claude

The issue is that fastlane requires an **interactive terminal** for certain confirmations. When running through Claude:

1. **No TTY (Terminal)** - Claude's bash environment isn't a full interactive terminal
2. **Confirmation Prompts** - Fastlane tries to ask "Would you like to set fastlane up?"
3. **Non-interactive Mode** - Setting `CI=true` helps but doesn't solve all issues

This is why the `beta` lane worked (it had all context from the build) but `deliver_metadata` alone doesn't work through Claude.

---

## ✨ Next Steps After Metadata Upload

### 1. Verify Upload
- Check App Store Connect to confirm metadata appears
- Verify both German and English localizations

### 2. Add Screenshots
You'll need to add screenshots for:
- iPhone 6.7" Display (iPhone 15 Pro Max, 14 Pro Max)
- iPhone 6.5" Display (iPhone 11 Pro Max, XS Max)

Screenshot dimensions:
- 6.7": 1290 x 2796 pixels
- 6.5": 1242 x 2688 pixels

### 3. Additional App Information
Configure in App Store Connect:
- **Category**: Shopping (Primary)
- **Age Rating**: Configure based on your content
- **Privacy Policy URL**: (if you collect user data)
- **Support URL**: Your support website
- **Copyright**: © 2025 Your Company Name

### 4. App Review Information
Before submitting:
- Add demo account credentials (if sign-in required)
- Add notes for reviewers
- Configure app privacy details
- Set up export compliance

### 5. Pricing & Availability
- Configure price tier (appears to be free)
- Select countries/regions for availability
- Set release date

---

## 🐛 Troubleshooting

### "Could not find fastlane in current directory"
**Solution**: Make sure you're in `/Users/user289697/Documents/newverse/iosApp` directory

### "Could not retrieve response as fastlane runs in non-interactive mode"
**Solution**: Run from Mac Terminal app, not through automated systems

### "No data" error
**Solution**: This happens when:
- The build hasn't finished processing yet
- The app version doesn't exist in App Store Connect
- Wait 10-30 minutes for build processing to complete

### Metadata upload seems to succeed but nothing shows in App Store Connect
**Solution**:
- Wait a few minutes for App Store Connect to update
- Refresh the page
- Check if you're looking at the correct version (1.0)

---

## 📞 Support

If you encounter issues:

1. **Check Fastlane Logs**: Look for specific error messages in terminal output
2. **App Store Connect Status**: Check [Apple System Status](https://www.apple.com/support/systemstatus/)
3. **Fastlane Documentation**: [fastlane docs](https://docs.fastlane.tools/)
4. **App Store Connect Help**: Available in App Store Connect dashboard

---

## 📝 Summary

**Best Practice**: Use your Mac's Terminal to run `./upload-metadata.sh` for automated uploads, or manually enter metadata through App Store Connect web interface for the most reliable method.

All metadata text is prepared in `APP_STORE_METADATA.md` for easy reference.

---

*Last Updated: 2026-04-05*
*Fastlane Version: 2.231.1*
*App Store Connect API: Configured with AuthKey_M4S9AWXXL7.p8*
