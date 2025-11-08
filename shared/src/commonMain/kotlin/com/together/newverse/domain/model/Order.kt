package com.together.newverse.domain.model

/**
 * Represents a customer order
 */
data class Order(
    val id: String = "",
    val buyerProfile: BuyerProfile = BuyerProfile(),
    val createdDate: Long = 0L,
    val sellerId: String = "",
    val marketId: String = "",
    val pickUpDate: Long = 0L,
    val message: String = "",
    val notFavourite: Boolean = true,
    val articles: List<OrderedProduct> = emptyList(),
)
