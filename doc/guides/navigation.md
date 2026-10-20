# Navigation Structure

**Status:** current — verified against `shared/src` on 2026-10-20
**Scope:** `commonMain/ui/navigation` (routes, drawer, scaffold shell),
`buyMain/ui/navigation` and `sellMain/ui/navigation` (per-flavor graphs)

Navigation Compose 2.9.1 (`org.jetbrains.androidx.navigation`), one shared route
sealed class, and a nav graph per flavor.

## File layout

The routes are shared; **everything that builds a graph or a scaffold is per
flavor.** This is the part the older doc got wrong — there is no single
`commonMain/NavGraph.kt`.

```
commonMain/ui/navigation/
├── NavRoutes.kt           # all routes + flavor filtering + display names
├── AppDrawer.kt           # drawer UI
├── MainAppScaffold.kt     # expect declaration
└── PlatformAction.kt

buyMain/ui/navigation/              sellMain/ui/navigation/
├── NavRoutes are shared            ├── AppScaffold.kt
├── AppScaffold.kt                  ├── CommonNavGraph.kt
├── CommonNavGraph.kt               ├── NavGraph.kt
├── NavGraph.kt                     └── MainAppScaffold.sell.kt
├── BuyerBottomBar.kt
└── MainAppScaffold.buy.kt
```

The buy flavor has a bottom bar; the sell flavor does not. Both have a drawer.

## Routes

`NavRoutes` is a sealed class with `Buy` and `Sell` nested sealed classes. Routes
carrying arguments expose a builder alongside the pattern.

**Common:** `home`, `login`, `register`, `about`

**Buy:** `buy/basket`, `buy/profile`, `buy/order_history`, `buy/favorites`,
`buy/product/{articleId}`, `buy/messages`, `buy/conversation/{conversationId}`,
`buy/contacts`, `buy/contacts/add`

**Sell:** `sell/overview`, `sell/orders`, `sell/order/{orderId}`,
`sell/create?articleId={articleId}`, `sell/profile`, `sell/pick_day`,
`sell/notification_settings`, `sell/import_preview`, `sell/conversations`,
`sell/conversation/{conversationId}`, `sell/abrechnung`, `sell/stock`,
`sell/customer/{buyerId}`

## Flavor filtering

Route visibility is decided by `BuildKonfig`, not by the source set:

```kotlin
NavRoutes.getRoutesForCurrentFlavor()  // drawer contents
NavRoutes.hasBuyFeatures()             // IS_BUY_APP || combined
NavRoutes.hasSellFeatures()            // IS_SELL_APP || combined
NavRoutes.isCombinedBuild()            // both flags false
```

A **combined** build (the Gradle default for development, both `IS_BUY_APP` and
`IS_SELL_APP` false) shows every route. See `guides/build-flavors.md`.

## Usage

```kotlin
navController.navigate(NavRoutes.Buy.Basket.route)

// With an argument — use the builder, don't hand-format the path
navController.navigate(NavRoutes.Buy.ProductDetail.createRoute(articleId))

// Back stack management
navController.navigate(NavRoutes.Login.route) {
    popUpTo(NavRoutes.Home.route) { saveState = true }
    launchSingleTop = true
}
```

Display names are **localized string resources**, not plain strings:

```kotlin
val nameRes: StringResource = NavRoutes.getDisplayNameRes(route)
Text(stringResource(nameRes))
```

A nav item's name and its badge are joined through the locale rather than
concatenated — see commit `2fb10cd`.

## Adding a screen

1. Add the route to the right nested class in `NavRoutes.kt`, with a `createRoute`
   builder if it takes arguments.
2. Add it to `getAllRoutesUnfiltered()` if it belongs in the drawer.
3. Add a `StringResource` branch to `getDisplayNameRes()`, and the strings to both
   `values/strings.xml` and `values-en/strings.xml`.
4. Add the `composable(...)` block to the **flavor's** `NavGraph.kt`
   (`buyMain/` or `sellMain/`), not to `commonMain`.
5. Register the ViewModel in that flavor's `di/AppModule.kt`.

## Back navigation

Use `navController.navigateUp()` / `popBackStack()`. iOS edge-swipe and Android
predictive back are both handled by the library — see
`guides/ios-back-navigation.md`. Do not use the Android-only `BackHandler`
composable in `commonMain`.
