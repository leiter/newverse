package com.together.newverse.domain.model

/**
 * Represents a seller/vendor profile
 */
data class SellerProfile(
    val id: String = "",
    val displayName: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val street: String = "",
    val houseNumber: String = "",
    val city: String = "",
    val zipCode: String = "",
    val telephoneNumber: String = "",
    val lat: String = "",
    val lng: String = "",
    val sellerId: String = "",
    val markets: List<Market> = emptyList(),
    val urls: List<String> = emptyList(),
    /**
     * How to reach the business, shown for every market. [Market.contacts] adds to
     * this per stall. [telephoneNumber] stays the seller's own record of their
     * number and is not published through it.
     */
    val contacts: List<SellerContact> = emptyList(),
    /**
     * Days on which a market does not happen. An entry with no market id closes
     * every market. Stored here rather than in a node of its own: a seller has a
     * handful a year, and the existing field-level read rule already lets buyers
     * see them without a rules change.
     */
    val closures: List<MarketClosure> = emptyList(),
)
