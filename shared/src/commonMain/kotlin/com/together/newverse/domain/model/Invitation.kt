package com.together.newverse.domain.model

/**
 * Represents a seller-to-buyer connection invitation.
 * Can be open (QR code / deep link) or targeted (seller-initiated to specific buyer).
 */
data class Invitation(
    val id: String,
    val sellerId: String,
    val buyerId: String? = null,
    val status: InvitationStatus = InvitationStatus.PENDING,
    val createdAt: Long,
    val expiresAt: Long,
    val sellerDisplayName: String = ""
) {
    fun isExpired(currentTimeMillis: Long): Boolean = currentTimeMillis >= expiresAt
}

enum class InvitationStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    EXPIRED
}
