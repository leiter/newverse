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
    val defaultMarket: String = "",
    val defaultPickUpTime: String = "",
    val placedOrderIds: Map<String, String> = emptyMap(), // Date -> orderId
    val favouriteArticles: List<String> = emptyList(), // List of article IDs marked as favourite
    val draftBasket: DraftBasket? = null, // Draft basket persisted for offline access
    val buyerUUID: String = "", // Stable UUID assigned by seller via deep link
    val street: String = "",
    val houseNumber: String = "",
    val isSelfPickup: Boolean = false // "Selbstabholer" — no delivery address needed
)
