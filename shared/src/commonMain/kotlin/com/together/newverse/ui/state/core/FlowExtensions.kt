@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.together.newverse.ui.state.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.scan

/**
 * Flow extension functions for working with AsyncState and AuthAwareState.
 */

/**
 * Chains a dependent async operation that only executes when the source is Success.
 * Useful for dependent data loading scenarios.
 *
 * Example:
 * ```
 * userFlow.asAsyncState()
 *     .thenLoad { user -> orderRepository.getOrdersForUser(user.id) }
 * ```
 */
fun <T, R> Flow<AsyncState<T>>.thenLoad(
    transform: suspend (T) -> Flow<R>
): Flow<AsyncState<R>> = flatMapLatest { state ->
    when (state) {
        is AsyncState.Initial -> flowOf(AsyncState.Initial)
        is AsyncState.Loading -> flowOf(AsyncState.Loading)
        is AsyncState.Error -> flowOf(state)
        is AsyncState.Success -> transform(state.data).asAsyncState()
    }
}

/**
 * Chains a dependent async operation with a suspend function instead of a Flow.
 */
fun <T, R> Flow<AsyncState<T>>.thenLoadSuspend(
    transform: suspend (T) -> R
): Flow<AsyncState<R>> = map { state ->
    when (state) {
        is AsyncState.Initial -> AsyncState.Initial
        is AsyncState.Loading -> AsyncState.Loading
        is AsyncState.Error -> state
        is AsyncState.Success -> try {
            AsyncState.Success(transform(state.data))
        } catch (e: Exception) {
            AsyncState.Error(e.message ?: "Error", e)
        }
    }
}

/**
 * Zips two AsyncState flows, emitting Success only when both are Success.
 * If either is Loading, emits Loading. If either is Error, emits that Error.
 */
fun <T1, T2, R> zipAsyncStates(
    flow1: Flow<AsyncState<T1>>,
    flow2: Flow<AsyncState<T2>>,
    transform: (T1, T2) -> R
): Flow<AsyncState<R>> = combine(flow1, flow2) { state1, state2 ->
    combineAsyncStates(state1, state2, transform)
}

/**
 * Zips three AsyncState flows.
 */
fun <T1, T2, T3, R> zipAsyncStates(
    flow1: Flow<AsyncState<T1>>,
    flow2: Flow<AsyncState<T2>>,
    flow3: Flow<AsyncState<T3>>,
    transform: (T1, T2, T3) -> R
): Flow<AsyncState<R>> = combine(flow1, flow2, flow3) { state1, state2, state3 ->
    combineAsyncStates(state1, state2, state3, transform)
}

/**
 * Caches the last successful value and returns it while loading new data.
 * This prevents UI flickering when refreshing data.
 *
 * Example:
 * ```
 * articlesFlow
 *     .asAsyncState()
 *     .cacheWhileLoading() // Shows last articles while refreshing
 * ```
 */
fun <T> Flow<AsyncState<T>>.cacheWhileLoading(): Flow<AsyncStateWithCache<T>> =
    scan(AsyncStateWithCache<T>(AsyncState.Initial, null)) { previous, current ->
        when (current) {
            is AsyncState.Success -> AsyncStateWithCache(current, current.data)
            is AsyncState.Loading -> AsyncStateWithCache(current, previous.cachedData)
            is AsyncState.Error -> AsyncStateWithCache(current, previous.cachedData)
            is AsyncState.Initial -> AsyncStateWithCache(current, previous.cachedData)
        }
    }

/**
 * Wrapper for AsyncState that includes cached data from previous successful loads.
 */
data class AsyncStateWithCache<T>(
    val state: AsyncState<T>,
    val cachedData: T?
) {
    /**
     * Returns the current data if Success, or cached data if Loading/Error with cache available.
     */
    fun getDisplayData(): T? = state.getOrNull() ?: cachedData

    /**
     * Returns true if we have data to display (either current or cached).
     */
    val hasDisplayData: Boolean get() = getDisplayData() != null

    /**
     * Returns true if currently loading but has cached data to show.
     */
    val isRefreshing: Boolean get() = state.isLoading && cachedData != null
}

/**
 * Converts AsyncStateWithCache back to simple AsyncState, using cached data for Loading state.
 */
fun <T> Flow<AsyncStateWithCache<T>>.useCacheForLoading(): Flow<AsyncState<T>> = map { cached ->
    when {
        cached.state is AsyncState.Loading && cached.cachedData != null ->
            AsyncState.Success(cached.cachedData)
        else -> cached.state
    }
}

/**
 * Performs a side effect on each successful emission.
 */
fun <T> Flow<AsyncState<T>>.onEachSuccess(action: suspend (T) -> Unit): Flow<AsyncState<T>> =
    onEach { state ->
        if (state is AsyncState.Success) {
            action(state.data)
        }
    }

/**
 * Performs a side effect on each error emission.
 */
fun <T> Flow<AsyncState<T>>.onEachError(
    action: suspend (message: String, cause: Throwable?) -> Unit
): Flow<AsyncState<T>> = onEach { state ->
    if (state is AsyncState.Error) {
        action(state.message, state.cause)
    }
}

/**
 * Maps error messages in an AsyncState flow.
 */
fun <T> Flow<AsyncState<T>>.mapError(
    transform: (String) -> String
): Flow<AsyncState<T>> = map { state ->
    when (state) {
        is AsyncState.Error -> AsyncState.Error(
            message = transform(state.message),
            cause = state.cause,
            retryable = state.retryable
        )
        else -> state
    }
}

/**
 * Provides a default value for errors, converting them to Success states.
 */
fun <T> Flow<AsyncState<T>>.defaultOnError(defaultValue: T): Flow<AsyncState<T>> = map { state ->
    when (state) {
        is AsyncState.Error -> AsyncState.Success(defaultValue)
        else -> state
    }
}

/**
 * Filters out Loading states, only emitting Initial, Success, or Error.
 * Useful when you don't want to show loading indicators for quick operations.
 */
fun <T> Flow<AsyncState<T>>.skipLoading(): Flow<AsyncState<T>> =
    filter { state -> state !is AsyncState.Loading }

/**
 * Maps the data inside AuthAwareState flows.
 */
fun <T, R> Flow<AuthAwareState<T>>.mapAuthData(
    transform: (T) -> R
): Flow<AuthAwareState<R>> = map { it.mapData(transform) }

/**
 * Combines two AuthAwareState flows.
 */
fun <T1, T2, R> combineAuthAwareFlows(
    flow1: Flow<AuthAwareState<T1>>,
    flow2: Flow<AuthAwareState<T2>>,
    transform: (T1, T2) -> R
): Flow<AuthAwareState<R>> = combine(flow1, flow2) { state1, state2 ->
    combineAuthAwareStates(state1, state2, transform)
}

/**
 * Executes a block only when the AuthAwareState becomes authenticated with success data.
 */
fun <T> Flow<AuthAwareState<T>>.onAuthenticated(
    action: suspend (userId: String, data: T) -> Unit
): Flow<AuthAwareState<T>> = onEach { state ->
    if (state is AuthAwareState.Authenticated && state.data is AsyncState.Success) {
        action(state.userId, state.data.data)
    }
}
