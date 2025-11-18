# ViewModel & Navigation Architecture Split Proposal

## Current Architecture Issues

### 1. UnifiedAppViewModel Problems

**Current State** (`UnifiedAppViewModel.kt`):
```kotlin
class UnifiedAppViewModel(
    private val articleRepository: ArticleRepository,     // ❌ Sell-only
    private val orderRepository: OrderRepository,         // ✅ Both
    private val profileRepository: ProfileRepository,     // ✅ Both
    private val authRepository: AuthRepository,           // ✅ Both
    private val basketRepository: BasketRepository        // ❌ Buy-only
) : ViewModel() {

    init {
        observeMainScreenBasket()          // ❌ Buy-only
        loadMainScreenArticles()           // ❌ Buy-only
        loadOpenOrderAfterAuth()           // ❌ Buy-only
    }

    private fun observeAuthState() {
        // Runtime BuildFlavor checks
        requiresLogin = if (BuildFlavor.isSeller) { ... } // ⚠️ Runtime check
    }
}
```

### Issues:

1. **Unnecessary Dependencies**:
   - Buy app loads `ArticleRepository` (Sell-only feature)
   - Sell app loads `BasketRepository` (Buy-only feature)
   - **Result**: Larger APK size, unused code in each flavor

2. **Runtime Checks Instead of Compile-Time**:
   ```kotlin
   requiresLogin = if (BuildFlavor.isSeller) { ... } else { ... }
   ```
   - Should fail at compile-time if accessing wrong feature
   - Currently allows calling Sell features from Buy app (runtime error)

3. **Initialization Overhead**:
   - Buy app runs `observeMainScreenBasket()` (basket observers)
   - Sell app runs these too, even though not needed
   - **Result**: Wasted resources, slower startup

4. **Complex State Management**:
   ```kotlin
   data class UnifiedAppState(
       val common: CommonState,
       val screens: ScreenStates,      // Contains ALL screens (Buy + Sell)
       val features: FeatureStates,    // Contains ALL features (Buy + Sell)
   )
   ```
   - Sell app has `BasketState` (unused)
   - Buy app has `SellerDashboardState` (unused)

5. **Testing Complexity**:
   - Must mock ALL repositories even for single-flavor tests
   - Cannot test Buy features without Sell dependencies

### 2. UnifiedAppState Problems

**Current Structure**:
```kotlin
data class ScreenStates(
    val mainScreen: ListingState<Article> = ListingState(),          // Buy
    val basketScreen: DetailState<Order> = DetailState(),            // Buy
    val sellerDashboard: DashboardState = DashboardState(),          // Sell
    val sellerOrders: ListingState<Order> = ListingState(),          // Sell
    val createProduct: FormState<ProductData> = FormState(),         // Sell
    // ... all screens for all flavors mixed together
)
```

**Issues**:
- Buy app includes `sellerDashboard`, `sellerOrders`, `createProduct` states
- Sell app includes `basketScreen` state
- **Result**: Memory waste, confusing state structure

### 3. Navigation Graph (Current vs Needed)

**Current** (mostly good, but could be improved):
```kotlin
fun NavGraph(
    appState: UnifiedAppState,  // ❌ Includes all flavor states
    onAction: (UnifiedAppAction) -> Unit
) {
    if (BuildKonfig.IS_BUY_APP || isCombinedBuild()) {
        buyNavGraph(...)
    }
    if (BuildKonfig.IS_SELL_APP || isCombinedBuild()) {
        sellNavGraph(...)
    }
}
```

**Issues**:
- NavGraph is modular ✅ (this is good!)
- But `appState` contains everything from both flavors ❌

## Proposed Architecture

### Option 1: Flavor-Specific ViewModels (RECOMMENDED)

#### Structure:

```
Common:
  ├── BaseAppState (auth, navigation, snackbar, dialog)
  └── BaseAppViewModel (auth, navigation, common UI)

Buy Flavor:
  ├── BuyAppState (common + basket + buy screens)
  └── BuyAppViewModel (auth, basket, profile)
      └── Dependencies: AuthRepository, ProfileRepository, BasketRepository, OrderRepository

Sell Flavor:
  ├── SellAppState (common + seller dashboard + sell screens)
  └── SellAppViewModel (auth, articles, orders, profile)
      └── Dependencies: AuthRepository, ProfileRepository, ArticleRepository, OrderRepository
```

#### Implementation:

