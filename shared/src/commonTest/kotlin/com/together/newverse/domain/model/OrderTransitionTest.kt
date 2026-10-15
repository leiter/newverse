package com.together.newverse.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * What happens to a demo order when the seller approves the buyer and it moves from
 * `demo_orders/` into `orders/`.
 */
class OrderTransitionTest {

    // A Thursday pickup, and points around the Tuesday 23:59 edit deadline before it.
    private val pickup = Instant.parse("2026-10-22T10:00:00Z")
    private val beforeDeadline = Instant.parse("2026-10-19T10:00:00Z") // Monday
    private val afterDeadline = Instant.parse("2026-10-21T10:00:00Z")  // Wednesday
    private val afterPickup = Instant.parse("2026-10-23T10:00:00Z")    // Friday

    private fun demoOrder(status: OrderStatus = OrderStatus.PLACED) = Order(
        id = "demo_1",
        pickUpDate = pickup.toEpochMilliseconds(),
        status = status,
        origin = OrderOrigin.DEMO
    )

    @Test
    fun `an editable demo order becomes a placed production order`() {
        assertEquals(OrderStatus.PLACED, demoOrder().statusAfterTransition(beforeDeadline))
    }

    @Test
    fun `past the edit deadline it becomes locked, not placed`() {
        // The seller is already preparing this pickup; the buyer must not be able to edit it.
        assertEquals(OrderStatus.LOCKED, demoOrder().statusAfterTransition(afterDeadline))
    }

    @Test
    fun `a finalized status is left alone`() {
        for (status in listOf(OrderStatus.CANCELLED, OrderStatus.COMPLETED, OrderStatus.NOT_PICKED_UP)) {
            assertEquals(status, demoOrder(status).statusAfterTransition(beforeDeadline))
        }
    }

    @Test
    fun `an order the seller can still act on moves`() {
        assertTrue(demoOrder().canTransitionToProduction(beforeDeadline))
        // Locked but not yet collected — the seller still has to hand it over.
        assertTrue(demoOrder().canTransitionToProduction(afterDeadline))
    }

    @Test
    fun `an order whose pickup has passed does not move`() {
        // It describes a pickup that never happened; moving it would plant a sale that
        // was never made in the seller's books.
        assertFalse(demoOrder().canTransitionToProduction(afterPickup))
    }

    @Test
    fun `a cancelled order does not move`() {
        assertFalse(demoOrder(OrderStatus.CANCELLED).canTransitionToProduction(beforeDeadline))
    }

    @Test
    fun `origin says whether an order is the seller's business`() {
        assertTrue(OrderOrigin.DEMO.isDemo)
        assertFalse(OrderOrigin.DEMO.isReal)
        // A transitioned order is fulfilled and booked exactly like a production one.
        assertTrue(OrderOrigin.TRANSITIONED.isReal)
        assertTrue(OrderOrigin.PRODUCTION.isReal)
    }

    @Test
    fun `a demo order kept in local storage survives the round trip`() {
        val order = demoOrder()
        assertEquals(OrderOrigin.DEMO, order.toDemoDto().toOrder().origin)
    }

    @Test
    fun `a local record written before the origin field is read as a demo order`() {
        val legacy = DemoOrderDto(
            id = "demo_1",
            buyerDisplayName = "Buyer",
            createdDate = 0L,
            sellerId = "seller",
            marketId = "",
            pickUpDate = pickup.toEpochMilliseconds(),
            message = "",
            status = "DEMO_ORDER",
            articles = emptyList()
        )
        val order = legacy.toOrder()
        assertEquals(OrderOrigin.DEMO, order.origin)
        // DEMO_ORDER is no longer a status; the lifecycle falls back to PLACED.
        assertEquals(OrderStatus.PLACED, order.status)
    }
}
