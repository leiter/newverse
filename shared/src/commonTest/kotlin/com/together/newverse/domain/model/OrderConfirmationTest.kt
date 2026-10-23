package com.together.newverse.domain.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** When a demo order becomes something the buyer has to answer for. */
class OrderConfirmationTest {

    private fun order(origin: OrderOrigin) = Order(id = "o1", origin = origin)

    @Test
    fun `an approved buyer's demo order is waiting on them`() {
        assertTrue(order(OrderOrigin.DEMO).awaitsConfirmation(isApproved = true))
    }

    @Test
    fun `an unapproved buyer has nothing to confirm`() {
        // They cannot place a real order at all, so the demo order is just their order.
        assertFalse(order(OrderOrigin.DEMO).awaitsConfirmation(isApproved = false))
    }

    @Test
    fun `a real order is never waiting on confirmation`() {
        for (origin in listOf(OrderOrigin.PRODUCTION, OrderOrigin.TRANSITIONED)) {
            assertFalse(order(origin).awaitsConfirmation(isApproved = true), "$origin")
            assertFalse(order(origin).awaitsConfirmation(isApproved = false), "$origin")
        }
    }

    @Test
    fun `a confirmed order does not ask twice`() {
        // Confirming mints TRANSITIONED, which is how the prompt stops coming back.
        val confirmed = order(OrderOrigin.DEMO).copy(origin = OrderOrigin.TRANSITIONED)

        assertFalse(confirmed.awaitsConfirmation(isApproved = true))
    }
}
