package com.together.newverse.ui.state

import androidx.lifecycle.viewModelScope
import com.together.newverse.domain.model.Article
import com.together.newverse.domain.repository.ArticleRepository
import com.together.newverse.domain.repository.AuthRepository
import com.together.newverse.ui.navigation.NavRoutes
import com.together.newverse.ui.state.core.AuthState
import com.together.newverse.ui.state.core.BaseAppViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.*
import org.jetbrains.compose.resources.getString

/**
 * Sell flavor ViewModel managing all app state for seller/vendor app.
 *
 * Uses SellAppState (flattened, seller-only state) with SellAction types.
 * Extends BaseAppViewModel for auth-driven state management via AuthFlowCoordinator.
 */
class SellAppViewModel(
    private val articleRepository: ArticleRepository,
    authRepository: AuthRepository
) : BaseAppViewModel<SellAppState, SellAction>(authRepository) {

    override val _state = MutableStateFlow(SellAppState())
    override val state: StateFlow<SellAppState> = _state.asStateFlow()

    // Pending import content - stored in ViewModel to survive configuration changes
    private val _pendingImportContent = MutableStateFlow<String?>(null)
    val pendingImportContent: StateFlow<String?> = _pendingImportContent.asStateFlow()

    fun setPendingImportContent(content: String?) {
        _pendingImportContent.value = content
    }

    init {
        // Initialize auth coordinator from base class
        initializeAuthCoordinator()

        // Observe auth state changes and sync to SellAppState
        observeAuthStateChanges()

        // Initialize app on startup
        initializeApp()
    }

    /**
     * Observes AuthFlowCoordinator's auth state and syncs to SellAppState.
     * This bridges the new AuthState to the existing UserState for backward compatibility.
     */
    private fun observeAuthStateChanges() {
        viewModelScope.launch {
            authCoordinator.authState.collect { authState ->
                _state.update { current ->
                    current.copy(
                        user = authState.toUserState(),
                        requiresLogin = authState !is AuthState.Authenticated,
                        meta = when (authState) {
                            is AuthState.Initializing -> current.meta.copy(
                                isInitializing = true,
                                initializationStep = InitializationStep.CheckingAuth
                            )
                            is AuthState.NotAuthenticated -> current.meta.copy(
                                isInitializing = false,
                                isInitialized = true,
                                initializationStep = InitializationStep.Complete
                            )
                            is AuthState.Authenticated -> current.meta.copy(
                                isInitializing = false,
                                isInitialized = true,
                                initializationStep = InitializationStep.Complete
                            )
                        }
                    )
                }

                // Load products when authenticated
                if (authState is AuthState.Authenticated) {
                    loadProducts()
                }
            }
        }
    }

    /**
     * Converts AuthState to UserState for backward compatibility with existing UI.
     */
    private fun AuthState.toUserState(): UserState = when (this) {
        is AuthState.Initializing -> UserState.Loading
        is AuthState.NotAuthenticated -> UserState.Guest
        is AuthState.Authenticated -> UserState.LoggedIn(
            id = userId,
            name = displayName ?: "",
            email = email ?: "",
            role = UserRole.SELLER,
            profileImageUrl = photoUrl
        )
    }

    // ===== Public Action Handlers =====

    override fun dispatch(action: SellAction) {
        when (action) {
            is SellNavigationAction -> handleNavigationAction(action)
            is SellUserAction -> handleUserAction(action)
            is SellProductAction -> handleProductAction(action)
            is SellOrderAction -> handleOrderAction(action)
            is SellUiAction -> handleUiAction(action)
            is SellProfileAction -> handleProfileAction(action)
            is SellCustomerAction -> { /* Handled by SellerProfileViewModel directly */ }
            is SellInvitationAction -> { /* Handled by SellerProfileViewModel directly */ }
            is SellMessagingAction -> { /* Handled by screen-level ViewModels */ }
        }
    }

    // ===== Action Handlers =====

    private fun handleNavigationAction(action: SellNavigationAction) {
        when (action) {
            is SellNavigationAction.NavigateTo -> navigateTo(action.route)
            is SellNavigationAction.NavigateBack -> navigateBack()
            is SellNavigationAction.OpenDrawer -> openDrawer()
            is SellNavigationAction.CloseDrawer -> closeDrawer()
        }
    }

    private fun handleUserAction(action: SellUserAction) {
        when (action) {
            is SellUserAction.Login -> login(action.email, action.password)
            is SellUserAction.LoginWithGoogle -> loginWithGoogle()
            is SellUserAction.LoginWithTwitter -> loginWithTwitter()
            is SellUserAction.LoginWithApple -> { /* Seller app doesn't support Apple Sign-In */ }
            is SellUserAction.Logout -> logout()
            is SellUserAction.ContinueAsGuest -> { /* Seller app requires login, guest not supported */ }
            is SellUserAction.Register -> register(action.email, action.password, action.name)
            is SellUserAction.UpdateProfile -> { /* Handled by SellerProfileViewModel.saveProfile() */ }
            is SellUserAction.RequestPasswordReset -> sendPasswordResetEmail(action.email)
        }
    }

    private fun handleProductAction(action: SellProductAction) {
        when (action) {
            is SellProductAction.LoadProducts -> loadProducts()
            is SellProductAction.RefreshProducts -> refreshProducts()
            is SellProductAction.SelectProduct -> selectProduct(action.product)
            is SellProductAction.ViewProductDetail -> { /* Handled via navigation in NavGraph */ }
            is SellProductAction.CreateProduct -> { /* Handled by CreateProductViewModel */ }
            is SellProductAction.UpdateProduct -> { /* Handled by CreateProductViewModel */ }
            is SellProductAction.DeleteProduct -> { /* Handled by OverviewViewModel */ }
        }
    }

    private fun handleOrderAction(action: SellOrderAction) {
        when (action) {
            is SellOrderAction.LoadOrders -> { /* Handled by OrdersViewModel */ }
            is SellOrderAction.ViewOrderDetail -> { /* Handled by navigation */ }
            is SellOrderAction.PlaceOrder -> { /* Not used in seller app */ }
            is SellOrderAction.CancelOrder -> { /* TODO */ }
        }
    }

    private fun handleUiAction(action: SellUiAction) {
        when (action) {
            is SellUiAction.ShowSnackbar -> showSnackbarInState(action.message, action.type)
            is SellUiAction.HideSnackbar -> hideSnackbarInState()
            is SellUiAction.ShowDialog -> showDialogInState(action.dialog)
            is SellUiAction.HideDialog -> hideDialogInState()
            is SellUiAction.ShowBottomSheet -> {
                _state.update { it.copy(ui = it.ui.copy(bottomSheet = action.sheet)) }
            }
            is SellUiAction.HideBottomSheet -> {
                _state.update { it.copy(ui = it.ui.copy(bottomSheet = null)) }
            }
            is SellUiAction.SetRefreshing -> setRefreshingInState(action.isRefreshing)
            is SellUiAction.ShowPasswordResetDialog -> showPasswordResetDialog()
            is SellUiAction.HidePasswordResetDialog -> hidePasswordResetDialog()
            is SellUiAction.SetAuthMode -> { /* Not applicable to seller app - requires login */ }
        }
    }

    private fun handleProfileAction(action: SellProfileAction) {
        when (action) {
            is SellProfileAction.LoadProfile -> { /* Handled by SellerProfileScreen */ }
            is SellProfileAction.UpdateProfileField -> { /* Handled by SellerProfileViewModel */ }
            is SellProfileAction.SaveProfile -> { /* Handled by SellerProfileViewModel.saveProfile() */ }
            is SellProfileAction.CancelProfileEdit -> { /* Handled by SellerProfileViewModel */ }
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

                // AuthFlowCoordinator handles auth checking automatically
                // We just wait for it to complete and then load products
                println("🚀 Sell App Init: Waiting for auth state...")

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

    // ===== Navigation =====

    private fun navigateTo(route: NavRoutes) {
        _state.update { current ->
            current.copy(
                navigation = current.navigation.copy(
                    previousRoute = current.navigation.currentRoute,
                    currentRoute = route,
                    backStack = current.navigation.backStack + route
                )
            )
        }
    }

    private fun navigateBack() {
        _state.update { current ->
            val backStack = current.navigation.backStack
            if (backStack.size > 1) {
                val newBackStack = backStack.dropLast(1)
                current.copy(
                    navigation = current.navigation.copy(
                        currentRoute = newBackStack.last(),
                        previousRoute = current.navigation.currentRoute,
                        backStack = newBackStack
                    )
                )
            } else current
        }
    }

    private fun openDrawer() {
        _state.update { current ->
            current.copy(
                navigation = current.navigation.copy(isDrawerOpen = true)
            )
        }
    }

    private fun closeDrawer() {
        _state.update { current ->
            current.copy(
                navigation = current.navigation.copy(isDrawerOpen = false)
            )
        }
    }

    // ===== Products =====

    private fun loadProducts() {
        // Only load if authenticated
        if (!isAuthenticated()) {
            println("📦 SellAppViewModel.loadProducts: Skipping - not authenticated")
            return
        }

        viewModelScope.launch {
            println("📦 SellAppViewModel.loadProducts: START")

            _state.update { current ->
                current.copy(
                    products = current.products.copy(isLoading = true, error = null)
                )
            }

            val sellerId = ""
            articleRepository.getArticles(sellerId)
                .catch { e ->
                    println("❌ SellAppViewModel.loadProducts: ERROR - ${e.message}")
                    _state.update { current ->
                        current.copy(
                            products = current.products.copy(
                                isLoading = false,
                                error = ErrorState(
                                    message = e.message ?: "Failed to load products",
                                    type = ErrorType.NETWORK
                                )
                            )
                        )
                    }
                }
                .collect { article ->
                    _state.update { current ->
                        val currentProducts = current.products.items.toMutableList()

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
                            products = current.products.copy(
                                isLoading = false,
                                items = currentProducts,
                                error = null
                            )
                        )
                    }
                }
        }
    }

    private fun refreshProducts() {
        _state.update { current ->
            current.copy(
                ui = current.ui.copy(isRefreshing = true)
            )
        }
        loadProducts()
        _state.update { current ->
            current.copy(
                ui = current.ui.copy(isRefreshing = false)
            )
        }
    }

    private fun selectProduct(product: Article) {
        _state.update { current ->
            current.copy(
                products = current.products.copy(selectedItem = product)
            )
        }
    }

    // ===== UI (state-embedded for backward compatibility) =====

    private fun showSnackbarInState(message: String, type: SnackbarType) {
        _state.update { current ->
            current.copy(
                ui = current.ui.copy(
                    snackbar = SnackbarState(message = message, type = type)
                )
            )
        }
    }

    private fun hideSnackbarInState() {
        _state.update { current ->
            current.copy(
                ui = current.ui.copy(snackbar = null)
            )
        }
    }

    private fun showDialogInState(dialog: DialogState) {
        _state.update { current ->
            current.copy(
                ui = current.ui.copy(dialog = dialog)
            )
        }
    }

    private fun hideDialogInState() {
        _state.update { current ->
            current.copy(
                ui = current.ui.copy(dialog = null)
            )
        }
    }

    private fun setRefreshingInState(isRefreshing: Boolean) {
        _state.update { current ->
            current.copy(
                ui = current.ui.copy(isRefreshing = isRefreshing)
            )
        }
    }

    // ===== Authentication =====

    private fun login(email: String, password: String) {
        viewModelScope.launch {
            _state.update { current ->
                current.copy(
                    auth = current.auth.copy(
                        isLoading = true,
                        error = null,
                        isSuccess = false
                    )
                )
            }

            authRepository.signInWithEmail(email, password)
                .onSuccess { userId ->
                    println("✅ Seller Login Success: userId=$userId")

                    // AuthFlowCoordinator will automatically update auth state
                    // which triggers observeAuthStateChanges() to update SellAppState

                    _state.update { current ->
                        current.copy(
                            auth = current.auth.copy(
                                isLoading = false,
                                isSuccess = true,
                                error = null
                            )
                        )
                    }

                    showSnackbarInState(getString(Res.string.snackbar_login_success), SnackbarType.SUCCESS)
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

                    showSnackbarInState(errorMessage, SnackbarType.ERROR)

                    _state.update { current ->
                        current.copy(
                            auth = current.auth.copy(
                                isLoading = false,
                                error = errorMessage,
                                isSuccess = false
                            )
                        )
                    }
                }
        }
    }

    private fun loginWithGoogle() {
        println("🔐 SellAppViewModel.loginWithGoogle: Triggering Google Sign-In")
        _state.update { current ->
            current.copy(triggerGoogleSignIn = true)
        }
    }

    private fun loginWithTwitter() {
        println("🔐 SellAppViewModel.loginWithTwitter: Triggering Twitter Sign-In")
        _state.update { current ->
            current.copy(triggerTwitterSignIn = true)
        }
    }

    /**
     * Send password reset email to the specified email address.
     */
    private fun sendPasswordResetEmail(email: String) {
        viewModelScope.launch {
            _state.update { current ->
                current.copy(
                    auth = current.auth.copy(
                        isLoading = true,
                        error = null,
                        passwordResetSent = false
                    )
                )
            }

            authRepository.sendPasswordResetEmail(email)
                .onSuccess {
                    println("✅ Password reset email sent to $email")
                    _state.update { current ->
                        current.copy(
                            auth = current.auth.copy(
                                isLoading = false,
                                passwordResetSent = true,
                                showPasswordResetDialog = false,
                                error = null
                            )
                        )
                    }
                    showSnackbarInState(getString(Res.string.password_reset_sent), SnackbarType.SUCCESS)
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
                            auth = current.auth.copy(
                                isLoading = false,
                                error = errorMessage,
                                passwordResetSent = false
                            )
                        )
                    }
                    showSnackbarInState(errorMessage, SnackbarType.ERROR)
                }
        }
    }

    /**
     * Show password reset dialog
     */
    private fun showPasswordResetDialog() {
        _state.update { current ->
            current.copy(
                auth = current.auth.copy(
                    showPasswordResetDialog = true,
                    passwordResetSent = false,
                    error = null
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
                auth = current.auth.copy(
                    showPasswordResetDialog = false,
                    error = null
                )
            )
        }
    }

    fun resetGoogleSignInTrigger() {
        _state.update { current ->
            current.copy(triggerGoogleSignIn = false)
        }
    }

    /**
     * Set auth error message (called when Google Sign-In fails)
     */
    fun setAuthError(errorMessage: String?) {
        println("🔴 SellAppViewModel.setAuthError: Setting error to: $errorMessage")
        _state.update { current ->
            println("🔴 SellAppViewModel.setAuthError: Current auth error was: ${current.auth.error}")
            current.copy(
                auth = current.auth.copy(
                    isLoading = false,
                    error = errorMessage
                )
            )
        }
        println("🔴 SellAppViewModel.setAuthError: New auth error is: ${_state.value.auth.error}")
    }

    fun resetTwitterSignInTrigger() {
        _state.update { current ->
            current.copy(triggerTwitterSignIn = false)
        }
    }

    private fun logout() {
        viewModelScope.launch {
            authRepository.signOut()
                .onSuccess {
                    // AuthFlowCoordinator will automatically update auth state
                    _state.update { current ->
                        current.copy(
                            triggerGoogleSignOut = true
                        )
                    }
                    showSnackbarInState(getString(Res.string.snackbar_logout_success), SnackbarType.SUCCESS)
                }
                .onFailure { error ->
                    showSnackbarInState(error.message ?: getString(Res.string.snackbar_logout_failed), SnackbarType.ERROR)
                }
        }
    }

    fun resetGoogleSignOutTrigger() {
        _state.update { current ->
            current.copy(triggerGoogleSignOut = false)
        }
    }

    fun resetAppleSignInTrigger() {
        _state.update { current ->
            current.copy(triggerAppleSignIn = false)
        }
    }

    private fun register(email: String, password: String, name: String) {
        viewModelScope.launch {
            _state.update { current ->
                current.copy(
                    auth = current.auth.copy(
                        isLoading = true,
                        error = null,
                        isSuccess = false
                    )
                )
            }

            authRepository.signUpWithEmail(email, password)
                .onSuccess { userId ->
                    // AuthFlowCoordinator will automatically update auth state
                    _state.update { current ->
                        current.copy(
                            auth = current.auth.copy(
                                isLoading = false,
                                error = null,
                                isSuccess = true
                            )
                        )
                    }

                    showSnackbarInState(getString(Res.string.snackbar_account_created), SnackbarType.SUCCESS)
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
                            auth = current.auth.copy(
                                isLoading = false,
                                error = errorMessage,
                                isSuccess = false
                            )
                        )
                    }

                    showSnackbarInState(errorMessage, SnackbarType.ERROR)
                }
        }
    }
}
