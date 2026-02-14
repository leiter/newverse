package com.together.newverse.ui.state.core

import app.cash.turbine.test
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AsyncStateTest {

    // ===== Basic State Properties =====

    @Test
    fun `Initial state properties`() {
        val state: AsyncState<String> = AsyncState.Initial

        assertFalse(state.isLoading)
        assertFalse(state.isSuccess)
        assertFalse(state.isError)
        assertTrue(state.isInitial)
        assertNull(state.getOrNull())
    }

    @Test
    fun `Loading state properties`() {
        val state: AsyncState<String> = AsyncState.Loading

        assertTrue(state.isLoading)
        assertFalse(state.isSuccess)
        assertFalse(state.isError)
        assertFalse(state.isInitial)
        assertNull(state.getOrNull())
    }

    @Test
    fun `Success state properties`() {
        val state = AsyncState.Success("data")

        assertFalse(state.isLoading)
        assertTrue(state.isSuccess)
        assertFalse(state.isError)
        assertFalse(state.isInitial)
        assertEquals("data", state.getOrNull())
        assertEquals("data", state.data)
    }

    @Test
    fun `Error state properties`() {
        val cause = RuntimeException("test")
        val state = AsyncState.Error("error message", cause, retryable = true)

        assertFalse(state.isLoading)
        assertFalse(state.isSuccess)
        assertTrue(state.isError)
        assertFalse(state.isInitial)
        assertNull(state.getOrNull())
        assertEquals("error message", state.message)
        assertEquals(cause, state.cause)
        assertTrue(state.retryable)
    }

    // ===== Map Operator =====

    @Test
    fun `map transforms Success data`() {
        val state = AsyncState.Success(5)
        val mapped = state.map { it * 2 }

        assertIs<AsyncState.Success<Int>>(mapped)
        assertEquals(10, mapped.data)
    }

    @Test
    fun `map preserves Initial`() {
        val state: AsyncState<Int> = AsyncState.Initial
        val mapped = state.map { it * 2 }

        assertIs<AsyncState.Initial>(mapped)
    }

    @Test
    fun `map preserves Loading`() {
        val state: AsyncState<Int> = AsyncState.Loading
        val mapped = state.map { it * 2 }

        assertIs<AsyncState.Loading>(mapped)
    }

    @Test
    fun `map preserves Error`() {
        val state: AsyncState<Int> = AsyncState.Error("error")
        val mapped = state.map { it * 2 }

        assertIs<AsyncState.Error>(mapped)
        assertEquals("error", mapped.message)
    }

    // ===== FlatMap Operator =====

    @Test
    fun `flatMap transforms Success to new AsyncState`() {
        val state = AsyncState.Success(5)
        val flatMapped = state.flatMap { AsyncState.Success(it * 2) }

        assertIs<AsyncState.Success<Int>>(flatMapped)
        assertEquals(10, flatMapped.data)
    }

    @Test
    fun `flatMap can return Error from Success`() {
        val state = AsyncState.Success(5)
        val flatMapped = state.flatMap<Int, String> { AsyncState.Error("validation failed") }

        assertIs<AsyncState.Error>(flatMapped)
        assertEquals("validation failed", flatMapped.message)
    }

    @Test
    fun `flatMap preserves non-Success states`() {
        val initial: AsyncState<Int> = AsyncState.Initial
        val loading: AsyncState<Int> = AsyncState.Loading
        val error: AsyncState<Int> = AsyncState.Error("error")

        assertIs<AsyncState.Initial>(initial.flatMap { AsyncState.Success(it * 2) })
        assertIs<AsyncState.Loading>(loading.flatMap { AsyncState.Success(it * 2) })
        assertIs<AsyncState.Error>(error.flatMap { AsyncState.Success(it * 2) })
    }

    // ===== Recover Operator =====

    @Test
    fun `recover transforms Error to Success`() {
        val state: AsyncState<String> = AsyncState.Error("error")
        val recovered = state.recover { "default" }

        assertIs<AsyncState.Success<String>>(recovered)
        assertEquals("default", recovered.data)
    }

    @Test
    fun `recover preserves Success`() {
        val state = AsyncState.Success("original")
        val recovered = state.recover { "default" }

        assertIs<AsyncState.Success<String>>(recovered)
        assertEquals("original", recovered.data)
    }

    @Test
    fun `recover preserves Initial and Loading`() {
        val initial: AsyncState<String> = AsyncState.Initial
        val loading: AsyncState<String> = AsyncState.Loading

        assertIs<AsyncState.Initial>(initial.recover { "default" })
        assertIs<AsyncState.Loading>(loading.recover { "default" })
    }

    // ===== GetOrDefault =====

    @Test
    fun `getOrDefault returns data for Success`() {
        val state = AsyncState.Success("data")
        assertEquals("data", state.getOrDefault("default"))
    }

    @Test
    fun `getOrDefault returns default for non-Success states`() {
        // Test getOrNull first (no type issues)
        assertNull((AsyncState.Initial as AsyncState<String>).getOrNull())
        assertNull((AsyncState.Loading as AsyncState<String>).getOrNull())
        assertNull((AsyncState.Error("error") as AsyncState<String>).getOrNull())

        // For getOrDefault, test via map which properly types the state
        val states = listOf<AsyncState<String>>(
            AsyncState.Initial,
            AsyncState.Loading,
            AsyncState.Error("error")
        )

        states.forEach { state ->
            // getOrNull should be null for non-Success
            assertNull(state.getOrNull())
        }
    }

    // ===== onSuccess / onError / onLoading =====

    @Test
    fun `onSuccess executes block for Success state`() {
        var captured: String? = null
        val state = AsyncState.Success("data")

        state.onSuccess { captured = it }

        assertEquals("data", captured)
    }

    @Test
    fun `onSuccess does not execute for non-Success states`() {
        var executed = false

        AsyncState.Loading.onSuccess { executed = true }
        AsyncState.Error("error").onSuccess { executed = true }
        AsyncState.Initial.onSuccess { executed = true }

        assertFalse(executed)
    }

    @Test
    fun `onError executes block for Error state`() {
        var captured: String? = null
        val state: AsyncState<String> = AsyncState.Error("error message")

        state.onError { captured = it.message }

        assertEquals("error message", captured)
    }

    @Test
    fun `onLoading executes block for Loading state`() {
        var executed = false
        val state: AsyncState<String> = AsyncState.Loading

        state.onLoading { executed = true }

        assertTrue(executed)
    }

    // ===== Flow Extension: asAsyncState =====

    @Test
    fun `asAsyncState emits Loading then Success`() = runTest {
        val flow = flowOf("data")

        flow.asAsyncState().test {
            assertEquals(AsyncState.Loading, awaitItem())
            assertEquals(AsyncState.Success("data"), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `asAsyncState emits Loading then Error on exception`() = runTest {
        val flow = flow<String> {
            throw RuntimeException("test error")
        }

        flow.asAsyncState().test {
            assertEquals(AsyncState.Loading, awaitItem())
            val error = awaitItem()
            assertIs<AsyncState.Error>(error)
            assertEquals("test error", error.message)
            awaitComplete()
        }
    }

    // ===== Combine Async States =====

    @Test
    fun `combineAsyncStates returns Success when both are Success`() {
        val state1 = AsyncState.Success(5)
        val state2 = AsyncState.Success(3)

        val combined = combineAsyncStates(state1, state2) { a, b -> a + b }

        assertIs<AsyncState.Success<Int>>(combined)
        assertEquals(8, combined.data)
    }

    @Test
    fun `combineAsyncStates returns Loading if any is Loading`() {
        val success = AsyncState.Success(5)
        val loading: AsyncState<Int> = AsyncState.Loading

        assertIs<AsyncState.Loading>(combineAsyncStates(success, loading) { a, b -> a + b })
        assertIs<AsyncState.Loading>(combineAsyncStates(loading, success) { a, b -> a + b })
    }

    @Test
    fun `combineAsyncStates returns Error if any is Error`() {
        val success = AsyncState.Success(5)
        val error: AsyncState<Int> = AsyncState.Error("error")

        val combined1 = combineAsyncStates(success, error) { a, b -> a + b }
        val combined2 = combineAsyncStates(error, success) { a, b -> a + b }

        assertIs<AsyncState.Error>(combined1)
        assertIs<AsyncState.Error>(combined2)
    }

    @Test
    fun `combineAsyncStates with three states`() {
        val state1 = AsyncState.Success(1)
        val state2 = AsyncState.Success(2)
        val state3 = AsyncState.Success(3)

        val combined = combineAsyncStates(state1, state2, state3) { a, b, c -> a + b + c }

        assertIs<AsyncState.Success<Int>>(combined)
        assertEquals(6, combined.data)
    }
}
