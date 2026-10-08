# Build Identity and Firebase Wiring

**Written:** 2026-10-08
**Status:** open — coordination note, nothing here is implemented yet
**Scope:** how a build picks its Firebase backend, and what identifies that build
(applicationId on Android, bundle ID on iOS), across both flavors and both platforms.

This started as a single iOS finding and turned out to touch the flavor axis, the
build-type axis, the Firebase app registrations and the store listings at once. The
items below are interdependent: doing one without deciding the others produces a
half-state that is worse than today. So this file records the decided design and the
agreed order, and nothing is changed until the matrix at the bottom is settled.

---

## Where things stand today

### Android

Backend is chosen by build type, through source-set `google-services.json`:

| Source set | Firebase project | Reaches |
|---|---|---|
| `androidApp/src/debug/` | `fire-one-58ddc` (dev) | debug builds |
| `androidApp/src/release/` | `bodenschaetze-a988e` (prod) | release builds |
| `androidApp/src/main/` | `fire-one-58ddc` (dev) | fallback for any other build type |

Identity is `com.together` plus a per-flavor suffix (`androidApp/build.gradle.kts`):
`com.together.buy` and `com.together.sell`. The debug build type sets **no**
`applicationIdSuffix`, so a debug build and a release build of the same flavor carry
the same applicationId.

### iOS

Backend is chosen by a `Copy Firebase Plist` build phase
(`iosApp/iosApp.xcodeproj/project.pbxproj:199-215`) running
`iosApp/copy-firebase-plist.sh`, which copies the selected plist into the app bundle.

Four Xcode configurations exist: `Debug-Buy`, `Release-Buy`, `Debug-Sell`,
`Release-Sell`. Bundle IDs are `com.together.buy` and `com.together.sell` — the same
two identifiers as Android, with no debug/release distinction.

---

## Finding 1 — iOS always reaches the dev backend

**Agreed to fix, deliberately deferred until the identity question below is settled.**

Two separate causes:

1. **The script resolves one axis only.** It tests `${CONFIGURATION}` for `*Buy*` and
   `*Sell*` and stops there. `"Release-Buy"` contains `"Buy"`, so it takes the first
   branch; `Debug-Buy` and `Release-Buy` receive the same file. There is no
   debug/release branch at all.

2. **There is no production plist to select.** All three plists in `iosApp/iosApp/`
   point at `fire-one-58ddc`:

   | File | Project | Bundle ID |
   |---|---|---|
   | `GoogleService-Info-Buy.plist` | `fire-one-58ddc` | `com.together.buy` |
   | `GoogleService-Info-Sell.plist` | `fire-one-58ddc` | `com.together.sell` |
   | `GoogleService-Info.plist` | `fire-one-58ddc` | `com.together.buy` |

   The third is dead weight: it appears in no build phase and no file reference
   (`PBXResourcesBuildPhase` holds only `Assets.xcassets`). Left over from before the
   script existed.

Consequence: an iOS release build today talks to the development project, silently.

### Planned change

The fix needs four plists on a 2x2 grid (debug/release x buy/sell). Two of them do not
exist and cannot be produced from the repo — they have to be downloaded from the
Firebase console, which likely means first registering two iOS apps in
`bodenschaetze-a988e`. That project was Android-first and probably has no iOS apps at
all. **Unverified: check the console.**

Worth doing even before those files exist, because it converts a silent wrong-backend
ship into a build error:

- [ ] Rewrite `copy-firebase-plist.sh` to resolve both axes to
      `GoogleService-Info-{Debug,Release}-{Buy,Sell}.plist`.
- [ ] Rename the two existing plists to their `Debug-` names.
- [ ] `exit 1` when the resolved file is missing, naming the file and the Firebase
      project to pull it from.
- [ ] Drop the "unknown configuration defaults to Buy" fallback — an unrecognised
      configuration should fail, not guess.
- [ ] Delete the stray `GoogleService-Info.plist`.
- [ ] Extend the build phase `inputPaths` to list all four plists.

Net effect: iOS debug builds behave exactly as now; iOS release builds become
impossible until the production plists land. That is the correct failure direction.

### Also part of Finding 1 — the Google Sign-In URL scheme is hardcoded

`iosApp/iosApp/Info.plist:30` pins the URL scheme to
`com.googleusercontent.apps.352833414422-llkofcdstuc7pcf0qubpratujmkrj106`. The
`352833414422` is the **dev** project number, so this is the dev project's reversed
client ID baked into a file that does not vary per configuration.

Supplying a production plist is therefore not sufficient on its own: Google Sign-In on
an iOS release build would still fail, because the returned redirect uses the prod
client ID and no registered URL scheme matches it. The scheme has to become
configuration-driven — either a build setting referenced as `$(...)` from Info.plist,
or `copy-firebase-plist.sh` patching the built Info.plist with the selected plist's
`REVERSED_CLIENT_ID`.

- [ ] Make the Google Sign-In URL scheme follow the selected plist.

**Unblocked** by the identity decision below: bundle IDs stay as they are, so the two
production plists are registered against `com.together.buy` / `com.together.sell` in
`bodenschaetze-a988e` and nothing has to be regenerated later.

---

## Finding 2 — debug and release share one identifier

Android debug sets no `applicationIdSuffix`, and iOS has no debug bundle ID. So for
each flavor there is exactly one identifier covering both backends. Effects:

- A dev build and a production build cannot be installed side by side. Installing one
  replaces the other, taking its local state with it.
- Nothing on the device says which backend an installed app is pointed at.

**Decided: accepted as-is.** See "Decided" below — one identifier per flavor stays.

---

## Finding 3 — no `.firebaserc`

There are no Firebase CLI project aliases. `firebase.json` points `database.rules` at
`firebase/database.rules.json` with no per-project targets, so every
`firebase deploy --only database` depends on remembering the right `--project`.

