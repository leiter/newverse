package com.together.newverse.data.repository

import com.together.newverse.domain.model.OrderStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class StoredOrderStatusTest {

    @Test
    fun `known statuses are read as stored`() {
        for (status in OrderStatus.entries - OrderStatus.DRAFT) {
            assertEquals(status, storedOrderStatus(status.name))
        }
    }

    @Test
    fun `a stored draft is a placed order`() {
        // Buyer edits used to store placed orders as DRAFT, editable past the deadline.
        assertEquals(OrderStatus.PLACED, storedOrderStatus("DRAFT"))
    }

    @Test
    fun `a missing or unknown status is a placed order`() {
        assertEquals(OrderStatus.PLACED, storedOrderStatus(null))
        assertEquals(OrderStatus.PLACED, storedOrderStatus("SOMETHING_NEWER"))
    }
}
