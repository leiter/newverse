package com.together.newverse.ui.state.core

/**
 * Wraps state that depends on authentication status.
 * Provides a type-safe way to handle unauthenticated, authenticating, and authenticated states.
 *
 * @param T The type of data available when authenticated
 */
sealed interface AuthAwareState<out T> {
    /**
     * Authentication status is being determined (e.g., checking persisted session).
     */
    data object AwaitingAuth : AuthAwareState<Nothing>

    /**
     * User is not authenticated and authentication is required to proceed.
     */
    data object AuthRequired : AuthAwareState<Nothing>

    /**
     * User is authenticated with associated data in an async state.
     */
    data class Authenticated<T>(
        val userId: String,
        val isAnonymous: Boolean,
        val data: AsyncState<T>
    ) : AuthAwareState<T> {
        /**
         * Returns the data if the async state is Success, null otherwise.
         */
        fun getDataOrNull(): T? = data.getOrNull()
    }

    /**
     * Returns true if this represents an authenticated user (regardless of data load status).
     */
    val isAuthenticated: Boolean get() = this is Authenticated

    /**
     * Returns true if the user is authenticated as a guest/anonymous user.
     */
    val isAnonymousUser: Boolean get() = (this as? Authenticated)?.isAnonymous == true

    /**
     * Returns true if awaiting authentication check.
     */
    val isAwaitingAuth: Boolean get() = this is AwaitingAuth

    /**
     * Returns true if authentication is required.
     */
    val requiresAuth: Boolean get() = this is AuthRequired

    /**
     * Returns the user ID if authenticated, null otherwise.
     */
    val userIdOrNull: String? get() = (this as? Authenticated)?.userId

    /**
     * Returns the inner AsyncState if authenticated, null otherwise.
     */
    val asyncStateOrNull: AsyncState<T>? get() = (this as? Authenticated)?.data
}

/**
 * Maps the data within an Authenticated state while preserving auth and async state semantics.
 */
inline fun <T, R> AuthAwareState<T>.mapData(transform: (T) -> R): AuthAwareState<R> = when (this) {
    is AuthAwareState.AwaitingAuth -> AuthAwareState.AwaitingAuth
    is AuthAwareState.AuthRequired -> AuthAwareState.AuthRequired
    is AuthAwareState.Authenticated -> AuthAwareState.Authenticated(
        userId = userId,
        isAnonymous = isAnonymous,
        data = data.map(transform)
    )
}

/**
 * Maps the AsyncState within an Authenticated state.
 */
inline fun <T, R> AuthAwareState<T>.mapAsyncState(
    transform: (AsyncState<T>) -> AsyncState<R>
): AuthAwareState<R> = when (this) {
    is AuthAwareState.AwaitingAuth -> AuthAwareState.AwaitingAuth
    is AuthAwareState.AuthRequired -> AuthAwareState.AuthRequired
    is AuthAwareState.Authenticated -> AuthAwareState.Authenticated(
        userId = userId,
        isAnonymous = isAnonymous,
        data = transform(data)
    )
}

/**
 * Executes the given block if this is an Authenticated state with Success data.
 */
inline fun <T> AuthAwareState<T>.onAuthenticatedSuccess(block: (userId: String, data: T) -> Unit): AuthAwareState<T> {
    if (this is AuthAwareState.Authenticated && data is AsyncState.Success) {
        block(userId, data.data)
    }
    return this
}

/**
 * Executes the given block if this requires authentication.
 */
inline fun <T> AuthAwareState<T>.onAuthRequired(block: () -> Unit): AuthAwareState<T> {
    if (this is AuthAwareState.AuthRequired) block()
    return this
}

/**
 * Executes the given block if this is awaiting auth.
 */
inline fun <T> AuthAwareState<T>.onAwaitingAuth(block: () -> Unit): AuthAwareState<T> {
    if (this is AuthAwareState.AwaitingAuth) block()
    return this
}

/**
 * Returns the data if authenticated with success, or the default value otherwise.
 */
fun <T> AuthAwareState<T>.getDataOrDefault(default: T): T = when (this) {
    is AuthAwareState.Authenticated -> data.getOrDefault(default)
    else -> default
}

/**
 * Combines two AuthAwareStates, requiring both to be authenticated.
 * If either requires auth, returns AuthRequired.
 * If either is awaiting auth, returns AwaitingAuth.
 * Otherwise combines the AsyncStates.
 */
fun <T1, T2, R> combineAuthAwareStates(
    state1: AuthAwareState<T1>,
    state2: AuthAwareState<T2>,
    transform: (T1, T2) -> R
): AuthAwareState<R> = when {
    state1 is AuthAwareState.AuthRequired || state2 is AuthAwareState.AuthRequired ->
        AuthAwareState.AuthRequired
    state1 is AuthAwareState.AwaitingAuth || state2 is AuthAwareState.AwaitingAuth ->
        AuthAwareState.AwaitingAuth
    state1 is AuthAwareState.Authenticated && state2 is AuthAwareState.Authenticated -> {
        // Use the first authenticated user's info
        AuthAwareState.Authenticated(
            userId = state1.userId,
            isAnonymous = state1.isAnonymous,
            data = combineAsyncStates(state1.data, state2.data, transform)
        )
    }
    else -> AuthAwareState.AwaitingAuth
}
