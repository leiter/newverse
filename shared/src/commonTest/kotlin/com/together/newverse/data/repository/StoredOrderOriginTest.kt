package com.together.newverse.data.repository

import com.together.newverse.domain.model.OrderOrigin
import com.together.newverse.domain.model.OrderStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class StoredOrderOriginTest {

    @Test
    fun `known origins are read as stored`() {
        for (origin in OrderOrigin.entries) {
            assertEquals(origin, storedOrderOrigin(origin.name))
        }
    }

    @Test
    fun `a record with no origin is a production order`() {
        assertEquals(OrderOrigin.PRODUCTION, storedOrderOrigin(null))
    }

    @Test
    fun `an origin written by a newer version reads as production`() {
        // Visible and bookable beats silently filtered away.
        assertEquals(OrderOrigin.PRODUCTION, storedOrderOrigin("SOMETHING_NEWER"))
    }

    @Test
    fun `the legacy isDemoOrder flag is read as a demo origin`() {
        assertEquals(
            OrderOrigin.DEMO,
            storedOrderOrigin(raw = null, legacyIsDemoOrder = true)
        )
        assertEquals(
            OrderOrigin.PRODUCTION,
            storedOrderOrigin(raw = null, legacyIsDemoOrder = false)
        )
    }

    @Test
    fun `the legacy DEMO_ORDER status is read as a demo origin`() {
        assertEquals(
            OrderOrigin.DEMO,
            storedOrderOrigin(raw = null, legacyStatus = LEGACY_DEMO_STATUS)
        )
    }

    @Test
    fun `a stored DEMO_ORDER status reads as placed`() {
        // The two halves of a legacy demo record: PLACED as the lifecycle, DEMO as the origin.
        assertEquals(OrderStatus.PLACED, storedOrderStatus(LEGACY_DEMO_STATUS))
        assertEquals(OrderOrigin.DEMO, storedOrderOrigin(null, legacyStatus = LEGACY_DEMO_STATUS))
    }

    @Test
    fun `an explicit origin wins over the legacy fields`() {
        // A transitioned order keeps whatever its old demo record happened to carry.
        assertEquals(
            OrderOrigin.TRANSITIONED,
            storedOrderOrigin(
                raw = OrderOrigin.TRANSITIONED.name,
                legacyIsDemoOrder = true,
                legacyStatus = LEGACY_DEMO_STATUS
            )
        )
    }
}
