# Apple App Store — Release Readiness Status

**Date:** 2026-08-05
**App:** BODENSCHÄTZE Buy (`com.together.buy`)
**Assessed on:** `ios_test_build_merge` @ `8184c83` (contains `origin/main` + iOS/Apple-Sign-In work)
**Companion doc:** `doc/apple-store-release-plan.md` (the plan; largely unexecuted for iOS)

---

## Verdict

**Not submittable yet.** Infrastructure is in good shape — fastlane lanes, signing, Appfile,
legal pages all exist. What is missing is the *content* layer (store text, screenshots) and
one broken version-bump wiring that will cause upload rejections.

---

## Blockers

### 1. Build-number bumping is silently broken

| Where | Value |
|---|---|
| `iosApp/iosApp/Info.plist` → `CFBundleVersion` | literal `41` (hand-edited) |
| `iosApp.xcodeproj` → `CURRENT_PROJECT_VERSION` | `1` |
| `iosApp.xcodeproj` → `GENERATE_INFOPLIST_FILE` | `NO` |
| `iosApp.xcodeproj` → `INFOPLIST_FILE` | `iosApp/Info.plist` |

The `bump_build` lane runs `increment_build_number(xcodeproj:)`, which only touches
`CURRENT_PROJECT_VERSION`. Since `Info.plist` hardcodes `CFBundleVersion` instead of
referencing `$(CURRENT_PROJECT_VERSION)`, the bump **has no effect on the shipped build**.
The `build`, `beta` and `deploy` lanes all call `bump_build`, so all three silently no-op.
This is why build numbers have been edited by hand (39 → 41 in `b294896`).

Consequence: a second upload at the same build number is rejected by App Store Connect.

**Fix:** set `CFBundleVersion` to `$(CURRENT_PROJECT_VERSION)` and set
`CURRENT_PROJECT_VERSION = 41` in both Buy configurations.

### 2. Store metadata is still generic "Newverse" boilerplate

Part 1 of the release plan was never executed for iOS. Current state of
`iosApp/fastlane/metadata/`:

| File | de-DE | en-US |
|---|---|---|
| `name.txt` | `Newverse` ❌ | `Newverse` ❌ |
| `description.txt` | generic marketplace copy ❌ | generic marketplace copy ❌ |
| `promotional_text.txt` | generic ❌ | generic ❌ |
| `keywords.txt` | generic ❌ | generic ❌ |
| `release_notes.txt` | "Verbesserungen und Fehlerbehebungen" ❌ | "Improvements and bug fixes" ❌ |
| `subtitle.txt` | **missing** ❌ | **missing** ❌ |

Two specific problems beyond staleness:
- **Name mismatch** — metadata says "Newverse" while the Android listing and all branding say
  "Bodenschätze". The App Store Connect record must agree.
- **Release notes** say "bug fixes" — wrong for a *first* release, and Apple review flags this.

The plan doc already contains finished, on-brand copy for every one of these files. It is a
copy-in job, not a writing job.

### 3. No screenshots exist

`find` returns no `screenshots` directory anywhere in the repo. Apple requires at least the
6.7" set (1290 × 2796) for submission.

`Deliverfile` sets `skip_screenshots(true)`, so `deliver_metadata` will still upload text
successfully — but **submission for review cannot proceed** without them. This is the
longest-lead item and should start first.

---

## Ready / no action needed

- **fastlane lanes** — `build`, `beta` (TestFlight), `deploy`, `deliver_metadata`,
  `sync_certificates`, `bump_version` all defined in `iosApp/fastlane/Fastfile`.
- **Appfile** — correct: `com.together.buy`, Apple ID `bodenschaetze@cutthecrap.link`,
  team `K4K982LMZ9`.
- **Signing** — manual, `iPhone Distribution`, provisioning profile `BodenschaetzeBuy`
  mapped to `com.together.buy` in the `build` lane.
- **Export compliance** — `ITSAppUsesNonExemptEncryption = false` is set, so the per-upload
  encryption prompt is skipped.
- **Legal pages** — `docs/privacy.html`, `terms.html`, `support.html` exist in both `de/`
  and `en/`. Needed for the App Store Connect listing URLs.
- **Version** — `MARKETING_VERSION = 1.0.0`.
- **Code compiles** — `:shared:compileKotlinIosSimulatorArm64` BUILD SUCCESSFUL on this branch.

---

## To verify (not yet checked)

- **`iosApp/fastlane/api_key.json` is tracked in git.** `AuthKey_*.p8` is correctly
  gitignored (line 59), but the JSON was not inspected. Confirm it contains no private key
  material before this branch goes anywhere public.
- **App Store Connect record** — whether the app entry exists, its name, and whether any
  build has been accepted. Not determinable from the repo.
- **Runtime behaviour** — the iOS app has been compiled but not run. Two known code-level
  concerns carried in from the merge: `NetworkConnectivity` (iOS) is a stub that always
  reports online, and `HeroProductCard` uses a fixed width that works against main's
  adaptive-layout direction.

---

## Notes on the plan doc

`doc/apple-store-release-plan.md` Part 3 says to create
`androidApp/fastlane/supply/metadata/`. That layout is wrong — fastlane `supply` reads
`fastlane/metadata/android/`, which already exists and is already branded Bodenschätze.
Android is out of scope here, but the plan doc's Part 3 should not be followed as written.

---

## Suggested order of work

1. Fix the `CFBundleVersion` / `CURRENT_PROJECT_VERSION` wiring (small, unblocks all uploads)
2. Capture screenshots (longest lead time)
3. Copy the finished metadata text from the plan doc into `iosApp/fastlane/metadata/`
4. Add the two `subtitle.txt` files
5. Verify character limits (name ≤ 30, subtitle ≤ 30, promo ≤ 170, keywords ≤ 100)
6. Dry run: `bundle exec fastlane ios deliver_metadata` from `iosApp/`
7. `bundle exec fastlane ios beta` → TestFlight
