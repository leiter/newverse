package com.together.newverse.domain.model

/**
 * Represents a product within an order
 */
data class OrderedProduct(
    val id: String = "",
    val productId: String = "-1",
    val productName: String = "",
    val unit: String = "",
    val price: Double = 0.0,
    val amount: String = "",
    val amountCount: Double = 0.0,
    val piecesCount: Int = -1,
)
