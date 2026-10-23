package com.together.newverse.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Filing an order in the buyer's index. One entry per order, whatever happened before.
 */
class OrderIndexTest {

    private val history = mapOf("20260919" to "a", "20260926" to "b")

    @Test
    fun `a new order is added`() {
        assertEquals(
            history + ("20261029" to "c"),
            orderIndexWith(history, "20261029", "c")
        )
    }

    @Test
    fun `filing an order that was deleted and saved again restores its entry`() {
        // The regression. Deleting pruned the entry, the basket kept the order id, so the
        // next save was an edit — and the edit path never filed it, leaving the record
        // present and the buyer unable to see it.
        assertEquals(
            history + ("20261029" to "c"),
            orderIndexWith(history, "20261029", "c")
        )
    }

    @Test
    fun `moving an order to another pickup day does not leave it filed under the old one`() {
        val index = history + ("20261029" to "c")

        val moved = orderIndexWith(index, "20261105", "c")

        assertEquals(history + ("20261105" to "c"), moved)
        assertEquals(1, moved.values.count { it == "c" }, "filed once, not twice")
    }

    @Test
    fun `filing the same order twice changes nothing`() {
        val once = orderIndexWith(history, "20261029", "c")

        assertEquals(once, orderIndexWith(once, "20261029", "c"))
    }

    @Test
    fun `a different order on the same day replaces it, because the index holds one per day`() {
        val index = mapOf("20261029" to "old")

        assertEquals(mapOf("20261029" to "new"), orderIndexWith(index, "20261029", "new"))
    }

    @Test
    fun `filing into an empty index`() {
        assertEquals(mapOf("20261029" to "c"), orderIndexWith(emptyMap(), "20261029", "c"))
    }
}
