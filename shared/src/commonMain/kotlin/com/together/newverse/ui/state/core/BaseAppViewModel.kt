package com.together.newverse.ui.state.core

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.together.newverse.domain.repository.AuthRepository
import com.together.newverse.ui.state.DialogState
import com.together.newverse.ui.state.GlobalUiState
import com.together.newverse.ui.state.SnackbarState
import com.together.newverse.ui.state.SnackbarType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Base ViewModel class providing common patterns for app ViewModels.
 *
 * Features:
 * - AuthFlowCoordinator for auth-driven state management
 * - ScreenStateRegistry for type-safe screen state management
 * - GlobalUiState for snackbars, dialogs, etc.
 * - Common helper methods for launching auth-dependent operations
 *
 * @param S The type of the main application state
 * @param A The type of actions this ViewModel handles
 */
abstract class BaseAppViewModel<S : Any, A : Any>(
    internal val authRepository: AuthRepository
) : ViewModel() {

    /**
     * Coordinator for auth-dependent flows and operations.
     */
    protected val authCoordinator = AuthFlowCoordinator(authRepository)

    /**
     * Authentication state flow - exposes the underlying auth state.
     */
    val authState: StateFlow<AuthState> get() = authCoordinator.authState

    /**
     * Registry for managing screen-specific states.
     */
    protected val screenRegistry = ScreenStateRegistry()

    /**
     * Global UI state (snackbars, dialogs, etc.) separate from main state.
     */
    private val _globalUiState = MutableStateFlow(GlobalUiState())
    val globalUiState: StateFlow<GlobalUiState> = _globalUiState.asStateFlow()

    /**
     * Internal state flow - subclasses must initialize this.
     * Note: Using internal visibility to allow extension functions access in flavor modules.
     */
    internal abstract val _state: MutableStateFlow<S>

    /**
     * Public state flow for observers.
     */
    abstract val state: StateFlow<S>

    /**
     * Initializes the auth coordinator. Call this in init{} block.
     */
    protected fun initializeAuthCoordinator() {
        authCoordinator.initialize(viewModelScope)
    }

    /**
     * Main action dispatcher - subclasses implement this to handle actions.
     */
    abstract fun dispatch(action: A)

    // ===== Auth-Aware Flow Builders =====

    /**
     * Creates an auth-dependent flow. The flow only executes when the user is authenticated.
     *
     * Example:
     * ```
     * val profileState = authFlow { userId ->
     *     profileRepository.observeProfile(userId)
     * }.stateIn(viewModelScope, SharingStarted.Lazily, AuthAwareState.AwaitingAuth)
     * ```
     */
    protected fun <T> authFlow(
        flowFactory: (userId: String) -> Flow<T>
    ): Flow<AuthAwareState<T>> = authCoordinator.whenAuthenticated(flowFactory)

    /**
     * Wraps an existing AsyncState flow with auth awareness.
     */
    protected fun <T> withAuthFlow(
        asyncStateFlow: Flow<AsyncState<T>>
    ): Flow<AuthAwareState<T>> = authCoordinator.withAuth(asyncStateFlow)

    /**
     * Executes a suspend function only when authenticated.
     * Returns Result.failure if not authenticated.
     */
    protected suspend fun <T> withAuth(
        block: suspend (userId: String) -> T
    ): Result<T> = authCoordinator.withAuthSuspend(block)

    /**
     * Launches a coroutine that only executes when authenticated.
     * The coroutine is cancelled if the user logs out.
     */
    protected fun launchAuthenticated(
        block: suspend CoroutineScope.(userId: String) -> Unit
    ): Job = viewModelScope.launch {
        val userId = authCoordinator.getCurrentUserId()
        if (userId != null) {
            block(userId)
        }
    }

    /**
     * Launches a coroutine that waits for authentication before executing.
     */
    protected fun launchWhenAuthenticated(
        block: suspend CoroutineScope.(userId: String) -> Unit
    ): Job = viewModelScope.launch {
        authCoordinator.authState.collect { authState ->
            if (authState is AuthState.Authenticated) {
                block(authState.userId)
                return@collect
            }
        }
    }

    // ===== Screen State Helpers =====

    /**
     * Gets the current state for a screen.
     */
    protected fun <T : Any> getScreenState(id: ScreenId<T>): T? = screenRegistry.getState(id)

    /**
     * Gets the current state for a screen, or a default value.
     */
    protected fun <T : Any> getScreenStateOrDefault(id: ScreenId<T>, default: T): T =
        screenRegistry.getStateOrDefault(id, default)

    /**
     * Updates the state for a screen.
     */
    protected fun <T : Any> updateScreenState(id: ScreenId<T>, update: (T?) -> T) {
        screenRegistry.updateState(id, update)
    }

    /**
     * Updates the state for a screen only if it exists.
     */
    protected fun <T : Any> updateScreenStateIfPresent(id: ScreenId<T>, update: (T) -> T) {
        screenRegistry.updateStateIfPresent(id, update)
    }

    /**
     * Observes the state for a screen.
     */
    protected fun <T : Any> observeScreenState(id: ScreenId<T>): Flow<T?> =
        screenRegistry.observeState(id)

    // ===== Global UI Helpers =====

    /**
     * Shows a snackbar with the given message and type.
     */
    protected fun showSnackbar(message: String, type: SnackbarType = SnackbarType.INFO) {
        _globalUiState.update { current ->
            current.copy(
                snackbar = SnackbarState(message = message, type = type)
            )
        }
    }

    /**
     * Hides the current snackbar.
     */
    protected fun hideSnackbar() {
        _globalUiState.update { current ->
            current.copy(snackbar = null)
        }
    }

    /**
     * Shows a dialog.
     */
    protected fun showDialog(dialog: DialogState) {
        _globalUiState.update { current ->
            current.copy(dialog = dialog)
        }
    }

    /**
     * Hides the current dialog.
     */
    protected fun hideDialog() {
        _globalUiState.update { current ->
            current.copy(dialog = null)
        }
    }

    /**
     * Sets the refreshing state.
     */
    protected fun setRefreshing(isRefreshing: Boolean) {
        _globalUiState.update { current ->
            current.copy(isRefreshing = isRefreshing)
        }
    }

    // ===== Auth State Convenience Methods =====

    /**
     * Gets the current user ID if authenticated.
     */
    protected fun getCurrentUserId(): String? = authCoordinator.getCurrentUserId()

    /**
     * Checks if the user is currently authenticated.
     */
    protected fun isAuthenticated(): Boolean = authCoordinator.isAuthenticated()

    /**
     * Checks if the current user is anonymous/guest.
     */
    protected fun isAnonymous(): Boolean = authCoordinator.isAnonymous()

    /**
     * Refreshes the current user info from the auth repository.
     */
    protected suspend fun refreshUserInfo() = authCoordinator.refreshUserInfo()

    // ===== Lifecycle =====

    override fun onCleared() {
        super.onCleared()
        screenRegistry.clear()
    }
}
