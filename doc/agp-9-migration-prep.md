# AGP 9.0 Migration — Plan Preparation

**Status:** Preparation / no migration work started
**Date of findings:** 2026-10-08
**Scope:** `:androidApp`, `:shared`, `:build-logic`, `settings.gradle.kts`
**Verdict:** Medium-high risk. Do not start while the production launch is in
flight. There is no forcing function yet; AGP 8.13.2 builds both flavors and
targets API 36 today.

This document is the groundwork for a migration plan, not the plan: it records
what was measured, what AGP 9.0 removes, which of those removals this project
actually depends on, and the two decisions that have to be made before any
sequencing is worth writing down.

---

## Where the project stands

| Thing | Version / value | Source |
|-------|-----------------|--------|
| AGP | 8.13.2 | `build.gradle.kts` |
| Gradle | 8.13 | `gradle/wrapper/gradle-wrapper.properties` |
| Kotlin | 2.3.0 (K2) | `build.gradle.kts` |
| Compose Multiplatform | 1.10.3 | `build.gradle.kts` |
| BuildKonfig | 0.15.2 | `build.gradle.kts` |
| google-services | 4.4.2 | `build.gradle.kts` |
| compileSdk / targetSdk / minSdk | 36 / 36 / 23 | `androidApp/build.gradle.kts` |
| JVM target | 17 (launcher JVM 21, Android Studio JBR) | `androidApp/build.gradle.kts` |

AGP 9.0 shipped in January 2026 and the line has moved on since (9.2 in April
2026), so this is a migration onto an established release, not onto something
new. Gradle's own floor from the other direction: Gradle 9.0 requires AGP ≥ 8.4.

---

## The structural blocker

The build already warns about it. `./gradlew :shared:tasks` prints, verbatim:

> ⚠️ The 'org.jetbrains.kotlin.multiplatform' plugin deprecated compatibility
> with Android Gradle plugin: 'com.android.library'
> The 'org.jetbrains.kotlin.multiplatform' plugin will not be compatible with
> 'com.android.library' starting with Android Gradle Plugin 9.0.0.
> **Solution:** Please use the 'com.android.kotlin.multiplatform.library' plugin
> instead of 'com.android.library'. See https://kotl.in/gradle/agp-new-kmp

The replacement plugin is **single-variant by design**: no product flavors, no
build types, no generated `BuildConfig` class. `:shared` depends on all three:

| Dependency | Where | What it does today |
|------------|-------|--------------------|
| `productFlavors { buy, sell }` + `flavorDimensions` | `shared/build.gradle.kts:269-281` | Selects buyer vs seller build |
| `sourceSets.getByName("buy"/"sell") { kotlin.srcDirs(...) }` | `shared/build.gradle.kts:283-288` | Compiles `src/buyMain/kotlin` or `src/sellMain/kotlin` — i.e. the entire flavor UI layer |
| `sourceSets.getByName("testBuy"/"testSell")` | `shared/build.gradle.kts:290-295` | Flavor-specific unit tests |
| `buildTypes { debug/release { buildConfigField("DEFAULT_SELLER_ID") } }` | `shared/build.gradle.kts:240-247` | Dev vs production seller uid baked into the APK |
| KMP source sets `androidBuy` / `androidSell` | `shared/build.gradle.kts:86-95` | Hand-wired `dependsOn(androidMain) + dependsOn(buyMain/sellMain)` |
| Flavor sniffing from task names | `shared/build.gradle.kts:304-316` | Feeds BuildKonfig **and** the iOS source-set choice at `:102` |

Google's documented workaround for flavors — "put the variance in a standalone
`com.android.library` module and consume it from `androidMain`" — does not fit
here, because the flavors select the whole shared UI, not resources or a small
platform slice.

### The two candidate shapes

**(A) Split `:shared` into `:shared` + `:shared-buy` + `:shared-sell`.**
Each becomes a single-variant KMP module. This matches the `buyMain`/`sellMain`
split that already exists in the source tree, and it removes the task-name
sniffing entirely — flavor selection becomes a module dependency in
`:androidApp`'s `buyImplementation` / `sellImplementation`. Clean, and the
honest cost: a multi-day refactor touching every source set, the iOS framework
wiring and the Koin modules.

**(B) Keep one `:shared` and drive source dirs from the sniffed flavor**,
replacing `sourceSets { ... srcDirs }` with
`androidComponents { onVariants { addStaticSourceDirectory(...) } }`.
Smallest diff, but it promotes `gradle.startParameter.taskRequests` from a
convenience into the mechanism that decides which app gets built — including for
iOS, via `flavorMain` at `shared/build.gradle.kts:102`.

