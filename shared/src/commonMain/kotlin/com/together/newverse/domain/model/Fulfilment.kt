package com.together.newverse.domain.model

/**
 * How an order reaches the buyer.
 *
 * [PICKUP] is a market slot: the buyer comes to the stall, and `Order.marketId` and
 * `pickUpDate` say which and when. [DELIVERY] is an address, and the seller needs to
 * know it — which is the whole reason this exists. Before it, an order carried the
 * buyer's name and email and nothing else, so a delivery order was indistinguishable
 * from a pickup and the address was unreachable: `buyer_profile` is readable only by
 * the buyer themselves.
 */
enum class Fulfilment {
    PICKUP,
    DELIVERY;

    companion object {
        /**
         * What a buyer's profile implies, as the default for a new order.
         *
         * Note `BuyerProfile.isSelfPickup` defaults to `false`, so a buyer who has
         * never touched the toggle reads as DELIVERY. That is the stored default and
         * is not reinterpreted here — the order carries what the profile said, and an
         * address the seller can see is the point.
         */
        fun fromProfile(profile: BuyerProfile?): Fulfilment =
            if (profile?.isSelfPickup == true) PICKUP else DELIVERY

        /** Parse a stored value; anything unknown or absent reads as [PICKUP]. */
        fun stored(raw: String?): Fulfilment =
            entries.firstOrNull { it.name == raw } ?: PICKUP
    }
}

/**
 * Where a delivery goes, as it stood when the order was placed.
 *
 * **Snapshotted, never looked up.** The seller cannot read `buyer_profile` at all, and
 * even if they could, a buyer who moves house after ordering must not silently
 * redirect an order the seller may already have loaded onto a van. Same discipline as
 * `OrderedProduct.price` and `Order.pricingRevision`: the order records what was true
 * at the moment of commitment.
 *
 * Empty for a pickup order. [isComplete] is what the checkout gate checks.
 */
data class DeliveryAddress(
    val street: String = "",
    val houseNumber: String = "",
    val zipCode: String = "",
    val city: String = "",
    /** Anything the buyer added — a doorbell name, a gate code, "hinterm Haus". */
    val note: String = "",
) {
    /** Enough to actually deliver to. Zip and city are optional: one village, one seller. */
    val isComplete: Boolean get() = street.isNotBlank() && houseNumber.isNotBlank()

    val isEmpty: Boolean get() = street.isBlank() && houseNumber.isBlank()

    /** One line for the seller's order list and packing view. */
    fun oneLine(): String = listOf(
        listOf(street, houseNumber).filter { it.isNotBlank() }.joinToString(" "),
        listOf(zipCode, city).filter { it.isNotBlank() }.joinToString(" ")
    ).filter { it.isNotBlank() }.joinToString(", ")

    companion object {
        val NONE = DeliveryAddress()

        /** The address a buyer's profile currently holds, as a starting point. */
        fun fromProfile(profile: BuyerProfile?): DeliveryAddress = DeliveryAddress(
            street = profile?.street.orEmpty(),
            houseNumber = profile?.houseNumber.orEmpty()
        )
    }
}
