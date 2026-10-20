package com.together.newverse.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CommittedDemandTest {

    private fun item(
        articleId: String = "apple",
        amount: Double = 2.0,
        unit: String = "kg"
    ) = OrderedProduct(
        productId = articleId,
        productName = articleId,
        unit = unit,
        amountCount = amount
    )

    private fun order(
        id: String = "o1",
        status: OrderStatus = OrderStatus.PLACED,
        origin: OrderOrigin = OrderOrigin.PRODUCTION,
        pickUpDate: Long = 1_000L,
        items: List<OrderedProduct> = listOf(item())
    ) = Order(
        id = id,
        status = status,
        origin = origin,
        pickUpDate = pickUpDate,
        articles = items
    )

    // --- which orders count ---------------------------------------------------

    @Test
    fun `placed and locked orders are what the seller still owes`() {
        assertTrue(order(status = OrderStatus.PLACED).countsAsCommitted())
        assertTrue(order(status = OrderStatus.LOCKED).countsAsCommitted())
    }

    @Test
    fun `a draft is not an order yet`() {
        assertTrue(!order(status = OrderStatus.DRAFT).countsAsCommitted())
    }

    @Test
    fun `cancelled and uncollected orders owe nothing`() {
        assertTrue(!order(status = OrderStatus.CANCELLED).countsAsCommitted())
        assertTrue(!order(status = OrderStatus.NOT_PICKED_UP).countsAsCommitted())
    }

    @Test
    fun `a completed order is excluded because handover already moved the stock`() {
        // The double-subtraction guard: PickupViewModel books a SALE movement on
        // handover, so counting the order as demand as well takes it off twice.
        assertTrue(!order(status = OrderStatus.COMPLETED).countsAsCommitted())
    }

    @Test
    fun `demo orders are phantom demand`() {
        assertTrue(!order(origin = OrderOrigin.DEMO).countsAsCommitted())
    }

    @Test
    fun `a transitioned order is real demand`() {
        // It started under demo_orders/ but the seller approved the buyer; it is
        // fulfilled and booked like any other.
        assertTrue(order(origin = OrderOrigin.TRANSITIONED).countsAsCommitted())
    }

    // --- the sum --------------------------------------------------------------

    @Test
    fun `quantities add up across orders`() {
        val demand = listOf(
            order("o1", items = listOf(item(amount = 2.0))),
            order("o2", items = listOf(item(amount = 3.5)))
        ).committedDemand()

        val apple = demand.getValue("apple")
        assertEquals(5.5, apple.quantity)
        assertEquals(2, apple.orderCount)
        assertEquals("kg", apple.unit)
    }

    @Test
    fun `two lines of one order both count, and the order counts once`() {
        val demand = listOf(
            order(items = listOf(item(amount = 2.0), item(amount = 1.0)))
        ).committedDemand()

        assertEquals(3.0, demand.getValue("apple").quantity)
        assertEquals(1, demand.getValue("apple").orderCount)
    }

    @Test
    fun `orders that owe nothing contribute nothing`() {
        val demand = listOf(
            order("o1", status = OrderStatus.COMPLETED),
            order("o2", origin = OrderOrigin.DEMO),
            order("o3", status = OrderStatus.CANCELLED)
        ).committedDemand()

        assertTrue(demand.isEmpty())
    }

    @Test
    fun `articles are keyed the way sales booking keys them`() {
        // The buyer app writes the article's database id into productId and leaves
        // id empty; a line written some other way falls back to id.
        val byProductId = OrderedProduct(productId = "apple", amountCount = 1.0, unit = "kg")
        val byId = OrderedProduct(id = "nuts", productId = "-1", amountCount = 2.0, unit = "kg")

        val demand = listOf(order(items = listOf(byProductId, byId))).committedDemand()

        assertEquals(setOf("apple", "nuts"), demand.keys)
        assertEquals("apple", byProductId.articleKey())
        assertEquals("nuts", byId.articleKey())
    }

    @Test
    fun `a line naming no article is dropped rather than piled under an empty key`() {
        val nameless = OrderedProduct(productId = "-1", amountCount = 4.0, unit = "kg")

        val demand = listOf(order(items = listOf(nameless, item(amount = 1.0)))).committedDemand()

        assertEquals(setOf("apple"), demand.keys)
    }

    @Test
    fun `rounding follows the pricing rules so the sum is comparable to a level`() {
        val demand = listOf(
            order("o1", items = listOf(item(amount = 0.1))),
            order("o2", items = listOf(item(amount = 0.2)))
        ).committedDemand()

        assertEquals(ProductPricing.roundQuantity(0.3), demand.getValue("apple").quantity)
    }

    // --- units ----------------------------------------------------------------

    @Test
    fun `lines ordered in different units are reported, not reconciled`() {
        val demand = listOf(
            order("o1", items = listOf(item(amount = 2.0, unit = "kg"))),
            order("o2", items = listOf(item(amount = 3.0, unit = "Stück")))
        ).committedDemand()

        val apple = demand.getValue("apple")
        assertEquals(setOf("kg", "Stück"), apple.units)
        assertNull(apple.unit)
    }

    @Test
    fun `a blank unit is not mistaken for a second unit`() {
        val demand = listOf(
            order("o1", items = listOf(item(amount = 2.0, unit = "kg"))),
            order("o2", items = listOf(item(amount = 1.0, unit = "")))
        ).committedDemand()

        assertEquals("kg", demand.getValue("apple").unit)
    }

    // --- slots ----------------------------------------------------------------

    @Test
    fun `a slot filter keeps only orders collected on those days`() {
        val orders = listOf(
            order("o1", pickUpDate = 1_000L, items = listOf(item(amount = 2.0))),
            order("o2", pickUpDate = 2_000L, items = listOf(item(amount = 5.0)))
        )

        assertEquals(2.0, orders.committedDemand(setOf(1_000L)).getValue("apple").quantity)
        assertEquals(5.0, orders.committedDemand(setOf(2_000L)).getValue("apple").quantity)
        // No filter is the buying question: everything still outstanding.
        assertEquals(7.0, orders.committedDemand().getValue("apple").quantity)
    }

    @Test
    fun `a slot nobody ordered for has no demand`() {
        assertTrue(listOf(order()).committedDemand(setOf(9_999L)).isEmpty())
    }
}
