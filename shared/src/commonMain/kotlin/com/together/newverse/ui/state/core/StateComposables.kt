package com.together.newverse.ui.state.core

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Composable helper for rendering AsyncState with appropriate UI for each state.
 *
 * @param state The AsyncState to render
 * @param onRetry Optional callback for retrying failed operations
 * @param modifier Modifier for the container
 * @param loadingContent Custom loading content (defaults to centered CircularProgressIndicator)
 * @param errorContent Custom error content (defaults to error message with retry button)
 * @param initialContent Custom initial content (defaults to nothing)
 * @param content Content to render when state is Success
 */
@Composable
fun <T> AsyncStateContent(
    state: AsyncState<T>,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    loadingContent: @Composable () -> Unit = { DefaultLoadingContent() },
    errorContent: @Composable (message: String, retryable: Boolean, onRetry: (() -> Unit)?) -> Unit = { message, retryable, retry ->
        DefaultErrorContent(message = message, retryable = retryable, onRetry = retry)
    },
    initialContent: @Composable () -> Unit = {},
    content: @Composable (T) -> Unit
) {
    Box(modifier = modifier) {
        when (state) {
            is AsyncState.Initial -> initialContent()
            is AsyncState.Loading -> loadingContent()
            is AsyncState.Success -> content(state.data)
            is AsyncState.Error -> errorContent(state.message, state.retryable, onRetry)
        }
    }
}

/**
 * Composable helper for rendering AuthAwareState with appropriate UI for auth states.
 *
 * @param state The AuthAwareState to render
 * @param onLogin Callback when user needs to log in
 * @param onRetry Optional callback for retrying failed data operations
 * @param modifier Modifier for the container
 * @param awaitingAuthContent Custom content while checking auth (defaults to loading)
 * @param authRequiredContent Custom content when auth is required (defaults to login prompt)
 * @param loadingContent Custom loading content for data loading
 * @param errorContent Custom error content for data errors
 * @param content Content to render when authenticated with successful data
 */
