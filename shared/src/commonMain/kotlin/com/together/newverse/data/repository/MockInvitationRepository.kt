package com.together.newverse.data.repository

import com.together.newverse.domain.model.Invitation
import com.together.newverse.domain.model.InvitationStatus
import com.together.newverse.domain.repository.InvitationRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * In-memory implementation of InvitationRepository for development/demo mode.
 */
class MockInvitationRepository : InvitationRepository {

    private val invitations = mutableMapOf<String, Invitation>()
    private val _pendingInvitations = MutableStateFlow<List<Invitation>>(emptyList())

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun createInvitation(
        sellerId: String,
        sellerDisplayName: String,
        expiresInMillis: Long,
        targetBuyerId: String?
    ): Result<Invitation> {
        delay(200)
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

        invitations[id] = invitation

        if (targetBuyerId != null) {
            updatePendingInvitations(targetBuyerId)
        }

        return Result.success(invitation)
    }

    override suspend fun getInvitation(invitationId: String): Result<Invitation> {
        delay(100)
        val invitation = invitations[invitationId]
            ?: return Result.failure(Exception("Invitation not found"))

        val now = Clock.System.now().toEpochMilliseconds()
        if (invitation.isExpired(now) && invitation.status == InvitationStatus.PENDING) {
            val expired = invitation.copy(status = InvitationStatus.EXPIRED)
            invitations[invitationId] = expired
            return Result.success(expired)
        }

        return Result.success(invitation)
    }

    override suspend fun acceptInvitation(
        invitationId: String,
        buyerId: String
    ): Result<Invitation> {
        delay(200)
        val invitation = invitations[invitationId]
            ?: return Result.failure(Exception("Invitation not found"))

        if (invitation.status != InvitationStatus.PENDING) {
            return Result.failure(Exception("Invitation is no longer pending"))
        }

        val now = Clock.System.now().toEpochMilliseconds()
        if (invitation.isExpired(now)) {
            invitations[invitationId] = invitation.copy(status = InvitationStatus.EXPIRED)
            return Result.failure(Exception("Invitation has expired"))
        }

        val accepted = invitation.copy(
            status = InvitationStatus.ACCEPTED,
            buyerId = buyerId
        )
        invitations[invitationId] = accepted
        updatePendingInvitations(buyerId)

        return Result.success(accepted)
    }

    override suspend fun rejectInvitation(
        invitationId: String,
        buyerId: String
    ): Result<Unit> {
        delay(100)
        val invitation = invitations[invitationId]
            ?: return Result.failure(Exception("Invitation not found"))

        invitations[invitationId] = invitation.copy(status = InvitationStatus.REJECTED)
        updatePendingInvitations(buyerId)

        return Result.success(Unit)
    }

    override fun observePendingInvitations(buyerId: String): Flow<List<Invitation>> {
        return _pendingInvitations.map { all ->
            val now = Clock.System.now().toEpochMilliseconds()
            all.filter { inv ->
                inv.buyerId == buyerId &&
                    inv.status == InvitationStatus.PENDING &&
                    !inv.isExpired(now)
            }
        }
    }

    override suspend fun revokeInvitation(invitationId: String): Result<Unit> {
        delay(100)
        val invitation = invitations[invitationId]
            ?: return Result.failure(Exception("Invitation not found"))

        invitations[invitationId] = invitation.copy(status = InvitationStatus.EXPIRED)
        invitation.buyerId?.let { updatePendingInvitations(it) }

        return Result.success(Unit)
    }

    private fun updatePendingInvitations(buyerId: String) {
        val now = Clock.System.now().toEpochMilliseconds()
        _pendingInvitations.value = invitations.values.filter { inv ->
            inv.buyerId == buyerId &&
                inv.status == InvitationStatus.PENDING &&
                !inv.isExpired(now)
        }
    }
}
