package com.together.newverse.data.firebase.model

import com.together.newverse.domain.model.Order
import com.together.newverse.domain.model.OrderedProduct
import com.together.newverse.domain.model.BuyerProfile

/**
 * Firebase DTO for Order
 * Matches the Firebase Realtime Database structure from universe project
 */
data class OrderDto(
    val buyerProfile: BuyerProfileDto = BuyerProfileDto(),
    val createdDate: Long = 0L,
    val sellerId: String = "",
    val marketId: String = "",
    val pickUpDate: Long = 0L,
    val message: String = "",
    val notFavourite: Boolean = true,
    val articles: List<OrderedProductDto> = emptyList(),
    val hiddenBySeller: Boolean = false,
    val hiddenByBuyer: Boolean = false
) {
    /**
     * Convert Firebase DTO to domain model
     */
    fun toDomain(id: String): Order {
        return Order(
            id = id,
            buyerProfile = buyerProfile.toDomain(),
            createdDate = createdDate,
            sellerId = sellerId,
            marketId = marketId,
            pickUpDate = pickUpDate,
            message = message,
            notFavourite = notFavourite,
            articles = articles.map { it.toDomain() },
            hiddenBySeller = hiddenBySeller,
            hiddenByBuyer = hiddenByBuyer
        )
    }

    companion object {
        /**
         * Convert domain model to Firebase DTO
         */
        fun fromDomain(order: Order): OrderDto {
            return OrderDto(
                buyerProfile = BuyerProfileDto.fromDomain(order.buyerProfile),
                createdDate = order.createdDate,
                sellerId = order.sellerId,
                marketId = order.marketId,
                pickUpDate = order.pickUpDate,
                message = order.message,
                notFavourite = order.notFavourite,
                articles = order.articles.map { OrderedProductDto.fromDomain(it) },
                hiddenBySeller = order.hiddenBySeller,
                hiddenByBuyer = order.hiddenByBuyer
            )
        }
    }
}

/**
 * Firebase DTO for BuyerProfile
 */
data class BuyerProfileDto(
    val displayName: String = "",
    val emailAddress: String = "",
    val telephoneNumber: String = "",
    val photoUrl: String = "",
    val anonymous: Boolean = true,
    val defaultMarket: String = "",
    val defaultTime: String = "", // Note: using "defaultTime" to match universe project
    val placedOrderIds: Map<String, String> = emptyMap(),
    val favouriteProductIds: List<String> = emptyList(), // Firebase field name
) {
    fun toDomain(): BuyerProfile {
        return BuyerProfile(
            displayName = displayName,
            emailAddress = emailAddress,
            telephoneNumber = telephoneNumber,
            photoUrl = photoUrl,
            anonymous = anonymous,
            defaultMarket = defaultMarket,
            defaultPickUpTime = defaultTime,
            placedOrderIds = placedOrderIds,
            favouriteArticles = favouriteProductIds // Map to domain name
        )
    }

    companion object {
        fun fromDomain(profile: BuyerProfile): BuyerProfileDto {
            return BuyerProfileDto(
                displayName = profile.displayName,
                emailAddress = profile.emailAddress,
                telephoneNumber = profile.telephoneNumber,
                photoUrl = profile.photoUrl,
                anonymous = profile.anonymous,
                defaultMarket = profile.defaultMarket,
                defaultTime = profile.defaultPickUpTime,
                placedOrderIds = profile.placedOrderIds,
                favouriteProductIds = profile.favouriteArticles // Map from domain name
            )
        }
    }
}

/**
 * Firebase DTO for OrderedProduct
 */
data class OrderedProductDto(
    val id: String = "",  // Firebase article ID
    val productId: String = "",
    val productName: String = "",
    val price: Double = 0.0,
    val unit: String = "",
    val amountCount: Double = 0.0,
) {
    fun toDomain(): OrderedProduct {
        return OrderedProduct(
            id = id,  // Firebase article ID for matching
            productId = productId,
            productName = productName,
            price = price,
            unit = unit,
            amountCount = amountCount
        )
    }

    companion object {
        fun fromDomain(product: OrderedProduct): OrderedProductDto {
            return OrderedProductDto(
                id = product.id,
                productId = product.productId,
                productName = product.productName,
                price = product.price,
                unit = product.unit,
                amountCount = product.amountCount
            )
        }
    }
}
