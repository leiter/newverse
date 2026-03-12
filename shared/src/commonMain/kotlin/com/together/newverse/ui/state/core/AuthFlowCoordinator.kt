@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.together.newverse.ui.state.core

import com.together.newverse.domain.repository.AuthRepository
import com.together.newverse.domain.repository.AuthUserInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Represents the authentication state of the application.
 * This is the source of truth for user authentication status.
 */
sealed interface AuthState {
    /**
     * Authentication status is being determined (checking persisted session).
     */
    data object Initializing : AuthState

    /**
     * User is not authenticated.
     */
    data object NotAuthenticated : AuthState

    /**
     * User is authenticated.
     */
    data class Authenticated(
        val userId: String,
        val email: String?,
        val displayName: String?,
        val photoUrl: String?,
        override val isAnonymous: Boolean
    ) : AuthState {
        companion object {
            fun fromUserInfo(userInfo: AuthUserInfo) = Authenticated(
                userId = userInfo.id,
                email = userInfo.email,
                displayName = userInfo.displayName,
                photoUrl = userInfo.photoUrl,
                isAnonymous = userInfo.isAnonymous
            )
        }
    }

    /**
     * Returns the user ID if authenticated, null otherwise.
     */
    val userIdOrNull: String? get() = (this as? Authenticated)?.userId

    /**
     * Returns true if the user is authenticated.
     */
    val isAuthenticated: Boolean get() = this is Authenticated

    /**
     * Returns true if the user is authenticated anonymously (guest).
     */
    val isAnonymous: Boolean get() = (this as? Authenticated)?.isAnonymous == true

    /**
     * Returns true if authentication is still being determined.
     */
    val isInitializing: Boolean get() = this is Initializing
}

/**
 * Coordinates authentication state and provides operators for auth-dependent flows.
 *
 * This is the central component for auth-driven state management. It:
 * - Manages the auth state lifecycle (initializing, authenticated, not authenticated)
 * - Provides operators to create auth-dependent flows
 * - Combines multiple auth-dependent flows
 *
 * Usage:
 * ```
 * val coordinator = AuthFlowCoordinator(authRepository)
 * coordinator.initialize(viewModelScope)
 *
 * // Create an auth-dependent flow
 * val profileState = coordinator.whenAuthenticated { userId ->
 *     profileRepository.observeProfile(userId)
 * }.stateIn(viewModelScope, SharingStarted.Lazily, AuthAwareState.AwaitingAuth)
 * ```
 */
