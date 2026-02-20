package com.together.newverse.test

import com.together.newverse.domain.model.Invitation
import com.together.newverse.domain.model.InvitationStatus
import com.together.newverse.domain.repository.InvitationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Fake implementation of InvitationRepository for testing.
 */
class FakeInvitationRepository : InvitationRepository {

    private val invitations = mutableMapOf<String, Invitation>()
    private val _pendingFlow = MutableStateFlow<List<Invitation>>(emptyList())

    // Configurable failure flags
    var shouldFailCreate = false
    var shouldFailGet = false
    var shouldFailAccept = false
    var shouldFailReject = false
    var shouldFailRevoke = false
    var failureMessage = "Test error"

    // Tracking
    var createCalled = false
        private set
    var lastCreatedInvitation: Invitation? = null
        private set
    var acceptCalled = false
        private set
    var lastAcceptedInvitationId: String? = null
        private set
    var rejectCalled = false
        private set
    var lastRejectedInvitationId: String? = null
        private set
    var revokeCalled = false
        private set

    // For test setup
    var currentTimeMillis: Long = System.currentTimeMillis()

    fun addInvitation(invitation: Invitation) {
        invitations[invitation.id] = invitation
        updatePending()
    }

    fun reset() {
        invitations.clear()
        _pendingFlow.value = emptyList()
        shouldFailCreate = false
        shouldFailGet = false
        shouldFailAccept = false
        shouldFailReject = false
        shouldFailRevoke = false
        failureMessage = "Test error"
        createCalled = false
        lastCreatedInvitation = null
        acceptCalled = false
        lastAcceptedInvitationId = null
        rejectCalled = false
        lastRejectedInvitationId = null
        revokeCalled = false
    }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun createInvitation(
        sellerId: String,
        sellerDisplayName: String,
        expiresInMillis: Long,
        targetBuyerId: String?
    ): Result<Invitation> {
        createCalled = true
        if (shouldFailCreate) return Result.failure(Exception(failureMessage))

        val invitation = Invitation(
            id = Uuid.random().toString(),
            sellerId = sellerId,
            buyerId = targetBuyerId,
            status = InvitationStatus.PENDING,
            createdAt = currentTimeMillis,
            expiresAt = currentTimeMillis + expiresInMillis,
            sellerDisplayName = sellerDisplayName
        )
        invitations[invitation.id] = invitation
        lastCreatedInvitation = invitation
        updatePending()
        return Result.success(invitation)
    }

    override suspend fun getInvitation(invitationId: String): Result<Invitation> {
        if (shouldFailGet) return Result.failure(Exception(failureMessage))

        val invitation = invitations[invitationId]
            ?: return Result.failure(Exception("Invitation not found"))

        // Auto-mark expired
        if (invitation.isExpired(currentTimeMillis) && invitation.status == InvitationStatus.PENDING) {
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
        acceptCalled = true
        lastAcceptedInvitationId = invitationId
        if (shouldFailAccept) return Result.failure(Exception(failureMessage))

        val invitation = invitations[invitationId]
            ?: return Result.failure(Exception("Invitation not found"))

        if (invitation.status != InvitationStatus.PENDING) {
            return Result.failure(Exception("Invitation is no longer pending"))
        }

        if (invitation.isExpired(currentTimeMillis)) {
            invitations[invitationId] = invitation.copy(status = InvitationStatus.EXPIRED)
            return Result.failure(Exception("Invitation has expired"))
        }

        val accepted = invitation.copy(status = InvitationStatus.ACCEPTED, buyerId = buyerId)
        invitations[invitationId] = accepted
        updatePending()
        return Result.success(accepted)
    }

    override suspend fun rejectInvitation(
        invitationId: String,
        buyerId: String
    ): Result<Unit> {
        rejectCalled = true
        lastRejectedInvitationId = invitationId
        if (shouldFailReject) return Result.failure(Exception(failureMessage))

        val invitation = invitations[invitationId]
            ?: return Result.failure(Exception("Invitation not found"))

        invitations[invitationId] = invitation.copy(status = InvitationStatus.REJECTED)
        updatePending()
        return Result.success(Unit)
    }

    override fun observePendingInvitations(buyerId: String): Flow<List<Invitation>> {
        return _pendingFlow.map { all ->
            all.filter { inv ->
                inv.buyerId == buyerId &&
                    inv.status == InvitationStatus.PENDING &&
                    !inv.isExpired(currentTimeMillis)
            }
        }
    }

    override suspend fun revokeInvitation(invitationId: String): Result<Unit> {
        revokeCalled = true
        if (shouldFailRevoke) return Result.failure(Exception(failureMessage))

        val invitation = invitations[invitationId]
            ?: return Result.failure(Exception("Invitation not found"))

        invitations[invitationId] = invitation.copy(status = InvitationStatus.EXPIRED)
        updatePending()
        return Result.success(Unit)
    }

    private fun updatePending() {
        _pendingFlow.value = invitations.values.filter { inv ->
            inv.status == InvitationStatus.PENDING && !inv.isExpired(currentTimeMillis)
        }
    }
}
