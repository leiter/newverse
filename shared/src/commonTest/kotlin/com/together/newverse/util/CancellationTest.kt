package com.together.newverse.util

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `catch (e: Exception)` cannot tell a cancelled coroutine from a failed one, because
 * `CancellationException` is an `Exception`. These pin both halves of what that costs
 * and what the guard changes -- see [rethrowIfCancellation].
 */
class CancellationTest {

    @Test
    fun `rethrowIfCancellation lets a real error past`() {
        val error = IllegalStateException("database unreachable")
        error.rethrowIfCancellation()  // returns, so the caller still handles it
    }

    @Test
    fun `rethrowIfCancellation rethrows a cancellation`() {
        assertFailsWith<CancellationException> {
            CancellationException("Job was cancelled").rethrowIfCancellation()
        }
    }

    @Test
    fun `runCatchingNonCancellation reports a real error as a failure`() {
        assertTrue(runCatchingNonCancellation { error("nope") }.isFailure)
    }

    @Test
    fun `runCatchingNonCancellation does not swallow a cancellation`() {
        assertFailsWith<CancellationException> {
            runCatchingNonCancellation { throw CancellationException("cancelled") }
        }
    }

    // The suspend-function half: this is the shape 143 repository methods are written
    // in, and the one where swallowing has a visible consequence. A cancelled call
    // returns Result.failure, so the caller shows an error for something that was
    // merely stopped -- and the cancellation never reaches the scope that asked for it.
    private suspend fun save(guarded: Boolean): Result<Unit> =
        try {
            while (true) yield()
            @Suppress("UNREACHABLE_CODE") Result.success(Unit)
        } catch (e: Exception) {
            if (guarded) e.rethrowIfCancellation()
            Result.failure(e)
        }

    @Test
    fun `an unguarded suspend function turns its own cancellation into a failure`() = runTest {
        var result: Result<Unit>? = null
        val job = launch(start = CoroutineStart.UNDISPATCHED) { result = save(guarded = false) }
        yield()
        job.cancel()
        job.join()

        // The call completed and reported failure. Nothing distinguishes this from the
        // database being unreachable, and the caller acts on it.
        assertTrue(result?.isFailure == true)
    }

    @Test
    fun `a guarded suspend function lets the cancellation past instead`() = runTest {
        var result: Result<Unit>? = null
        val job = launch(start = CoroutineStart.UNDISPATCHED) { result = save(guarded = true) }
        yield()
        job.cancel()
        job.join()

        assertEquals(null, result)
    }

    // The flow half. Measured 2026-11-16 rather than assumed: a fallback emitted from
    // the catch does NOT reach the collector -- `emit` into a cancelled collector
    // throws on its own -- so the wrong value never gets published. What the broad
    // catch actually costs here is that the error path runs at all: a Log.e line for a
    // navigation, and whatever else the handler does on its way.
    private fun orders(guarded: Boolean, onErrorPath: () -> Unit): Flow<List<String>> = flow {
        try {
            emit(listOf("order"))
            while (true) yield()
        } catch (e: Exception) {
            if (guarded) e.rethrowIfCancellation()
            onErrorPath()
            emit(emptyList())
        }
    }

    @Test
    fun `an unguarded flow runs its error path when the collector goes away`() = runTest {
        var errorPathRan = false
        val seen = mutableListOf<List<String>>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            orders(guarded = false) { errorPathRan = true }.collect { seen.add(it) }
        }
        yield()
        job.cancel()
        job.join()

        assertTrue(errorPathRan, "the catch treated the cancellation as a failure")
        // ...but the fallback it tried to emit was never delivered.
        assertEquals(listOf(listOf("order")), seen)
    }

    @Test
    fun `a guarded flow does not`() = runTest {
        var errorPathRan = false
        val seen = mutableListOf<List<String>>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            orders(guarded = true) { errorPathRan = true }.collect { seen.add(it) }
        }
        yield()
        job.cancel()
        job.join()

        assertFalse(errorPathRan, "leaving a screen is not an error")
        assertEquals(listOf(listOf("order")), seen)
    }
}
