# Newverse iOS App Store Metadata

**App Identifier:** com.together.buy
**Apple ID:** markro77@arcor.de
**Team ID:** K4K982LMZ9
**App Store ID:** 6757431931

---

## 🇩🇪 German (de-DE) - Primary Language

### App Name
```
Newverse
```

### Promotional Text (170 characters max)
```
Entdecke lokale Produkte von Händlern in deiner Nähe!
```

### Description (4000 characters max)
```
Newverse - Dein lokaler Marktplatz

Entdecke regionale Produkte von lokalen Verkäufern in deiner Nähe. Mit Newverse kannst du:

- Frische Produkte von lokalen Anbietern entdecken
- Einfach bestellen und zur Abholung vormerken
- Deine Lieblingsprodukte speichern
- Bestellungen verwalten und den Status verfolgen

Unterstütze lokale Händler und genieße frische, regionale Produkte.
```

### Keywords (100 characters max, comma-separated)
```
marktplatz,lokal,regional,einkaufen,frisch,produkte,bauernmarkt,händler,bestellung
```

### What's New (Release Notes - 4000 characters max)
```
- Verbesserungen und Fehlerbehebungen
```

---

## 🇺🇸 English (en-US) - Secondary Language

### App Name
```
Newverse
```

### Promotional Text (170 characters max)
```
Discover local products from vendors in your area!
```

### Description (4000 characters max)
```
Newverse - Your Local Marketplace

Discover regional products from local sellers in your area. With Newverse you can:

- Discover fresh products from local vendors
- Easily order and schedule for pickup
- Save your favorite products
- Manage orders and track their status

Support local merchants and enjoy fresh, regional products.
```

### Keywords (100 characters max, comma-separated)
```
marketplace,local,regional,shopping,fresh,products,farmers,market,vendor,order
```

### What's New (Release Notes - 4000 characters max)
```
- Improvements and bug fixes
```

---

## 📝 App Store Connect Upload Instructions

### Method 1: Manual Entry via Web Interface

1. Go to [App Store Connect](https://appstoreconnect.apple.com)
2. Navigate to **My Apps** → **Newverse** (App ID: 6757431931)
3. Select your app version (1.0)
4. Click on **App Information** in the left sidebar
5. For each language (German primary, English secondary):
   - Copy the **App Name** from above
   - Copy the **Promotional Text**
   - Copy the **Description**
   - Copy the **Keywords**
6. Under **Version Information**:
   - Copy the **What's New** (Release Notes) text
7. Click **Save** after entering all information

### Method 2: Automated Upload (When Interactive Mode Available)

```bash
cd iosApp
bundle exec fastlane deliver_metadata
```

---

## ✅ Checklist Before Submission

- [ ] App name matches in both languages
- [ ] Promotional text is under 170 characters
- [ ] Description clearly explains app functionality
- [ ] Keywords are comma-separated, under 100 characters
- [ ] Release notes are included
- [ ] Screenshots uploaded (if required)
- [ ] Privacy Policy URL added (if collecting user data)
- [ ] Support URL added
- [ ] Age rating configured
- [ ] Build successfully uploaded to TestFlight

---

## 📱 Additional Information Needed for App Store

You may also need to provide:

- **Category:** Shopping (Primary)
- **Copyright:** © 2025 Newverse
- **Privacy Policy URL:** https://leiter.github.io/newverse/privacy.html
- **Support URL:** https://leiter.github.io/newverse/support.html
- **Marketing URL:** https://leiter.github.io/newverse/ (Optional)
- **Terms of Service URL:** https://leiter.github.io/newverse/terms.html
- **App Icon:** 1024x1024px (should be in your Xcode project)
- **Screenshots:**
  - iPhone 6.7" display (iPhone 15 Pro Max, 14 Pro Max, etc.)
  - iPhone 6.5" display (iPhone 11 Pro Max, XS Max, etc.)
  - Optional: iPad screenshots

### 🔗 GitHub Pages URLs (After Deployment)

Once you've pushed the changes and enabled GitHub Pages:

```
Support Page:       https://leiter.github.io/newverse/support.html
Privacy Policy:     https://leiter.github.io/newverse/privacy.html
Terms of Service:   https://leiter.github.io/newverse/terms.html
Main Landing Page:  https://leiter.github.io/newverse/
```

**Note:** These URLs will work after you:
1. Push the docs/ folder to GitHub
2. Enable GitHub Pages in repository settings
3. Wait 2-5 minutes for deployment

See `GITHUB_PAGES_SETUP.md` in the root directory for detailed setup instructions.

---

## 🔄 Next Steps After Metadata Upload

1. **Wait for build processing** (10-30 minutes typically)
2. **Add screenshots** if not already uploaded
3. **Configure pricing** (appears to be free app)
4. **Set up App Store availability** (regions/countries)
5. **Submit for review** when ready

---

*Last Updated: 2026-04-05*
*Build Version: 27*
*Fastlane Configuration: iosApp/fastlane/*
