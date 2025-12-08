# Newverse Project Guide

## Overview

**Newverse** is a Kotlin Multiplatform (KMP) marketplace app with separate buyer and seller flavors. It enables customers to browse products, manage orders, and sellers to manage inventory and fulfill orders.

## Tech Stack

| Category | Technology |
|----------|------------|
| Language | Kotlin 2.0.21 (K2 compiler) |
| UI | Compose Multiplatform 1.7.1 |
| Architecture | MVVM + Repository Pattern |
| DI | Koin 4.0.0 |
| Async | Coroutines 1.9.0, StateFlow |
| Backend | Firebase (GitLive SDK for cross-platform) |
| Images | Coil3 3.0.4 |
| Navigation | Navigation Compose 2.8.0-alpha10 |

## Project Structure

```
newverse/
├── androidApp/          # Android app module
│   └── src/
│       ├── buy/         # Buy flavor resources
│       └── sell/        # Sell flavor resources
├── shared/              # KMP shared code
│   └── src/
│       ├── commonMain/  # Shared code (domain, data, ui, di)
│       ├── buyMain/     # Buy flavor screens & navigation
│       ├── sellMain/    # Sell flavor screens & navigation
│       ├── androidMain/ # Android implementations
│       └── iosMain/     # iOS implementations
└── doc/                 # Documentation
```

## Build Flavors

| Flavor | Package | Purpose |
|--------|---------|---------|
| buy | com.together.newverse.buy | Customer app - browse, order, checkout |
| sell | com.together.newverse.sell | Seller app - manage products & orders |

```bash
./gradlew :androidApp:assembleBuyDebug
./gradlew :androidApp:assembleSellDebug
```

## Architecture Patterns

### MVVM + State Hoisting

```kotlin
// Screen (Stateful) - has ViewModel
@Composable
fun ProductsScreen(viewModel: ProductsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    ProductsContent(state = state, onAction = viewModel::onAction)
}

// Content (Stateless) - receives state + callbacks
@Composable
fun ProductsContent(state: State, onAction: (Action) -> Unit) { }
```

### Action Pattern

```kotlin
sealed interface ProductsAction {
    data class AddToBasket(val article: Article) : ProductsAction
    data object Refresh : ProductsAction
}
```

### Repository Pattern

- Interfaces in `domain/repository/`
- Implementations in `data/repository/`
- GitLive implementations for cross-platform Firebase

## Key Domain Models

| Model | Purpose |
|-------|---------|
| `Order` | Customer order with status, items, pickup date |
| `Product` / `Article` | Marketplace product |
| `OrderedProduct` | Item in basket/order with quantity |
| `BuyerProfile` | Customer profile with favorites, order history |
| `SellerProfile` | Vendor profile with markets |

## Order Business Rules

- **Pickup Day:** Always Thursday
- **Edit Deadline:** Tuesday 23:59 before pickup
- **Order States:** DRAFT → PLACED → LOCKED → COMPLETED

```kotlin
// Monday/Tuesday → This Thursday
// Wednesday+ → Next Thursday
val pickupDate = OrderDateUtils.calculateNextPickupDate()
val canEdit = order.canEdit() // checks deadline
```

## Key Files

### ViewModels
- `shared/src/buyMain/.../BuyAppViewModel.kt`
- `shared/src/sellMain/.../SellAppViewModel.kt`
- `shared/src/commonMain/.../ui/screens/buy/BasketViewModel.kt`

### Repositories
- `shared/src/commonMain/.../domain/repository/` (interfaces)
- `shared/src/commonMain/.../data/repository/` (implementations)

### Navigation
- `shared/src/buyMain/.../navigation/BuyNavGraph.kt`
- `shared/src/sellMain/.../navigation/SellNavGraph.kt`

### DI
- `shared/src/buyMain/.../AppModule.kt`
- `shared/src/sellMain/.../AppModule.kt`

## Firebase Structure

```
/sellers/{sellerId}/
  ├── articles/{articleId}    # Products
  ├── orders/{date}/{orderId} # Orders
  └── profile                 # Seller profile

/buyers/{buyerId}/
  └── profile                 # Buyer profile with order history
```

## Feature Flags

In `FeatureFlags.kt`:
- `authProvider` - FIREBASE, GITLIVE, AUTO
- `useGitLiveStorage` - Storage provider selection
- `gitLiveRolloutPercentage` - Gradual migration control

## Coding Conventions

### State Management
- Use `StateFlow` (not LiveData)
- Use sealed interfaces for UI states
- Keep ViewModels platform-agnostic

### Compose
- Hoist state to Screen level
- Pass callbacks, not ViewModels to children
- Use `koinViewModel()` for injection

### Naming
- Screens: `*Screen.kt` (stateful), `*Content` (stateless)
- ViewModels: `*ViewModel.kt`
- Actions: `*Action` sealed interface
- States: `*State` or `*UiState`

## Common Tasks

### Add New Screen

1. Create `NewScreen.kt` with ViewModel injection
2. Add route to `NavRoutes.kt`
3. Add to NavGraph (`BuyNavGraph.kt` or `SellNavGraph.kt`)
4. Register ViewModel in AppModule

### Add Repository

1. Define interface in `domain/repository/`
2. Create GitLive implementation in `data/repository/`
3. Register in Koin module

### Add Localized String

1. Add to `shared/src/commonMain/composeResources/values/strings.xml`
2. Add translation to `values-en/strings.xml`
3. Use: `stringResource(Res.string.my_string)`

## Documentation

All docs in `doc/` directory:
- `architecture.md` - MVVM, state patterns
- `navigation.md` - Route structure
- `ordering-business-rules.md` - Order logic
- `iOS-Setup-Guide.md` - iOS build setup

## Current Status

- Android: Functional for both flavors
- iOS: In progress with known issues
- Localization: German (primary), English

## Debug Tips

```bash
# Filter logs
adb logcat -s "BasketViewModel" -s "GitLiveAuthRepository"

# Check auth state
adb logcat | grep -E "Auth|Firebase"
```
