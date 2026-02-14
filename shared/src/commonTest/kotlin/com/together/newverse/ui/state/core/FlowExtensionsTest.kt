package com.together.newverse.ui.state.core

import app.cash.turbine.test
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FlowExtensionsTest {

    // ===== thenLoad =====

    @Test
    fun `thenLoad chains flows when Success`() = runTest {
        val source = flowOf(AsyncState.Success(5))

        source.thenLoad { value ->
            flowOf(value * 2)
        }.test {
            // thenLoad uses asAsyncState() which emits Loading first
            val loading = awaitItem()
            assertIs<AsyncState.Loading>(loading)

            val result = awaitItem()
            assertIs<AsyncState.Success<Int>>(result)
            assertEquals(10, result.data)
            awaitComplete()
        }
    }

    @Test
    fun `thenLoad preserves Initial`() = runTest {
        val source = flowOf<AsyncState<Int>>(AsyncState.Initial)

        source.thenLoad { value ->
            flowOf(value * 2)
        }.test {
            assertIs<AsyncState.Initial>(awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `thenLoad preserves Loading`() = runTest {
        val source = flowOf<AsyncState<Int>>(AsyncState.Loading)

        source.thenLoad { value ->
            flowOf(value * 2)
        }.test {
            assertIs<AsyncState.Loading>(awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `thenLoad preserves Error`() = runTest {
        val source = flowOf<AsyncState<Int>>(AsyncState.Error("error"))

        source.thenLoad { value ->
            flowOf(value * 2)
        }.test {
            val result = awaitItem()
            assertIs<AsyncState.Error>(result)
            assertEquals("error", result.message)
            awaitComplete()
        }
    }

    // ===== thenLoadSuspend =====

    @Test
    fun `thenLoadSuspend transforms Success with suspend function`() = runTest {
        val source = flowOf(AsyncState.Success(5))

        source.thenLoadSuspend { value ->
            value * 2
        }.test {
            val result = awaitItem()
            assertIs<AsyncState.Success<Int>>(result)
            assertEquals(10, result.data)
            awaitComplete()
        }
    }

    @Test
    fun `thenLoadSuspend catches exceptions`() = runTest {
        val source = flowOf(AsyncState.Success(5))

        source.thenLoadSuspend<Int, Int> { _ ->
            throw RuntimeException("transform failed")
        }.test {
            val result = awaitItem()
            assertIs<AsyncState.Error>(result)
            assertEquals("transform failed", result.message)
            awaitComplete()
        }
    }

    // ===== zipAsyncStates =====

    @Test
    fun `zipAsyncStates combines two Success flows`() = runTest {
        val flow1 = flowOf(AsyncState.Success(5))
        val flow2 = flowOf(AsyncState.Success(3))

        zipAsyncStates(flow1, flow2) { a, b -> a + b }.test {
            val result = awaitItem()
            assertIs<AsyncState.Success<Int>>(result)
            assertEquals(8, result.data)
            awaitComplete()
        }
    }

    @Test
    fun `zipAsyncStates returns Loading if any is Loading`() = runTest {
        val flow1 = flowOf(AsyncState.Success(5))
        val flow2 = flowOf<AsyncState<Int>>(AsyncState.Loading)

        zipAsyncStates(flow1, flow2) { a, b -> a + b }.test {
            assertIs<AsyncState.Loading>(awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `zipAsyncStates returns Error if any is Error`() = runTest {
        val flow1 = flowOf(AsyncState.Success(5))
        val flow2 = flowOf<AsyncState<Int>>(AsyncState.Error("error"))

        zipAsyncStates(flow1, flow2) { a, b -> a + b }.test {
            val result = awaitItem()
            assertIs<AsyncState.Error>(result)
            assertEquals("error", result.message)
            awaitComplete()
        }
    }

    @Test
    fun `zipAsyncStates with three flows`() = runTest {
        val flow1 = flowOf(AsyncState.Success(1))
        val flow2 = flowOf(AsyncState.Success(2))
        val flow3 = flowOf(AsyncState.Success(3))

        zipAsyncStates(flow1, flow2, flow3) { a, b, c -> a + b + c }.test {
            val result = awaitItem()
            assertIs<AsyncState.Success<Int>>(result)
            assertEquals(6, result.data)
            awaitComplete()
        }
    }

    // ===== cacheWhileLoading =====

    @Test
    fun `cacheWhileLoading caches last Success during Loading`() = runTest {
        val source = MutableStateFlow<AsyncState<String>>(AsyncState.Success("cached"))

        source.cacheWhileLoading().test {
            // scan emits initial accumulator first
            val initial = awaitItem()
            assertIs<AsyncState.Initial>(initial.state)
            assertNull(initial.cachedData)

            // Then the first Success
            val success = awaitItem()
            assertEquals("cached", success.getDisplayData())
            assertFalse(success.isRefreshing)

            // Transition to Loading - should still have cached data
            source.value = AsyncState.Loading
            val loading = awaitItem()
            assertEquals("cached", loading.getDisplayData())
            assertTrue(loading.isRefreshing)

            // New success
            source.value = AsyncState.Success("new data")
            val newSuccess = awaitItem()
            assertEquals("new data", newSuccess.getDisplayData())
            assertFalse(newSuccess.isRefreshing)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `cacheWhileLoading preserves cache during Error`() = runTest {
        val source = MutableStateFlow<AsyncState<String>>(AsyncState.Success("cached"))

        source.cacheWhileLoading().test {
            awaitItem() // Initial accumulator
            awaitItem() // Initial success

            source.value = AsyncState.Error("error")
            val error = awaitItem()
            assertEquals("cached", error.cachedData)
            assertIs<AsyncState.Error>(error.state)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `AsyncStateWithCache hasDisplayData`() {
        val withData = AsyncStateWithCache(AsyncState.Success("data"), "data")
        val loadingWithCache = AsyncStateWithCache<String>(AsyncState.Loading, "cached")
        val loadingNoCache = AsyncStateWithCache<String>(AsyncState.Loading, null)

        assertTrue(withData.hasDisplayData)
        assertTrue(loadingWithCache.hasDisplayData)
        assertFalse(loadingNoCache.hasDisplayData)
    }

    // ===== useCacheForLoading =====

    @Test
    fun `useCacheForLoading converts cached Loading to Success`() = runTest {
        val cached = AsyncStateWithCache<String>(AsyncState.Loading, "cached")

        flowOf(cached).useCacheForLoading().test {
            val result = awaitItem()
            assertIs<AsyncState.Success<String>>(result)
            assertEquals("cached", result.data)
            awaitComplete()
        }
    }

    @Test
    fun `useCacheForLoading preserves Loading without cache`() = runTest {
        val noCache = AsyncStateWithCache<String>(AsyncState.Loading, null)

        flowOf(noCache).useCacheForLoading().test {
            assertIs<AsyncState.Loading>(awaitItem())
            awaitComplete()
        }
    }

    // ===== onEachSuccess =====

    @Test
    fun `onEachSuccess executes action on Success`() = runTest {
        var capturedValue: String? = null
        val source = flowOf(AsyncState.Success("data"))

        source.onEachSuccess { capturedValue = it }.test {
            awaitItem()
            awaitComplete()
        }

        assertEquals("data", capturedValue)
    }

    @Test
    fun `onEachSuccess does not execute on Error`() = runTest {
        var actionCalled = false
        val source = flowOf<AsyncState<String>>(AsyncState.Error("error"))

        source.onEachSuccess { actionCalled = true }.test {
            awaitItem()
            awaitComplete()
        }

        assertFalse(actionCalled)
    }

    // ===== onEachError =====

    @Test
    fun `onEachError executes action on Error`() = runTest {
        var capturedMessage: String? = null
        val source = flowOf<AsyncState<String>>(AsyncState.Error("error message"))

        source.onEachError { message, _ -> capturedMessage = message }.test {
            awaitItem()
            awaitComplete()
        }

        assertEquals("error message", capturedMessage)
    }

    // ===== mapError =====

    @Test
    fun `mapError transforms error message`() = runTest {
        val source = flowOf<AsyncState<String>>(AsyncState.Error("original"))

        source.mapError { "Modified: $it" }.test {
            val result = awaitItem()
            assertIs<AsyncState.Error>(result)
            assertEquals("Modified: original", result.message)
            awaitComplete()
        }
    }

    @Test
    fun `mapError preserves Success`() = runTest {
        val source = flowOf(AsyncState.Success("data"))

        source.mapError { "Modified: $it" }.test {
            val result = awaitItem()
            assertIs<AsyncState.Success<String>>(result)
            assertEquals("data", result.data)
            awaitComplete()
        }
    }

    // ===== defaultOnError =====

    @Test
    fun `defaultOnError converts Error to Success with default`() = runTest {
        val source = flowOf<AsyncState<String>>(AsyncState.Error("error"))

        source.defaultOnError("default").test {
            val result = awaitItem()
            assertIs<AsyncState.Success<String>>(result)
            assertEquals("default", result.data)
            awaitComplete()
        }
    }

    @Test
    fun `defaultOnError preserves Success`() = runTest {
        val source = flowOf(AsyncState.Success("original"))

        source.defaultOnError("default").test {
            val result = awaitItem()
            assertIs<AsyncState.Success<String>>(result)
            assertEquals("original", result.data)
            awaitComplete()
        }
    }

    // ===== skipLoading =====

    @Test
    fun `skipLoading filters out Loading states`() = runTest {
        val source = flow {
            emit(AsyncState.Initial)
            emit(AsyncState.Loading)
            emit(AsyncState.Success("data"))
            emit(AsyncState.Loading)
            emit(AsyncState.Error("error"))
        }

        source.skipLoading().test {
            assertIs<AsyncState.Initial>(awaitItem())
            assertIs<AsyncState.Success<String>>(awaitItem())
            assertIs<AsyncState.Error>(awaitItem())
            awaitComplete()
        }
    }
}
