package com.together.newverse.data.repository

import com.together.newverse.domain.model.OrderOrigin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Which trunks a buyer's history is read from, and what provenance each record gets.
 *
 * The rule being pinned: demo mode governs what a buyer may *place*, not what they may
 * *see* of their own past. A returning buyer who is not approved still reads the orders
 * they placed before.
 */
class BuyerOrderHistoryTest {

    @Test
    fun `an approved buyer reads only the real trunk`() {
        // Anything they placed while unapproved was moved into orders/ on approval,
        // so a second listener on demo_orders/ would find nothing but cost a read.
        assertEquals(listOf(false), historyTrunks(isDemo = false))
    }

    @Test
    fun `a buyer in demo mode reads both trunks`() {
        assertEquals(listOf(false, true), historyTrunks(isDemo = true))
    }

    @Test
    fun `the real trunk is read first so a half-transitioned order resolves to the real copy`() {
        // combine() preserves this order and distinctBy keeps the first of a repeated
        // id, so "real first" is what makes the real copy win.
        val trunks = historyTrunks(isDemo = true)
        assertTrue(trunks.indexOf(false) < trunks.indexOf(true))
    }

    @Test
    fun `anything in the demo trunk is a demo order whatever it stores`() {
        for (stored in OrderOrigin.entries) {
            assertEquals(
                OrderOrigin.DEMO,
                historyOrigin(fromDemoTrunk = true, stored = stored),
                "a record under demo_orders/ storing $stored"
            )
        }
    }

    @Test
    fun `the real trunk keeps the provenance it stores`() {
        // TRANSITIONED has to survive the round trip, or the seller loses the fact that
        // the order predates the buyer's approval.
        assertEquals(
            OrderOrigin.TRANSITIONED,
            historyOrigin(fromDemoTrunk = false, stored = OrderOrigin.TRANSITIONED)
        )
        assertEquals(
            OrderOrigin.PRODUCTION,
            historyOrigin(fromDemoTrunk = false, stored = OrderOrigin.PRODUCTION)
        )
    }

    @Test
    fun `a demo-marked record under orders is left out`() {
        // A legacy half-state. It was hidden before a demo buyer read this trunk at all,
        // and reading the trunk must not make it appear.
        assertNull(historyOrigin(fromDemoTrunk = false, stored = OrderOrigin.DEMO))
    }

    @Test
    fun `a demo buyer's real history is not relabelled as demo`() {
        // The regression this whole change exists to prevent: the old code applied
        // `if (isDemo) copy(origin = DEMO)` to every record it read, so once both trunks
        // are read, a real order would have been shown to the buyer as a demo order.
        val realOrdersWhileInDemoMode = historyTrunks(isDemo = true)
            .filterNot { it }
            .map { historyOrigin(fromDemoTrunk = it, stored = OrderOrigin.PRODUCTION) }

        assertEquals(listOf(OrderOrigin.PRODUCTION), realOrdersWhileInDemoMode)
    }
}
