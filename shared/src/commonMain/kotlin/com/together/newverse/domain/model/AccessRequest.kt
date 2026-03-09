package com.together.newverse.domain.model

data class AccessRequest(
    val sellerId: String,
    val buyerUUID: String,
    val buyerDisplayName: String,
    val requestedAt: Long
)
