package com.together.newverse.ui.state.core

import androidx.compose.runtime.Composable

/**
 * Composable helper for rendering content based on AsyncState.
 *
 * Provides a clean pattern for handling loading, error, and success states
 * with optional custom UI for each state.
 *
 * Example usage:
 * ```
 * val articlesState: AsyncState<List<Article>> = viewModel.articlesState
 *
 * AsyncStateContent(
 *     state = articlesState,
 *     onRetry = { viewModel.loadArticles() }
 * ) { articles ->
 *     ArticleList(articles = articles)
 * }
 * ```
 *
 * @param state The AsyncState to render
 * @param onRetry Optional retry callback for error state (only shown if error is retryable)
 * @param initialContent Optional composable for Initial state (defaults to nothing)
 * @param loadingContent Optional composable for Loading state (defaults to nothing)
 * @param errorContent Optional custom error composable (overrides default)
 * @param content Composable for Success state, receives the data
 */
@Composable
fun <T> AsyncStateContent(
    state: AsyncState<T>,
    onRetry: (() -> Unit)? = null,
    initialContent: @Composable () -> Unit = {},
    loadingContent: @Composable () -> Unit = {},
    errorContent: (@Composable (message: String, retryable: Boolean) -> Unit)? = null,
    content: @Composable (T) -> Unit
) {
    when (state) {
        is AsyncState.Initial -> initialContent()
        is AsyncState.Loading -> loadingContent()
        is AsyncState.Error -> {
            if (errorContent != null) {
                errorContent(state.message, state.retryable)
            } else {
                // Default error handling - consumers should provide custom errorContent
                // or wrap this with their own error UI
            }
        }
        is AsyncState.Success -> content(state.data)
    }
}

/**
 * Composable helper for rendering content based on AuthAwareState.
 *
 * Handles authentication-dependent UI with proper states for:
 * - Awaiting auth (initializing)
 * - Auth required (not logged in)
 * - Authenticated (with nested AsyncState for data)
 *
 * Example usage:
 * ```
 * val profileState: AuthAwareState<BuyerProfile> = viewModel.profileState
 *
 * AuthAwareContent(
 *     state = profileState,
 *     onLoginRequired = { navController.navigate("login") },
 *     onRetry = { viewModel.loadProfile() }
 * ) { profile ->
 *     ProfileContent(profile = profile)
 * }
 * ```
 *
 * @param state The AuthAwareState to render
 * @param onLoginRequired Callback when user needs to authenticate
 * @param onRetry Optional retry callback for error states
 * @param awaitingAuthContent Optional composable while checking auth (defaults to nothing)
 * @param authRequiredContent Optional custom auth required composable (overrides default)
 * @param loadingContent Optional composable for authenticated loading state
 * @param errorContent Optional custom error composable for authenticated error state
 * @param content Composable for authenticated success state, receives the data
 */
@Composable
fun <T> AuthAwareContent(
    state: AuthAwareState<T>,
    onLoginRequired: () -> Unit = {},
    onRetry: (() -> Unit)? = null,
    awaitingAuthContent: @Composable () -> Unit = {},
    authRequiredContent: (@Composable () -> Unit)? = null,
    loadingContent: @Composable () -> Unit = {},
    errorContent: (@Composable (message: String, retryable: Boolean) -> Unit)? = null,
    content: @Composable (T) -> Unit
) {
    when (state) {
        is AuthAwareState.AwaitingAuth -> awaitingAuthContent()
        is AuthAwareState.AuthRequired -> {
            if (authRequiredContent != null) {
                authRequiredContent()
            } else {
                // Default: invoke onLoginRequired callback
                // Consumers should provide custom UI or handle navigation
                onLoginRequired()
            }
        }
        is AuthAwareState.Authenticated -> {
            AsyncStateContent(
                state = state.data,
                onRetry = onRetry,
                loadingContent = loadingContent,
                errorContent = errorContent,
                content = content
            )
        }
    }
}

/**
 * Composable helper with combined loading indicator.
 *
 * Shows a single loading state when either auth is initializing or data is loading.
 * Useful for screens that don't need to distinguish between these states.
 *
 * @param state The AuthAwareState to render
 * @param onLoginRequired Callback when user needs to authenticate
 * @param onRetry Optional retry callback for error states
 * @param loadingContent Composable for any loading state (auth or data)
 * @param authRequiredContent Optional custom auth required composable
 * @param errorContent Optional custom error composable
 * @param content Composable for success state
 */
@Composable
fun <T> AuthAwareContentWithLoading(
    state: AuthAwareState<T>,
    onLoginRequired: () -> Unit = {},
    onRetry: (() -> Unit)? = null,
    loadingContent: @Composable () -> Unit = {},
    authRequiredContent: (@Composable () -> Unit)? = null,
    errorContent: (@Composable (message: String, retryable: Boolean) -> Unit)? = null,
    content: @Composable (T) -> Unit
) {
    when (state) {
        is AuthAwareState.AwaitingAuth -> loadingContent()
        is AuthAwareState.AuthRequired -> {
            if (authRequiredContent != null) {
                authRequiredContent()
            } else {
                onLoginRequired()
            }
        }
        is AuthAwareState.Authenticated -> {
            when (state.data) {
                is AsyncState.Initial,
                is AsyncState.Loading -> loadingContent()
                is AsyncState.Error -> {
                    if (errorContent != null) {
                        errorContent(state.data.message, state.data.retryable)
                    }
                }
                is AsyncState.Success -> content(state.data.data)
            }
        }
    }
}

/**
 * Extension function to check if AuthAwareState is in any loading state.
 */
val <T> AuthAwareState<T>.isLoading: Boolean
    get() = this is AuthAwareState.AwaitingAuth ||
            (this is AuthAwareState.Authenticated && this.data.isLoading)

/**
 * Extension function to get error message from AuthAwareState if in error state.
 */
val <T> AuthAwareState<T>.errorMessage: String?
    get() = (this as? AuthAwareState.Authenticated)?.data?.let { data ->
        (data as? AsyncState.Error)?.message
    }

/**
 * Extension function to get data from AuthAwareState if available.
 */
fun <T> AuthAwareState<T>.getDataOrNull(): T? =
    (this as? AuthAwareState.Authenticated)?.data?.getOrNull()
