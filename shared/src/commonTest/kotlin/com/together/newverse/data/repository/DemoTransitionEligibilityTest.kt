package com.together.newverse.data.repository

import com.together.newverse.domain.model.Order
import com.together.newverse.domain.model.OrderStatus
import com.together.newverse.test.FakeOrderRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

/**
 * Approval decides *where* a buyer may collect, and a demo order placed somewhere else
 * does not get to ride in on it.
 *
 * A demo order carries the price of the market it was placed at. If the seller then
 * grants a different stall, moving the order would charge one market's prices for a
 * stall the buyer cannot reach, and repricing it would change a total they already
 * confirmed. So it stays in `demo_orders/` and the buyer is asked —
 * `doc/guides/market-pricing-and-access-design.md` §2a.
 */
class DemoTransitionEligibilityTest {

    /**
     * [daysOut] varies the pickup date because the result is keyed by date — one order
     * per cycle, which is the shape `BuyerProfile.placedOrderIds` has always had. Two
     * orders sharing a pickup day collapse into one entry, so a test that means "both
     * moved" has to put them on different days.
     */
    private fun order(id: String, marketId: String, daysOut: Int = 3) = Order(
        id = id,
        sellerId = "s1",
        marketId = marketId,
        pickUpDate = Clock.System.now().plus(daysOut.days).toEpochMilliseconds(),
        status = OrderStatus.PLACED
    )

    @Test
    fun `an order at a granted market moves`() = runTest {
        val repo = FakeOrderRepository()

        val result = repo.transitionDemoOrdersToProduction(
            sellerId = "s1",
            remoteOrderIds = emptyMap(),
            localOrders = listOf(order("o1", "m1")),
            allowedMarkets = listOf("m1")
        ).getOrThrow()

        assertEquals(1, result.transitioned.size)
        assertFalse(result.hasRefusals)
    }

    @Test
    fun `an order at a market the seller did not grant is refused, not moved`() = runTest {
        val repo = FakeOrderRepository()

        val result = repo.transitionDemoOrdersToProduction(
            sellerId = "s1",
            remoteOrderIds = emptyMap(),
            localOrders = listOf(order("o1", "m2")),
            allowedMarkets = listOf("m1")
        ).getOrThrow()

        assertTrue(result.transitioned.isEmpty())
        assertTrue(result.hasRefusals)
        assertEquals(listOf("m2"), result.notEligibleMarketIds)
    }

    @Test
    fun `a mixed batch moves what it can and reports what it cannot`() = runTest {
        val repo = FakeOrderRepository()

        val result = repo.transitionDemoOrdersToProduction(
            sellerId = "s1",
            remoteOrderIds = emptyMap(),
            localOrders = listOf(
                order("o1", "m1", daysOut = 3),
                order("o2", "m2", daysOut = 10),
                order("o3", "m3", daysOut = 17)
            ),
            allowedMarkets = listOf("m1")
        ).getOrThrow()

        assertEquals(1, result.transitioned.size)
        assertEquals(setOf("m2", "m3"), result.notEligibleMarketIds.toSet())
    }

    @Test
    fun `an empty grant means every market, as it does everywhere else`() = runTest {
        val repo = FakeOrderRepository()

        val result = repo.transitionDemoOrdersToProduction(
            sellerId = "s1",
            remoteOrderIds = emptyMap(),
            localOrders = listOf(order("o1", "m1", daysOut = 3), order("o2", "m2", daysOut = 10)),
            allowedMarkets = emptyList()
        ).getOrThrow()

        assertEquals(2, result.transitioned.size)
        assertFalse(result.hasRefusals)
    }

    @Test
    fun `an order naming no market is not refused by a narrow grant`() = runTest {
        val repo = FakeOrderRepository()

        // Every order placed before markets were an entity, and every order from a
        // single-market seller, carries an empty marketId. Refusing those would strand
        // the orders this feature was meant to leave untouched.
        val result = repo.transitionDemoOrdersToProduction(
            sellerId = "s1",
            remoteOrderIds = emptyMap(),
            localOrders = listOf(order("o1", "")),
            allowedMarkets = listOf("m1")
        ).getOrThrow()

        assertEquals(1, result.transitioned.size)
        assertFalse(result.hasRefusals)
    }

    @Test
    fun `the same unmet market is reported once, not per order`() = runTest {
        val repo = FakeOrderRepository()

        val result = repo.transitionDemoOrdersToProduction(
            sellerId = "s1",
            remoteOrderIds = emptyMap(),
            localOrders = listOf(order("o1", "m2", daysOut = 3), order("o2", "m2", daysOut = 10)),
            allowedMarkets = listOf("m1")
        ).getOrThrow()

        assertEquals(listOf("m2"), result.notEligibleMarketIds)
    }

    @Test
    fun `the grant reaches the repository`() = runTest {
        val repo = FakeOrderRepository()

        repo.transitionDemoOrdersToProduction(
            sellerId = "s1",
            remoteOrderIds = emptyMap(),
            localOrders = listOf(order("o1", "m1")),
            allowedMarkets = listOf("m1", "m4")
        )

        assertEquals(listOf("m1", "m4"), repo.transitions.single().allowedMarkets)
    }
}