@Composable
fun <T> AuthAwareContent(
    state: AuthAwareState<T>,
    modifier: Modifier = Modifier,
    onLogin: () -> Unit = {},
    onRetry: (() -> Unit)? = null,
    awaitingAuthContent: @Composable () -> Unit = { DefaultLoadingContent() },
    authRequiredContent: @Composable (onLogin: () -> Unit) -> Unit = { login ->
        DefaultAuthRequiredContent(onLogin = login)
    },
    loadingContent: @Composable () -> Unit = { DefaultLoadingContent() },
    errorContent: @Composable (message: String, retryable: Boolean, onRetry: (() -> Unit)?) -> Unit = { message, retryable, retry ->
        DefaultErrorContent(message = message, retryable = retryable, onRetry = retry)
    },
    content: @Composable (T) -> Unit
) {
    Box(modifier = modifier) {
        when (state) {
            is AuthAwareState.AwaitingAuth -> awaitingAuthContent()
            is AuthAwareState.AuthRequired -> authRequiredContent(onLogin)
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
}

/**
 * Composable helper for rendering AuthAwareState with context about the authenticated user.
 *
 * @param state The AuthAwareState to render
 * @param onLogin Callback when user needs to log in
 * @param onRetry Optional callback for retrying failed data operations
 * @param modifier Modifier for the container
 * @param content Content to render with userId, isAnonymous flag, and data
 */
@Composable
fun <T> AuthAwareContentWithUser(
    state: AuthAwareState<T>,
    modifier: Modifier = Modifier,
    onLogin: () -> Unit = {},
    onRetry: (() -> Unit)? = null,
    awaitingAuthContent: @Composable () -> Unit = { DefaultLoadingContent() },
    authRequiredContent: @Composable (onLogin: () -> Unit) -> Unit = { login ->
        DefaultAuthRequiredContent(onLogin = login)
    },
    loadingContent: @Composable () -> Unit = { DefaultLoadingContent() },
    errorContent: @Composable (message: String, retryable: Boolean, onRetry: (() -> Unit)?) -> Unit = { message, retryable, retry ->
        DefaultErrorContent(message = message, retryable = retryable, onRetry = retry)
    },
    content: @Composable (userId: String, isAnonymous: Boolean, data: T) -> Unit
) {
    Box(modifier = modifier) {
        when (state) {
            is AuthAwareState.AwaitingAuth -> awaitingAuthContent()
            is AuthAwareState.AuthRequired -> authRequiredContent(onLogin)
            is AuthAwareState.Authenticated -> {
                when (val data = state.data) {
                    is AsyncState.Initial -> {}
                    is AsyncState.Loading -> loadingContent()
                    is AsyncState.Success -> content(state.userId, state.isAnonymous, data.data)
                    is AsyncState.Error -> errorContent(data.message, data.retryable, onRetry)
                }
            }
        }
    }
}

/**
 * Composable that shows content only when authenticated (regardless of data load state).
 * Useful for showing partial UI while data is still loading.
 */
@Composable
fun <T> AuthenticatedOnly(
    state: AuthAwareState<T>,
    modifier: Modifier = Modifier,
    onLogin: () -> Unit = {},
    authRequiredContent: @Composable (onLogin: () -> Unit) -> Unit = { login ->
        DefaultAuthRequiredContent(onLogin = login)
    },
    content: @Composable (userId: String, isAnonymous: Boolean, asyncState: AsyncState<T>) -> Unit
) {
    Box(modifier = modifier) {
        when (state) {
            is AuthAwareState.AwaitingAuth -> DefaultLoadingContent()
            is AuthAwareState.AuthRequired -> authRequiredContent(onLogin)
            is AuthAwareState.Authenticated -> content(state.userId, state.isAnonymous, state.data)
        }
    }
}

/**
 * Composable for AsyncStateWithCache that shows cached data while refreshing.
 */
@Composable
fun <T> CachedAsyncStateContent(
    state: AsyncStateWithCache<T>,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    refreshingOverlay: @Composable () -> Unit = { DefaultRefreshingOverlay() },
    loadingContent: @Composable () -> Unit = { DefaultLoadingContent() },
    errorContent: @Composable (message: String, retryable: Boolean, cachedData: T?, onRetry: (() -> Unit)?) -> Unit = { message, retryable, cached, retry ->
        if (cached != null) {
            // Show cached data with error banner
            DefaultErrorBanner(message = message, retryable = retryable, onRetry = retry)
        } else {
            DefaultErrorContent(message = message, retryable = retryable, onRetry = retry)
        }
    },
    content: @Composable (T) -> Unit
) {
    Box(modifier = modifier) {
        val displayData = state.getDisplayData()

        when {
            state.isRefreshing && displayData != null -> {
                // Show cached data with refreshing indicator
                content(displayData)
                refreshingOverlay()
            }
            state.state is AsyncState.Success -> {
                content(state.state.data)
            }
            state.state is AsyncState.Loading && displayData == null -> {
                loadingContent()
            }
            state.state is AsyncState.Error -> {
                errorContent(state.state.message, state.state.retryable, state.cachedData, onRetry)
            }
            else -> {
                // Initial state
            }
        }
    }
}

// ===== Default UI Components =====

@Composable
private fun DefaultLoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun DefaultErrorContent(
    message: String,
    retryable: Boolean,
    onRetry: (() -> Unit)?
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.error
            )
            if (retryable && onRetry != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onRetry) {
                    Text("Retry")
                }
            }
        }
    }
}

@Composable
private fun DefaultAuthRequiredContent(onLogin: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Please log in to continue",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onLogin) {
                Text("Log In")
            }
        }
    }
}

@Composable
private fun DefaultRefreshingOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            strokeWidth = 2.dp
        )
    }
}

@Composable
private fun DefaultErrorBanner(
    message: String,
    retryable: Boolean,
    onRetry: (() -> Unit)?
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            if (retryable && onRetry != null) {
                Button(onClick = onRetry) {
                    Text("Retry")
                }
            }
        }
    }
}
