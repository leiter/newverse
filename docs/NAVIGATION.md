# Navigation Structure

This document describes the navigation system implemented in the Newverse KMP project.

## Overview

The app uses **Jetpack Compose Navigation** with a **Modal Navigation Drawer** to navigate between screens. All fragments from the Universe project have been converted to Compose screens.

## Architecture

### Navigation Components

1. **NavRoutes.kt** - Sealed class hierarchy defining all navigation routes
2. **NavGraph.kt** - Navigation graph with all route-to-screen mappings
3. **AppScaffold.kt** - Main app structure with drawer and top bar
4. **AppDrawer.kt** - Navigation drawer UI with categorized menu items

## Screen Categories

### Common Screens
- **Home** - Main landing screen with app overview
- **About** - App information and version details
- **Login** - Authentication screen with email/password fields

### Customer (Buy) Screens
- **Browse Products** - Product listing with add-to-basket functionality
- **Shopping Basket** - Cart view with checkout option
- **Customer Profile** - User profile and order history

### Seller Screens
- **Product Overview** - Dashboard with stats and product list
- **Manage Orders** - Order management with status tracking
- **Create Product** - Form to add new products
- **Seller Profile** - Business profile and settings
- **Pick Delivery Days** - Select available delivery days

## Screen Mapping from Universe

| Universe Fragment | Newverse Screen | Route |
|-------------------|-----------------|-------|
| MainActivity (Home) | MainScreen | `home` |
| AboutFragment | AboutScreen | `about` |
| LoginFragment | LoginScreen | `login` |
| ProductsFragment | ProductsScreen | `buy/products` |
| BasketFragment | BasketScreen | `buy/basket` |
| ClientProfileFragment | CustomerProfileScreen | `buy/profile` |
| ProductViewsFragment | OverviewScreen | `sell/overview` |
| ShowOrdersFragment | OrdersScreen | `sell/orders` |
| CreateFragment | CreateProductScreen | `sell/create` |
| ProfileFragment | SellerProfileScreen | `sell/profile` |
| PickDayFragment | PickDayScreen | `sell/pick_day` |

## Navigation Flow

```
MainActivity
    └── NewverseTheme
        └── AppScaffold
            ├── TopAppBar (with menu button)
            ├── ModalNavigationDrawer
            │   └── AppDrawer (categorized menu)
            └── NavHost
                ├── Home
                ├── Common Screens
                ├── Buy Screens
                └── Sell Screens
```

## Usage Examples

### Navigate to a Screen

```kotlin
navController.navigate(NavRoutes.Buy.Products.route)
```

### Navigate with Pop Behavior

```kotlin
navController.navigate(NavRoutes.Login.route) {
    popUpTo(NavRoutes.Home.route) {
        saveState = true
    }
    launchSingleTop = true
    restoreState = true
}
```

### Get Display Name for Route

```kotlin
val displayName = NavRoutes.getDisplayName(NavRoutes.Buy.Products)
// Returns: "Browse Products"
```

### Get All Routes

```kotlin
val allRoutes = NavRoutes.getAllRoutes()
// Returns list of all navigation routes
```

## Drawer Structure

The navigation drawer organizes items into three categories:

```
Newverse
├── Common
│   ├── Home
│   ├── About
│   └── Login
├── Customer Features
│   ├── Browse Products
│   ├── Shopping Basket
│   └── Customer Profile
└── Seller Features
    ├── Product Overview
    ├── Manage Orders
    ├── Create Product
    ├── Seller Profile
    └── Pick Delivery Day
```

## Adding New Screens

### 1. Define the Route

```kotlin
// In NavRoutes.kt
sealed class YourCategory(route: String) : NavRoutes(route) {
    data object NewScreen : YourCategory("category/new_screen")
}
```

### 2. Create the Screen

```kotlin
// In screens/category/NewScreen.kt
@Composable
fun NewScreen() {
    Column(modifier = Modifier.fillMaxSize()) {
        Text("New Screen Content")
    }
}
```

### 3. Add to NavGraph

```kotlin
// In NavGraph.kt
composable(NavRoutes.YourCategory.NewScreen.route) {
    NewScreen()
}
```

### 4. Add Display Name

```kotlin
// In NavRoutes.kt companion object
fun getDisplayName(route: NavRoutes): String = when (route) {
    // ... existing cases
    YourCategory.NewScreen -> "New Screen Title"
}
```

### 5. Add to Category

```kotlin
// In NavRoutes.kt companion object
fun getCategory(route: NavRoutes): String = when (route) {
    // ... existing cases
    is YourCategory -> "Your Category Name"
}
```

## State Management

- **DrawerState**: Manages drawer open/closed state
- **NavController**: Handles navigation state and back stack
- **currentBackStackEntry**: Provides current route for highlighting

## Navigation Features

### Current Implementation
- ✅ Modal navigation drawer
- ✅ Categorized menu items
- ✅ Active route highlighting
- ✅ Dynamic screen titles
- ✅ State preservation on navigation
- ✅ Single-top launch mode
- ✅ Proper back stack management

### Future Enhancements
- 🔜 Deep linking support
- 🔜 Navigation arguments (product ID, order ID, etc.)
- 🔜 Nested navigation graphs
- 🔜 Bottom navigation for frequent screens
- 🔜 Conditional navigation (auth-protected routes)
- 🔜 Navigation animations/transitions

## Screen Files

```
shared/src/commonMain/kotlin/com/together/newverse/ui/
├── navigation/
│   ├── NavRoutes.kt           # Route definitions
│   ├── NavGraph.kt            # Navigation graph
│   ├── AppScaffold.kt         # Main app structure
│   └── AppDrawer.kt           # Drawer UI
├── screens/
│   ├── common/
│   │   ├── AboutScreen.kt
│   │   └── LoginScreen.kt
│   ├── buy/
│   │   ├── ProductsScreen.kt
│   │   ├── BasketScreen.kt
│   │   └── CustomerProfileScreen.kt
│   └── sell/
│       ├── OverviewScreen.kt
│       ├── OrdersScreen.kt
│       ├── CreateProductScreen.kt
│       ├── SellerProfileScreen.kt
│       └── PickDayScreen.kt
└── MainScreen.kt              # Home screen
```

## Best Practices

1. **Use Sealed Classes** - Type-safe route definitions
2. **Centralize Routes** - All routes in NavRoutes.kt
3. **Categorize Screens** - Group related screens together
4. **State Preservation** - Save/restore state on navigation
5. **Single Top** - Avoid duplicate screens in back stack
6. **Descriptive Names** - Clear route and screen names
7. **Consistent Structure** - Follow established patterns

## Testing Navigation

To test navigation:

1. **Build and run the app**: `./gradlew :androidApp:installDebug`
2. **Open the drawer**: Tap the menu icon (☰) in the top bar
3. **Navigate**: Select any screen from the categorized list
4. **Verify**: Screen title updates and content displays correctly
5. **Back Navigation**: Use system back button to navigate backward

## Dependencies

- `androidx.navigation:navigation-compose:2.8.0-alpha10` - Navigation for Compose
- Part of shared module, works across all platforms

## Notes

- All screens are currently dummy implementations with placeholder UI
- Navigation state is managed by Compose Navigation
- Drawer closes automatically after selecting an item
- Route selection is highlighted in the drawer
- Screen titles update based on current route
