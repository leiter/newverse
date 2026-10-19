package com.together.newverse.ui.screens.sell

import com.together.newverse.domain.model.AccessStatus
import com.together.newverse.test.FakeArticleRepository
import com.together.newverse.test.FakeAuthRepository
import com.together.newverse.test.FakeMarketRepository
import com.together.newverse.test.FakeOrderRepository
import com.together.newverse.test.FakeProfileRepository
import com.together.newverse.test.MainDispatcherRule
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A production request — a buyer asking to place real orders — is bound to a
 * market. Approving it says yes to the stall that was asked about, rather than
 * silently admitting the buyer everywhere.
 */
class ApproveRequestMarketTest {

    private val dispatcherRule = MainDispatcherRule()

    private lateinit var profileRepository: FakeProfileRepository
    private lateinit var authRepository: FakeAuthRepository
    private lateinit var articleRepository: FakeArticleRepository
    private lateinit var orderRepository: FakeOrderRepository
    private lateinit var marketRepository: FakeMarketRepository

    @BeforeTest
    fun setUp() {
        dispatcherRule.setup()
        profileRepository = FakeProfileRepository()
        authRepository = FakeAuthRepository()
        articleRepository = FakeArticleRepository()
        orderRepository = FakeOrderRepository()
        marketRepository = FakeMarketRepository()
        authRepository.setCurrentUserId("seller_123")
    }

    @AfterTest
    fun tearDown() {
        profileRepository.reset()
        authRepository.reset()
        articleRepository.reset()
        orderRepository.reset()
        marketRepository.reset()
        dispatcherRule.tearDown()
    }

    private fun createViewModel() = SellerProfileViewModel(
        profileRepository = profileRepository,
        authRepository = authRepository,
        articleRepository = articleRepository,
        orderRepository = orderRepository,
        marketRepository = marketRepository
    )

    private suspend fun requestFrom(buyerId: String, marketId: String) {
        profileRepository.currentBuyerId = buyerId
        profileRepository.submitAccessRequest("seller_123", "Alice", marketId)
    }

    private fun approvedScope(buyerId: String): List<String> =
        profileRepository.accessFor("seller_123", buyerId)?.allowedMarkets.orEmpty()

    @Test
    fun `approving grants the market the buyer asked for`() = runTest {
        requestFrom("buyer_1", "m1")
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.approveRequest("buyer_1")
        advanceUntilIdle()

        assertEquals(AccessStatus.APPROVED, profileRepository.accessFor("seller_123", "buyer_1")?.status)
        assertEquals(listOf("m1"), approvedScope("buyer_1"))
    }

    // A single-market seller's buyer, and every request made before requests
    // carried a market, asks to order anywhere.
    @Test
    fun `a request naming no market grants every market`() = runTest {
        requestFrom("buyer_2", "")
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.approveRequest("buyer_2")
        advanceUntilIdle()

        assertEquals(emptyList(), approvedScope("buyer_2"))
    }

    @Test
    fun `the seller can widen the answer beyond what was asked`() = runTest {
        requestFrom("buyer_3", "m1")
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.approveRequest("buyer_3", listOf("m1", "m2"))
        advanceUntilIdle()

        assertEquals(listOf("m1", "m2"), approvedScope("buyer_3"))
    }

    @Test
    fun `the seller can admit everywhere by passing an empty scope`() = runTest {
        requestFrom("buyer_4", "m1")
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.approveRequest("buyer_4", emptyList())
        advanceUntilIdle()

        assertEquals(emptyList(), approvedScope("buyer_4"))
    }
}
