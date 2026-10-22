package com.together.newverse.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A delivery order says where it goes, and keeps saying it.
 *
 * The seller cannot read `buyer_profile` — it is readable only by the buyer — so an
 * order that does not carry its own destination has none, and no way to recover one.
 * Before this existed an order carried the buyer's name and email and nothing else,
 * which made a delivery indistinguishable from a pickup.
 *
 * `doc/guides/ordering-business-rules.md` § Delivery.
 */
class FulfilmentTest {

    private fun profile(selfPickup: Boolean, street: String = "Hauptstr.", nr: String = "4") =
        BuyerProfile(
            id = "b1",
            displayName = "Test",
            street = street,
            houseNumber = nr,
            isSelfPickup = selfPickup
        )

    // ===== what the profile implies =====

    @Test
    fun `a self-pickup buyer defaults to pickup`() {
        assertEquals(Fulfilment.PICKUP, Fulfilment.fromProfile(profile(selfPickup = true)))
    }

    @Test
    fun `a buyer who has not opted into self-pickup defaults to delivery`() {
        // isSelfPickup defaults to false, so this is also what an untouched profile
        // means. The point of carrying it on the order is that the seller finds out.
        assertEquals(Fulfilment.DELIVERY, Fulfilment.fromProfile(profile(selfPickup = false)))
    }

    @Test
    fun `no profile at all reads as delivery, not as a silent pickup`() {
        assertEquals(Fulfilment.DELIVERY, Fulfilment.fromProfile(null))
    }

    // ===== what the market allows =====

    private fun market(id: String = "m1", delivers: Boolean = true) =
        Market(id = id, name = "Markt $id", dayIndex = 3, allowsDelivery = delivers)

    @Test
    fun `a pickup-only market turns a delivery into a pickup`() {
        assertEquals(
            Fulfilment.PICKUP,
            Fulfilment.forMarket(market(delivers = false), Fulfilment.DELIVERY)
        )
    }

    @Test
    fun `a market that delivers leaves the choice alone`() {
        assertEquals(
            Fulfilment.DELIVERY,
            Fulfilment.forMarket(market(delivers = true), Fulfilment.DELIVERY)
        )
        assertEquals(
            Fulfilment.PICKUP,
            Fulfilment.forMarket(market(delivers = true), Fulfilment.PICKUP)
        )
    }

    @Test
    fun `a pickup-only market does not disturb a pickup`() {
        assertEquals(
            Fulfilment.PICKUP,
            Fulfilment.forMarket(market(delivers = false), Fulfilment.PICKUP)
        )
    }

    @Test
    fun `no market chosen is not a pickup-only market`() {
        // An order from before markets were an entity, or a basket with no slot yet.
        // Neither says anything about delivery, and reading it as pickup-only would
        // withdraw delivery from every such order.
        assertEquals(Fulfilment.DELIVERY, Fulfilment.forMarket(null, Fulfilment.DELIVERY))
    }

    @Test
    fun `a market stored before the field keeps delivery`() {
        // The default is true, which is what the database rules also read an absent
        // allowsDelivery as. Flipping that default would strip delivery from every
        // market already in the database.
        assertTrue(Market(id = "m1").allowsDelivery)
        assertEquals(
            Fulfilment.DELIVERY,
            Fulfilment.forMarket(Market(id = "m1"), Fulfilment.DELIVERY)
        )
    }

    @Test
    fun `the market outranks a profile that asks for delivery`() {
        // The whole point: isSelfPickup defaults to false, so every untouched profile
        // asks for delivery and a pickup-only stall is the common path.
        val wanted = Fulfilment.fromProfile(profile(selfPickup = false))

        assertEquals(Fulfilment.DELIVERY, wanted)
        assertEquals(Fulfilment.PICKUP, Fulfilment.forMarket(market(delivers = false), wanted))
    }

    @Test
    fun `an order already placed as a delivery is not rewritten by the market`() {
        // Same discipline as a closure: the market turning pickup-only does not retract
        // an order the seller may already have loaded onto a van. Only an edit, which
        // goes through forMarket again, moves it.
        val placed = Order(
            fulfilment = Fulfilment.DELIVERY,
            deliveryAddress = DeliveryAddress("Hauptstr.", "4"),
            marketId = "m1",
            status = OrderStatus.PLACED
        )

        assertEquals(Fulfilment.DELIVERY, placed.fulfilment)
        assertTrue(placed.isDelivery)
    }

    // ===== reading stored orders =====

    @Test
    fun `an order written before fulfilment existed reads as pickup`() {
        // Every such order was collected at a stall; delivery did not exist.
        assertEquals(Fulfilment.PICKUP, Fulfilment.stored(null))
        assertEquals(Fulfilment.PICKUP, Fulfilment.stored(""))
    }

