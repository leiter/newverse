package com.together.newverse.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A draft basket stores the slot the buyer chose, not just its date.
 *
 * The field needs no conversion: a draft written before the slot picker simply has
 * no market, which is exactly what a single-market seller's draft has today. The
 * tests here pin that equivalence, because the node is persisted under
 * `buyer_profile` and a wrong default would silently re-point someone's basket at
 * another stall.
 */
class DraftBasketSlotTest {

    @Test
    fun `a draft written before the slot picker reads back with no market`() {
        // What the old shape deserializes to: a date and nothing else.
        val legacy = DraftBasket(selectedPickupDate = "20241024")
        assertEquals("", legacy.selectedMarketId)
    }

    @Test
    fun `a draft keeps the market it was saved with`() {
        val draft = DraftBasket(selectedPickupDate = "20241026", selectedMarketId = "m2")
        assertEquals("20241026", draft.selectedPickupDate)
        assertEquals("m2", draft.selectedMarketId)
    }

    @Test
    fun `clearing a stale slot clears both halves`() {
        val draft = DraftBasket(
            items = listOf(OrderedProduct(productId = "a", amountCount = 1.0)),
            selectedPickupDate = "20241026",
            selectedMarketId = "m2"
        )

        // What the restore path writes back when the slot no longer holds.
        val cleared = draft.copy(selectedPickupDate = null, selectedMarketId = "")

        assertEquals(null, cleared.selectedPickupDate)
        assertEquals("", cleared.selectedMarketId)
        // The items are the buyer's work and survive; only the slot is dropped.
        assertEquals(1, cleared.items.size)
    }
}
