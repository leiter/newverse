package com.together.newverse.domain.model

/**
 * Represents a market location with schedule
 */
data class Market(
    val id: String = "",
    val name: String = "",
    val street: String = "",
    val houseNumber: String = "",
    val city: String = "",
    val zipCode: String = "",
    val dayOfWeek: String = "",
    val begin: String = "",
    val end: String = "",
    val dayIndex: Int = -1,
    /**
     * How to reach *this stall*, set in the market dialog next to its address and
     * hours. Additive to [SellerProfile.contacts], never a replacement for them —
     * see `doc/seller-contact-design.md`.
     */
    val contacts: List<SellerContact> = emptyList(),
)
