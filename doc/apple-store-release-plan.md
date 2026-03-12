# Plan: App Store Release Assets — BODENSCHÄTZE (Buy & Sell)

## Context

The app "BODENSCHÄTZE" is a KMP marketplace for a local organic farmers market (Eric Dehn, Fürstenwalde/Spree). It has two separate apps:
- **Buy** (`com.together.buy`): Customer app — browse products, order for Thursday pickup
- **Sell** (`com.together.sell`): Vendor app — manage products, orders, customer access

Existing iOS fastlane metadata (`iosApp/fastlane/metadata/de-DE/` and `en-US/`) uses generic "Newverse" branding and thin copy. No screenshots exist. Android has no store metadata at all. `Deliverfile` already has `skip_screenshots(true)` so screenshots are optional for first upload.

---

## Scope

1. **Text assets**: Rewrite all metadata files (name, subtitle, description, promotional text, keywords, release notes) for both languages — fully branded as BODENSCHÄTZE
2. **Screenshot plan**: Define exactly which screens to capture, on which device, and create them using the self-controlling loop (ADB screenshots on connected Android device)
3. **Android Play Store**: Create the equivalent metadata directory structure under `androidApp/fastlane/supply/metadata/` (the standard `supply` layout)

Only the **Buy app** goes to the public App Store / Play Store. The **Sell app** is distributed separately (invite-only / TestFlight / internal track). Both will get complete text assets; screenshots will focus on the Buy app first.

---

## Part 1 — Text Assets

### iOS — Buy App (`iosApp/fastlane/metadata/`)

#### `de-DE/name.txt`
```
Bodenschätze
```

#### `de-DE/subtitle.txt` *(new file, max 30 chars)*
```
Frisch vom Markt bestellen
```

#### `de-DE/promotional_text.txt` *(max 170 chars)*
```
Bio, regional, frisch – direkt vom Wochenmarkt zu dir. Bestelle deine Lieblingsprodukte von Bodenschätze und hole sie donnerstags ab.
```

#### `de-DE/description.txt` *(max 4000 chars)*
```
BODENSCHÄTZE – Frisch vom Markt

Bio. Regional. Frisch. Seit 2020 bringt Bodenschätze die besten Produkte vom Ökomarkt direkt zu dir.

MIT DER APP KANNST DU:
• Aktuelle Produkte entdecken – Gemüse, Obst, Kräuter und mehr
• Bequem vorbestellen und donnerstags am Marktstand abholen
• Deinen Warenkorb verwalten und Bestellungen einsehen
• Dein Kundenprofil pflegen und Lieblingsprodukte speichern

WO FINDEST DU UNS?
Ökomarkt im Hansaviertel
Neue Gartenstraße, 15517 Fürstenwalde / Spree
Jeden Donnerstag – frisch, regional, bio.

Fragen? Erreichbar unter bodenschaetze@posteo.de oder 0172 - 46 23 741.

Bodenschätze – weil gutes Essen aus der Region kommt.
```

#### `de-DE/keywords.txt` *(max 100 chars)*
```
bio,regional,markt,frisch,gemüse,bauernmarkt,wochenmarkt,bestellen,abholen,ökomarkt
```

#### `de-DE/release_notes.txt`
```
Erste Version der Bodenschätze-App.

• Produkte entdecken und vorbestellen
• Donnerstags am Markt abholen
• Kundenprofil und Bestellhistorie
```

---

#### `en-US/name.txt`
```
Bodenschätze
```

#### `en-US/subtitle.txt` *(new file)*
```
Order fresh from the market
```

#### `en-US/promotional_text.txt`
```
Organic, regional, fresh – straight from the weekly market. Order your favourite products from Bodenschätze and pick them up every Thursday.
```

#### `en-US/description.txt`
```
BODENSCHÄTZE – Fresh From the Market

Organic. Regional. Fresh. Since 2020, Bodenschätze brings the best products from the local eco market right to your table.

WITH THE APP YOU CAN:
• Discover seasonal products – vegetables, fruit, herbs and more
• Pre-order with ease and pick up at the market every Thursday
• Manage your basket and view your order history
• Save your favourite products and manage your profile

WHERE TO FIND US:
Ökomarkt im Hansaviertel
Neue Gartenstraße, 15517 Fürstenwalde / Spree
Every Thursday – fresh, regional, organic.

Questions? Reach us at bodenschaetze@posteo.de or +49 172 4623741.

Bodenschätze – because great food comes from the region.
```

#### `en-US/keywords.txt`
```
organic,regional,market,fresh,vegetables,farmers market,weekly market,order,pickup,eco
```

#### `en-US/release_notes.txt`
```
First release of the Bodenschätze app.

• Discover and pre-order products
• Pick up every Thursday at the market
• Customer profile and order history
```

