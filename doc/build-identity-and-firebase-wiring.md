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

### Change made 2026-10-08 (not yet verified in Xcode)

The buy app was registered as an Apple app in `bodenschaetze-a988e` and its
`GoogleService-Info.plist` downloaded. **The sell flavor on iOS is deliberately out of
scope for now**, so no production plist exists for it and `Release-Sell` fails the
build by design.

Plists now follow both axes:

| Configuration | File | Project |
|---|---|---|
| `Debug-Buy` | `GoogleService-Info-Debug-Buy.plist` | `fire-one-58ddc` |
| `Release-Buy` | `GoogleService-Info-Release-Buy.plist` | `bodenschaetze-a988e` |
| `Debug-Sell` | `GoogleService-Info-Debug-Sell.plist` | `fire-one-58ddc` |
| `Release-Sell` | *(absent)* | build fails with a message |

- `copy-firebase-plist.sh` resolves all four configurations, and exits non-zero on an
  unknown configuration or a missing plist instead of defaulting to Buy.
- The stray unreferenced `GoogleService-Info.plist` was deleted.
- The build phase's `inputPaths` lists all four plists.

The Google Sign-In URL scheme is handled by a build setting rather than by patching the
built `Info.plist`: the "Copy Firebase Plist" phase runs second, before `Resources`, so
the bundle's `Info.plist` does not exist when it runs. `Info.plist` therefore contains
`$(GOOGLE_REVERSED_CLIENT_ID)`, defined per configuration in the Xcode project.

That makes the reversed client id two copies of one fact, which is the drift that caused
the Android bug below, so the script **verifies** the build setting against the selected
plist's `REVERSED_CLIENT_ID` and fails the build if they disagree.

**Unverified:** none of this has been run through Xcode -- it was written on Linux. The
shell logic was exercised directly (correct file per configuration; non-zero exit for an
unknown configuration, a missing plist and a mismatched client id) and the pbxproj was
checked for balanced structure and dangling references, but the first real macOS build
may still need adjustment.

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

**Fixed 2026-10-08.** There were no project aliases, so every
`firebase deploy --only database` depended on remembering the right `--project`, with
six rules commits still undeployed and a day spent switching between the two projects.

`.firebaserc` now defines `dev` -> `fire-one-58ddc` and `prod` ->
`bodenschaetze-a988e`, with **`default` pointing at dev** so a forgotten `-P` lands on
the harmless project rather than on production.

```
firebase deploy --only database -P prod
```

## Finding 4 — `src/main/google-services.json` was a silent fallback

**Fixed 2026-10-08.** It pointed at the development project, so any build type added
later would have inherited that backend with no error. Everything this app gets right
about environments now rests on build-type-specific config -- the seller id, the Google
Sign-In client id, the iOS plist -- and this file sat underneath that as a trapdoor.

Deleted. `src/debug/` and `src/release/` cover every variant, verified by building
`buyDebug`, `buyRelease` and `sellRelease` afterwards and reading back the resolved
database URL for each. A build type without its own config now fails instead of
quietly reaching development.

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
