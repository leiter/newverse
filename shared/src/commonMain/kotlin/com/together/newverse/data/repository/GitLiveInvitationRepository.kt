package com.together.newverse.data.repository

import com.together.newverse.domain.model.Invitation
import com.together.newverse.domain.model.InvitationStatus
import com.together.newverse.domain.repository.InvitationRepository
import com.together.newverse.domain.repository.ProfileRepository
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.database.database
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Firebase Realtime Database implementation of InvitationRepository.
 *
 * Firebase structure:
 * - invitations/{invitationId}/ - invitation data
 * - buyer_invitations/{buyerId}/{invitationId}: true - index for buyer queries
 */
class GitLiveInvitationRepository(
    private val profileRepository: ProfileRepository
) : InvitationRepository {

    private val database = Firebase.database
    private val invitationsRef = database.reference("invitations")
    private val buyerInvitationsRef = database.reference("buyer_invitations")

    // Local cache for observed invitations
    private val _pendingInvitations = MutableStateFlow<List<Invitation>>(emptyList())

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun createInvitation(
        sellerId: String,
        sellerDisplayName: String,
        expiresInMillis: Long,
        targetBuyerId: String?
    ): Result<Invitation> {
        return try {
            val now = Clock.System.now().toEpochMilliseconds()
            val id = Uuid.random().toString()

            val invitation = Invitation(
                id = id,
                sellerId = sellerId,
                buyerId = targetBuyerId,
                status = InvitationStatus.PENDING,
                createdAt = now,
                expiresAt = now + expiresInMillis,
                sellerDisplayName = sellerDisplayName
            )

            // Write invitation data
            val ref = invitationsRef.child(id)
            ref.child("id").setValue(id)
            ref.child("sellerId").setValue(sellerId)
            ref.child("buyerId").setValue(targetBuyerId)
            ref.child("status").setValue(InvitationStatus.PENDING.name)
            ref.child("createdAt").setValue(now)
            ref.child("expiresAt").setValue(now + expiresInMillis)
            ref.child("sellerDisplayName").setValue(sellerDisplayName)

            // If targeted, add to buyer_invitations index
            if (targetBuyerId != null) {
                buyerInvitationsRef.child(targetBuyerId).child(id).setValue(true)
            }

            println("Invitation created: $id for seller $sellerId")
            Result.success(invitation)
        } catch (e: Exception) {
            println("Failed to create invitation: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun getInvitation(invitationId: String): Result<Invitation> {
        return try {
            val snapshot = invitationsRef.child(invitationId).valueEvents
                .let { flow ->
                    var result: Invitation? = null
                    flow.collect { snap ->
                        if (snap.exists) {
                            result = parseInvitation(snap)
                        }
                        return@collect
                    }
                    result
                }

            if (snapshot == null) {
                return Result.failure(Exception("Invitation not found"))
            }

            // Auto-mark expired
            val now = Clock.System.now().toEpochMilliseconds()
            if (snapshot.isExpired(now) && snapshot.status == InvitationStatus.PENDING) {
                invitationsRef.child(invitationId).child("status")
                    .setValue(InvitationStatus.EXPIRED.name)
                return Result.success(snapshot.copy(status = InvitationStatus.EXPIRED))
            }

            Result.success(snapshot)
        } catch (e: Exception) {
            println("Failed to get invitation: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun acceptInvitation(
        invitationId: String,
        buyerId: String
    ): Result<Invitation> {
        return try {
            val invitationResult = getInvitation(invitationId)
            val invitation = invitationResult.getOrElse { return Result.failure(it) }

            // Validate
            if (invitation.status != InvitationStatus.PENDING) {
                return Result.failure(Exception("Invitation is no longer pending (status: ${invitation.status})"))
            }

            val now = Clock.System.now().toEpochMilliseconds()
            if (invitation.isExpired(now)) {
                invitationsRef.child(invitationId).child("status")
                    .setValue(InvitationStatus.EXPIRED.name)
                return Result.failure(Exception("Invitation has expired"))
            }

            // If targeted, verify buyer matches
            if (invitation.buyerId != null && invitation.buyerId != buyerId) {
                return Result.failure(Exception("This invitation was not sent to you"))
            }

            // Update status
            val ref = invitationsRef.child(invitationId)
            ref.child("status").setValue(InvitationStatus.ACCEPTED.name)
            ref.child("buyerId").setValue(buyerId)

            // Register buyer as known client
            profileRepository.addKnownClient(invitation.sellerId, buyerId)

            // Remove from buyer_invitations index
            buyerInvitationsRef.child(buyerId).child(invitationId).removeValue()

            val accepted = invitation.copy(
                status = InvitationStatus.ACCEPTED,
                buyerId = buyerId
            )
            println("Invitation $invitationId accepted by buyer $buyerId")
            Result.success(accepted)
        } catch (e: Exception) {
            println("Failed to accept invitation: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun rejectInvitation(
        invitationId: String,
        buyerId: String
    ): Result<Unit> {
        return try {
            invitationsRef.child(invitationId).child("status")
                .setValue(InvitationStatus.REJECTED.name)
            buyerInvitationsRef.child(buyerId).child(invitationId).removeValue()
            println("Invitation $invitationId rejected by buyer $buyerId")
            Result.success(Unit)
        } catch (e: Exception) {
            println("Failed to reject invitation: ${e.message}")
            Result.failure(e)
        }
    }

    override fun observePendingInvitations(buyerId: String): Flow<List<Invitation>> {
        // Watch buyer_invitations/{buyerId} for changes
        return buyerInvitationsRef.child(buyerId).valueEvents.map { snapshot ->
            if (!snapshot.exists) return@map emptyList()

            val invitations = mutableListOf<Invitation>()
            snapshot.children.forEach { child ->
                val invitationId = child.key ?: return@forEach
                try {
                    val invResult = getInvitation(invitationId)
                    invResult.getOrNull()?.let { inv ->
                        if (inv.status == InvitationStatus.PENDING &&
                            !inv.isExpired(Clock.System.now().toEpochMilliseconds())
                        ) {
                            invitations.add(inv)
                        }
                    }
                } catch (e: Exception) {
                    println("Failed to fetch invitation $invitationId: ${e.message}")
                }
            }
            invitations
        }
    }

    override suspend fun revokeInvitation(invitationId: String): Result<Unit> {
        return try {
            val invitationResult = getInvitation(invitationId)
            val invitation = invitationResult.getOrElse { return Result.failure(it) }

            invitationsRef.child(invitationId).child("status")
                .setValue(InvitationStatus.EXPIRED.name)

            // Clean up buyer index if targeted
            invitation.buyerId?.let { buyerId ->
                buyerInvitationsRef.child(buyerId).child(invitationId).removeValue()
            }

            println("Invitation $invitationId revoked")
            Result.success(Unit)
        } catch (e: Exception) {
            println("Failed to revoke invitation: ${e.message}")
            Result.failure(e)
        }
    }

    private fun parseInvitation(snapshot: dev.gitlive.firebase.database.DataSnapshot): Invitation {
        return Invitation(
            id = snapshot.child("id").value<String>(),
            sellerId = snapshot.child("sellerId").value<String>(),
            buyerId = snapshot.child("buyerId").value<String?>(),
            status = try {
                InvitationStatus.valueOf(snapshot.child("status").value<String>())
            } catch (e: Exception) {
                InvitationStatus.PENDING
            },
            createdAt = snapshot.child("createdAt").value<Long>(),
            expiresAt = snapshot.child("expiresAt").value<Long>(),
            sellerDisplayName = try {
                snapshot.child("sellerDisplayName").value<String>()
            } catch (e: Exception) {
                ""
            }
        )
    }
}
