package com.together.newverse.domain.model

/**
 * Represents a buyer/customer profile
 */
data class BuyerProfile(
    val id: String = "",
    val displayName: String = "",
    val emailAddress: String = "",
    val telephoneNumber: String = "",
    val photoUrl: String = "",
    val anonymous: Boolean = true,
    /**
     * The [Market.id] the buyer usually collects at, or empty.
     *
     * An id, not a name: it resolves against `markets/{sellerId}/{marketId}`. It was
     * a free-form string that nothing validated until markets became an entity.
     */
    val defaultMarket: String = "",
    val defaultPickUpTime: String = "",
    val placedOrderIds: Map<String, String> = emptyMap(), // Date -> orderId
    val favouriteArticles: List<String> = emptyList(), // List of article IDs marked as favourite
    val draftBasket: DraftBasket? = null, // Draft basket persisted for offline access
    val street: String = "",
    val houseNumber: String = "",
    val isSelfPickup: Boolean = false // "Selbstabholer" — no delivery address needed
)

/**
 * The display name a new profile is given before the buyer states their own.
 *
 * It is stored rather than rendered, so it travels with the profile — into the
 * order the seller reads, not just the profile screen.
 */
const val PLACEHOLDER_DISPLAY_NAME = "Bodenschatz"

/**
 * Every placeholder a stored profile may carry, including the two that predate
 * [PLACEHOLDER_DISPLAY_NAME] and are still in the database. A name counts as the
 * buyer's own only if it is none of these.
 */
val PLACEHOLDER_DISPLAY_NAMES = setOf(PLACEHOLDER_DISPLAY_NAME, "New User", "Neuer Kunde")
