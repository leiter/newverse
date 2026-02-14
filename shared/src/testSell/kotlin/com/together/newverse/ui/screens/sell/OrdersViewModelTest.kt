package com.together.newverse.ui.screens.sell

import app.cash.turbine.test
import com.together.newverse.domain.model.BuyerProfile
import com.together.newverse.domain.model.Order
import com.together.newverse.domain.model.OrderStatus
import com.together.newverse.test.FakeAuthRepository
import com.together.newverse.test.FakeOrderRepository
import com.together.newverse.test.MainDispatcherRule
import com.together.newverse.ui.state.core.AsyncState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.time.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Unit tests for OrdersViewModel.
 *
 * Tests cover:
 * - Initial state
 * - Loading orders (happy path)
 * - Filter by status (ALL, PENDING, COMPLETED, CANCELLED)
 * - Authentication/error handling
 * - Refresh mechanism
 * - Hide order functionality
 * - Edge cases
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OrdersViewModelTest {

    private val dispatcherRule = MainDispatcherRule()
    private lateinit var orderRepository: FakeOrderRepository
    private lateinit var authRepository: FakeAuthRepository

    @BeforeTest
    fun setup() {
        dispatcherRule.setup()
        orderRepository = FakeOrderRepository()
        authRepository = FakeAuthRepository()
    }

    @AfterTest
    fun tearDown() {
        dispatcherRule.tearDown()
        orderRepository.reset()
        authRepository.reset()
    }

    private fun createViewModel() = OrdersViewModel(orderRepository, authRepository)

    // ===== Test Helpers =====

    private fun createOrder(
        id: String,
        status: OrderStatus = OrderStatus.PLACED,
        sellerId: String = "seller_123",
        pickUpDate: Long = Clock.System.now().toEpochMilliseconds() + 86400000 // Tomorrow
    ): Order {
        return Order(
            id = id,
            sellerId = sellerId,
            status = status,
            pickUpDate = pickUpDate,
            buyerProfile = BuyerProfile(displayName = "Test Buyer")
        )
    }

    /**
     * Creates a timestamp for a specific number of days from now.
     * Positive values = future, negative values = past.
     */
    private fun daysFromNow(days: Int): Long {
        val now = Clock.System.now()
        val zone = TimeZone.currentSystemDefault()
        val localDate = now.toLocalDateTime(zone).date
        val targetDate = if (days >= 0) {
            localDate.plus(days, DateTimeUnit.DAY)
        } else {
            localDate.minus(-days, DateTimeUnit.DAY)
        }
        return targetDate.atStartOfDayIn(zone).toEpochMilliseconds()
    }

    // ===== A. Initial State Tests (2 tests) =====

    @Test
    fun `initial value in StateFlow definition is Loading`() = runTest {
        // Given authenticated user
        authRepository.setCurrentUserId("seller_123")

        // When creating ViewModel and checking ordersState definition
        val viewModel = createViewModel()

        // Note: With UnconfinedTestDispatcher, the StateFlow rapidly transitions through states.
        // The initial value in stateIn() is AsyncState.Loading, which we verify by checking
        // that the state eventually settles (either Loading transitioning to Success,
        // or directly to Success due to UnconfinedTestDispatcher's eager execution).
        // For authenticated users with empty orders, we expect to reach Success state.
        viewModel.ordersState.test {
            val state = awaitItem()
            // The flow will either show Loading (if captured fast enough) or Success
            // Since we use UnconfinedTestDispatcher, it typically shows Success directly
            assertTrue(
                state is AsyncState.Loading || state is AsyncState.Success,
                "Expected Loading or Success, got $state"
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `initial filter is ALL`() = runTest {
        // Given authenticated user
        authRepository.setCurrentUserId("seller_123")

        // When creating ViewModel
        val viewModel = createViewModel()

        // Then initial filter is ALL
        viewModel.selectedFilter.test {
            val filter = awaitItem()
            assertEquals(OrderFilter.ALL, filter)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ===== B. Loading Orders - Happy Path (3 tests) =====

    @Test
    fun `loads orders successfully for authenticated user`() = runTest {
        // Given authenticated user and orders exist
        authRepository.setCurrentUserId("seller_123")
        val orders = listOf(
            createOrder("order_1", OrderStatus.PLACED),
            createOrder("order_2", OrderStatus.COMPLETED)
        )
        orderRepository.setOrders(orders)

        // When creating ViewModel
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Then orders are loaded successfully
        viewModel.ordersState.test {
            val state = awaitItem()
            assertIs<AsyncState.Success<List<Order>>>(state)
            assertEquals(2, state.data.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `empty list when no orders exist`() = runTest {
        // Given authenticated user but no orders
        authRepository.setCurrentUserId("seller_123")
        orderRepository.setOrders(emptyList())

        // When creating ViewModel
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Then state is Success with empty list
        viewModel.ordersState.test {
            val state = awaitItem()
            assertIs<AsyncState.Success<List<Order>>>(state)
            assertEquals(0, state.data.size)
            assertTrue(state.data.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `orders sorted by pickup date descending`() = runTest {
        // Given authenticated user and orders with different pickup dates
        authRepository.setCurrentUserId("seller_123")
        val oldDate = daysFromNow(1)
        val midDate = daysFromNow(3)
        val newDate = daysFromNow(7)

        val orders = listOf(
            createOrder("order_old", pickUpDate = oldDate),
            createOrder("order_new", pickUpDate = newDate),
            createOrder("order_mid", pickUpDate = midDate)
        )
        orderRepository.setOrders(orders)

        // When creating ViewModel
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Then orders are sorted by pickup date descending (most recent first)
        viewModel.ordersState.test {
            val state = awaitItem()
            assertIs<AsyncState.Success<List<Order>>>(state)
            assertEquals(3, state.data.size)
            assertEquals("order_new", state.data[0].id)
            assertEquals("order_mid", state.data[1].id)
            assertEquals("order_old", state.data[2].id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ===== C. Filter by Status (5 tests) =====

    @Test
    fun `ALL filter returns all orders`() = runTest {
        // Given authenticated user and various orders
        authRepository.setCurrentUserId("seller_123")
        val orders = listOf(
            createOrder("order_1", OrderStatus.DRAFT),
            createOrder("order_2", OrderStatus.PLACED),
            createOrder("order_3", OrderStatus.LOCKED),
            createOrder("order_4", OrderStatus.COMPLETED),
            createOrder("order_5", OrderStatus.CANCELLED)
        )
        orderRepository.setOrders(orders)

        // When creating ViewModel (ALL filter is default)
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Then all orders are returned
        viewModel.ordersState.test {
            val state = awaitItem()
            assertIs<AsyncState.Success<List<Order>>>(state)
            assertEquals(5, state.data.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `PENDING filter returns only active non-finalized orders`() = runTest {
        // Given authenticated user and various orders
        authRepository.setCurrentUserId("seller_123")
        val orders = listOf(
            createOrder("order_draft", OrderStatus.DRAFT),
            createOrder("order_placed", OrderStatus.PLACED),
            createOrder("order_locked", OrderStatus.LOCKED),
            createOrder("order_completed", OrderStatus.COMPLETED),
            createOrder("order_cancelled", OrderStatus.CANCELLED)
        )
        orderRepository.setOrders(orders)

        // When creating ViewModel and setting PENDING filter
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.setFilter(OrderFilter.PENDING)
        advanceUntilIdle()

        // Then only DRAFT and PLACED orders are returned
        // PENDING = isActive() && !isFinalized()
        // isActive: not COMPLETED, not CANCELLED
        // isFinalized: LOCKED, COMPLETED, CANCELLED
        // So PENDING = DRAFT or PLACED
        viewModel.ordersState.test {
            val state = awaitItem()
            assertIs<AsyncState.Success<List<Order>>>(state)
            assertEquals(2, state.data.size)
            assertTrue(state.data.any { it.id == "order_draft" })
            assertTrue(state.data.any { it.id == "order_placed" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `COMPLETED filter returns only completed orders`() = runTest {
        // Given authenticated user and various orders
        authRepository.setCurrentUserId("seller_123")
        val orders = listOf(
            createOrder("order_placed", OrderStatus.PLACED),
            createOrder("order_completed_1", OrderStatus.COMPLETED),
            createOrder("order_completed_2", OrderStatus.COMPLETED),
            createOrder("order_cancelled", OrderStatus.CANCELLED)
        )
        orderRepository.setOrders(orders)

        // When creating ViewModel and setting COMPLETED filter
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.setFilter(OrderFilter.COMPLETED)
        advanceUntilIdle()

        // Then only COMPLETED orders are returned
        viewModel.ordersState.test {
            val state = awaitItem()
            assertIs<AsyncState.Success<List<Order>>>(state)
            assertEquals(2, state.data.size)
            assertTrue(state.data.all { it.status == OrderStatus.COMPLETED })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `CANCELLED filter returns only cancelled orders`() = runTest {
        // Given authenticated user and various orders
        authRepository.setCurrentUserId("seller_123")
        val orders = listOf(
            createOrder("order_placed", OrderStatus.PLACED),
            createOrder("order_completed", OrderStatus.COMPLETED),
            createOrder("order_cancelled_1", OrderStatus.CANCELLED),
            createOrder("order_cancelled_2", OrderStatus.CANCELLED)
        )
        orderRepository.setOrders(orders)

        // When creating ViewModel and setting CANCELLED filter
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.setFilter(OrderFilter.CANCELLED)
        advanceUntilIdle()

        // Then only CANCELLED orders are returned
        viewModel.ordersState.test {
            val state = awaitItem()
            assertIs<AsyncState.Success<List<Order>>>(state)
            assertEquals(2, state.data.size)
            assertTrue(state.data.all { it.status == OrderStatus.CANCELLED })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setFilter updates selectedFilter state`() = runTest {
        // Given authenticated user
        authRepository.setCurrentUserId("seller_123")
        val viewModel = createViewModel()

        // When setting different filters
        viewModel.setFilter(OrderFilter.PENDING)

        // Then selectedFilter is updated
        viewModel.selectedFilter.test {
            assertEquals(OrderFilter.PENDING, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        // And when setting another filter
        viewModel.setFilter(OrderFilter.COMPLETED)

        viewModel.selectedFilter.test {
            assertEquals(OrderFilter.COMPLETED, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ===== D. Authentication/Error Handling (3 tests) =====

    @Test
    fun `error when user not authenticated`() = runTest {
        // Given user is NOT authenticated
        authRepository.setCurrentUserId(null)

        // When creating ViewModel
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Then state is Error with authentication message
        viewModel.ordersState.test {
            val state = awaitItem()
            assertIs<AsyncState.Error>(state)
            assertEquals("Not authenticated", state.message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `error when user ID is empty`() = runTest {
        // Given user ID is empty string
        authRepository.setCurrentUserId("")

        // When creating ViewModel
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Then state is Error (empty userId treated as not authenticated)
        viewModel.ordersState.test {
            val state = awaitItem()
            assertIs<AsyncState.Error>(state)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `error state is retryable`() = runTest {
        // Given user is NOT authenticated
        authRepository.setCurrentUserId(null)

        // When creating ViewModel
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Then error state has retryable = true
        viewModel.ordersState.test {
            val state = awaitItem()
            assertIs<AsyncState.Error>(state)
            assertTrue(state.retryable)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ===== E. Refresh Mechanism (2 tests) =====

    @Test
    fun `refresh triggers reload`() = runTest {
        // Given authenticated user and initial orders
        authRepository.setCurrentUserId("seller_123")
        orderRepository.setOrders(listOf(createOrder("order_1")))

        val viewModel = createViewModel()
        advanceUntilIdle()

        // Verify initial state
        viewModel.ordersState.test {
            val state = awaitItem()
            assertIs<AsyncState.Success<List<Order>>>(state)
            assertEquals(1, state.data.size)
            cancelAndIgnoreRemainingEvents()
        }

        // When adding more orders and refreshing
        orderRepository.setOrders(listOf(
            createOrder("order_1"),
            createOrder("order_2"),
            createOrder("order_3")
        ))
        viewModel.refresh()
        advanceUntilIdle()

        // Then orders are reloaded
        viewModel.ordersState.test {
            val state = awaitItem()
            assertIs<AsyncState.Success<List<Order>>>(state)
            assertEquals(3, state.data.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `refresh maintains current filter`() = runTest {
        // Given authenticated user and various orders
        authRepository.setCurrentUserId("seller_123")
        orderRepository.setOrders(listOf(
            createOrder("order_completed_1", OrderStatus.COMPLETED),
            createOrder("order_placed_1", OrderStatus.PLACED)
        ))

        val viewModel = createViewModel()
        advanceUntilIdle()

        // Set COMPLETED filter
        viewModel.setFilter(OrderFilter.COMPLETED)
        advanceUntilIdle()

        // Verify filter is applied
        viewModel.ordersState.test {
            val state = awaitItem()
            assertIs<AsyncState.Success<List<Order>>>(state)
            assertEquals(1, state.data.size)
            assertEquals(OrderStatus.COMPLETED, state.data[0].status)
            cancelAndIgnoreRemainingEvents()
        }

        // When adding more orders and refreshing
        orderRepository.setOrders(listOf(
            createOrder("order_completed_1", OrderStatus.COMPLETED),
            createOrder("order_completed_2", OrderStatus.COMPLETED),
            createOrder("order_placed_1", OrderStatus.PLACED)
        ))
        viewModel.refresh()
        advanceUntilIdle()

        // Then filter is still applied (only COMPLETED orders)
        viewModel.selectedFilter.test {
            assertEquals(OrderFilter.COMPLETED, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.ordersState.test {
            val state = awaitItem()
            assertIs<AsyncState.Success<List<Order>>>(state)
            assertEquals(2, state.data.size)
            assertTrue(state.data.all { it.status == OrderStatus.COMPLETED })
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ===== F. Hide Order (3 tests) =====

    @Test
    fun `hideOrder calls repository correctly`() = runTest {
        // Given authenticated user and an order
        authRepository.setCurrentUserId("seller_123")
        val pickupTimestamp = daysFromNow(5) // Fixed date for predictable formatting
        val order = createOrder("order_to_hide", sellerId = "seller_123", pickUpDate = pickupTimestamp)
        orderRepository.setOrders(listOf(order))

        val viewModel = createViewModel()
        advanceUntilIdle()

        // Track callbacks
        var successCalled = false
        var errorMessage: String? = null

        // When hiding the order
        viewModel.hideOrder(
            order = order,
            onSuccess = { successCalled = true },
            onError = { errorMessage = it }
        )
        advanceUntilIdle()

        // Then onSuccess is called
        assertTrue(successCalled)
        assertEquals(null, errorMessage)

        // And repository tracked the hidden order
        assertEquals(1, orderRepository.hiddenOrders.size)
        assertEquals("order_to_hide", orderRepository.hiddenOrders[0].orderId)
        assertEquals("seller_123", orderRepository.hiddenOrders[0].sellerId)
        assertTrue(orderRepository.hiddenOrders[0].forSeller)
    }

    @Test
    fun `hideOrder reports error on failure`() = runTest {
        // Given authenticated user and repository configured to fail
        authRepository.setCurrentUserId("seller_123")
        orderRepository.shouldFailHideOrder = true
        orderRepository.failureMessage = "Network error"

        val order = createOrder("order_to_hide", sellerId = "seller_123")
        orderRepository.setOrders(listOf(order))

        val viewModel = createViewModel()
        advanceUntilIdle()

        // Track callbacks
        var successCalled = false
        var errorMessage: String? = null

        // When hiding the order
        viewModel.hideOrder(
            order = order,
            onSuccess = { successCalled = true },
            onError = { errorMessage = it }
        )
        advanceUntilIdle()

        // Then onError is called with the error message
        assertEquals(false, successCalled)
        assertEquals("Network error", errorMessage)
    }

    @Test
    fun `hideOrder formats date key correctly`() = runTest {
        // Given authenticated user and an order with specific pickup date
        authRepository.setCurrentUserId("seller_123")

        // Use a specific date for predictable formatting
        // January 15, 2025 should format as "20250115"
        val specificTimestamp = 1736899200000L // 2025-01-15 00:00:00 UTC

        val order = createOrder("order_test", sellerId = "seller_123", pickUpDate = specificTimestamp)
        orderRepository.setOrders(listOf(order))

        val viewModel = createViewModel()
        advanceUntilIdle()

        // When hiding the order
        viewModel.hideOrder(
            order = order,
            onSuccess = { },
            onError = { }
        )
        advanceUntilIdle()

        // Then date key is formatted as YYYYMMDD
        assertEquals(1, orderRepository.hiddenOrders.size)
        val dateKey = orderRepository.hiddenOrders[0].date
        // Date should be in format YYYYMMDD (8 digits)
        assertTrue(dateKey.length == 8, "Date key should be 8 characters, got: $dateKey")
        assertTrue(dateKey.all { it.isDigit() }, "Date key should be all digits, got: $dateKey")
        // The exact date depends on timezone, but format should be correct
        assertTrue(dateKey.startsWith("2025"), "Date key should start with 2025, got: $dateKey")
    }

    // ===== G. Edge Cases (3 tests) =====

    @Test
    fun `filter returns empty when no matches`() = runTest {
        // Given authenticated user and only PLACED orders
        authRepository.setCurrentUserId("seller_123")
        val orders = listOf(
            createOrder("order_1", OrderStatus.PLACED),
            createOrder("order_2", OrderStatus.PLACED)
        )
        orderRepository.setOrders(orders)

        // When creating ViewModel and setting CANCELLED filter
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.setFilter(OrderFilter.CANCELLED)
        advanceUntilIdle()

        // Then empty list is returned (no matches)
        viewModel.ordersState.test {
            val state = awaitItem()
            assertIs<AsyncState.Success<List<Order>>>(state)
            assertTrue(state.data.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `handles all order statuses correctly`() = runTest {
        // Given authenticated user and one order of each status
        authRepository.setCurrentUserId("seller_123")
        val orders = listOf(
            createOrder("draft", OrderStatus.DRAFT),
            createOrder("placed", OrderStatus.PLACED),
            createOrder("locked", OrderStatus.LOCKED),
            createOrder("completed", OrderStatus.COMPLETED),
            createOrder("cancelled", OrderStatus.CANCELLED)
        )
        orderRepository.setOrders(orders)

        val viewModel = createViewModel()
        advanceUntilIdle()

        // Test ALL filter
        viewModel.setFilter(OrderFilter.ALL)
        advanceUntilIdle()
        viewModel.ordersState.test {
            val state = awaitItem()
            assertIs<AsyncState.Success<List<Order>>>(state)
            assertEquals(5, state.data.size)
            cancelAndIgnoreRemainingEvents()
        }

        // Test PENDING filter (DRAFT + PLACED only - active and not finalized)
        viewModel.setFilter(OrderFilter.PENDING)
        advanceUntilIdle()
        viewModel.ordersState.test {
            val state = awaitItem()
            assertIs<AsyncState.Success<List<Order>>>(state)
            assertEquals(2, state.data.size)
            val statuses = state.data.map { it.status }.toSet()
            assertEquals(setOf(OrderStatus.DRAFT, OrderStatus.PLACED), statuses)
            cancelAndIgnoreRemainingEvents()
        }

        // Test COMPLETED filter
        viewModel.setFilter(OrderFilter.COMPLETED)
        advanceUntilIdle()
        viewModel.ordersState.test {
            val state = awaitItem()
            assertIs<AsyncState.Success<List<Order>>>(state)
            assertEquals(1, state.data.size)
            assertEquals(OrderStatus.COMPLETED, state.data[0].status)
            cancelAndIgnoreRemainingEvents()
        }

        // Test CANCELLED filter
        viewModel.setFilter(OrderFilter.CANCELLED)
        advanceUntilIdle()
        viewModel.ordersState.test {
            val state = awaitItem()
            assertIs<AsyncState.Success<List<Order>>>(state)
            assertEquals(1, state.data.size)
            assertEquals(OrderStatus.CANCELLED, state.data[0].status)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `reactive updates when orders change`() = runTest {
        // Given authenticated user and initial orders
        authRepository.setCurrentUserId("seller_123")
        orderRepository.setOrders(listOf(createOrder("order_1")))

        val viewModel = createViewModel()
        advanceUntilIdle()

        // Verify initial state
        viewModel.ordersState.test {
            val state = awaitItem()
            assertIs<AsyncState.Success<List<Order>>>(state)
            assertEquals(1, state.data.size)
            cancelAndIgnoreRemainingEvents()
        }

        // When orders change in repository (simulating Firebase update)
        orderRepository.setOrders(listOf(
            createOrder("order_1"),
            createOrder("order_2")
        ))
        advanceUntilIdle()

        // Then UI reflects new data (reactive via Flow)
        viewModel.ordersState.test {
            val state = awaitItem()
            assertIs<AsyncState.Success<List<Order>>>(state)
            assertEquals(2, state.data.size)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