Given the undeployed rules work tracked in `doc/pre-release-checklist.md`, a
wrong-project rules deploy is the highest-consequence mistake currently available. This
item is independent of the identity question and can be done at any time.

---

## Finding 4 — `src/main/google-services.json` is a silent fallback

It points at dev. Any build type added later inherits the dev backend with no error.
Deleting it makes a missing configuration fail the build instead. Minor, and
independent of everything else.

---

## Finding 5 — the default seller id is a third per-environment value

**Fixed 2026-10-08.** Found while debugging "no articles, stuck loading" on a
production build.

The marketplace has one seller, but its auth uid differs per Firebase project:
`cPkcZSiF3LMXjWoqW6AqpA9paoO2` in `fire-one-58ddc`, `2e2h2VdsyqM7QakqUfCVLkFCsUh1` in
`bodenschaetze-a988e`. Both `GitLiveArticleRepository.DEFAULT_SELLER_ID` and
`DefaultSellerConfig.sellerId` hardcoded the **dev** uid, so a production build read
`/articles/cPkcZSiF3LMXjWoqW6AqpA9paoO2` — a node that does not exist there — and
showed an empty catalogue. It worked in dev only by coincidence, because there the
hardcoded uid is the real seller.

It also broke seller connection permanently in production. `BuyerSellerConfig.init`
clears any stored seller id that differs from `demoSellerId`, and in production every
valid id differs from the dev uid, so a connection made through `newverse://connect`
was wiped on the next launch.

The fix treats the seller id as following the backend, like `google-services.json` and
the plist:

- `shared/build.gradle.kts` sets `buildConfigField("String", "DEFAULT_SELLER_ID", …)`
  per build type, next to named `DEV_SELLER_ID` / `PROD_SELLER_ID` constants.
- `expect val defaultSellerId` (`shared/src/commonMain/.../data/config/DefaultSellerId.kt`)
  with four actuals: Android reads `BuildConfig`; iOS keys off `Platform.isDebugBinary`,
  the same debug/release distinction `copy-firebase-plist.sh` uses; js takes the dev
  value.
- `DefaultSellerConfig.sellerId` and `GitLiveArticleRepository.DEFAULT_SELLER_ID`
  resolve through it, which also repairs the `BuyerSellerConfig.init` comparison.

**Whenever a build's Firebase project changes, this value changes with it.** Three
things now travel together per environment: `google-services.json` / the plist, the
Google Sign-In URL scheme on iOS, and the default seller id.

Verified on a Pixel 7a with a locally signed `buyRelease`: the production catalogue
loads, and the first article shown (`Milan`, 3,80 EUR/kg) matches the production export.

---

## Decided: one identifier per flavor, TestFlight is a release build

**Decided 2026-10-08.**

**One applicationId / bundle ID per flavor** — `com.together.buy` and
`com.together.sell` — with no build-type suffix on either platform. This works only
because the two Firebase projects were kept: a Firebase app registration is scoped to
a project, so the same package can be registered in `fire-one-58ddc` with the debug
signing certs and in `bodenschaetze-a988e` with the release ones, and the two never
meet. Collapsing to one Firebase project would have forced suffixes.

Consequences accepted:

- Debug and release builds of a flavor cannot be installed side by side. Switching
  requires an uninstall, which takes local state with it.
- The `newverse://connect` and `newverse://contact` deep links stay unscoped, which is
  fine precisely because co-installation is not possible.

**TestFlight is a release build against the production backend.** `Release-Buy` /
`Release-Sell` feed both `fastlane beta` and `fastlane deploy`
(`iosApp/fastlane/Fastfile:113-149`), and both select the production plist. No `Beta-*`
configuration is added.

The reason is that with one bundle ID there is one App Store Connect record, so a
TestFlight build *is* the submission candidate. Pointing it at dev would mean building
twice and never promoting — relying on operator discipline at the moment of shipping.
More importantly, `doc/pre-release-checklist.md` describes the exact bug class that
has to be caught before launch: works on dev, broken on prod. TestFlight is the only
pre-release exercise of the production backend, so aiming it at dev would remove the
one check that matters most.

Tester data is kept out of real customer data with a **dedicated test seller in
production** instead. Orders and articles are already seller-scoped
(`/orders/{sellerId}/…`, `/articles/{sellerId}/…`) and buyers attach to a seller
through `newverse://connect`, so a test seller isolates tester traffic naturally while
still exercising the real rules, indexes and Storage bucket.

Resulting matrix:

| Build | Backend | Selected by |
|---|---|---|
| Android debug (sideload) | dev | `androidApp/src/debug/google-services.json` |
| Android release, incl. Play internal track | prod | `androidApp/src/release/google-services.json` |
| iOS `Debug-*` (Xcode to device/simulator) | dev | `GoogleService-Info-Debug-*.plist` |
| iOS `Release-*`, TestFlight **and** App Store | prod | `GoogleService-Info-Release-*.plist` |

No Play Store debug application is wanted; Android's internal testing track is a
release build on prod, matching TestFlight.

---

## Reference: what identifier proliferation would have cost

Kept for the record, in case the decision is revisited.

| Consumer | Per added identifier |
|---|---|
| Firebase app registrations | one app per project, per identifier; one config file each |
| Google Sign-In / OAuth clients | one client ID per identifier, per project |
| Apple Sign-In | service IDs and capability wiring per bundle ID |
| Play Console / App Store Connect | unaffected — store listings track release IDs only |
| Firebase Storage / Dynamic Links / deep links | per-identifier allowlists where configured |

Current count is 2 identifiers (buy, sell). Adding a debug suffix on both platforms
makes it 4, and each one needs registering in whichever Firebase projects it talks to.
