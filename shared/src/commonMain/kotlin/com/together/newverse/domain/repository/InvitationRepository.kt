package com.together.newverse.domain.repository

import com.together.newverse.domain.model.Invitation
import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing seller-buyer connection invitations.
 * Handles creation, validation, acceptance/rejection, and observation of invitations.
 */
interface InvitationRepository {

    /**
     * Create a new invitation from a seller.
     * @param targetBuyerId if null, creates an open invitation (QR/deep link); if set, creates a targeted invitation
     */
    suspend fun createInvitation(
        sellerId: String,
        sellerDisplayName: String,
        expiresInMillis: Long = DEFAULT_EXPIRY,
        targetBuyerId: String? = null
    ): Result<Invitation>

    /**
     * Get an invitation by ID, checking expiry.
     */
    suspend fun getInvitation(invitationId: String): Result<Invitation>

    /**
     * Accept a pending invitation and register the buyer as a known client.
     */
    suspend fun acceptInvitation(invitationId: String, buyerId: String): Result<Invitation>

    /**
     * Reject a pending invitation.
     */
    suspend fun rejectInvitation(invitationId: String, buyerId: String): Result<Unit>

    /**
     * Observe pending invitations for a buyer (seller-initiated invitations).
     */
    fun observePendingInvitations(buyerId: String): Flow<List<Invitation>>

    /**
     * Revoke an invitation (seller cancels it).
     */
    suspend fun revokeInvitation(invitationId: String): Result<Unit>

    companion object {
        const val DEFAULT_EXPIRY = 24 * 60 * 60 * 1000L // 24 hours
    }
}