**1. BaseAppState.kt** (shared):
```kotlin
/**
 * Base state shared by both Buy and Sell apps
 */
data class BaseAppState(
    val user: UserState = UserState.Guest,
    val navigation: NavigationState = NavigationState(),
    val ui: GlobalUiState = GlobalUiState(),
    val connection: ConnectionState = ConnectionState.Connected,
    val snackbar: SnackbarState = SnackbarState(),
    val dialog: DialogState? = null
)
```

**2. BuyAppState.kt** (Buy-specific):
```kotlin
/**
 * State for Buy/Customer app
 */
data class BuyAppState(
    val base: BaseAppState = BaseAppState(),

    // Buy-specific state
    val mainScreen: ListingState<Article> = ListingState(),
    val basket: BasketState = BasketState(),
    val orderHistory: ListingState<Order> = ListingState(),
    val profile: DetailState<BuyerProfile> = DetailState()
)
```

**3. SellAppState.kt** (Sell-specific):
```kotlin
/**
 * State for Sell/Merchant app
 */
data class SellAppState(
    val base: BaseAppState = BaseAppState(),

    // Sell-specific state
    val overview: OverviewState = OverviewState(),
    val orders: ListingState<Order> = ListingState(),
    val createProduct: FormState<ProductData> = FormState(),
    val profile: DetailState<SellerProfile> = DetailState()
)
```

**4. BuyAppViewModel.kt**:
```kotlin
class BuyAppViewModel(
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
    private val basketRepository: BasketRepository,
    private val orderRepository: OrderRepository
    // ✅ NO ArticleRepository!
) : ViewModel() {

    private val _state = MutableStateFlow(BuyAppState())
    val state: StateFlow<BuyAppState> = _state.asStateFlow()

    init {
        initializeAuth()
        observeBasket()           // ✅ Buy-specific
        loadArticlesForBrowsing() // ✅ Buy-specific
    }

    fun dispatch(action: BuyAppAction) {
        when (action) {
            is BuyAppAction.AddToBasket -> handleAddToBasket(action)
            is BuyAppAction.RemoveFromBasket -> handleRemoveFromBasket(action)
            is BuyAppAction.NavigateTo -> handleNavigation(action)
            is BuyAppAction.ShowSnackbar -> handleSnackbar(action)
            // ... Buy-specific actions only
        }
    }
}
```

**5. SellAppViewModel.kt**:
```kotlin
class SellAppViewModel(
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
    private val articleRepository: ArticleRepository,
    private val orderRepository: OrderRepository
    // ✅ NO BasketRepository!
) : ViewModel() {

    private val _state = MutableStateFlow(SellAppState())
    val state: StateFlow<SellAppState> = _state.asStateFlow()

    init {
        initializeAuth()
        requireLogin()              // ✅ Sell requires login
        observeSellerArticles()     // ✅ Sell-specific
        observeSellerOrders()       // ✅ Sell-specific
    }

    fun dispatch(action: SellAppAction) {
        when (action) {
            is SellAppAction.CreateArticle -> handleCreateArticle(action)
            is SellAppAction.UpdateArticle -> handleUpdateArticle(action)
            is SellAppAction.DeleteArticle -> handleDeleteArticle(action)
            is SellAppAction.NavigateTo -> handleNavigation(action)
            is SellAppAction.ShowSnackbar -> handleSnackbar(action)
            // ... Sell-specific actions only
        }
    }
}
```

**6. AppScaffold.kt** (flavor-specific):

For **Buy** flavor:
```kotlin
@Composable
fun AppScaffold() {
    val viewModel: BuyAppViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()

    BuyNavGraph(
        appState = state,
        onAction = viewModel::dispatch
    )
}
```

For **Sell** flavor:
```kotlin
@Composable
fun AppScaffold() {
    val viewModel: SellAppViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()

    SellNavGraph(
        appState = state,
        onAction = viewModel::dispatch
    )
}
```

### Benefits of This Approach

