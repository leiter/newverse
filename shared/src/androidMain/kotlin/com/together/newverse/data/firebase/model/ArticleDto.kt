package com.together.newverse.data.firebase.model

import com.together.newverse.domain.model.Article

/**
 * Firebase DTO for Article
 * Matches the Firebase Realtime Database structure
 */
data class ArticleDto(
    val productId: String = "",
    val productName: String = "",
    val available: Boolean = false,
    val unit: String = "",
    val price: Double = 0.0,
    val weighPerPiece: Double = 0.0, // Note: keeping original typo from universe for compatibility
    val imageUrl: String = "",
    val category: String = "",
    val searchTerms: String = "",
    val detailInfo: String = "",
) {
    /**
     * Convert Firebase DTO to domain model
     */
    fun toDomain(id: String): Article {
        return Article(
            id = id,
            productId = productId,
            productName = productName,
            available = available,
            unit = unit,
            price = price,
            weightPerPiece = weighPerPiece,
            imageUrl = imageUrl,
            category = category,
            searchTerms = searchTerms,
            detailInfo = detailInfo
        )
    }

    companion object {
        /**
         * Convert domain model to Firebase DTO
         */
        fun fromDomain(article: Article): ArticleDto {
            return ArticleDto(
                productId = article.productId,
                productName = article.productName,
                available = article.available,
                unit = article.unit,
                price = article.price,
                weighPerPiece = article.weightPerPiece,
                imageUrl = article.imageUrl,
                category = article.category,
                searchTerms = article.searchTerms,
                detailInfo = article.detailInfo
            )
        }
    }
}