**This choice is the plan.** Everything else is mechanical.

---

## What AGP 9.0 changes that this project must absorb

From the Android migration guide for the new KMP library plugin:

- `src/main` → `src/androidMain`, `src/test` → `src/androidHostTest`,
  `src/androidTest` → `src/androidDeviceTest`.
- Custom source dirs: `sourceSets { srcDir(...) }` →
  `androidComponents { onVariants { addStaticSourceDirectory(...) } }`
  (it *adds*, where `setSrcDirs` replaced).
- Android resources are **off by default**; opt in with
  `kotlin { android { androidResources { enable = true } } }`.
- Host and device tests are **opt-in**: `withHostTest { }` / `withDeviceTest { }`.
- `onVariants` receives a single variant; `withBuildType("release")` no longer applies.
- `matchingFallbacks` / `missingDimensionStrategy` → `localDependencySelection`
  (affects `androidApp/build.gradle.kts:112,120`).
- `debugImplementation` → `"androidRuntimeClasspath"(...)`.
- `BuildConfig` → BuildKonfig or equivalent (already applied here).
- Java compilation opt-in via `withJava()`; one `compilerOptions` JVM target
  instead of `compileOptions` + `kotlinOptions`.
- Built-in Kotlin is on by default, so `kotlin("android")` at
  `androidApp/build.gradle.kts:6` goes away.

Temporary opt-outs `android.builtInKotlin=false` and `android.newDsl=false`
exist, **but stop working in AGP 10.0**. No documented flag was found that keeps
`kotlin("multiplatform")` + `com.android.library` working on AGP 9 — that
pairing looks like a hard stop, so the opt-outs buy time on the DSL, not on the
blocker above.

---

## Measured favourable findings

These were probed in this project, not assumed, and each one deletes a chunk of
the usual migration work:

1. **The configuration cache already works.** `./gradlew :androidApp:assembleBuyDebug
   --configuration-cache` completes and stores an entry. The single biggest
   Gradle 9 hurdle is already cleared.
2. **`:shared` has no Android resources at all.** Every `res/` directory lives
   under `androidApp/src/{main,debug,buy,sell}`; the shared UI uses
   `composeResources`. So the new plugin's `androidResources` opt-in is a no-op
   here, and the removal of `android.nonTransitiveRClass` (set to `false` in
   `gradle.properties`) costs nothing — every `R.` reference in the codebase
   resolves inside `androidApp`, including `R.string.default_web_client_id`
   (generated into the app module by google-services) and the `debug` layout used
   by `TestContainerActivity`.
3. **BuildKonfig is already applied** to `:shared` and is exactly what the
   migration guide names as the `BuildConfig` replacement for KMP.
4. **Compose Multiplatform 1.10.3** is past the 1.9.3 AGP-9 fix and is marked
   Pass on the community status page.
5. **Kotlin 2.3.0** is above AGP 9's KGP 2.2.10 floor, so no forced upgrade.
6. **Much of the Android-side flavor wiring in `:shared` is redundant** with the
   hand-written KMP `dependsOn` graph (`shared/build.gradle.kts:62-120`). The
   flavor test source sets in particular already exist as KMP source sets
   (`testBuy`, `testSell`), so the Android-side `getByName("testBuy")` mapping
   may simply fall away rather than need replacing.

---

## Ranked risks

| # | Risk | Severity | Note |
|---|------|----------|------|
| 1 | `:shared` flavor + build-type variance has no direct replacement | **High — structural** | Decided by the A/B choice above |
| 2 | `DEFAULT_SELLER_ID` moving to BuildKonfig **fails at runtime, not at build time** | **High — silent** | A wrong seller uid shows an empty catalogue, not a build error. This exact symptom cost time on 2026-10-08 (stale APK, pre-18:22 build). Today it is per *build type*; BuildKonfig's existing sniffing is per *flavor*, so the debug/release axis has to be added |
| 3 | Flavor sniffing becomes load-bearing, including for iOS | Medium | `shared/build.gradle.kts:102,304-316`; iOS is the hardest surface to verify and is already "in progress with known issues" |
| 4 | BuildKonfig 0.15.2 and google-services 4.4.2 are **unlisted** on the AGP-9 status page | Medium | Unknown, not broken. google-services only applies to `:androidApp`, which stays `com.android.application`, so it is the lesser of the two |
| 5 | `kotlin("android")` vs built-in Kotlin; `matchingFallbacks` → `localDependencySelection` | Low | Mechanical |
| 6 | Gradle 8.13 → 9.x, plus deprecated Android-style source dirs | Low, noisy | Build says: `shared/src/testBuy/kotlin` → `androidUnitTestBuy/kotlin`, same for `testSell` |
| 7 | `:build-logic` is **dead and already broken** | Housekeeping, do first | See below |
| 8 | `webApp` / `js(IR)` cannot verify anything | None (accepted) | Web is unmaintained per CLAUDE.md and `compileKotlinJs` already fails. Not a regression signal during migration |

