package com.together.newverse.ui.state

import com.together.newverse.ui.state.core.AsyncState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Tests for ListingState and its conversion to AsyncState
 */
class ListingStateTest {

    // ===== toAsyncState() Converter Tests =====

    @Test
    fun `toAsyncState returns Loading when isLoading is true`() {
        val state = ListingState<String>(isLoading = true)
        val asyncState = state.toAsyncState()

        assertIs<AsyncState.Loading>(asyncState)
    }

    @Test
    fun `toAsyncState returns Error when error is present`() {
        val error = ErrorState(
            message = "Something went wrong",
            type = ErrorType.NETWORK,
            retryable = true
        )
        val state = ListingState<String>(error = error)
        val asyncState = state.toAsyncState()

        assertIs<AsyncState.Error>(asyncState)
        assertEquals("Something went wrong", asyncState.message)
        assertTrue(asyncState.retryable)
    }

    @Test
    fun `toAsyncState returns Error with retryable false when error is not retryable`() {
        val error = ErrorState(
            message = "Authentication failed",
            type = ErrorType.AUTHENTICATION,
            retryable = false
        )
        val state = ListingState<String>(error = error)
        val asyncState = state.toAsyncState()

        assertIs<AsyncState.Error>(asyncState)
        assertEquals("Authentication failed", asyncState.message)
        assertEquals(false, asyncState.retryable)
    }

    @Test
    fun `toAsyncState returns Success with items when not loading and no error`() {
        val items = listOf("item1", "item2", "item3")
        val state = ListingState(items = items)
        val asyncState = state.toAsyncState()

        assertIs<AsyncState.Success<List<String>>>(asyncState)
        assertEquals(3, asyncState.data.size)
        assertEquals(items, asyncState.data)
    }

    @Test
    fun `toAsyncState returns Success with empty list when no items`() {
        val state = ListingState<String>(items = emptyList())
        val asyncState = state.toAsyncState()

        assertIs<AsyncState.Success<List<String>>>(asyncState)
        assertTrue(asyncState.data.isEmpty())
    }

    @Test
    fun `toAsyncState prioritizes loading over success`() {
        // When loading is true, should return Loading even if items are present
        val state = ListingState(
            isLoading = true,
            items = listOf("item1", "item2")
        )
        val asyncState = state.toAsyncState()

        assertIs<AsyncState.Loading>(asyncState)
    }

    @Test
    fun `toAsyncState prioritizes loading over error`() {
        // When loading is true, should return Loading even if error is present
        val state = ListingState<String>(
            isLoading = true,
            error = ErrorState("Error", type = ErrorType.GENERAL)
        )
        val asyncState = state.toAsyncState()

        assertIs<AsyncState.Loading>(asyncState)
    }

    @Test
    fun `toAsyncState prioritizes error over success when not loading`() {
        // When error is present and not loading, should return Error even if items are present
        val state = ListingState(
            isLoading = false,
            error = ErrorState("Network error", type = ErrorType.NETWORK),
            items = listOf("stale item")
        )
        val asyncState = state.toAsyncState()

        assertIs<AsyncState.Error>(asyncState)
        assertEquals("Network error", asyncState.message)
    }

    // ===== Type Alias Tests =====

    @Test
    fun `OrderHistoryScreenState type alias works correctly`() {
        val state: OrderHistoryScreenState = ListingState(
            isLoading = false,
            items = emptyList()
        )

        assertIs<ListingState<com.together.newverse.domain.model.Order>>(state)
    }

    @Test
    fun `ProductsScreenState type alias works correctly`() {
        val state: ProductsScreenState = ListingState(
            isLoading = true
        )

        assertIs<ListingState<com.together.newverse.domain.model.Article>>(state)
    }

    @Test
    fun `OrdersScreenState type alias works correctly`() {
        val state: OrdersScreenState = ListingState(
            error = ErrorState("Server error", type = ErrorType.SERVER)
        )

        assertIs<ListingState<com.together.newverse.domain.model.Order>>(state)
    }
}