---

### Android Play Store — Buy App

**Directory to create:** `androidApp/fastlane/supply/metadata/`

Standard `supply` (fastlane) layout:
```
androidApp/fastlane/supply/metadata/
  de-DE/
    title.txt           (max 50 chars)
    short_description.txt (max 80 chars)
    full_description.txt  (max 4000 chars)
    changelogs/
      default.txt
  en-US/
    title.txt
    short_description.txt
    full_description.txt
    changelogs/
      default.txt
```

**de-DE content:**
- `title.txt`: `Bodenschätze`
- `short_description.txt`: `Bio & regionale Produkte vom Wochenmarkt vorbestellen und abholen`
- `full_description.txt`: Same as iOS de-DE description above
- `changelogs/default.txt`: Same as iOS de-DE release_notes

**en-US content:**
- `title.txt`: `Bodenschätze`
- `short_description.txt`: `Pre-order organic & regional products from your local weekly market`
- `full_description.txt`: Same as iOS en-US description above
- `changelogs/default.txt`: Same as iOS en-US release_notes

---

## Part 2 — Screenshots

### Required sizes

**iOS App Store (minimum required):**
| Label | Resolution | Device |
|---|---|---|
| 6.7" (required) | 1290 × 2796 px | iPhone 15 Pro Max |
| 6.5" (required) | 1242 × 2688 px | iPhone 11 Pro Max |
| 5.5" (required) | 1242 × 2208 px | iPhone 8 Plus |

**Google Play (required):**
- Phone: 1080 × 1920 px minimum, max 2 MB, PNG/JPEG

### Screens to capture for Buy App (5 screenshots)

| # | Screen | What to show |
|---|---|---|
| 1 | Login/splash | BODENSCHÄTZE branding, "Frisch vom Markt" tagline |
| 2 | Product list | Products grid with names, prices, carrot logo |
| 3 | Product detail | Single product with Add-to-basket button |
| 4 | Basket/checkout | Cart with items, total, pickup date (Thursday) |
| 5 | Profile/orders | Order history or profile screen |

### Screenshot approach

Screenshots are taken via self-controlling loop:
1. Install Buy debug APK on connected device: `adb install`
2. Navigate to each screen via `adb shell input tap` / keyevents
3. Capture: `adb shell screencap -p /sdcard/s.png && adb pull /sdcard/s.png ./tmp/`
4. Resize/crop to required dimensions with `convert` (ImageMagick)
5. Save to `iosApp/fastlane/screenshots/en-US/` and `androidApp/fastlane/supply/screenshots/`

---

## Files to Create / Modify

### Modify (text rewrites):
- `iosApp/fastlane/metadata/de-DE/name.txt`
- `iosApp/fastlane/metadata/de-DE/description.txt`
- `iosApp/fastlane/metadata/de-DE/promotional_text.txt`
- `iosApp/fastlane/metadata/de-DE/keywords.txt`
- `iosApp/fastlane/metadata/de-DE/release_notes.txt`
- `iosApp/fastlane/metadata/en-US/name.txt`
- `iosApp/fastlane/metadata/en-US/description.txt`
- `iosApp/fastlane/metadata/en-US/promotional_text.txt`
- `iosApp/fastlane/metadata/en-US/keywords.txt`
- `iosApp/fastlane/metadata/en-US/release_notes.txt`

### Create (new files):
- `iosApp/fastlane/metadata/de-DE/subtitle.txt`
- `iosApp/fastlane/metadata/en-US/subtitle.txt`
- `androidApp/fastlane/supply/metadata/de-DE/title.txt`
- `androidApp/fastlane/supply/metadata/de-DE/short_description.txt`
- `androidApp/fastlane/supply/metadata/de-DE/full_description.txt`
- `androidApp/fastlane/supply/metadata/de-DE/changelogs/default.txt`
- `androidApp/fastlane/supply/metadata/en-US/title.txt`
- `androidApp/fastlane/supply/metadata/en-US/short_description.txt`
- `androidApp/fastlane/supply/metadata/en-US/full_description.txt`
- `androidApp/fastlane/supply/metadata/en-US/changelogs/default.txt`

### Screenshots (after text assets are done):
- `iosApp/fastlane/screenshots/en-US/*.png` (5 screens)
- `iosApp/fastlane/screenshots/de-DE/*.png` (5 screens, same content)
- `androidApp/fastlane/supply/screenshots/en-US/*.png`

---

## Verification

1. Review all text files match character limits (iOS: name ≤30, subtitle ≤30, promo ≤170, keywords ≤100)
2. `./gradlew :androidApp:assembleBuyDebug` still passes after file creation
3. `bundle exec fastlane deliver --metadata_only` (dry run) from `iosApp/` to validate iOS metadata format
