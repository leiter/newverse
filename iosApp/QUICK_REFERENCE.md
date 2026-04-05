# Newverse iOS - Quick Reference

## 🚀 Common Commands

### Upload Beta Build to TestFlight
```bash
cd /Users/user289697/Documents/newverse/iosApp
bundle exec fastlane beta
```

### Upload Metadata Only
```bash
cd /Users/user289697/Documents/newverse/iosApp
./upload-metadata.sh
```
or from your Mac Terminal:
```bash
bundle exec fastlane deliver_metadata
```

### Build for Simulator (Debug)
```bash
bundle exec fastlane build_simulator
```

### Increment Version Number
```bash
bundle exec fastlane bump_version type:patch  # 1.0.0 → 1.0.1
bundle exec fastlane bump_version type:minor  # 1.0.0 → 1.1.0
bundle exec fastlane bump_version type:major  # 1.0.0 → 2.0.0
```

### Sync Certificates
```bash
bundle exec fastlane sync_certificates
```

---

## 📱 App Information

| Field | Value |
|-------|-------|
| App Name | Newverse |
| Bundle ID | com.together.buy |
| App Store ID | 6757431931 |
| Team ID | K4K982LMZ9 |
| Apple ID | markro77@arcor.de |
| Current Version | 1.0 |
| Current Build | 27 |

---

## 🔗 Important Links

- **App Store Connect**: https://appstoreconnect.apple.com
- **Apple Developer**: https://developer.apple.com
- **TestFlight**: https://testflight.apple.com
- **App Store Connect API Keys**: https://appstoreconnect.apple.com/access/api

---

## 📂 Important Files

| File | Purpose |
|------|---------|
| `fastlane/Fastfile` | Fastlane automation configuration |
| `fastlane/Appfile` | App identifiers and team IDs |
| `fastlane/Deliverfile` | App Store delivery settings |
| `fastlane/metadata/` | App Store metadata (descriptions, keywords) |
| `fastlane/AuthKey_M4S9AWXXL7.p8` | App Store Connect API key |
| `APP_STORE_METADATA.md` | All metadata ready for copy/paste |
| `METADATA_UPLOAD_GUIDE.md` | Detailed metadata upload instructions |
| `upload-metadata.sh` | Script to upload metadata |

---

## 🛠️ Development Workflow

### 1. Make Code Changes
Edit files in `iosApp/` or `shared/` as needed

### 2. Test Locally
```bash
# Open in Xcode
open iosApp.xcworkspace

# Or build for simulator
bundle exec fastlane build_simulator
```

### 3. Prepare for Release
```bash
# Update version if needed
bundle exec fastlane bump_version type:patch

# Build and upload to TestFlight
bundle exec fastlane beta
```

### 4. Update Metadata (if needed)
```bash
# Edit files in fastlane/metadata/
# Then upload
./upload-metadata.sh
```

### 5. Submit for Review
- Go to App Store Connect
- Select build for version
- Fill in required information
- Submit

---

## 📊 Build Status Tracking

After uploading to TestFlight:
1. Build processing: **10-30 minutes**
2. TestFlight ready: **After processing completes**
3. App Store review: **1-3 days typically**

---

## 🚨 Common Issues

### Build Fails
```bash
# Clean build
cd ../..
./gradlew clean
cd iosApp
bundle exec fastlane build
```

### Certificate Issues
```bash
bundle exec fastlane sync_certificates
```

### Metadata Won't Upload
- Use web interface: https://appstoreconnect.apple.com
- Or run from Mac Terminal (not through automation)

---

## 🔐 Credentials Location

- **API Key**: `fastlane/AuthKey_M4S9AWXXL7.p8`
- **Key ID**: M4S9AWXXL7
- **Issuer ID**: e3c717d8-00fc-4ff7-8a05-65f96d2b1562
- **Provisioning Profile**: BodenschaetzeBuy

---

## 📞 Emergency Contacts

If something goes wrong:
1. Check **Fastlane logs** in terminal
2. Check **App Store Connect** for build status
3. Check **Apple System Status**: https://www.apple.com/support/systemstatus/
4. Review **Xcode errors** if build fails

---

*Quick Reference Card - Keep this handy!*
*Last Updated: 2026-04-05*
