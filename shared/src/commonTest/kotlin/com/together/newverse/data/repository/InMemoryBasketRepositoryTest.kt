package com.together.newverse.data.repository

import app.cash.turbine.test
import com.together.newverse.domain.model.DraftBasket
import com.together.newverse.domain.model.OrderedProduct
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for InMemoryBasketRepository.
 *
 * Tests cover:
 * - Adding items
 * - Removing items
 * - Updating quantities
 * - Clearing basket
 * - Calculating totals
 * - Loading from orders
 * - Draft basket functionality
 */
class InMemoryBasketRepositoryTest {

    private lateinit var repository: InMemoryBasketRepository

    @BeforeTest
    fun setup() {
        repository = InMemoryBasketRepository()
    }

    // ===== Test Helpers =====

    private fun createOrderedProduct(
        id: String = "",
        productId: String,
        name: String = "Product $productId",
        price: Double = 10.0,
        quantity: Double = 1.0
    ): OrderedProduct {
        return OrderedProduct(
            id = id,
            productId = productId,
            productName = name,
            price = price,
            amountCount = quantity,
            amount = quantity.toString(),
            piecesCount = quantity.toInt()
        )
    }

    // ===== A. Initial State (3 tests) =====

    @Test
    fun `initial basket is empty`() = runTest {
        repository.observeBasket().test {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `initial total is zero`() = runTest {
        assertEquals(0.0, repository.getTotal())
    }

    @Test
    fun `initial item count is zero`() = runTest {
        assertEquals(0, repository.getItemCount())
    }

    // ===== B. Add Item (5 tests) =====

    @Test
    fun `addItem adds new product to basket`() = runTest {
        val product = createOrderedProduct(productId = "prod_1", name = "Apple", price = 2.50)

        repository.addItem(product)

        repository.observeBasket().test {
            val items = awaitItem()
            assertEquals(1, items.size)
            assertEquals("Apple", items[0].productName)
            assertEquals(2.50, items[0].price)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `addItem increases quantity for existing product by productId`() = runTest {
        val product1 = createOrderedProduct(productId = "prod_1", quantity = 2.0)
        val product2 = createOrderedProduct(productId = "prod_1", quantity = 3.0)

        repository.addItem(product1)
        repository.addItem(product2)

        repository.observeBasket().test {
            val items = awaitItem()
            assertEquals(1, items.size)
            assertEquals(5.0, items[0].amountCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `addItem increases quantity for existing product by id`() = runTest {
        val product1 = createOrderedProduct(id = "order_item_1", productId = "prod_1", quantity = 2.0)
        val product2 = createOrderedProduct(id = "order_item_1", productId = "prod_1", quantity = 1.0)

        repository.addItem(product1)
        repository.addItem(product2)

        repository.observeBasket().test {
            val items = awaitItem()
            assertEquals(1, items.size)
            assertEquals(3.0, items[0].amountCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `addItem adds multiple different products`() = runTest {
        repository.addItem(createOrderedProduct(productId = "prod_1", name = "Apple"))
        repository.addItem(createOrderedProduct(productId = "prod_2", name = "Banana"))
        repository.addItem(createOrderedProduct(productId = "prod_3", name = "Cherry"))

        repository.observeBasket().test {
            val items = awaitItem()
            assertEquals(3, items.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `addItem updates amount string`() = runTest {
        val product = createOrderedProduct(productId = "prod_1", quantity = 2.5)

        repository.addItem(product)

        repository.observeBasket().test {
            val items = awaitItem()
            assertEquals("2.5", items[0].amount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ===== C. Remove Item (4 tests) =====

    @Test
    fun `removeItem removes product by productId`() = runTest {
        repository.addItem(createOrderedProduct(productId = "prod_1"))
        repository.addItem(createOrderedProduct(productId = "prod_2"))

        repository.removeItem("prod_1")

        repository.observeBasket().test {
            val items = awaitItem()
            assertEquals(1, items.size)
            assertEquals("prod_2", items[0].productId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `removeItem removes product by id`() = runTest {
        repository.addItem(createOrderedProduct(id = "item_1", productId = "prod_1"))
        repository.addItem(createOrderedProduct(id = "item_2", productId = "prod_2"))

        repository.removeItem("item_1")

        repository.observeBasket().test {
            val items = awaitItem()
            assertEquals(1, items.size)
            assertEquals("item_2", items[0].id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `removeItem does nothing for nonexistent product`() = runTest {
        repository.addItem(createOrderedProduct(productId = "prod_1"))

        repository.removeItem("nonexistent")

        repository.observeBasket().test {
            val items = awaitItem()
            assertEquals(1, items.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `removeItem works on empty basket`() = runTest {
        repository.removeItem("any_id")

        repository.observeBasket().test {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ===== D. Update Quantity (4 tests) =====

    @Test
    fun `updateQuantity changes product quantity by productId`() = runTest {
        repository.addItem(createOrderedProduct(productId = "prod_1", quantity = 1.0))

        repository.updateQuantity("prod_1", 5.0)

        repository.observeBasket().test {
            val items = awaitItem()
            assertEquals(5.0, items[0].amountCount)
            assertEquals("5.0", items[0].amount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updateQuantity changes product quantity by id`() = runTest {
        repository.addItem(createOrderedProduct(id = "item_1", productId = "prod_1", quantity = 2.0))

        repository.updateQuantity("item_1", 10.0)

        repository.observeBasket().test {
            val items = awaitItem()
            assertEquals(10.0, items[0].amountCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updateQuantity does nothing for nonexistent product`() = runTest {
        repository.addItem(createOrderedProduct(productId = "prod_1", quantity = 3.0))

        repository.updateQuantity("nonexistent", 10.0)

        repository.observeBasket().test {
            val items = awaitItem()
            assertEquals(3.0, items[0].amountCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updateQuantity updates piecesCount proportionally`() = runTest {
        // Product with 2 pieces at quantity 1.0 (2 pieces per unit)
        val product = OrderedProduct(
            productId = "prod_1",
            productName = "Product",
            price = 5.0,
            amountCount = 1.0,
            piecesCount = 2
        )
        repository.addItem(product)

        // Update to quantity 3.0, should be 6 pieces
        repository.updateQuantity("prod_1", 3.0)

        repository.observeBasket().test {
            val items = awaitItem()
            assertEquals(3.0, items[0].amountCount)
            assertEquals(6, items[0].piecesCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ===== E. Clear Basket (3 tests) =====

    @Test
    fun `clearBasket removes all items`() = runTest {
        repository.addItem(createOrderedProduct(productId = "prod_1"))
        repository.addItem(createOrderedProduct(productId = "prod_2"))
        repository.addItem(createOrderedProduct(productId = "prod_3"))

        repository.clearBasket()

        repository.observeBasket().test {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clearBasket resets loaded order info`() = runTest {
        repository.loadOrderItems(
            items = listOf(createOrderedProduct(productId = "prod_1")),
            orderId = "order_123",
            orderDate = "20240101"
        )
        assertNotNull(repository.getLoadedOrderInfo())

        repository.clearBasket()

        assertNull(repository.getLoadedOrderInfo())
    }

    @Test
    fun `clearBasket on empty basket works`() = runTest {
        repository.clearBasket()

        repository.observeBasket().test {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ===== F. Totals and Count (4 tests) =====

    @Test
    fun `getTotal calculates sum correctly`() = runTest {
        repository.addItem(createOrderedProduct(productId = "prod_1", price = 5.0, quantity = 2.0))
        repository.addItem(createOrderedProduct(productId = "prod_2", price = 3.0, quantity = 4.0))

        // 5.0 * 2.0 + 3.0 * 4.0 = 10.0 + 12.0 = 22.0
        assertEquals(22.0, repository.getTotal())
    }

    @Test
    fun `getTotal returns zero for empty basket`() = runTest {
        assertEquals(0.0, repository.getTotal())
    }

    @Test
    fun `getItemCount returns number of items`() = runTest {
        repository.addItem(createOrderedProduct(productId = "prod_1"))
        repository.addItem(createOrderedProduct(productId = "prod_2"))
        repository.addItem(createOrderedProduct(productId = "prod_3"))

        assertEquals(3, repository.getItemCount())
    }

    @Test
    fun `getTotal updates after quantity change`() = runTest {
        repository.addItem(createOrderedProduct(productId = "prod_1", price = 10.0, quantity = 1.0))
        assertEquals(10.0, repository.getTotal())

        repository.updateQuantity("prod_1", 3.0)

        assertEquals(30.0, repository.getTotal())
    }

    // ===== G. Load Order Items (4 tests) =====

    @Test
    fun `loadOrderItems replaces basket contents`() = runTest {
        repository.addItem(createOrderedProduct(productId = "existing"))

        val orderItems = listOf(
            createOrderedProduct(productId = "order_1"),
            createOrderedProduct(productId = "order_2")
        )
        repository.loadOrderItems(orderItems, "order_123", "20240101")

        repository.observeBasket().test {
            val items = awaitItem()
            assertEquals(2, items.size)
            assertEquals("order_1", items[0].productId)
            assertEquals("order_2", items[1].productId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadOrderItems sets order info`() = runTest {
        repository.loadOrderItems(
            items = listOf(createOrderedProduct(productId = "prod_1")),
            orderId = "order_456",
            orderDate = "20240215"
        )

        val orderInfo = repository.getLoadedOrderInfo()
        assertNotNull(orderInfo)
        assertEquals("order_456", orderInfo.first)
        assertEquals("20240215", orderInfo.second)
    }

    @Test
    fun `getLoadedOrderInfo returns null when no order loaded`() = runTest {
        repository.addItem(createOrderedProduct(productId = "prod_1"))

        assertNull(repository.getLoadedOrderInfo())
    }

    @Test
    fun `loadOrderItems with empty list clears basket`() = runTest {
        repository.addItem(createOrderedProduct(productId = "existing"))

        repository.loadOrderItems(emptyList(), "order_123", "20240101")

        repository.observeBasket().test {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ===== H. Draft Basket (5 tests) =====

    @Test
    fun `hasDraftBasket returns true when items exist without order`() = runTest {
        repository.addItem(createOrderedProduct(productId = "prod_1"))

        assertTrue(repository.hasDraftBasket())
    }

    @Test
    fun `hasDraftBasket returns false when basket is empty`() = runTest {
        assertFalse(repository.hasDraftBasket())
    }

    @Test
    fun `hasDraftBasket returns false when items loaded from order`() = runTest {
        repository.loadOrderItems(
            items = listOf(createOrderedProduct(productId = "prod_1")),
            orderId = "order_123",
            orderDate = "20240101"
        )

        assertFalse(repository.hasDraftBasket())
    }

    @Test
    fun `loadFromProfile loads draft items`() = runTest {
        val draftBasket = DraftBasket(
            items = listOf(
                createOrderedProduct(productId = "draft_1"),
                createOrderedProduct(productId = "draft_2")
            ),
            selectedPickupDate = "2024-01-15",
            lastModified = 1234567890L
        )

        repository.loadFromProfile(draftBasket)

        repository.observeBasket().test {
            val items = awaitItem()
            assertEquals(2, items.size)
            assertEquals("draft_1", items[0].productId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `toDraftBasket creates draft from current basket`() = runTest {
        repository.addItem(createOrderedProduct(productId = "prod_1", name = "Apple"))
        repository.addItem(createOrderedProduct(productId = "prod_2", name = "Banana"))

        val draftBasket = repository.toDraftBasket("2024-02-20")

        assertEquals(2, draftBasket.items.size)
        assertEquals("2024-02-20", draftBasket.selectedPickupDate)
        assertTrue(draftBasket.lastModified > 0)
    }

    // ===== I. Observable Flow (2 tests) =====

    @Test
    fun `observeBasket emits updates when items change`() = runTest {
        repository.observeBasket().test {
            // Initial empty state
            assertTrue(awaitItem().isEmpty())

            // Add item
            repository.addItem(createOrderedProduct(productId = "prod_1"))
            assertEquals(1, awaitItem().size)

            // Add another
            repository.addItem(createOrderedProduct(productId = "prod_2"))
            assertEquals(2, awaitItem().size)

            // Remove one
            repository.removeItem("prod_1")
            assertEquals(1, awaitItem().size)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeBasket returns StateFlow`() = runTest {
        val flow = repository.observeBasket()

        // StateFlow should always have a current value
        assertNotNull(flow.value)
        assertTrue(flow.value.isEmpty())
    }
}
