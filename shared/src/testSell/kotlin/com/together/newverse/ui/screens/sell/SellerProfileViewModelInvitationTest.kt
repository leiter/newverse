package com.together.newverse.ui.screens.sell

import com.together.newverse.domain.model.SellerProfile
import com.together.newverse.test.FakeArticleRepository
import com.together.newverse.test.FakeAuthRepository
import com.together.newverse.test.FakeInvitationRepository
import com.together.newverse.test.FakeOrderRepository
import com.together.newverse.test.FakeProfileRepository
import com.together.newverse.test.MainDispatcherRule
import com.together.newverse.ui.state.core.AsyncState
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
 * Tests for invitation functionality in SellerProfileViewModel.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SellerProfileViewModelInvitationTest {

    private val dispatcherRule = MainDispatcherRule()
    private lateinit var profileRepository: FakeProfileRepository
    private lateinit var authRepository: FakeAuthRepository
    private lateinit var articleRepository: FakeArticleRepository
    private lateinit var orderRepository: FakeOrderRepository
    private lateinit var invitationRepository: FakeInvitationRepository

    private val testSellerId = "seller-123"
    private val testProfile = SellerProfile(
        id = testSellerId,
        displayName = "Test Farm"
    )

    @BeforeTest
    fun setup() {
        dispatcherRule.setup()
        profileRepository = FakeProfileRepository()
        authRepository = FakeAuthRepository()
        articleRepository = FakeArticleRepository()
        orderRepository = FakeOrderRepository()
        invitationRepository = FakeInvitationRepository()

        // Set up seller auth and profile
        authRepository.setCurrentUserId(testSellerId)
        profileRepository.setSellerProfile(testProfile)
    }

    @AfterTest
    fun tearDown() {
        profileRepository.reset()
        authRepository.reset()
        articleRepository.reset()
        orderRepository.reset()
        invitationRepository.reset()
        dispatcherRule.tearDown()
    }

    private fun createViewModel() = SellerProfileViewModel(
        profileRepository = profileRepository,
        authRepository = authRepository,
        articleRepository = articleRepository,
        orderRepository = orderRepository,
        invitationRepository = invitationRepository
    )

    @Test
    fun `generate invitation creates valid deep link`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.generateInvitation(expiryMinutes = 60)
        advanceUntilIdle()

        val state = viewModel.invitationState.value
        assertNotNull(state.currentInvitation)
        assertNotNull(state.deepLink)
        assertTrue(state.deepLink!!.contains("newverse://connect"))
        assertTrue(state.deepLink!!.contains("sellerId=$testSellerId"))
        assertTrue(state.deepLink!!.contains("inviteId="))
        assertTrue(state.deepLink!!.contains("expires="))
        assertEquals(testSellerId, state.currentInvitation!!.sellerId)
        assertEquals("Test Farm", state.currentInvitation!!.sellerDisplayName)
    }

    @Test
    fun `send invitation to buyer creates targeted invitation`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.sendInvitationToBuyer("buyer-456")
        advanceUntilIdle()

        val state = viewModel.invitationState.value
        assertNotNull(state.lastSentInvitation)
        assertEquals("buyer-456", state.lastSentInvitation!!.buyerId)
        assertEquals(testSellerId, state.lastSentInvitation!!.sellerId)
        assertTrue(invitationRepository.createCalled)
    }

    @Test
    fun `send invitation with blank buyer id is no-op`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.sendInvitationToBuyer("")
        advanceUntilIdle()

        val state = viewModel.invitationState.value
        assertNull(state.lastSentInvitation)
    }

    @Test
    fun `revoke invitation clears current invitation`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Generate first
        viewModel.generateInvitation()
        advanceUntilIdle()

        val invitationId = viewModel.invitationState.value.currentInvitation!!.id

        // Revoke
        viewModel.revokeInvitation(invitationId)
        advanceUntilIdle()

        val state = viewModel.invitationState.value
        assertNull(state.currentInvitation)
        assertNull(state.deepLink)
        assertTrue(invitationRepository.revokeCalled)
    }

    @Test
    fun `generate invitation shows loading state`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Before generating
        assertEquals(false, viewModel.invitationState.value.isGenerating)

        viewModel.generateInvitation()
        advanceUntilIdle()

        // After generating (should be done)
        assertEquals(false, viewModel.invitationState.value.isGenerating)
        assertNotNull(viewModel.invitationState.value.currentInvitation)
    }
}
