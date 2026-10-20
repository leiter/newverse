# Build Flavors and BuildKonfig

**Status:** current — verified against `androidApp/build.gradle.kts` and
`shared/build.gradle.kts` on 2026-10-20
**Scope:** flavor identity, compile-time flavor detection, build commands

How a build knows whether it is the buyer app, the seller app, or a development
build with both halves.

## Application IDs

Base `applicationId` is `com.together`, with a per-flavor suffix:

| Flavor | applicationId | versionNameSuffix |
|--------|---------------|-------------------|
| buy | `com.together.buy` | `-buy` |
| sell | `com.together.sell` | `-sell` |

> `CLAUDE.md` lists these as `com.together.newverse.buy` / `.sell`. That is wrong —
> the Gradle config above is authoritative. Use `com.together.buy` with `adb`.

`versionCode` comes from `versionCodeFor("buy")` / `versionCodeFor("sell")`, which
read **one counter per flavor** out of `version.properties`. The two flavors
advance independently.

## Three configurations, not two

`shared/build.gradle.kts` resolves the flavor itself and falls back to a third,
**combined** configuration when neither flavor is unambiguously selected — which is
the case for a plain `:shared` build, so it is the default during development.

| Field | buy | sell | combined |
|-------|-----|------|----------|
| `APP_NAME` | "Newverse Buy" | "Newverse Sell" | "Newverse" |
| `IS_BUY_APP` | `true` | `false` | `false` |
| `IS_SELL_APP` | `false` | `true` | `false` |
| `USER_TYPE` | `"buy"` | `"sell"` | `"combined"` |

Note the trap: **in a combined build both booleans are `false`.** Code that asks
`if (IS_BUY_APP)` silently takes the seller path. Ask through the `NavRoutes`
helpers instead, which treat combined as "both":

```kotlin
NavRoutes.isCombinedBuild()   // !IS_BUY_APP && !IS_SELL_APP
NavRoutes.hasBuyFeatures()    // IS_BUY_APP || combined
NavRoutes.hasSellFeatures()   // IS_SELL_APP || combined
```

## Usage

```kotlin
import com.together.newverse.shared.BuildKonfig

val appName = BuildKonfig.APP_NAME
if (BuildKonfig.IS_BUY_APP) { /* buyer-only */ }
```

There is no `BuildFlavor.kt` wrapper any more — older docs referenced
`BuildFlavor.isBuyFlavor`, `BuildFlavor.isSeller` and
`BuildFlavor.allowsGuestAccess`. All three are gone; read `BuildKonfig` directly, or
use the `NavRoutes` helpers.

Flavor-specific *code* does not need a flag at all — put it in the
`shared/src/buyMain` or `shared/src/sellMain` source set. The flags are for shared
code that has to branch, which should be rare.

## Build commands

```bash
./gradlew :androidApp:assembleBuyDebug
./gradlew :androidApp:assembleSellDebug
./gradlew :androidApp:assembleBuyRelease
./gradlew :androidApp:assembleSellRelease

./gradlew :shared:generateBuildKonfig          # regenerate by hand
./gradlew clean :shared:generateBuildKonfig    # after changing the config block
```

## Adding a field

In `shared/build.gradle.kts`, inside `buildkonfig { defaultConfigs { ... } }` — note
this project branches on a resolved `currentFlavor` inside a single `defaultConfigs`
block, rather than using named `defaultConfigs("buy")` blocks:

```kotlin
when (currentFlavor) {
    "buy"  -> buildConfigField(Type.STRING, "API_URL", "https://buy.example.com")
    "sell" -> buildConfigField(Type.STRING, "API_URL", "https://sell.example.com")
    else   -> buildConfigField(Type.STRING, "API_URL", "https://dev.example.com")
}
```

Every branch must set every field, or the combined build will not compile.

Types: `Type.STRING`, `Type.BOOLEAN`, `Type.INT`, `Type.LONG`, `Type.FLOAT`.

## Which Firebase backend a build talks to

Not decided by the flavor alone, and currently not fully wired — see
`plans/build-identity-and-firebase-wiring.md` before changing anything about
applicationIds, bundle IDs or `google-services.json` placement.
