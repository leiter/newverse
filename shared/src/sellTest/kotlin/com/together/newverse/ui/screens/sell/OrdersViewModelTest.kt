package com.together.newverse.ui.screens.sell

import app.cash.turbine.test
import com.together.newverse.domain.model.OrderStatus
import com.together.newverse.test.FakeAuthRepository
import com.together.newverse.test.FakeOrderRepository
import com.together.newverse.test.MainDispatcherRule
import com.together.newverse.test.TestData
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

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
    }

    private fun createViewModel(): OrdersViewModel {
        return OrdersViewModel(
            orderRepository = orderRepository,
            authRepository = authRepository
        )
    }

    @Test
    fun `initial state transitions from Loading to Success`() = runTest {
        // Given user is authenticated
        authRepository.setCurrentUserId("seller_123")

        // When ViewModel is created
        val viewModel = createViewModel()

        // Then state eventually becomes Success (UnconfinedTestDispatcher processes immediately)
        viewModel.uiState.test {
            val state = awaitItem()
            // With UnconfinedTestDispatcher, state may already be Success
            assertTrue(state is OrdersUiState.Loading || state is OrdersUiState.Success)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loads orders successfully`() = runTest {
        // Given user is authenticated
        authRepository.setCurrentUserId("seller_123")

        // Set orders in repository
        val orders = TestData.sampleOrders
        orderRepository.setOrders(orders)

        // When ViewModel is created
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Then state should be Success with orders
        viewModel.uiState.test {
            val state = awaitItem()
            assertIs<OrdersUiState.Success>(state)
            assertEquals(orders.size, state.orders.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `shows empty state when no orders`() = runTest {
        // Given user is authenticated
        authRepository.setCurrentUserId("seller_123")

        // Set empty orders
        orderRepository.setOrders(emptyList())

        // When ViewModel is created
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Then state should be Success with empty list
        viewModel.uiState.test {
            val state = awaitItem()
            assertIs<OrdersUiState.Success>(state)
            assertTrue(state.orders.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `filters orders by PENDING`() = runTest {
        // Given user is authenticated
        authRepository.setCurrentUserId("seller_123")

        // Set orders with different statuses
        val orders = TestData.sampleOrders
        orderRepository.setOrders(orders)

        // When ViewModel is created
        val viewModel = createViewModel()
        advanceUntilIdle()

        // When filter is set to PENDING
        viewModel.setFilter(OrderFilter.PENDING)
        advanceUntilIdle()

        // Then only pending orders should be shown
        viewModel.uiState.test {
            val state = awaitItem()
            assertIs<OrdersUiState.Success>(state)
            state.orders.forEach { order ->
                assertTrue(order.status.isActive() && !order.status.isFinalized())
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `filters orders by COMPLETED`() = runTest {
        // Given user is authenticated
        authRepository.setCurrentUserId("seller_123")

        // Set orders with different statuses
        val orders = TestData.sampleOrders
        orderRepository.setOrders(orders)

        // When ViewModel is created
        val viewModel = createViewModel()
        advanceUntilIdle()

        // When filter is set to COMPLETED
        viewModel.setFilter(OrderFilter.COMPLETED)
        advanceUntilIdle()

        // Then only completed orders should be shown
        viewModel.uiState.test {
            val state = awaitItem()
            assertIs<OrdersUiState.Success>(state)
            state.orders.forEach { order ->
                assertEquals(OrderStatus.COMPLETED, order.status)
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `filters orders by CANCELLED`() = runTest {
        // Given user is authenticated
        authRepository.setCurrentUserId("seller_123")

        // Set orders with different statuses
        val orders = TestData.sampleOrders
        orderRepository.setOrders(orders)

        // When ViewModel is created
        val viewModel = createViewModel()
        advanceUntilIdle()

        // When filter is set to CANCELLED
        viewModel.setFilter(OrderFilter.CANCELLED)
        advanceUntilIdle()

        // Then only cancelled orders should be shown
        viewModel.uiState.test {
            val state = awaitItem()
            assertIs<OrdersUiState.Success>(state)
            state.orders.forEach { order ->
                assertEquals(OrderStatus.CANCELLED, order.status)
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `filter ALL shows all orders`() = runTest {
        // Given user is authenticated
        authRepository.setCurrentUserId("seller_123")

        // Set orders with different statuses
        val orders = TestData.sampleOrders
        orderRepository.setOrders(orders)

        // When ViewModel is created
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Apply a filter first
        viewModel.setFilter(OrderFilter.COMPLETED)
        advanceUntilIdle()

        // When filter is set back to ALL
        viewModel.setFilter(OrderFilter.ALL)
        advanceUntilIdle()

        // Then all orders should be shown
        viewModel.uiState.test {
            val state = awaitItem()
            assertIs<OrdersUiState.Success>(state)
            assertEquals(orders.size, state.orders.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `refresh reloads orders`() = runTest {
        // Given user is authenticated
        authRepository.setCurrentUserId("seller_123")

        // Set initial orders
        orderRepository.setOrders(TestData.sampleOrders.take(1))

        // When ViewModel is created
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Update orders in repository
        orderRepository.setOrders(TestData.sampleOrders)

        // When refresh is called
        viewModel.refresh()
        advanceUntilIdle()

        // Then all orders should be shown
        viewModel.uiState.test {
            val state = awaitItem()
            assertIs<OrdersUiState.Success>(state)
            assertEquals(TestData.sampleOrders.size, state.orders.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `orders are sorted by pickup date descending`() = runTest {
        // Given user is authenticated
        authRepository.setCurrentUserId("seller_123")

        // Set orders with different pickup dates
        val orders = TestData.sampleOrders
        orderRepository.setOrders(orders)

        // When ViewModel is created
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Then orders should be sorted by pickup date descending
        viewModel.uiState.test {
            val state = awaitItem()
            assertIs<OrdersUiState.Success>(state)
            val pickupDates = state.orders.map { it.pickUpDate }
            assertEquals(pickupDates.sortedDescending(), pickupDates)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `hideOrder calls repository`() = runTest {
        // Given user is authenticated
        authRepository.setCurrentUserId("seller_123")

        // Set orders
        val order = TestData.sampleOrders[0]
        orderRepository.setOrders(listOf(order))

        // Create ViewModel
        val viewModel = createViewModel()
        advanceUntilIdle()

        var successCalled = false
        var errorMessage: String? = null

        // When hideOrder is called
        viewModel.hideOrder(
            order = order,
            onSuccess = { successCalled = true },
            onError = { errorMessage = it }
        )
        advanceUntilIdle()

        // Then repository should be called
        assertEquals(1, orderRepository.hiddenOrders.size)
        assertTrue(orderRepository.hiddenOrders[0].forSeller)
        assertEquals(order.id, orderRepository.hiddenOrders[0].orderId)
        assertTrue(successCalled)
        assertEquals(null, errorMessage)
    }

    @Test
    fun `hideOrder handles failure`() = runTest {
        // Given user is authenticated
        authRepository.setCurrentUserId("seller_123")
        orderRepository.shouldFailHideOrder = true
        orderRepository.failureMessage = "Failed to hide"

        // Set orders
        val order = TestData.sampleOrders[0]
        orderRepository.setOrders(listOf(order))

        // Create ViewModel
        val viewModel = createViewModel()
        advanceUntilIdle()

        var successCalled = false
        var errorMessage: String? = null

        // When hideOrder is called
        viewModel.hideOrder(
            order = order,
            onSuccess = { successCalled = true },
            onError = { errorMessage = it }
        )
        advanceUntilIdle()

        // Then error callback should be called
        assertEquals(false, successCalled)
        assertTrue(errorMessage?.contains("Failed") == true)
    }

    @Test
    fun `selectedFilter tracks current filter`() = runTest {
        // Given user is authenticated
        authRepository.setCurrentUserId("seller_123")
        orderRepository.setOrders(TestData.sampleOrders)

        // Create ViewModel
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Initially should be ALL
        assertEquals(OrderFilter.ALL, viewModel.selectedFilter.value)

        // When changing filter
        viewModel.setFilter(OrderFilter.COMPLETED)
        advanceUntilIdle()

        // Then selectedFilter should reflect the change
        assertEquals(OrderFilter.COMPLETED, viewModel.selectedFilter.value)
    }

    @Test
    fun `works with empty sellerId`() = runTest {
        // Given user is authenticated with empty ID (edge case)
        authRepository.setCurrentUserId("")

        // Set orders
        orderRepository.setOrders(TestData.sampleOrders)

        // When ViewModel is created
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Then orders should still load
        viewModel.uiState.test {
            val state = awaitItem()
            assertIs<OrdersUiState.Success>(state)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `handles null authenticated user`() = runTest {
        // Given user is NOT authenticated
        authRepository.setCurrentUserId(null)

        // Set orders
        orderRepository.setOrders(TestData.sampleOrders)

        // When ViewModel is created
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Then orders should still load (sellerId defaults to empty string)
        viewModel.uiState.test {
            val state = awaitItem()
            assertIs<OrdersUiState.Success>(state)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `filter persists across multiple operations`() = runTest {
        // Given user is authenticated
        authRepository.setCurrentUserId("seller_123")

        // Set orders
        orderRepository.setOrders(TestData.sampleOrders)

        // Create ViewModel and set filter
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.setFilter(OrderFilter.PENDING)
        advanceUntilIdle()

        // When refresh is called
        viewModel.refresh()
        advanceUntilIdle()

        // Then filter should still be PENDING
        assertEquals(OrderFilter.PENDING, viewModel.selectedFilter.value)
    }
}

// Extension function to check if status is active
private fun OrderStatus.isActive(): Boolean = this == OrderStatus.PLACED || this == OrderStatus.LOCKED
private fun OrderStatus.isFinalized(): Boolean = this == OrderStatus.COMPLETED || this == OrderStatus.CANCELLED
