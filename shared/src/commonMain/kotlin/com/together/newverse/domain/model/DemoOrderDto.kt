package com.together.newverse.domain.model

import kotlinx.serialization.Serializable

/**
 * Serializable DTO for persisting demo orders locally.
 * Keeps domain models (Order, OrderedProduct) free of serialization annotations.
 */
@Serializable
data class DemoOrderDto(
    val id: String,
    val buyerDisplayName: String,
    val createdDate: Long,
    val sellerId: String,
    val marketId: String,
    val pickUpDate: Long,
    val message: String,
    val status: String,
    val articles: List<DemoOrderedProductDto>,
    /** Absent in records written before [OrderOrigin] existed; those are all demo orders. */
    val origin: String = OrderOrigin.DEMO.name
)

@Serializable
data class DemoOrderedProductDto(
    val id: String,
    val productId: String,
    val productName: String,
    val unit: String,
    val price: Double,
    val amount: String,
    val amountCount: Double,
    val piecesCount: Int
)

fun Order.toDemoDto(): DemoOrderDto = DemoOrderDto(
    id = id,
    buyerDisplayName = buyerProfile.displayName,
    createdDate = createdDate,
    sellerId = sellerId,
    marketId = marketId,
    pickUpDate = pickUpDate,
    message = message,
    status = status.name,
    articles = articles.map { it.toDemoDto() },
    origin = origin.name
)

fun DemoOrderDto.toOrder(): Order = Order(
    id = id,
    buyerProfile = BuyerProfile(displayName = buyerDisplayName),
    createdDate = createdDate,
    sellerId = sellerId,
    marketId = marketId,
    pickUpDate = pickUpDate,
    message = message,
    // A stored "DEMO_ORDER" predates the status/origin split and means PLACED.
    status = OrderStatus.entries.find { it.name == status } ?: OrderStatus.PLACED,
    articles = articles.map { it.toOrderedProduct() },
    origin = OrderOrigin.entries.find { it.name == origin } ?: OrderOrigin.DEMO
)

private fun OrderedProduct.toDemoDto(): DemoOrderedProductDto = DemoOrderedProductDto(
    id = id,
    productId = productId,
    productName = productName,
    unit = unit,
    price = price,
    amount = amount,
    amountCount = amountCount,
    piecesCount = piecesCount
)

private fun DemoOrderedProductDto.toOrderedProduct(): OrderedProduct = OrderedProduct(
    id = id,
    productId = productId,
    productName = productName,
    unit = unit,
    price = price,
    amount = amount,
    amountCount = amountCount,
    piecesCount = piecesCount
)
