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
    val knownClientIds: List<String> = emptyList(),
)
