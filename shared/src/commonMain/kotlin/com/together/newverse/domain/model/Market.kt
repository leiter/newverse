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
)
