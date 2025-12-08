# Navigation Structure

## Overview

Uses Jetpack Compose Navigation with Modal Navigation Drawer.

## Route Categories

```
NavRoutes (sealed class)
├── Common: Home, About, Login
├── Buy: Products, Basket, CustomerProfile
└── Sell: Overview, Orders, CreateProduct, SellerProfile, PickDay
```

## Key Files

```
shared/src/commonMain/.../ui/navigation/
├── NavRoutes.kt      # Route definitions
├── NavGraph.kt       # Route-to-screen mappings
├── AppScaffold.kt    # Main structure + drawer
└── AppDrawer.kt      # Drawer UI
```

## Usage

```kotlin
// Navigate to screen
navController.navigate(NavRoutes.Buy.Products.route)

// Navigate with back stack management
navController.navigate(NavRoutes.Login.route) {
    popUpTo(NavRoutes.Home.route) { saveState = true }
    launchSingleTop = true
}

// Get display name
val name = NavRoutes.getDisplayName(NavRoutes.Buy.Products) // "Browse Products"
```

## Adding New Screens

1. **Define route** in `NavRoutes.kt`:
```kotlin
sealed class YourCategory(route: String) : NavRoutes(route) {
    data object NewScreen : YourCategory("category/new_screen")
}
```

2. **Create screen** composable

3. **Add to NavGraph**:
```kotlin
composable(NavRoutes.YourCategory.NewScreen.route) {
    NewScreen()
}
```

4. **Add display name** in `NavRoutes.getDisplayName()`

5. **Add category** in `NavRoutes.getCategory()`

## Screen Mapping (Universe → Newverse)

| Universe Fragment | Newverse Route |
|-------------------|----------------|
| MainActivity (Home) | `home` |
| ProductsFragment | `buy/products` |
| BasketFragment | `buy/basket` |
| ProductViewsFragment | `sell/overview` |
| ShowOrdersFragment | `sell/orders` |
| CreateFragment | `sell/create` |
