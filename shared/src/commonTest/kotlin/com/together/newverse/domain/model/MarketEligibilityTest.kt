package com.together.newverse.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Approval says whether a buyer may order; `allowedMarkets` says where.
 *
 * Empty means **all** — which is what every record written before scoping existed
 * means, and what an unrestricted buyer means. Reading it as "none" would lock
 * those buyers out of ordering entirely, so the direction of that default is the
 * thing most worth pinning.
 */
class MarketEligibilityTest {

    private val kreuzberg = Market(id = "m1", name = "Kreuzberg", dayIndex = 3)
    private val neukoelln = Market(id = "m2", name = "Neukölln", dayIndex = 5)
    private val markets = listOf(kreuzberg, neukoelln)

    @Test
    fun `an unscoped buyer may use every market`() {
        val access = BuyerAccess(buyerId = "b1", status = AccessStatus.APPROVED)
        assertTrue(access.mayUse("m1"))
        assertTrue(access.mayUse("m2"))
        assertEquals(markets, access.eligible(markets))
    }

    @Test
    fun `a scoped buyer may use only what they were given`() {
        val access = BuyerAccess(buyerId = "b1", status = AccessStatus.APPROVED, allowedMarkets = listOf("m1"))
        assertTrue(access.mayUse("m1"))
        assertFalse(access.mayUse("m2"))
        assertEquals(listOf(kreuzberg), access.eligible(markets))
    }

    @Test
    fun `a scope naming a market the seller no longer runs yields nothing for it`() {
        val access = BuyerAccess(buyerId = "b1", allowedMarkets = listOf("gone"))
        assertEquals(emptyList(), access.eligible(markets))
    }

    // ===== the encoding the security rule compares =====

    @Test
    fun `a scope encodes to one comparable string`() {
        assertEquals("m1,m2", MarketScope.encode(listOf("m1", "m2")))
    }

    // Sorted and deduplicated, so two sellers who tick the same boxes in a
    // different order produce the same string — which is what the rule compares.
    @Test
    fun `encoding is order-independent and deduplicated`() {
        assertEquals(MarketScope.encode(listOf("m2", "m1")), MarketScope.encode(listOf("m1", "m2")))
        assertEquals("m1,m2", MarketScope.encode(listOf("m2", "m1", "m2")))
    }

    @Test
    fun `everywhere encodes to the empty string`() {
        assertEquals("", MarketScope.encode(emptyList()))
        assertEquals("", MarketScope.encode(listOf("", "   ")))
    }

    @Test
    fun `decoding is the inverse, and tolerates what a human might have typed`() {
        assertEquals(listOf("m1", "m2"), MarketScope.decode("m1,m2"))
        assertEquals(listOf("m1", "m2"), MarketScope.decode(" m1 , m2 "))
        assertEquals(emptyList(), MarketScope.decode(""))
        assertEquals(emptyList(), MarketScope.decode(null))
        assertEquals(emptyList(), MarketScope.decode(",,"))
    }

    @Test
    fun `a round trip preserves the scope`() {
        val scope = listOf("m2", "m1")
        assertEquals(scope.sorted(), MarketScope.decode(MarketScope.encode(scope)))
    }
}
