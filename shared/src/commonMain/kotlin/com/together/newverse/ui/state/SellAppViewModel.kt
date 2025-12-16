package com.together.newverse.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.together.newverse.domain.model.Article
import com.together.newverse.domain.repository.ArticleRepository
import com.together.newverse.domain.repository.AuthRepository
import com.together.newverse.ui.navigation.NavRoutes
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.*
import org.jetbrains.compose.resources.getString

/**
 * Sell flavor ViewModel managing all app state for seller/vendor app
 *
 * This is the single ViewModel for the sell flavor, implementing
 * a Redux-like pattern with actions and reducers.
 *
 * Note: This ViewModel is slimmed down compared to BuyAppViewModel,
 * as sellers don't need basket, favorites, or buyer-specific features.
 */
class SellAppViewModel(
    private val articleRepository: ArticleRepository,
    private val authRepository: AuthRepository
) : ViewModel(), AppViewModel {

    private val _state = MutableStateFlow(UnifiedAppState())
    override val state: StateFlow<UnifiedAppState> = _state.asStateFlow()

    // Pending import content - stored in ViewModel to survive configuration changes
    private val _pendingImportContent = MutableStateFlow<String?>(null)
    val pendingImportContent: StateFlow<String?> = _pendingImportContent.asStateFlow()

    fun setPendingImportContent(content: String?) {
        _pendingImportContent.value = content
    }

    init {
        // Initialize app on startup
        initializeApp()

        // Observe auth state changes
        observeAuthState()

        // Load articles after auth is ready
        viewModelScope.launch {
            authRepository.observeAuthState()
                .filterNotNull()
                .first()
            loadProducts()
        }
    }

    private fun observeAuthState() {
        viewModelScope.launch {
            authRepository.observeAuthState().collect { userId ->
                _state.update { current ->
                    current.copy(
                        common = current.common.copy(
                            user = if (userId != null) {
                                UserState.LoggedIn(
                                    id = userId,
                                    email = "",
                                    name = "",
                                    role = UserRole.SELLER
                                )
                            } else {
                                UserState.Guest
                            },
                            // Seller flavor: require login if no user
                            requiresLogin = userId == null
                        )
                    )
                }
            }
        }
    }

    // ===== Public Action Handlers =====

    override fun dispatch(action: UnifiedAppAction) {
        when (action) {
            is UnifiedNavigationAction -> handleNavigationAction(action)
            is UnifiedUserAction -> handleUserAction(action)
            is UnifiedProductAction -> handleProductAction(action)
            is UnifiedBasketAction -> { /* Not used in seller app */ }
            is UnifiedOrderAction -> handleOrderAction(action)
            is UnifiedUiAction -> handleUiAction(action)
            is UnifiedProfileAction -> handleProfileAction(action)
            is UnifiedSearchAction -> { /* Not used in seller app */ }
            is UnifiedFilterAction -> { /* Not used in seller app */ }
            is UnifiedMainScreenAction -> { /* Not used in seller app - seller uses OverviewScreen */ }
            is UnifiedBasketScreenAction -> { /* Not used in seller app */ }
        }
    }

    // ===== Action Handlers =====

    private fun handleNavigationAction(action: UnifiedNavigationAction) {
        when (action) {
            is UnifiedNavigationAction.NavigateTo -> navigateTo(action.route)
            is UnifiedNavigationAction.NavigateBack -> navigateBack()
            is UnifiedNavigationAction.OpenDrawer -> openDrawer()
            is UnifiedNavigationAction.CloseDrawer -> closeDrawer()
        }
    }

    private fun handleUserAction(action: UnifiedUserAction) {
        when (action) {
            is UnifiedUserAction.Login -> login(action.email, action.password)
            is UnifiedUserAction.LoginWithGoogle -> loginWithGoogle()
            is UnifiedUserAction.LoginWithTwitter -> loginWithTwitter()
            is UnifiedUserAction.Logout -> logout()
            is UnifiedUserAction.Register -> register(action.email, action.password, action.name)
            is UnifiedUserAction.UpdateProfile -> { /* TODO */ }
            is UnifiedUserAction.RequestPasswordReset -> sendPasswordResetEmail(action.email)
        }
    }

    private fun handleProductAction(action: UnifiedProductAction) {
        when (action) {
            is UnifiedProductAction.LoadProducts -> loadProducts()
            is UnifiedProductAction.RefreshProducts -> refreshProducts()
            is UnifiedProductAction.SelectProduct -> selectProduct(action.product)
            is UnifiedProductAction.ViewProductDetail -> { /* TODO */ }
            is UnifiedProductAction.CreateProduct -> { /* Handled by CreateProductViewModel */ }
            is UnifiedProductAction.UpdateProduct -> { /* TODO */ }
            is UnifiedProductAction.DeleteProduct -> { /* Handled by OverviewViewModel */ }
        }
    }

    private fun handleOrderAction(action: UnifiedOrderAction) {
        when (action) {
            is UnifiedOrderAction.LoadOrders -> { /* Handled by OrdersViewModel */ }
            is UnifiedOrderAction.ViewOrderDetail -> { /* Handled by navigation */ }
            is UnifiedOrderAction.PlaceOrder -> { /* Not used in seller app */ }
            is UnifiedOrderAction.CancelOrder -> { /* TODO */ }
        }
    }

    private fun handleUiAction(action: UnifiedUiAction) {
        when (action) {
            is UnifiedUiAction.ShowSnackbar -> showSnackbar(action.message, action.type)
            is UnifiedUiAction.HideSnackbar -> hideSnackbar()
            is UnifiedUiAction.ShowDialog -> showDialog(action.dialog)
            is UnifiedUiAction.HideDialog -> hideDialog()
            is UnifiedUiAction.ShowBottomSheet -> { /* TODO */ }
            is UnifiedUiAction.HideBottomSheet -> { /* TODO */ }
            is UnifiedUiAction.SetRefreshing -> setRefreshing(action.isRefreshing)
            is UnifiedUiAction.ShowPasswordResetDialog -> showPasswordResetDialog()
            is UnifiedUiAction.HidePasswordResetDialog -> hidePasswordResetDialog()
        }
    }

    private fun handleProfileAction(action: UnifiedProfileAction) {
        when (action) {
            is UnifiedProfileAction.LoadProfile -> { /* Handled by SellerProfileScreen */ }
            is UnifiedProfileAction.UpdateProfileField -> { /* TODO */ }
            is UnifiedProfileAction.SaveProfile -> { /* TODO */ }
            is UnifiedProfileAction.CancelProfileEdit -> { /* TODO */ }
            is UnifiedProfileAction.LoadCustomerProfile -> { /* Not used in seller app */ }
            is UnifiedProfileAction.LoadOrderHistory -> { /* Not used in seller app */ }
            is UnifiedProfileAction.RefreshCustomerProfile -> { /* Not used in seller app */ }
            is UnifiedProfileAction.SaveBuyerProfile -> { /* Not used in seller app */ }
        }
    }

    // ===== Initialization =====

    private fun initializeApp() {
        viewModelScope.launch {
            try {
                _state.update { it.copy(
                    meta = it.meta.copy(
                        isInitializing = true,
                        initializationStep = InitializationStep.CheckingAuth
                    )
                )}

                checkAuthenticationStatus()

                // Wait for auth state to stabilize
                val userId = authRepository.observeAuthState()
                    .filterNotNull()
                    .first()

                println("🚀 Sell App Init: Authentication complete, user ID: $userId")

                // Load products
                _state.update { it.copy(
                    meta = it.meta.copy(
                        initializationStep = InitializationStep.LoadingArticles
                    )
                )}
                loadProducts()

                // Mark initialization complete
                _state.update { it.copy(
                    meta = it.meta.copy(
                        isInitializing = false,
                        isInitialized = true,
                        initializationStep = InitializationStep.Complete
                    )
                )}

                println("🚀 Sell App Init: Initialization complete!")

            } catch (e: Exception) {
                println("❌ Sell App Init: Error - ${e.message}")
                _state.update { it.copy(
                    meta = it.meta.copy(
                        isInitializing = false,
                        isInitialized = false,
                        initializationStep = InitializationStep.Failed(
                            step = "initialization",
                            message = e.message ?: "Unknown error"
                        )
                    )
                )}
            }
        }
    }

    private suspend fun checkAuthenticationStatus() {
        try {
            println("🔍 Sell App: Checking authentication")

            authRepository.checkPersistedAuth().fold(
                onSuccess = { userId ->
                    if (userId != null) {
                        println("✅ Sell App: Restored auth session for user: $userId")
                    } else {
                        // Seller flavor requires login
                        println("🏪 Sell App: No auth - login required")
                        _state.update { current ->
                            current.copy(
                                common = current.common.copy(
                                    user = UserState.Guest,
                                    requiresLogin = true
                                ),
                                meta = current.meta.copy(
                                    isInitializing = false,
                                    isInitialized = true,
                                    initializationStep = InitializationStep.Complete
                                )
                            )
                        }
                    }
                },
                onFailure = { error ->
                    println("⚠️ Sell App: Failed to check auth - ${error.message}")
                    _state.update { current ->
                        current.copy(
                            common = current.common.copy(
                                user = UserState.Guest,
                                requiresLogin = true
                            ),
                            meta = current.meta.copy(
                                isInitializing = false,
                                isInitialized = true,
                                initializationStep = InitializationStep.Complete
                            )
                        )
                    }
                }
            )
        } catch (e: Exception) {
            println("❌ Sell App: Exception checking auth - ${e.message}")
            _state.update { current ->
                current.copy(
                    common = current.common.copy(
                        user = UserState.Guest,
                        requiresLogin = true
                    ),
                    meta = current.meta.copy(
                        isInitializing = false,
                        isInitialized = true,
                        initializationStep = InitializationStep.Complete
                    )
                )
            }
        }
    }

    // ===== Navigation =====

    private fun navigateTo(route: NavRoutes) {
        _state.update { current ->
            current.copy(
                common = current.common.copy(
                    navigation = current.common.navigation.copy(
                        previousRoute = current.common.navigation.currentRoute,
                        currentRoute = route,
                        backStack = current.common.navigation.backStack + route
                    )
                )
            )
        }
    }

    private fun navigateBack() {
        _state.update { current ->
            val backStack = current.common.navigation.backStack
            if (backStack.size > 1) {
                val newBackStack = backStack.dropLast(1)
                current.copy(
                    common = current.common.copy(
                        navigation = current.common.navigation.copy(
                            currentRoute = newBackStack.last(),
                            previousRoute = current.common.navigation.currentRoute,
                            backStack = newBackStack
                        )
                    )
                )
            } else current
        }
    }

    private fun openDrawer() {
        _state.update { current ->
            current.copy(
                common = current.common.copy(
                    navigation = current.common.navigation.copy(isDrawerOpen = true)
                )
            )
        }
    }

    private fun closeDrawer() {
        _state.update { current ->
            current.copy(
                common = current.common.copy(
                    navigation = current.common.navigation.copy(isDrawerOpen = false)
                )
            )
        }
    }

    // ===== Products =====

    private fun loadProducts() {
        viewModelScope.launch {
            println("📦 SellAppViewModel.loadProducts: START")

            _state.update { current ->
                current.copy(
                    screens = current.screens.copy(
                        products = current.screens.products.copy(isLoading = true, error = null)
                    )
                )
            }

            val sellerId = ""
            articleRepository.getArticles(sellerId)
                .catch { e ->
                    println("❌ SellAppViewModel.loadProducts: ERROR - ${e.message}")
                    _state.update { current ->
                        current.copy(
                            screens = current.screens.copy(
                                products = current.screens.products.copy(
                                    isLoading = false,
                                    error = ErrorState(
                                        message = e.message ?: "Failed to load products",
                                        type = ErrorType.NETWORK
                                    )
                                )
                            )
                        )
                    }
                }
                .collect { article ->
                    _state.update { current ->
                        val currentProducts = current.screens.products.items.toMutableList()

                        when (article.mode) {
                            Article.MODE_ADDED -> {
                                // Check if article already exists to avoid duplicates
                                val existingIndex = currentProducts.indexOfFirst { it.id == article.id }
                                if (existingIndex >= 0) {
                                    currentProducts[existingIndex] = article
                                } else {
                                    currentProducts.add(article)
                                }
                            }
                            Article.MODE_CHANGED -> {
                                val index = currentProducts.indexOfFirst { it.id == article.id }
                                if (index >= 0) currentProducts[index] = article
                            }
                            Article.MODE_REMOVED -> currentProducts.removeAll { it.id == article.id }
                        }

                        current.copy(
                            screens = current.screens.copy(
                                products = current.screens.products.copy(
                                    isLoading = false,
                                    items = currentProducts,
                                    error = null
                                )
                            )
                        )
                    }
                }
        }
    }

    private fun refreshProducts() {
        _state.update { current ->
            current.copy(
                common = current.common.copy(
                    ui = current.common.ui.copy(isRefreshing = true)
                )
            )
        }
        loadProducts()
        _state.update { current ->
            current.copy(
                common = current.common.copy(
                    ui = current.common.ui.copy(isRefreshing = false)
                )
            )
        }
    }

    private fun selectProduct(product: Article) {
        _state.update { current ->
            current.copy(
                screens = current.screens.copy(
                    products = current.screens.products.copy(selectedItem = product)
                )
            )
        }
    }

    // ===== UI =====

    private fun showSnackbar(message: String, type: SnackbarType) {
        _state.update { current ->
            current.copy(
                common = current.common.copy(
                    ui = current.common.ui.copy(
                        snackbar = SnackbarState(message = message, type = type)
                    )
                )
            )
        }
    }

    private fun hideSnackbar() {
        _state.update { current ->
            current.copy(
                common = current.common.copy(
                    ui = current.common.ui.copy(snackbar = null)
                )
            )
        }
    }

    private fun showDialog(dialog: DialogState) {
        _state.update { current ->
            current.copy(
                common = current.common.copy(
                    ui = current.common.ui.copy(dialog = dialog)
                )
            )
        }
    }

    private fun hideDialog() {
        _state.update { current ->
            current.copy(
                common = current.common.copy(
                    ui = current.common.ui.copy(dialog = null)
                )
            )
        }
    }

    private fun setRefreshing(isRefreshing: Boolean) {
        _state.update { current ->
            current.copy(
                common = current.common.copy(
                    ui = current.common.ui.copy(isRefreshing = isRefreshing)
                )
            )
        }
    }

    // ===== Authentication =====

    private fun login(email: String, password: String) {
        viewModelScope.launch {
            _state.update { current ->
                current.copy(
                    screens = current.screens.copy(
                        auth = current.screens.auth.copy(
                            isLoading = true,
                            error = null,
                            isSuccess = false
                        )
                    )
                )
            }

            authRepository.signInWithEmail(email, password)
                .onSuccess { userId ->
                    println("✅ Seller Login Success: userId=$userId")

                    _state.update { current ->
                        current.copy(
                            screens = current.screens.copy(
                                auth = current.screens.auth.copy(
                                    isLoading = false,
                                    isSuccess = true,
                                    error = null
                                )
                            ),
                            common = current.common.copy(
                                requiresLogin = false
                            ),
                            meta = current.meta.copy(
                                isInitializing = false,
                                isInitialized = true,
                                initializationStep = InitializationStep.Complete
                            )
                        )
                    }

                    showSnackbar(getString(Res.string.snackbar_login_success), SnackbarType.SUCCESS)
                }
                .onFailure { error ->
                    val errorMessage = when {
                        error.message?.contains("No account found", true) == true ->
                            getString(Res.string.error_no_account)
                        error.message?.contains("Incorrect password", true) == true ->
                            getString(Res.string.error_wrong_password)
                        error.message?.contains("Invalid email", true) == true ->
                            getString(Res.string.error_email_invalid)
                        error.message?.contains("network", true) == true ||
                        error.message?.contains("Unable to resolve host", true) == true ||
                        error.message?.contains("No address associated", true) == true ||
                        error.message?.contains("failed to connect", true) == true ||
                        error.message?.contains("timeout", true) == true ||
                        error.message?.contains("UnknownHostException", true) == true ->
                            getString(Res.string.error_no_internet)
                        error.message?.contains("Too many", true) == true ->
                            getString(Res.string.error_too_many_attempts)
                        else -> error.message ?: getString(Res.string.error_login_failed)
                    }

                    showSnackbar(errorMessage, SnackbarType.ERROR)

                    _state.update { current ->
                        current.copy(
                            screens = current.screens.copy(
                                auth = current.screens.auth.copy(
                                    isLoading = false,
                                    error = errorMessage,
                                    isSuccess = false
                                )
                            )
                        )
                    }
                }
        }
    }

    private fun loginWithGoogle() {
        println("🔐 SellAppViewModel.loginWithGoogle: Triggering Google Sign-In")
        _state.update { current ->
            current.copy(
                common = current.common.copy(triggerGoogleSignIn = true)
            )
        }
    }

    private fun loginWithTwitter() {
        println("🔐 SellAppViewModel.loginWithTwitter: Triggering Twitter Sign-In")
        _state.update { current ->
            current.copy(
                common = current.common.copy(triggerTwitterSignIn = true)
            )
        }
    }

    /**
     * Send password reset email to the specified email address.
     */
    private fun sendPasswordResetEmail(email: String) {
        viewModelScope.launch {
            _state.update { current ->
                current.copy(
                    screens = current.screens.copy(
                        auth = current.screens.auth.copy(
                            isLoading = true,
                            error = null,
                            passwordResetSent = false
                        )
                    )
                )
            }

            authRepository.sendPasswordResetEmail(email)
                .onSuccess {
                    println("✅ Password reset email sent to $email")
                    _state.update { current ->
                        current.copy(
                            screens = current.screens.copy(
                                auth = current.screens.auth.copy(
                                    isLoading = false,
                                    passwordResetSent = true,
                                    showPasswordResetDialog = false,
                                    error = null
                                )
                            )
                        )
                    }
                    showSnackbar(getString(Res.string.password_reset_sent), SnackbarType.SUCCESS)
                }
                .onFailure { error ->
                    println("❌ Password reset failed: ${error.message}")
                    val errorMessage = when {
                        error.message?.contains("No account found", true) == true ->
                            getString(Res.string.error_no_account)
                        error.message?.contains("Invalid email", true) == true ->
                            getString(Res.string.error_email_invalid)
                        else -> error.message ?: getString(Res.string.password_reset_failed)
                    }
                    _state.update { current ->
                        current.copy(
                            screens = current.screens.copy(
                                auth = current.screens.auth.copy(
                                    isLoading = false,
                                    error = errorMessage,
                                    passwordResetSent = false
                                )
                            )
                        )
                    }
                    showSnackbar(errorMessage, SnackbarType.ERROR)
                }
        }
    }

    /**
     * Show password reset dialog
     */
    private fun showPasswordResetDialog() {
        _state.update { current ->
            current.copy(
                screens = current.screens.copy(
                    auth = current.screens.auth.copy(
                        showPasswordResetDialog = true,
                        passwordResetSent = false,
                        error = null
                    )
                )
            )
        }
    }

    /**
     * Hide password reset dialog
     */
    private fun hidePasswordResetDialog() {
        _state.update { current ->
            current.copy(
                screens = current.screens.copy(
                    auth = current.screens.auth.copy(
                        showPasswordResetDialog = false,
                        error = null
                    )
                )
            )
        }
    }

    override fun resetGoogleSignInTrigger() {
        _state.update { current ->
            current.copy(
                common = current.common.copy(triggerGoogleSignIn = false)
            )
        }
    }

    /**
     * Set auth error message (called when Google Sign-In fails)
     */
    fun setAuthError(errorMessage: String?) {
        println("🔴 SellAppViewModel.setAuthError: Setting error to: $errorMessage")
        _state.update { current ->
            println("🔴 SellAppViewModel.setAuthError: Current auth error was: ${current.screens.auth.error}")
            current.copy(
                screens = current.screens.copy(
                    auth = current.screens.auth.copy(
                        isLoading = false,
                        error = errorMessage
                    )
                )
            )
        }
        println("🔴 SellAppViewModel.setAuthError: New auth error is: ${_state.value.screens.auth.error}")
    }

    override fun resetTwitterSignInTrigger() {
        _state.update { current ->
            current.copy(
                common = current.common.copy(triggerTwitterSignIn = false)
            )
        }
    }

    private fun logout() {
        viewModelScope.launch {
            authRepository.signOut()
                .onSuccess {
                    _state.update { current ->
                        current.copy(
                            common = current.common.copy(
                                user = UserState.Guest,
                                requiresLogin = true,
                                triggerGoogleSignOut = true
                            )
                        )
                    }
                    showSnackbar(getString(Res.string.snackbar_logout_success), SnackbarType.SUCCESS)
                }
                .onFailure { error ->
                    showSnackbar(error.message ?: getString(Res.string.snackbar_logout_failed), SnackbarType.ERROR)
                }
        }
    }

    override fun resetGoogleSignOutTrigger() {
        _state.update { current ->
            current.copy(
                common = current.common.copy(triggerGoogleSignOut = false)
            )
        }
    }

    private fun register(email: String, password: String, name: String) {
        viewModelScope.launch {
            _state.update { current ->
                current.copy(
                    screens = current.screens.copy(
                        auth = current.screens.auth.copy(
                            isLoading = true,
                            error = null,
                            isSuccess = false
                        )
                    )
                )
            }

            authRepository.signUpWithEmail(email, password)
                .onSuccess { userId ->
                    _state.update { current ->
                        current.copy(
                            common = current.common.copy(
                                user = UserState.LoggedIn(
                                    id = userId,
                                    name = name,
                                    email = email,
                                    role = UserRole.SELLER
                                )
                            ),
                            screens = current.screens.copy(
                                auth = current.screens.auth.copy(
                                    isLoading = false,
                                    error = null,
                                    isSuccess = true
                                )
                            )
                        )
                    }

                    showSnackbar(getString(Res.string.snackbar_account_created), SnackbarType.SUCCESS)
                    delay(1500)
                    navigateTo(NavRoutes.Login)
                }
                .onFailure { error ->
                    val errorMessage = when {
                        error.message?.contains("email-already-in-use") == true ->
                            getString(Res.string.error_email_in_use)
                        error.message?.contains("weak-password") == true ->
                            getString(Res.string.error_weak_password)
                        else -> getString(Res.string.error_registration_failed)
                    }

                    _state.update { current ->
                        current.copy(
                            screens = current.screens.copy(
                                auth = current.screens.auth.copy(
                                    isLoading = false,
                                    error = errorMessage,
                                    isSuccess = false
                                )
                            )
                        )
                    }

                    showSnackbar(errorMessage, SnackbarType.ERROR)
                }
        }
    }
}