### Risk 7 in detail

`:build-logic` does not compile: `./gradlew :build-logic:compileKotlin` fails
with *"Unsupported Kotlin plugin version — Language version 1.8 is no longer
supported; please, use version 2.0 or greater."* It is also:

- included **twice** in `settings.gradle.kts` (lines 4 and 23), as a regular
  project rather than `includeBuild`;
- never applied — `androidApp` uses `id("com.android.application")` directly, not
  `newverse.android.application`;
- a stale duplicate of `androidApp`'s configuration, with `compileSdk = 35`,
  `targetSdk = 37` and a single `VERSION_CODE` that predates the per-flavor
  counters in `version.properties`.

It is invisible today only because nothing depends on it. During a migration it
is a trap: delete it (and the duplicate `include`) before touching anything else.

---

## Preparation work worth doing regardless

None of this commits to the migration, and all of it is useful on its own:

1. Delete `:build-logic` and both `include(":build-logic")` lines.
2. Rename `shared/src/testBuy/kotlin` → `androidUnitTestBuy/kotlin` and
   `testSell` likewise, clearing the deprecation warning (or set
   `kotlin.mpp.androidSourceSetLayoutV2AndroidStyleDirs.nowarn=true` if the
   rename is deferred).
3. Write the runtime invariants below into `doc/pre-release-checklist.md`, since
   they are what the migration can silently break.
4. Check BuildKonfig's release notes for an AGP-9-compatible version before
   committing to shape (B), which leans on it harder.

---

## Verification invariants (what a migration must prove)

Build success proves almost nothing here. These are runtime facts:

- `BuildConfig.DEFAULT_SELLER_ID` in a **buy release** APK is the production
  seller uid, and in a **buy debug** APK the dev uid. Verify in the built APK,
  not in the source.
- `IS_BUY_APP` / `IS_SELL_APP` / `USER_TYPE` from BuildKonfig match the flavor
  actually being assembled, for all four variants.
- The buy APK contains buyer screens only and the sell APK seller screens only —
  i.e. the flavor source-set selection still works after the Android flavor
  mechanism is gone.
- An iOS build of each flavor still picks up the right `flavorMain`
  (`Debug-Buy`, `Release-Buy`, `Debug-Sell`, `Release-Sell` per the cocoapods
  `xcodeConfigurationToNativeBuildType` map).
- Both flavors install and show real catalogue data against the production
  Firebase project.

---

## Open decisions

1. **Shape (A) module split or (B) single module with sniffed source dirs?**
   Everything else follows from this.
2. **When?** Nothing forces the move until a dependency drops AGP 8 support or
   AGP 10 removes the `builtInKotlin` / `newDsl` opt-outs. Recommended: after the
   Play launch settles.
3. **Does a spike come first?** A throwaway branch that bumps only `:androidApp`
   to AGP 9 would turn the `:shared` estimate into a measurement for a few hours'
   work, without committing to either shape.

---

## Sources

- [Set up the Android Gradle library plugin for KMP](https://developer.android.com/kotlin/multiplatform/plugin) — the single-variant limits and the migration steps
- [AGP 9.0 release notes](https://developer.android.com/build/releases/agp-9-0-0-release-notes)
- [AGP DSL/API migration timeline](https://developer.android.com/build/releases/gradle-plugin-roadmap)
- [Update your Kotlin projects for AGP 9.0 (JetBrains)](https://blog.jetbrains.com/kotlin/2026/01/update-your-projects-for-agp9/) — opt-out flags and their AGP 10 expiry
- [Upgrading to Gradle 9](https://docs.gradle.org/current/userguide/upgrading_major_version_9.html)
- [Community AGP 9.0 plugin status](https://agp-status.frybits.com/agp-9.0.0/)
