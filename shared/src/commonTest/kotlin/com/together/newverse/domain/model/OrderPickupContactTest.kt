package com.together.newverse.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * What an order says about who takes receipt of it — and whether the seller can act
 * on it without retyping anything.
 *
 * These are the three derived readings every seller screen goes through, plus the seam
 * between two deliberately different rules: the buyer's number is accepted in whatever
 * shape they write it, and the *dial* has to cope with that shape.
 */
class OrderPickupContactTest {

    private fun order(
        buyerName: String = "Alice Schmidt",
        pickupName: String = "",
        pickupPhone: String = "",
        pickupTime: String = "",
        fulfilment: Fulfilment = Fulfilment.PICKUP,
    ) = Order(
        buyerProfile = BuyerProfile(id = "b1", displayName = buyerName),
        pickupName = pickupName,
        pickupPhone = pickupPhone,
        pickupTime = pickupTime,
        fulfilment = fulfilment,
        deliveryAddress = if (fulfilment == Fulfilment.DELIVERY) {
            DeliveryAddress("Hauptstr.", "4")
        } else DeliveryAddress.NONE
    )

    // ===== a collector worth naming =====

    @Test
    fun `no name means the buyer themselves`() {
        assertNull(order().statedPickupName)
    }

    @Test
    fun `the buyer's own name means the buyer themselves`() {
        // The seller's card is titled with this name. Repeating it in a row below
        // would make the ordinary case look like an exception.
        assertNull(order(pickupName = "Alice Schmidt").statedPickupName)
    }

    @Test
    fun `a name differing only in spacing still means the buyer`() {
        assertNull(order(pickupName = "  Alice Schmidt  ").statedPickupName)
    }

    @Test
    fun `somebody else is worth naming`() {
        assertEquals("Jonas Weber", order(pickupName = "Jonas Weber").statedPickupName)
    }

    // ===== a number, and dialling it =====

    @Test
    fun `no number is not a number`() {
        assertNull(order().statedPickupPhone)
    }

    @Test
    fun `a number given is stated as the buyer wrote it`() {
        // What the seller reads is their spacing: it is how they would say it out loud.
        assertEquals("0170 1234567", order(pickupPhone = "0170 1234567").statedPickupPhone)
    }

    @Test
    fun `the shapes the order accepts all dial`() {
        // The checkout field takes digits, +, brackets, slashes, dots, dashes and
        // spaces, because that is how people write their own numbers. `tel:` takes
        // none of it. The seller's row puts the number through the same normalize its
        // own contact rows use, so this is the seam worth pinning down.
        val dialled = mapOf(
            "0170 1234567" to "01701234567",
            "+49 170 1234567" to "+491701234567",
            "030 / 12 34 56" to "030123456",
            "(030) 123456" to "030123456",
            "030-123.456" to "030123456"
        )
        dialled.forEach { (written, expected) ->
            assertEquals(
                expected,
                ContactChannel.PHONE.normalize(written),
                "'$written' does not dial"
            )
        }
    }

    @Test
    fun `a number with nothing dialable in it normalizes to nothing`() {
        // The row falls back to plain text rather than offering a tap that opens an
        // empty dialler.
        assertEquals("", ContactChannel.PHONE.normalize("( ) / -"))
    }

    // ===== the time, for completeness of the trio =====

    @Test
    fun `a pickup states its time`() {
        assertEquals("14:30", order(pickupTime = "14:30").statedPickupTime)
    }

    @Test
    fun `a delivery states no time, whatever it carries`() {
        // A delivery arrives when the round reaches it. The write path clears this
        // field; an older record could still hold one, and it is still not a promise.
        assertNull(
            order(pickupTime = "14:30", fulfilment = Fulfilment.DELIVERY).statedPickupTime
        )
    }

    @Test
    fun `a delivery still states who takes it in and on what number`() {
        // Unlike the time and the address, these two are not exclusive: somebody is
        // at the door.
        val delivery = order(
            pickupName = "Jonas Weber",
            pickupPhone = "0170 1234567",
            fulfilment = Fulfilment.DELIVERY
        )
        assertEquals("Jonas Weber", delivery.statedPickupName)
        assertEquals("0170 1234567", delivery.statedPickupPhone)
    }
}