class AuthFlowCoordinator(
    private val authRepository: AuthRepository
) {
    private val _authState = MutableStateFlow<AuthState>(AuthState.Initializing)

    /**
     * Observable authentication state.
     */
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    /**
     * Initializes the coordinator by checking persisted auth and observing auth state changes.
     * Should be called once when the ViewModel is created.
     *
     * Strategy:
     * 1. Try checkPersistedAuth() for a fast-path synchronous read.
     * 2. Observe authStateChanged for the definitive, ongoing state.
     */
    fun initialize(scope: CoroutineScope) {
        println("[NV_AuthFlowCoordinator] initialize: START - launching coroutine")
        scope.launch {
            println("[NV_AuthFlowCoordinator] initialize: Coroutine started, checking persisted auth...")
            // Fast-path: read currentUser synchronously (works if Firebase has already loaded)
            val persistedResult = authRepository.checkPersistedAuth()
            println("[NV_AuthFlowCoordinator] initialize: checkPersistedAuth returned: $persistedResult")

            var initialCheckDone = false

            persistedResult.fold(
                onSuccess = { userId ->
                    println("[NV_AuthFlowCoordinator] initialize: checkPersistedAuth SUCCESS - userId=$userId")
                    if (userId != null) {
                        // Firebase already has a user — set Authenticated immediately
                        initialCheckDone = true
                        println("[NV_AuthFlowCoordinator] initialize: Getting current user info...")
                        val userInfo = authRepository.getCurrentUserInfo()
                        println("[NV_AuthFlowCoordinator] initialize: getCurrentUserInfo returned: $userInfo")
                        if (userInfo != null) {
                            _authState.value = AuthState.Authenticated.fromUserInfo(userInfo)
                            println("[NV_AuthFlowCoordinator] initialize: Auth state set to Authenticated (from userInfo)")
                        } else {
                            val isAnon = authRepository.isAnonymous()
                            _authState.value = AuthState.Authenticated(
                                userId = userId,
                                email = null,
                                displayName = null,
                                photoUrl = null,
                                isAnonymous = isAnon
                            )
                            println("[NV_AuthFlowCoordinator] initialize: Auth state set to Authenticated (fallback)")
                        }
                    } else {
                        // currentUser was null — Firebase may still be loading the persisted session.
                        // Stay Initializing; authStateChanged will emit the real state shortly.
                        println("[NV_AuthFlowCoordinator] initialize: currentUser null — waiting for authStateChanged")
                    }
                },
                onFailure = { error ->
                    println("[NV_AuthFlowCoordinator] initialize: checkPersistedAuth FAILURE - ${error.message}, waiting for authStateChanged")
                }
            )

            // Observe for ongoing auth state changes
            println("[NV_AuthFlowCoordinator] initialize: Setting up observeAuthState collection...")
            authRepository.observeAuthState().collect { userId ->
                println("[NV_AuthFlowCoordinator] observeAuthState: Collected userId=$userId, currentState=${_authState.value}")
                if (userId != null) {
                    val userInfo = authRepository.getCurrentUserInfo()
                    println("[NV_AuthFlowCoordinator] observeAuthState: getCurrentUserInfo returned: $userInfo")
                    if (userInfo != null) {
                        _authState.value = AuthState.Authenticated.fromUserInfo(userInfo)
                        println("[NV_AuthFlowCoordinator] observeAuthState: Auth state updated to Authenticated")
                    } else {
                        val isAnon = authRepository.isAnonymous()
                        _authState.value = AuthState.Authenticated(
                            userId = userId,
                            email = null,
                            displayName = null,
                            photoUrl = null,
                            isAnonymous = isAnon
                        )
                        println("[NV_AuthFlowCoordinator] observeAuthState: Auth state updated to Authenticated (fallback)")
                    }
                } else if (!initialCheckDone) {
                    // First null emission during startup — Firebase Auth may still be restoring
                    // the persisted session from SharedPreferences. Wait briefly before declaring
                    // NotAuthenticated, then re-check.
                    initialCheckDone = true
                    println("[NV_AuthFlowCoordinator] observeAuthState: First null during init — waiting for Firebase to restore session...")
                    delay(1500)
                    val restoredUserId = authRepository.getCurrentUserId()
                    println("[NV_AuthFlowCoordinator] observeAuthState: After wait, getCurrentUserId=$restoredUserId")
                    if (restoredUserId != null) {
                        val userInfo = authRepository.getCurrentUserInfo()
                        if (userInfo != null) {
                            _authState.value = AuthState.Authenticated.fromUserInfo(userInfo)
                            println("[NV_AuthFlowCoordinator] observeAuthState: Session restored after wait!")
                        } else {
                            val isAnon = authRepository.isAnonymous()
                            _authState.value = AuthState.Authenticated(
                                userId = restoredUserId,
                                email = null,
                                displayName = null,
                                photoUrl = null,
                                isAnonymous = isAnon
                            )
                            println("[NV_AuthFlowCoordinator] observeAuthState: Session restored (fallback) after wait!")
                        }
                    } else {
                        _authState.value = AuthState.NotAuthenticated
                        println("[NV_AuthFlowCoordinator] observeAuthState: No session after wait — NotAuthenticated")
                    }
                } else {
                    _authState.value = AuthState.NotAuthenticated
                    println("[NV_AuthFlowCoordinator] observeAuthState: Auth state updated to NotAuthenticated")
                }
            }
        }
        println("[NV_AuthFlowCoordinator] initialize: END - coroutine launched")
    }

    /**
     * Updates the auth state directly. Useful after sign-in/sign-out operations.
     */
    fun updateAuthState(state: AuthState) {
        _authState.value = state
    }

    /**
     * Refreshes the current user info from the auth repository.
     */
    suspend fun refreshUserInfo() {
        val currentUserId = authRepository.getCurrentUserId()
        if (currentUserId != null) {
            val userInfo = authRepository.getCurrentUserInfo()
            if (userInfo != null) {
                _authState.value = AuthState.Authenticated.fromUserInfo(userInfo)
            }
        }
    }

    /**
     * Core operator: Creates a flow that executes only when authenticated.
     * Automatically handles auth state transitions and wraps the result in AuthAwareState.
     *
     * @param flowFactory Factory function that creates a flow given the authenticated user's ID
     * @return Flow of AuthAwareState that reflects both auth and data loading status
     */
    fun <T> whenAuthenticated(
        flowFactory: (userId: String) -> Flow<T>
    ): Flow<AuthAwareState<T>> = authState.flatMapLatest { auth ->
        when (auth) {
            is AuthState.Initializing -> flowOf(AuthAwareState.AwaitingAuth)
            is AuthState.NotAuthenticated -> flowOf(AuthAwareState.AuthRequired)
            is AuthState.Authenticated -> flowFactory(auth.userId)
                .map<T, AuthAwareState<T>> { data ->
                    AuthAwareState.Authenticated(
                        userId = auth.userId,
                        isAnonymous = auth.isAnonymous,
                        data = AsyncState.Success(data)
                    )
                }
                .onStart {
                    emit(
                        AuthAwareState.Authenticated(
                            userId = auth.userId,
                            isAnonymous = auth.isAnonymous,
                            data = AsyncState.Loading
                        )
                    )
                }
                .catch { error ->
                    emit(
                        AuthAwareState.Authenticated(
                            userId = auth.userId,
                            isAnonymous = auth.isAnonymous,
                            data = AsyncState.Error(
                                message = error.message ?: "Unknown error",
                                cause = error
                            )
                        )
                    )
                }
        }
    }

    /**
     * Creates an AuthAwareState flow from an existing AsyncState flow.
     * Useful when you already have an async operation and want to add auth awareness.
     */
    fun <T> withAuth(
        asyncStateFlow: Flow<AsyncState<T>>
    ): Flow<AuthAwareState<T>> = authState.flatMapLatest { auth ->
        when (auth) {
            is AuthState.Initializing -> flowOf(AuthAwareState.AwaitingAuth)
            is AuthState.NotAuthenticated -> flowOf(AuthAwareState.AuthRequired)
            is AuthState.Authenticated -> asyncStateFlow.map { asyncState ->
                AuthAwareState.Authenticated(
                    userId = auth.userId,
                    isAnonymous = auth.isAnonymous,
                    data = asyncState
                )
            }
        }
    }

    /**
     * Combines two auth-dependent flows into a single AuthAwareState flow.
     * Both flows will be executed with the same authenticated user ID.
     */
    fun <T1, T2, R> combineAuthenticated(
        flow1: (userId: String) -> Flow<T1>,
        flow2: (userId: String) -> Flow<T2>,
        transform: (T1, T2) -> R
    ): Flow<AuthAwareState<R>> = authState.flatMapLatest { auth ->
        when (auth) {
            is AuthState.Initializing -> flowOf(AuthAwareState.AwaitingAuth)
            is AuthState.NotAuthenticated -> flowOf(AuthAwareState.AuthRequired)
            is AuthState.Authenticated -> combine(
                flow1(auth.userId).asAsyncState(),
                flow2(auth.userId).asAsyncState()
            ) { state1, state2 ->
                AuthAwareState.Authenticated(
                    userId = auth.userId,
                    isAnonymous = auth.isAnonymous,
                    data = combineAsyncStates(state1, state2, transform)
                )
            }
        }
    }

    /**
     * Combines three auth-dependent flows into a single AuthAwareState flow.
     */
    fun <T1, T2, T3, R> combineAuthenticated(
        flow1: (userId: String) -> Flow<T1>,
        flow2: (userId: String) -> Flow<T2>,
        flow3: (userId: String) -> Flow<T3>,
        transform: (T1, T2, T3) -> R
    ): Flow<AuthAwareState<R>> = authState.flatMapLatest { auth ->
        when (auth) {
            is AuthState.Initializing -> flowOf(AuthAwareState.AwaitingAuth)
            is AuthState.NotAuthenticated -> flowOf(AuthAwareState.AuthRequired)
            is AuthState.Authenticated -> combine(
                flow1(auth.userId).asAsyncState(),
                flow2(auth.userId).asAsyncState(),
                flow3(auth.userId).asAsyncState()
            ) { state1, state2, state3 ->
                AuthAwareState.Authenticated(
                    userId = auth.userId,
                    isAnonymous = auth.isAnonymous,
                    data = combineAsyncStates(state1, state2, state3, transform)
                )
            }
        }
    }

    /**
     * Executes a suspend function only when authenticated.
     * Returns Result.failure if not authenticated.
     */
    suspend fun <T> withAuthSuspend(block: suspend (userId: String) -> T): Result<T> {
        val currentAuth = _authState.value
        return when (currentAuth) {
            is AuthState.Authenticated -> runCatching { block(currentAuth.userId) }
            is AuthState.NotAuthenticated -> Result.failure(
                IllegalStateException("User is not authenticated")
            )
            is AuthState.Initializing -> Result.failure(
                IllegalStateException("Authentication is still initializing")
            )
        }
    }

    /**
     * Gets the current user ID if authenticated.
     */
    fun getCurrentUserId(): String? = (_authState.value as? AuthState.Authenticated)?.userId

    /**
     * Checks if the user is currently authenticated.
     */
    fun isAuthenticated(): Boolean = _authState.value is AuthState.Authenticated

    /**
     * Checks if the current user is anonymous/guest.
     */
    fun isAnonymous(): Boolean = (_authState.value as? AuthState.Authenticated)?.isAnonymous == true

    /**
     * Creates a StateFlow from an auth-dependent flow factory.
     * Convenience method for common usage pattern.
     */
    fun <T> stateFlowWhenAuthenticated(
        scope: CoroutineScope,
        sharingStarted: SharingStarted = SharingStarted.Lazily,
        flowFactory: (userId: String) -> Flow<T>
    ): StateFlow<AuthAwareState<T>> = whenAuthenticated(flowFactory)
        .stateIn(scope, sharingStarted, AuthAwareState.AwaitingAuth)
}
