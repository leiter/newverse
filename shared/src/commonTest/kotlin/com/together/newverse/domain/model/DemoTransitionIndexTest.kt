package com.together.newverse.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What a transition is allowed to do to the buyer's order index.
 *
 * The rule: a transition knows what it moved and what it deleted. It knows nothing
 * about the orders the buyer already had, so it may add and it may prune the records it
 * removed — it may never decide the whole index.
 */
class DemoTransitionIndexTest {

    private val history = mapOf(
        "20260919" to "seed-a",
        "20260926" to "seed-b",
        "20261003" to "seed-c"
    )

    @Test
    fun `a moved order is added without disturbing the history`() {
        val result = DemoTransitionResult(transitioned = mapOf("20261031" to "prod-1"))

        assertEquals(history + ("20261031" to "prod-1"), result.applyTo(history))
    }

    @Test
    fun `nothing moving leaves the index untouched`() {
        // The regression. A buyer approved for one market only has their single demo
        // order refused, so `transitioned` comes back empty — and assigning it wholesale
        // emptied the index, taking every historic order with it.
        val refused = DemoTransitionResult(notEligibleMarketIds = listOf("market-x"))

        assertEquals(history, refused.applyTo(history))
    }

    @Test
    fun `an order left behind on purpose keeps its entry`() {
        // It is still in demo_orders/, and the buyer is meant to be asked about it.
        val index = history + ("20261029" to "demo-1")
        val refused = DemoTransitionResult(notEligibleMarketIds = listOf("market-x"))

        assertEquals(index, refused.applyTo(index))
    }

    @Test
    fun `only a record that was actually deleted is pruned`() {
        val index = history + ("20260101" to "stale-1")
        val result = DemoTransitionResult(
            transitioned = mapOf("20261031" to "prod-1"),
            dropped = mapOf("20260101" to "stale-1"),
            expired = 1
        )

        assertEquals(history + ("20261031" to "prod-1"), result.applyTo(index))
    }

    @Test
    fun `a re-keyed order replaces its own entry rather than doubling up`() {
        // A locally held order is minted a fresh id on the same pickup day, and the
        // index holds one order per day.
        val index = mapOf("20261031" to "demo-local")
        val result = DemoTransitionResult(transitioned = mapOf("20261031" to "prod-1"))

        assertEquals(mapOf("20261031" to "prod-1"), result.applyTo(index))
    }

    @Test
    fun `an empty result on an empty index stays empty`() {
        assertEquals(emptyMap(), DemoTransitionResult().applyTo(emptyMap()))
    }
}
