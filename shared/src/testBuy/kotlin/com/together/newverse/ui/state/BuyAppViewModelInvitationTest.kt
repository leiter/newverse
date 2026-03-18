package com.together.newverse.ui.state

import com.together.newverse.domain.model.Invitation
import com.together.newverse.domain.model.InvitationStatus
import com.together.newverse.test.FakeArticleRepository
import com.together.newverse.test.FakeAuthRepository
import com.together.newverse.test.FakeBasketRepository
import com.together.newverse.test.FakeInvitationRepository
import com.together.newverse.test.FakeOrderRepository
import com.together.newverse.test.FakeProfileRepository
import com.together.newverse.test.FakeSellerConfig
import com.together.newverse.test.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for invitation-related functionality in BuyAppViewModel.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BuyAppViewModelInvitationTest {

    private val dispatcherRule = MainDispatcherRule()
    private lateinit var articleRepository: FakeArticleRepository
    private lateinit var orderRepository: FakeOrderRepository
    private lateinit var profileRepository: FakeProfileRepository
    private lateinit var authRepository: FakeAuthRepository
    private lateinit var basketRepository: FakeBasketRepository
    private lateinit var invitationRepository: FakeInvitationRepository
    private lateinit var sellerConfig: FakeSellerConfig

    @BeforeTest
    fun setup() {
        dispatcherRule.setup()
        articleRepository = FakeArticleRepository()
        orderRepository = FakeOrderRepository()
        profileRepository = FakeProfileRepository()
        authRepository = FakeAuthRepository()
        basketRepository = FakeBasketRepository()
        invitationRepository = FakeInvitationRepository()
        sellerConfig = FakeSellerConfig()
    }

    @AfterTest
    fun tearDown() {
        articleRepository.reset()
        orderRepository.reset()
        profileRepository.reset()
        authRepository.reset()
        basketRepository.reset()
        invitationRepository.reset()
        dispatcherRule.tearDown()
    }

    private fun createViewModel() = BuyAppViewModel(
        articleRepository = articleRepository,
        orderRepository = orderRepository,
        profileRepository = profileRepository,
        authRepository = authRepository,
        basketRepository = basketRepository,
        sellerConfig = sellerConfig,
        invitationRepository = invitationRepository
    )

    private fun createTestInvitation(
        id: String = "invite-1",
        sellerId: String = "seller-1",
        buyerId: String? = null,
        status: InvitationStatus = InvitationStatus.PENDING,
        expiresAt: Long = System.currentTimeMillis() + 86400000L,
        sellerDisplayName: String = "Test Seller"
    ) = Invitation(
        id = id,
        sellerId = sellerId,
        buyerId = buyerId,
        status = status,
        createdAt = System.currentTimeMillis(),
        expiresAt = expiresAt,
        sellerDisplayName = sellerDisplayName
    )

    // ===== Expired Invitation Tests =====

    @Test
    fun `expired invitation is rejected with error`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Create an expired invitation
        val expired = createTestInvitation(
            expiresAt = System.currentTimeMillis() - 1000L // already expired
        )
        invitationRepository.addInvitation(expired)

        viewModel.dispatch(
            BuySellerAction.ConnectWithInvitation(
                sellerId = expired.sellerId,
                invitationId = expired.id,
                expiresAt = expired.expiresAt
            )
        )
        advanceUntilIdle()

        // Should NOT show confirmation dialog
        assertNull(viewModel.state.value.showConnectionConfirmDialog)
    }

    // ===== Valid Invitation Shows Dialog =====

    @Test
    fun `valid invitation shows confirmation dialog`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val invitation = createTestInvitation()
        invitationRepository.addInvitation(invitation)

        viewModel.dispatch(
            BuySellerAction.ConnectWithInvitation(
                sellerId = invitation.sellerId,
                invitationId = invitation.id,
                expiresAt = invitation.expiresAt
            )
        )
        advanceUntilIdle()

        // Should show confirmation dialog
        val dialog = viewModel.state.value.showConnectionConfirmDialog
        assertNotNull(dialog)
        assertEquals("Test Seller", dialog.sellerDisplayName)
        assertEquals(invitation.id, dialog.invitation.id)
    }

    // ===== Confirm Connection =====

    @Test
    fun `confirming connection accepts invitation and connects to seller`() = runTest {
        authRepository.setCurrentUserId("buyer-1")
        val viewModel = createViewModel()
        advanceUntilIdle()

        val invitation = createTestInvitation()
        invitationRepository.addInvitation(invitation)

        // Show dialog first
        viewModel.dispatch(
            BuySellerAction.ConnectWithInvitation(
                sellerId = invitation.sellerId,
                invitationId = invitation.id,
                expiresAt = invitation.expiresAt
            )
        )
        advanceUntilIdle()

        // Confirm
        viewModel.dispatch(BuySellerAction.ConfirmConnection)
        advanceUntilIdle()

        // Dialog should be dismissed
        assertNull(viewModel.state.value.showConnectionConfirmDialog)
        // Invitation should have been accepted
        assertTrue(invitationRepository.acceptCalled)
        assertEquals(invitation.id, invitationRepository.lastAcceptedInvitationId)
    }

    // ===== Dismiss Dialog =====

    @Test
    fun `dismissing connection dialog clears dialog state`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val invitation = createTestInvitation()
        invitationRepository.addInvitation(invitation)

        viewModel.dispatch(
            BuySellerAction.ConnectWithInvitation(
                sellerId = invitation.sellerId,
                invitationId = invitation.id,
                expiresAt = invitation.expiresAt
            )
        )
        advanceUntilIdle()

        // Dismiss
        viewModel.dispatch(BuySellerAction.DismissConnectionDialog)
        advanceUntilIdle()

        assertNull(viewModel.state.value.showConnectionConfirmDialog)
    }

    // ===== Accept Pending Invitation =====

    @Test
    fun `accepting pending invitation connects to seller`() = runTest {
        authRepository.setCurrentUserId("buyer-1")
        val viewModel = createViewModel()
        advanceUntilIdle()

        val invitation = createTestInvitation(buyerId = "buyer-1")
        invitationRepository.addInvitation(invitation)

        viewModel.dispatch(BuySellerAction.AcceptPendingInvitation(invitation.id))
        advanceUntilIdle()

        assertTrue(invitationRepository.acceptCalled)
        assertEquals(invitation.id, invitationRepository.lastAcceptedInvitationId)
    }

    // ===== Reject Pending Invitation =====

    @Test
    fun `rejecting pending invitation removes it from list`() = runTest {
        authRepository.setCurrentUserId("buyer-1")
        val viewModel = createViewModel()
        advanceUntilIdle()

        val invitation = createTestInvitation(buyerId = "buyer-1")
        invitationRepository.addInvitation(invitation)

        viewModel.dispatch(BuySellerAction.RejectPendingInvitation(invitation.id))
        advanceUntilIdle()

        assertTrue(invitationRepository.rejectCalled)
        assertEquals(invitation.id, invitationRepository.lastRejectedInvitationId)
    }
}