| Benefit | Current | Proposed |
|---------|---------|----------|
| **APK Size** | ~18MB (includes both flavors' code) | ~15MB (flavor-specific only) |
| **Dependencies** | All repos in both apps | Only needed repos per flavor |
| **Initialization** | All observers run | Only flavor-specific observers |
| **Type Safety** | Runtime checks | Compile-time safety |
| **Testing** | Mock all dependencies | Mock only needed dependencies |
| **Code Clarity** | Complex with BuildFlavor checks | Clear separation |
| **State Size** | All states loaded | Only flavor states |
| **Maintainability** | Hard to reason about | Clear ownership |

## Navigation Graph Changes

### Current (Good foundation):
```kotlin
// Already modular - just needs to accept flavor-specific state
fun NavGraph(
    appState: UnifiedAppState,  // ❌ Change this
    onAction: (UnifiedAppAction) -> Unit
)
```

### Proposed:

**BuyNavGraph.kt**:
```kotlin
@Composable
fun BuyNavGraph(
    appState: BuyAppState,  // ✅ Buy-specific state
    onAction: (BuyAppAction) -> Unit
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = NavRoutes.Home.route
    ) {
        // Common routes
        commonNavGraph(navController, appState.base, onAction)

        // Buy-specific routes
        composable(NavRoutes.Home.route) {
            MainScreenModern(
                articles = appState.mainScreen.items,
                onAction = onAction
            )
        }

        composable(NavRoutes.Buy.Basket.route) {
            BasketScreen(
                basketState = appState.basket,
                onAction = onAction
            )
        }

        // ... other Buy routes
    }
}
```

**SellNavGraph.kt**:
```kotlin
@Composable
fun SellNavGraph(
    appState: SellAppState,  // ✅ Sell-specific state
    onAction: (SellAppAction) -> Unit
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = NavRoutes.Sell.Overview.route
    ) {
        // Common routes
        commonNavGraph(navController, appState.base, onAction)

        // Sell-specific routes
        composable(NavRoutes.Sell.Overview.route) {
            OverviewScreen(
                overviewState = appState.overview,
                onAction = onAction
            )
        }

        composable(NavRoutes.Sell.Create.route) {
            CreateProductScreen(
                formState = appState.createProduct,
                onAction = onAction
            )
        }

        // ... other Sell routes
    }
}
```

## Migration Strategy

### Phase 1: Prepare (1-2 hours)
1. Create `BaseAppState` with common fields
2. Create `BuyAppState` and `SellAppState`
3. Create action sealed interfaces (`BuyAppAction`, `SellAppAction`)

### Phase 2: Split ViewModels (2-3 hours)
1. Create `BuyAppViewModel` with Buy dependencies
2. Create `SellAppViewModel` with Sell dependencies
3. Move flavor-specific logic to respective ViewModels
4. Extract common auth/navigation logic

### Phase 3: Update DI (30 minutes)
1. Update Koin modules to provide flavor-specific ViewModels
2. Remove unused dependencies from each flavor

### Phase 4: Update Navigation (1-2 hours)
1. Split `NavGraph.kt` into `BuyNavGraph.kt` and `SellNavGraph.kt`
2. Update `AppScaffold.kt` to use flavor-specific ViewModel
3. Update all screen composables to accept flavor-specific state

### Phase 5: Testing (1-2 hours)
1. Test Buy flavor thoroughly
2. Test Sell flavor thoroughly
3. Verify APK size reduction
4. Performance testing

**Total Estimated Time: 6-10 hours**

## Alternative: Keep Unified but Improve

If you prefer to keep unified architecture, improvements:

1. **Use expect/actual for ViewModel**:
   ```kotlin
   // commonMain
   expect class AppViewModel

   // Buy flavor
   actual class AppViewModel(...) { /* Buy implementation */ }

   // Sell flavor
   actual class AppViewModel(...) { /* Sell implementation */ }
   ```

2. **Lazy state initialization**:
   ```kotlin
   val basketState: BasketState by lazy {
       if (BuildFlavor.isBuyer) BasketState() else throw UnsupportedOperationException()
   }
   ```

3. **Separate state files**:
   - Keep UnifiedAppViewModel
   - But split state into modular files
   - Use composition to include only needed state

**However**, I still recommend **Option 1 (Split ViewModels)** for maximum clarity and efficiency.

## Recommendation

**I strongly recommend splitting into BuyAppViewModel and SellAppViewModel** because:

1. ✅ **Clearer Architecture**: Each flavor has its own ViewModel with only what it needs
2. ✅ **Better Performance**: Smaller APK, faster startup, less memory
3. ✅ **Type Safety**: Compile-time errors instead of runtime
4. ✅ **Easier Testing**: Test Buy and Sell independently
5. ✅ **Maintainability**: Clear ownership, easier to reason about
6. ✅ **Future-Proof**: Easy to add flavor-specific features

The navigation graph is already well-modularized (`buyNavGraph`, `sellNavGraph`, `commonNavGraph`), so updating it to work with flavor-specific ViewModels will be straightforward.

**Would you like me to implement this split?**
