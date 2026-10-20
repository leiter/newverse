package com.together.newverse.data.firebase.model

import com.together.newverse.domain.model.Order
import com.together.newverse.domain.model.OrderedProduct
import com.together.newverse.domain.model.BuyerProfile
import com.together.newverse.domain.model.PriceCause
import com.together.newverse.domain.model.PriceComponent

/**
 * Firebase DTO for Order
 * Matches the Firebase Realtime Database structure from universe project
 */
data class OrderDto(
    val buyerProfile: BuyerProfileDto = BuyerProfileDto(),
    val createdDate: Long = 0L,
    val sellerId: String = "",
    val marketId: String = "",
    val pricingRevision: Long = 0L,
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
            pricingRevision = pricingRevision,
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
                pricingRevision = order.pricingRevision,
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
    val id: String = "",  // usually empty; the article's database id is in productId
    val productId: String = "",
    val productName: String = "",
    val price: Double = 0.0,
    val basePrice: Double = 0.0,
    val priceComponents: List<PriceComponentDto> = emptyList(),
    val unit: String = "",
    val amountCount: Double = 0.0,
) {
    fun toDomain(): OrderedProduct {
        return OrderedProduct(
            id = id,
            productId = productId,
            productName = productName,
            price = price,
            basePrice = basePrice,
            priceComponents = priceComponents.mapNotNull { it.toDomain() },
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
                basePrice = product.basePrice,
                priceComponents = product.priceComponents.map { PriceComponentDto.fromDomain(it) },
                unit = product.unit,
                amountCount = product.amountCount
            )
        }
    }
}

/**
 * Firebase DTO for one line of a price breakdown.
 *
 * [toDomain] returns null for a cause this build does not know, so an older app
 * reading a newer order drops the line rather than inventing a cause for it.
 */
data class PriceComponentDto(
    val cause: String = "",
    val amount: Double = 0.0,
    val note: String = "",
) {
    fun toDomain(): PriceComponent? {
        val parsed = PriceCause.entries.firstOrNull { it.name == cause } ?: return null
        return PriceComponent(cause = parsed, amount = amount, note = note)
    }

    companion object {
        fun fromDomain(component: PriceComponent) = PriceComponentDto(
            cause = component.cause.name,
            amount = component.amount,
            note = component.note
        )
    }
}
