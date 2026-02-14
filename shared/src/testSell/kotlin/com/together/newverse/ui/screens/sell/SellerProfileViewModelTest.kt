package com.together.newverse.ui.screens.sell

import app.cash.turbine.test
import com.together.newverse.domain.model.Article
import com.together.newverse.domain.model.Market
import com.together.newverse.test.FakeArticleRepository
import com.together.newverse.test.FakeAuthRepository
import com.together.newverse.test.FakeOrderRepository
import com.together.newverse.test.FakeProfileRepository
import com.together.newverse.test.MainDispatcherRule
import com.together.newverse.test.TestData
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SellerProfileViewModelTest {

    private val dispatcherRule = MainDispatcherRule()
    private lateinit var profileRepository: FakeProfileRepository
    private lateinit var authRepository: FakeAuthRepository
    private lateinit var articleRepository: FakeArticleRepository
    private lateinit var orderRepository: FakeOrderRepository

    @BeforeTest
    fun setup() {
        dispatcherRule.setup()
        profileRepository = FakeProfileRepository()
        authRepository = FakeAuthRepository()
        articleRepository = FakeArticleRepository()
        orderRepository = FakeOrderRepository()
    }

    @AfterTest
    fun tearDown() {
        dispatcherRule.tearDown()
    }

    private fun createViewModel(): SellerProfileViewModel {
        return SellerProfileViewModel(
            profileRepository = profileRepository,
            authRepository = authRepository,
            articleRepository = articleRepository,
            orderRepository = orderRepository
        )
    }

    @Test
    fun `loads profile on init`() = runTest {
        // Given user is authenticated
        authRepository.setCurrentUserId("seller_123")
        profileRepository.setSellerProfile(TestData.sampleSellerProfile)

        // When ViewModel is created
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Then profile should be loaded
        viewModel.uiState.test {
            val state = awaitItem()
            assertFalse(state.isLoading)
            assertNotNull(state.profile)
            assertEquals(TestData.sampleSellerProfile.displayName, state.profile?.displayName)
            assertNull(state.error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `shows error when not authenticated`() = runTest {
        // Given user is NOT authenticated
        authRepository.setCurrentUserId(null)

        // When ViewModel is created
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Then error should be shown
        viewModel.uiState.test {
            val state = awaitItem()
            assertFalse(state.isLoading)
            assertEquals("Not authenticated", state.error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `handles profile load failure`() = runTest {
        // Given user is authenticated but profile fetch fails
        authRepository.setCurrentUserId("seller_123")
        profileRepository.shouldFailGetSellerProfile = true
        profileRepository.failureMessage = "Network error"

        // When ViewModel is created
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Then error should be shown
        viewModel.uiState.test {
            val state = awaitItem()
            assertFalse(state.isLoading)
            assertNotNull(state.error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `saves profile successfully`() = runTest {
        // Given user is authenticated
        authRepository.setCurrentUserId("seller_123")
        profileRepository.setSellerProfile(TestData.sampleSellerProfile)

        val viewModel = createViewModel()
        advanceUntilIdle()

        // When saving profile
        val updatedProfile = TestData.sampleSellerProfile.copy(displayName = "Updated Name")
        viewModel.saveProfile(updatedProfile)
        advanceUntilIdle()

        // Then profile should be saved
        assertTrue(profileRepository.saveSellerProfileCalled)
        assertEquals(updatedProfile, profileRepository.lastSavedSellerProfile)

        // And UI state should reflect the update
        viewModel.uiState.test {
            val state = awaitItem()
            assertFalse(state.isLoading)
            assertEquals("Updated Name", state.profile?.displayName)
            assertNull(state.error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `handles profile save failure`() = runTest {
        // Given user is authenticated
        authRepository.setCurrentUserId("seller_123")
        profileRepository.setSellerProfile(TestData.sampleSellerProfile)
        profileRepository.shouldFailSaveSellerProfile = true
        profileRepository.failureMessage = "Save failed"

        val viewModel = createViewModel()
        advanceUntilIdle()

        // When saving profile
        val updatedProfile = TestData.sampleSellerProfile.copy(displayName = "Updated Name")
        viewModel.saveProfile(updatedProfile)
        advanceUntilIdle()

        // Then error should be shown
        viewModel.uiState.test {
            val state = awaitItem()
            assertFalse(state.isLoading)
            assertNotNull(state.error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `adds market to profile`() = runTest {
        // Given user is authenticated with profile
        authRepository.setCurrentUserId("seller_123")
        val initialProfile = TestData.sampleSellerProfile.copy(markets = emptyList())
        profileRepository.setSellerProfile(initialProfile)

        val viewModel = createViewModel()
        advanceUntilIdle()

        // When adding market
        val newMarket = TestData.sampleMarkets[0]
        viewModel.addMarket(newMarket)
        advanceUntilIdle()

        // Then market should be added
        assertTrue(profileRepository.saveSellerProfileCalled)
        assertEquals(1, profileRepository.lastSavedSellerProfile?.markets?.size)
        assertEquals(newMarket, profileRepository.lastSavedSellerProfile?.markets?.first())
    }

    @Test
    fun `updates existing market`() = runTest {
        // Given user is authenticated with profile containing markets
        authRepository.setCurrentUserId("seller_123")
        profileRepository.setSellerProfile(TestData.sampleSellerProfile)

        val viewModel = createViewModel()
        advanceUntilIdle()

        // When updating market
        val existingMarket = TestData.sampleMarkets[0]
        val updatedMarket = existingMarket.copy(name = "Updated Market Name")
        viewModel.updateMarket(updatedMarket)
        advanceUntilIdle()

        // Then market should be updated
        assertTrue(profileRepository.saveSellerProfileCalled)
        val savedMarket = profileRepository.lastSavedSellerProfile?.markets?.find { it.id == updatedMarket.id }
        assertEquals("Updated Market Name", savedMarket?.name)
    }

    @Test
    fun `removes market from profile`() = runTest {
        // Given user is authenticated with profile containing markets
        authRepository.setCurrentUserId("seller_123")
        profileRepository.setSellerProfile(TestData.sampleSellerProfile)

        val viewModel = createViewModel()
        advanceUntilIdle()

        // When removing market
        val marketToRemove = TestData.sampleMarkets[0]
        viewModel.removeMarket(marketToRemove.id)
        advanceUntilIdle()

        // Then market should be removed
        assertTrue(profileRepository.saveSellerProfileCalled)
        val remainingMarkets = profileRepository.lastSavedSellerProfile?.markets ?: emptyList()
        assertFalse(remainingMarkets.any { it.id == marketToRemove.id })
    }

    @Test
    fun `showMarketDialog updates state`() = runTest {
        // Given user is authenticated
        authRepository.setCurrentUserId("seller_123")
        profileRepository.setSellerProfile(TestData.sampleSellerProfile)

        val viewModel = createViewModel()
        advanceUntilIdle()

        // When showing market dialog
        viewModel.showMarketDialog()

        // Then dialog should be visible
        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state.showMarketDialog)
            assertNull(state.editingMarket)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `showMarketDialog with market sets editing market`() = runTest {
        // Given user is authenticated
        authRepository.setCurrentUserId("seller_123")
        profileRepository.setSellerProfile(TestData.sampleSellerProfile)

        val viewModel = createViewModel()
        advanceUntilIdle()

        // When showing market dialog with market
        val market = TestData.sampleMarkets[0]
        viewModel.showMarketDialog(market)

        // Then dialog should show with editing market
        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state.showMarketDialog)
            assertEquals(market, state.editingMarket)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `hideMarketDialog updates state`() = runTest {
        // Given user is authenticated and dialog is open
        authRepository.setCurrentUserId("seller_123")
        profileRepository.setSellerProfile(TestData.sampleSellerProfile)

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.showMarketDialog(TestData.sampleMarkets[0])

        // When hiding dialog
        viewModel.hideMarketDialog()

        // Then dialog should be hidden
        viewModel.uiState.test {
            val state = awaitItem()
            assertFalse(state.showMarketDialog)
            assertNull(state.editingMarket)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `showPaymentInfo updates state`() = runTest {
        // Given user is authenticated
        authRepository.setCurrentUserId("seller_123")
        profileRepository.setSellerProfile(TestData.sampleSellerProfile)

        val viewModel = createViewModel()
        advanceUntilIdle()

        // When showing payment info
        viewModel.showPaymentInfo()

        // Then payment info should be visible
        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state.showPaymentInfo)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `hidePaymentInfo updates state`() = runTest {
        // Given user is authenticated and payment info is shown
        authRepository.setCurrentUserId("seller_123")
        profileRepository.setSellerProfile(TestData.sampleSellerProfile)

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.showPaymentInfo()

        // When hiding payment info
        viewModel.hidePaymentInfo()

        // Then payment info should be hidden
        viewModel.uiState.test {
            val state = awaitItem()
            assertFalse(state.showPaymentInfo)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loads product count on init`() = runTest {
        // Given user is authenticated
        authRepository.setCurrentUserId("seller_123")
        profileRepository.setSellerProfile(TestData.sampleSellerProfile)

        val viewModel = createViewModel()
        advanceUntilIdle()

        // When articles are emitted
        TestData.sampleArticles.forEach { article ->
            articleRepository.emitArticle(article.copy(mode = Article.MODE_ADDED))
        }
        advanceUntilIdle()

        // Then product count should be updated
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(TestData.sampleArticles.size, state.productCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loads order count on init`() = runTest {
        // Given user is authenticated
        authRepository.setCurrentUserId("seller_123")
        profileRepository.setSellerProfile(TestData.sampleSellerProfile)
        orderRepository.setOrders(TestData.sampleOrders)

        // When ViewModel is created
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Then order count should be loaded
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(TestData.sampleOrders.size, state.orderCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `refresh reloads profile and stats`() = runTest {
        // Given user is authenticated
        authRepository.setCurrentUserId("seller_123")
        profileRepository.setSellerProfile(TestData.sampleSellerProfile)

        val viewModel = createViewModel()
        advanceUntilIdle()

        // When refresh is called
        viewModel.refresh()
        advanceUntilIdle()

        // Then profile should be reloaded (stats reset to 0 initially)
        viewModel.uiState.test {
            val state = awaitItem()
            // Profile should still be loaded
            assertNotNull(state.profile)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `addMarket does nothing when no profile`() = runTest {
        // Given user is authenticated but no profile loaded
        authRepository.setCurrentUserId("seller_123")
        // Don't set profile

        val viewModel = createViewModel()
        advanceUntilIdle()

        // When adding market
        val newMarket = TestData.sampleMarkets[0]
        viewModel.addMarket(newMarket)
        advanceUntilIdle()

        // Then save should not be called
        assertFalse(profileRepository.saveSellerProfileCalled)
    }

    @Test
    fun `updateMarket does nothing when no profile`() = runTest {
        // Given user is authenticated but no profile loaded
        authRepository.setCurrentUserId("seller_123")
        // Don't set profile

        val viewModel = createViewModel()
        advanceUntilIdle()

        // When updating market
        viewModel.updateMarket(TestData.sampleMarkets[0])
        advanceUntilIdle()

        // Then save should not be called
        assertFalse(profileRepository.saveSellerProfileCalled)
    }

    @Test
    fun `removeMarket does nothing when no profile`() = runTest {
        // Given user is authenticated but no profile loaded
        authRepository.setCurrentUserId("seller_123")
        // Don't set profile

        val viewModel = createViewModel()
        advanceUntilIdle()

        // When removing market
        viewModel.removeMarket("market_1")
        advanceUntilIdle()

        // Then save should not be called
        assertFalse(profileRepository.saveSellerProfileCalled)
    }

    @Test
    fun `handles article MODE_REMOVED correctly`() = runTest {
        // Given user is authenticated
        authRepository.setCurrentUserId("seller_123")
        profileRepository.setSellerProfile(TestData.sampleSellerProfile)

        val viewModel = createViewModel()
        advanceUntilIdle()

        // Add articles first
        TestData.sampleArticles.forEach { article ->
            articleRepository.emitArticle(article.copy(mode = Article.MODE_ADDED))
        }
        advanceUntilIdle()

        // When an article is removed
        val removedArticle = TestData.sampleArticles[0].copy(mode = Article.MODE_REMOVED)
        articleRepository.emitArticle(removedArticle)
        advanceUntilIdle()

        // Then product count should decrease
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(TestData.sampleArticles.size - 1, state.productCount)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
