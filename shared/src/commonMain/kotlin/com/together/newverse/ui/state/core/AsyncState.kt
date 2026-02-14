package com.together.newverse.ui.state.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/**
 * Generic sealed interface representing the state of an asynchronous operation.
 * Provides a type-safe way to handle loading, success, and error states.
 *
 * @param T The type of data held in the Success state
 */
sealed interface AsyncState<out T> {
    /**
     * Initial state before any operation has started.
     */
    data object Initial : AsyncState<Nothing>

    /**
     * Operation is in progress.
     */
    data object Loading : AsyncState<Nothing>

    /**
     * Operation completed successfully with data.
     */
    data class Success<T>(val data: T) : AsyncState<T>

    /**
     * Operation failed with an error.
     */
    data class Error(
        val message: String,
        val cause: Throwable? = null,
        val retryable: Boolean = true
    ) : AsyncState<Nothing>

    /**
     * Returns the data if this is a Success state, null otherwise.
     */
    fun getOrNull(): T? = (this as? Success)?.data

    /**
     * Returns the data if this is a Success state, or the default value otherwise.
     */
    fun getOrDefault(default: @UnsafeVariance T): T = getOrNull() ?: default

    /**
     * Returns the data if this is a Success state, or throws the error if Error state.
     * @throws IllegalStateException if neither Success nor Error
     */
    fun getOrThrow(): T = when (this) {
        is Success -> data
        is Error -> throw cause ?: IllegalStateException(message)
        else -> throw IllegalStateException("Cannot get value from $this")
    }

    /**
     * Returns true if this is a Loading state.
     */
    val isLoading: Boolean get() = this is Loading

    /**
     * Returns true if this is a Success state.
     */
    val isSuccess: Boolean get() = this is Success

    /**
     * Returns true if this is an Error state.
     */
    val isError: Boolean get() = this is Error

    /**
     * Returns true if this is the Initial state.
     */
    val isInitial: Boolean get() = this is Initial
}

/**
 * Transforms the data in a Success state using the given function.
 * Other states are passed through unchanged.
 */
inline fun <T, R> AsyncState<T>.map(transform: (T) -> R): AsyncState<R> = when (this) {
    is AsyncState.Initial -> AsyncState.Initial
    is AsyncState.Loading -> AsyncState.Loading
    is AsyncState.Success -> AsyncState.Success(transform(data))
    is AsyncState.Error -> this
}

/**
 * Transforms the data in a Success state using the given function that returns another AsyncState.
 * Useful for chaining dependent async operations.
 */
inline fun <T, R> AsyncState<T>.flatMap(transform: (T) -> AsyncState<R>): AsyncState<R> = when (this) {
    is AsyncState.Initial -> AsyncState.Initial
    is AsyncState.Loading -> AsyncState.Loading
    is AsyncState.Success -> transform(data)
    is AsyncState.Error -> this
}

/**
 * Executes the given block if this is a Success state.
 */
inline fun <T> AsyncState<T>.onSuccess(block: (T) -> Unit): AsyncState<T> {
    if (this is AsyncState.Success) block(data)
    return this
}

/**
 * Executes the given block if this is an Error state.
 */
inline fun <T> AsyncState<T>.onError(block: (AsyncState.Error) -> Unit): AsyncState<T> {
    if (this is AsyncState.Error) block(this)
    return this
}

/**
 * Executes the given block if this is a Loading state.
 */
inline fun <T> AsyncState<T>.onLoading(block: () -> Unit): AsyncState<T> {
    if (this is AsyncState.Loading) block()
    return this
}

/**
 * Recovers from an Error state by providing an alternative value.
 */
inline fun <T> AsyncState<T>.recover(recovery: (AsyncState.Error) -> T): AsyncState<T> = when (this) {
    is AsyncState.Error -> AsyncState.Success(recovery(this))
    else -> this
}

/**
 * Recovers from an Error state by providing an alternative AsyncState.
 */
inline fun <T> AsyncState<T>.recoverWith(recovery: (AsyncState.Error) -> AsyncState<T>): AsyncState<T> = when (this) {
    is AsyncState.Error -> recovery(this)
    else -> this
}

// ===== Flow Extensions =====

/**
 * Converts a Flow<T> to Flow<AsyncState<T>>, automatically handling loading and error states.
 * Emits Loading first, then Success for each value, and Error if the flow throws.
 */
fun <T> Flow<T>.asAsyncState(): Flow<AsyncState<T>> = this
    .map<T, AsyncState<T>> { AsyncState.Success(it) }
    .onStart { emit(AsyncState.Loading) }
    .catch { emit(AsyncState.Error(it.message ?: "Unknown error", it)) }

/**
 * Converts a Flow<T> to Flow<AsyncState<T>> starting with Initial state.
 * Useful when you want to distinguish between "never loaded" and "loading".
 */
fun <T> Flow<T>.asAsyncStateWithInitial(): Flow<AsyncState<T>> = this
    .map<T, AsyncState<T>> { AsyncState.Success(it) }
    .catch { emit(AsyncState.Error(it.message ?: "Unknown error", it)) }

/**
 * Maps the data within Flow<AsyncState<T>> while preserving async state semantics.
 */
fun <T, R> Flow<AsyncState<T>>.mapData(transform: (T) -> R): Flow<AsyncState<R>> =
    map { it.map(transform) }

/**
 * Combines two AsyncState flows into a single flow.
 * The result is Loading if either is Loading, Error if either is Error,
 * and Success only when both are Success.
 */
fun <T1, T2, R> combineAsyncStates(
    state1: AsyncState<T1>,
    state2: AsyncState<T2>,
    transform: (T1, T2) -> R
): AsyncState<R> = when {
    state1 is AsyncState.Error -> state1
    state2 is AsyncState.Error -> state2
    state1 is AsyncState.Loading || state2 is AsyncState.Loading -> AsyncState.Loading
    state1 is AsyncState.Initial || state2 is AsyncState.Initial -> AsyncState.Initial
    state1 is AsyncState.Success && state2 is AsyncState.Success ->
        AsyncState.Success(transform(state1.data, state2.data))
    else -> AsyncState.Initial
}

/**
 * Combines three AsyncState values into a single AsyncState.
 */
fun <T1, T2, T3, R> combineAsyncStates(
    state1: AsyncState<T1>,
    state2: AsyncState<T2>,
    state3: AsyncState<T3>,
    transform: (T1, T2, T3) -> R
): AsyncState<R> = when {
    state1 is AsyncState.Error -> state1
    state2 is AsyncState.Error -> state2
    state3 is AsyncState.Error -> state3
    state1 is AsyncState.Loading || state2 is AsyncState.Loading || state3 is AsyncState.Loading -> AsyncState.Loading
    state1 is AsyncState.Initial || state2 is AsyncState.Initial || state3 is AsyncState.Initial -> AsyncState.Initial
    state1 is AsyncState.Success && state2 is AsyncState.Success && state3 is AsyncState.Success ->
        AsyncState.Success(transform(state1.data, state2.data, state3.data))
    else -> AsyncState.Initial
}