    @Test
    fun `an unknown stored value reads as pickup rather than throwing`() {
        assertEquals(Fulfilment.PICKUP, Fulfilment.stored("TELEPORT"))
    }

    @Test
    fun `both known values round-trip`() {
        Fulfilment.entries.forEach { f ->
            assertEquals(f, Fulfilment.stored(f.name))
        }
    }

    // ===== the address =====

    @Test
    fun `street and number are what make an address deliverable`() {
        assertTrue(DeliveryAddress(street = "Hauptstr.", houseNumber = "4").isComplete)
        assertFalse(DeliveryAddress(street = "Hauptstr.").isComplete)
        assertFalse(DeliveryAddress(houseNumber = "4").isComplete)
        assertFalse(DeliveryAddress.NONE.isComplete)
    }

    @Test
    fun `postcode and town are optional`() {
        // One seller, one area. Requiring them would block a buyer who leaves them out.
        assertTrue(DeliveryAddress(street = "Hauptstr.", houseNumber = "4", city = "").isComplete)
    }

    @Test
    fun `whitespace is not an address`() {
        assertFalse(DeliveryAddress(street = "   ", houseNumber = "4").isComplete)
    }

    @Test
    fun `oneLine reads as an address and omits what is missing`() {
        assertEquals(
            "Hauptstr. 4, 15517 Fürstenwalde",
            DeliveryAddress("Hauptstr.", "4", "15517", "Fürstenwalde").oneLine()
        )
        assertEquals("Hauptstr. 4", DeliveryAddress("Hauptstr.", "4").oneLine())
        assertEquals("", DeliveryAddress.NONE.oneLine())
    }

    @Test
    fun `an address seeded from a profile carries its street and number`() {
        val seeded = DeliveryAddress.fromProfile(profile(selfPickup = false, street = "Bahnhofstr.", nr = "12a"))

        assertEquals("Bahnhofstr.", seeded.street)
        assertEquals("12a", seeded.houseNumber)
        assertTrue(seeded.isComplete)
    }

    // ===== the order =====

    @Test
    fun `a delivery order with an address is deliverable`() {
        val order = Order(
            fulfilment = Fulfilment.DELIVERY,
            deliveryAddress = DeliveryAddress("Hauptstr.", "4")
        )

        assertTrue(order.isDelivery)
        assertFalse(order.isDeliveryWithoutAddress)
    }

    @Test
    fun `a delivery order with no address is flagged, not silently shown as blank`() {
        // Should be unreachable — the checkout gate refuses it — but orders written
        // before fulfilment existed carry neither flag nor address.
        val order = Order(fulfilment = Fulfilment.DELIVERY)

        assertTrue(order.isDeliveryWithoutAddress)
    }

    @Test
    fun `a pickup order is never flagged for a missing address`() {
        val order = Order(fulfilment = Fulfilment.PICKUP)

        assertFalse(order.isDelivery)
        assertFalse(order.isDeliveryWithoutAddress)
    }

    @Test
    fun `an order defaults to pickup`() {
        // So a legacy order, or one built by code that predates this, is never
        // mistaken for a delivery the seller has to act on.
        assertEquals(Fulfilment.PICKUP, Order().fulfilment)
        assertFalse(Order().isDelivery)
    }

    @Test
    fun `the snapshot does not follow the profile`() {
        // The case that matters: the buyer switches to delivery after ordering.
        val placed = Order(
            fulfilment = Fulfilment.PICKUP,
            marketId = "m1",
            status = OrderStatus.PLACED
        )

        val nowWants = Fulfilment.fromProfile(profile(selfPickup = false))

        assertEquals(Fulfilment.DELIVERY, nowWants)
        assertEquals(
            Fulfilment.PICKUP,
            placed.fulfilment,
            "a profile change re-aimed an order the seller may already have packed"
        )
    }

    @Test
    fun `reordering carries nothing stale about where it goes`() {
        // prepareForReorder builds a fresh draft; the fulfilment comes from the basket
        // again rather than being inherited unexamined.
        val old = Order(
            fulfilment = Fulfilment.DELIVERY,
            deliveryAddress = DeliveryAddress("Hauptstr.", "4"),
            status = OrderStatus.COMPLETED
        )

        val draft = old.prepareForReorder(profile(selfPickup = false))

        assertEquals(OrderStatus.DRAFT, draft.status)
        // Carried, because the buyer reordering the same thing to the same place is
        // the common case — but the checkout re-snapshots it from the basket.
        assertEquals(Fulfilment.DELIVERY, draft.fulfilment)
        assertEquals("Hauptstr.", draft.deliveryAddress.street)
    }
}
