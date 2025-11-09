package com.together.newverse.domain.model

/**
 * Represents a product/article available for purchase
 */
data class Article(
    val id: String = "",
    val productId: String = "",
    val productName: String = "",
    val available: Boolean = false,
    val unit: String = "",
    val price: Double = 0.0,
    val weightPerPiece: Double = 0.0,
    val imageUrl: String = "",
    val category: String = "",
    val searchTerms: String = "",
    val detailInfo: String = "",
    val mode: Int = MODE_UNDEFINED
) {
    companion object {
        const val MODE_ADDED = 0
        const val MODE_CHANGED = 1
        const val MODE_MOVED = 2
        const val MODE_REMOVED = 3
        const val MODE_UNDEFINED = -1
    }
}
