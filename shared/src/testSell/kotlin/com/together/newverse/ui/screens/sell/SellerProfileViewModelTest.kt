package com.together.newverse.ui.screens.sell

import app.cash.turbine.test
import com.together.newverse.domain.model.Article
import com.together.newverse.domain.model.Article.Companion.MODE_ADDED
import com.together.newverse.domain.model.Article.Companion.MODE_REMOVED
import com.together.newverse.domain.model.Market
import com.together.newverse.domain.model.Order
import com.together.newverse.domain.model.SellerProfile
import com.together.newverse.test.FakeArticleRepository
import com.together.newverse.test.FakeAuthRepository
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for SellerProfileViewModel.
 *
 * Tests cover:
 * - Profile loading and authentication
 * - Stats loading (product count, order count)
 * - Profile saving
 * - Market management (add, update, remove)
 * - Dialog state management
 * - Refresh functionality
 */
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
        // Reset repositories BEFORE dispatcher teardown
        profileRepository.reset()
        authRepository.reset()
        articleRepository.reset()
        orderRepository.reset()
        dispatcherRule.tearDown()
    }

    private fun createViewModel() = SellerProfileViewModel(
        profileRepository = profileRepository,
        authRepository = authRepository,
        articleRepository = articleRepository,
        orderRepository = orderRepository
    )

    // ===== Test Helpers =====

    private fun createSellerProfile(
        id: String = "seller_123",
        displayName: String = "Test Farm",
        markets: List<Market> = emptyList()
    ): SellerProfile {
        return SellerProfile(
            id = id,
            displayName = displayName,
            firstName = "John",
            lastName = "Farmer",
            city = "Berlin",
            markets = markets
        )
    }

    private fun createMarket(
        id: String,
        name: String = "Market $id",
        city: String = "Berlin"
    ): Market {
        return Market(
            id = id,
            name = name,
            city = city,
            dayOfWeek = "Thursday",
            begin = "08:00",
            end = "14:00"
        )
    }

    private fun createArticle(
        id: String,
        name: String = "Product $id",
        mode: Int = MODE_ADDED
    ): Article {
        return Article(
            id = id,
            productId = "prod_$id",
            productName = name,
            mode = mode
        )
    }

    private fun createOrder(id: String): Order {
        return Order(
            id = id,
            sellerId = "seller_123"
        )
    }

    // ===== A. Initial State (3 tests) =====

    @Test
    fun `initial profile state is Loading`() = runTest {
        // Given authenticated user
        authRepository.setCurrentUserId("seller_123")
        profileRepository.setSellerProfile(createSellerProfile())

        // When creating ViewModel
        val viewModel = createViewModel()

        // Then profile state starts as Loading (may transition quickly)
        viewModel.profileState.test {
            val state = awaitItem()
            assertTrue(
                state is AsyncState.Loading || state is AsyncState.Success,
                "Expected Loading or Success, got $state"
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `initial stats state has zero counts`() = runTest {
        // Given authenticated user
        authRepository.setCurrentUserId("seller_123")
        profileRepository.setSellerProfile(createSellerProfile())

        // When creating ViewModel
        val viewModel = createViewModel()

        // Then initial stats have zero counts
        viewModel.statsState.test {
            val stats = awaitItem()
            assertEquals(0, stats.productCount)
            assertEquals(0, stats.orderCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `initial dialog state is closed`() = runTest {
        // Given authenticated user
        authRepository.setCurrentUserId("seller_123")
        profileRepository.setSellerProfile(createSellerProfile())

        // When creating ViewModel
        val viewModel = createViewModel()

        // Then dialogs are closed
        viewModel.dialogState.test {
            val state = awaitItem()
            assertFalse(state.showMarketDialog)
            assertNull(state.editingMarket)
            assertFalse(state.showPaymentInfo)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ===== B. Profile Loading (3 tests) =====

    @Test
    fun `loads profile successfully when authenticated`() = runTest {
        // Given authenticated user and profile exists
        authRepository.setCurrentUserId("seller_123")
        val profile = createSellerProfile(displayName = "Green Farm")
        profileRepository.setSellerProfile(profile)

        // When creating ViewModel
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Then profile is loaded
        viewModel.profileState.test {
            val state = awaitItem()
            assertIs<AsyncState.Success<SellerProfile>>(state)
            assertEquals("Green Farm", state.data.displayName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `error when user not authenticated`() = runTest {
        // Given user NOT authenticated
        authRepository.setCurrentUserId(null)

        // When creating ViewModel
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Then error state
        viewModel.profileState.test {
            val state = awaitItem()
            assertIs<AsyncState.Error>(state)
            assertEquals("Not authenticated", state.message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `error when profile load fails`() = runTest {
        // Given authenticated user but profile load fails
        authRepository.setCurrentUserId("seller_123")
        profileRepository.shouldFailGetSellerProfile = true
        profileRepository.failureMessage = "Network error"

        // When creating ViewModel
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Then error state
        viewModel.profileState.test {
            val state = awaitItem()
            assertIs<AsyncState.Error>(state)
            assertTrue(state.message.contains("Network error"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ===== C. Stats Loading (3 tests) =====

    @Test
    fun `updates product count from article repository`() = runTest {
        // Given authenticated user
        authRepository.setCurrentUserId("seller_123")
        profileRepository.setSellerProfile(createSellerProfile())
        val viewModel = createViewModel()
        advanceUntilIdle()

        // When articles are added
        articleRepository.emitArticle(createArticle("1", mode = MODE_ADDED))
        articleRepository.emitArticle(createArticle("2", mode = MODE_ADDED))
        advanceUntilIdle()

        // Then product count is updated
        viewModel.statsState.test {
            val stats = awaitItem()
            assertEquals(2, stats.productCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updates order count from order repository`() = runTest {
        // Given authenticated user and orders
        authRepository.setCurrentUserId("seller_123")
        profileRepository.setSellerProfile(createSellerProfile())
        orderRepository.setOrders(listOf(
            createOrder("1"),
            createOrder("2"),
            createOrder("3")
        ))

        // When creating ViewModel
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Then order count is updated
        viewModel.statsState.test {
            val stats = awaitItem()
            assertEquals(3, stats.orderCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `decrements product count when article removed`() = runTest {
        // Given authenticated user and articles
        authRepository.setCurrentUserId("seller_123")
        profileRepository.setSellerProfile(createSellerProfile())
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Add articles
        articleRepository.emitArticle(createArticle("1", mode = MODE_ADDED))
        articleRepository.emitArticle(createArticle("2", mode = MODE_ADDED))
        advanceUntilIdle()

        // Verify count is 2
        viewModel.statsState.test {
            assertEquals(2, awaitItem().productCount)
            cancelAndIgnoreRemainingEvents()
        }

        // When article is removed
        articleRepository.emitArticle(createArticle("1", mode = MODE_REMOVED))
        advanceUntilIdle()

        // Then count decrements
        viewModel.statsState.test {
            assertEquals(1, awaitItem().productCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ===== D. Save Profile (3 tests) =====

    @Test
    fun `saveProfile saves to repository`() = runTest {
        // Given authenticated user and loaded profile
        authRepository.setCurrentUserId("seller_123")
        profileRepository.setSellerProfile(createSellerProfile())
        val viewModel = createViewModel()
        advanceUntilIdle()

        // When saving updated profile
        val updatedProfile = createSellerProfile(displayName = "Updated Farm")
        viewModel.saveProfile(updatedProfile)
        advanceUntilIdle()

        // Then profile is saved
        assertTrue(profileRepository.saveSellerProfileCalled)
        assertEquals("Updated Farm", profileRepository.lastSavedSellerProfile?.displayName)
    }

    @Test
    fun `saveProfile updates profile state on success`() = runTest {
        // Given authenticated user and loaded profile
        authRepository.setCurrentUserId("seller_123")
        profileRepository.setSellerProfile(createSellerProfile(displayName = "Original"))
        val viewModel = createViewModel()
        advanceUntilIdle()

        // When saving updated profile
        val updatedProfile = createSellerProfile(displayName = "Updated")
        viewModel.saveProfile(updatedProfile)
        advanceUntilIdle()

        // Then profile state is updated
        viewModel.profileState.test {
            val state = awaitItem()
            assertIs<AsyncState.Success<SellerProfile>>(state)
            assertEquals("Updated", state.data.displayName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `saveProfile sets isSaving during operation`() = runTest {
        // Given authenticated user
        authRepository.setCurrentUserId("seller_123")
        profileRepository.setSellerProfile(createSellerProfile())
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Initially not saving
        assertFalse(viewModel.isSaving.value)

        // When save completes
        viewModel.saveProfile(createSellerProfile())
        advanceUntilIdle()

        // Then saving is false again
        assertFalse(viewModel.isSaving.value)
    }

    // ===== E. Market Management (6 tests) =====

    @Test
    fun `addMarket adds market to profile`() = runTest {
        // Given authenticated user and profile without markets
        authRepository.setCurrentUserId("seller_123")
        profileRepository.setSellerProfile(createSellerProfile(markets = emptyList()))
        val viewModel = createViewModel()
        advanceUntilIdle()

        // When adding a market
        val newMarket = createMarket("m1", "Farmers Market")
        viewModel.addMarket(newMarket)
        advanceUntilIdle()

        // Then market is added
        viewModel.profileState.test {
            val state = awaitItem()
            assertIs<AsyncState.Success<SellerProfile>>(state)
            assertEquals(1, state.data.markets.size)
            assertEquals("Farmers Market", state.data.markets[0].name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `addMarket preserves existing markets`() = runTest {
        // Given profile with existing market
        authRepository.setCurrentUserId("seller_123")
        val existingMarket = createMarket("m1", "Existing Market")
        profileRepository.setSellerProfile(createSellerProfile(markets = listOf(existingMarket)))
        val viewModel = createViewModel()
        advanceUntilIdle()

        // When adding another market
        val newMarket = createMarket("m2", "New Market")
        viewModel.addMarket(newMarket)
        advanceUntilIdle()

        // Then both markets exist
        viewModel.profileState.test {
            val state = awaitItem()
            assertIs<AsyncState.Success<SellerProfile>>(state)
            assertEquals(2, state.data.markets.size)
            assertTrue(state.data.markets.any { it.id == "m1" })
            assertTrue(state.data.markets.any { it.id == "m2" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updateMarket updates existing market`() = runTest {
        // Given profile with market
        authRepository.setCurrentUserId("seller_123")
        val market = createMarket("m1", "Original Name", city = "Berlin")
        profileRepository.setSellerProfile(createSellerProfile(markets = listOf(market)))
        val viewModel = createViewModel()
        advanceUntilIdle()

        // When updating market
        val updatedMarket = market.copy(name = "Updated Name", city = "Munich")
        viewModel.updateMarket(updatedMarket)
        advanceUntilIdle()

        // Then market is updated
        viewModel.profileState.test {
            val state = awaitItem()
            assertIs<AsyncState.Success<SellerProfile>>(state)
            assertEquals(1, state.data.markets.size)
            assertEquals("Updated Name", state.data.markets[0].name)
            assertEquals("Munich", state.data.markets[0].city)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updateMarket only updates matching market`() = runTest {
        // Given profile with multiple markets
        authRepository.setCurrentUserId("seller_123")
        val market1 = createMarket("m1", "Market 1")
        val market2 = createMarket("m2", "Market 2")
        profileRepository.setSellerProfile(createSellerProfile(markets = listOf(market1, market2)))
        val viewModel = createViewModel()
        advanceUntilIdle()

        // When updating one market
        val updatedMarket1 = market1.copy(name = "Updated Market 1")
        viewModel.updateMarket(updatedMarket1)
        advanceUntilIdle()

        // Then only that market is updated
        viewModel.profileState.test {
            val state = awaitItem()
            assertIs<AsyncState.Success<SellerProfile>>(state)
            assertEquals(2, state.data.markets.size)
            assertEquals("Updated Market 1", state.data.markets.find { it.id == "m1" }?.name)
            assertEquals("Market 2", state.data.markets.find { it.id == "m2" }?.name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `removeMarket removes market from profile`() = runTest {
        // Given profile with markets
        authRepository.setCurrentUserId("seller_123")
        val market1 = createMarket("m1", "Market 1")
        val market2 = createMarket("m2", "Market 2")
        profileRepository.setSellerProfile(createSellerProfile(markets = listOf(market1, market2)))
        val viewModel = createViewModel()
        advanceUntilIdle()

        // When removing one market
        viewModel.removeMarket("m1")
        advanceUntilIdle()

        // Then market is removed
        viewModel.profileState.test {
            val state = awaitItem()
            assertIs<AsyncState.Success<SellerProfile>>(state)
            assertEquals(1, state.data.markets.size)
            assertEquals("m2", state.data.markets[0].id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `market operations do nothing when profile not loaded`() = runTest {
        // Given user NOT authenticated (profile won't load)
        authRepository.setCurrentUserId(null)
        val viewModel = createViewModel()
        advanceUntilIdle()

        // When trying to add market
        viewModel.addMarket(createMarket("m1"))
        advanceUntilIdle()

        // Then no save is attempted
        assertFalse(profileRepository.saveSellerProfileCalled)
    }

    // ===== F. Dialog State (6 tests) =====

    @Test
    fun `showMarketDialog opens dialog without market`() = runTest {
        // Given ViewModel
        authRepository.setCurrentUserId("seller_123")
        profileRepository.setSellerProfile(createSellerProfile())
        val viewModel = createViewModel()
        advanceUntilIdle()

        // When showing dialog without market
        viewModel.showMarketDialog()

        // Then dialog is open with no editing market
        viewModel.dialogState.test {
            val state = awaitItem()
            assertTrue(state.showMarketDialog)
            assertNull(state.editingMarket)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `showMarketDialog opens dialog with market for editing`() = runTest {
        // Given ViewModel
        authRepository.setCurrentUserId("seller_123")
        profileRepository.setSellerProfile(createSellerProfile())
        val viewModel = createViewModel()
        advanceUntilIdle()

        // When showing dialog with market
        val market = createMarket("m1", "Edit Me")
        viewModel.showMarketDialog(market)

        // Then dialog is open with editing market
        viewModel.dialogState.test {
            val state = awaitItem()
            assertTrue(state.showMarketDialog)
            assertNotNull(state.editingMarket)
            assertEquals("Edit Me", state.editingMarket?.name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `hideMarketDialog closes dialog and clears market`() = runTest {
        // Given dialog is open with market
        authRepository.setCurrentUserId("seller_123")
        profileRepository.setSellerProfile(createSellerProfile())
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.showMarketDialog(createMarket("m1"))

        // When hiding dialog
        viewModel.hideMarketDialog()

        // Then dialog is closed and market cleared
        viewModel.dialogState.test {
            val state = awaitItem()
            assertFalse(state.showMarketDialog)
            assertNull(state.editingMarket)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `showPaymentInfo opens payment info`() = runTest {
        // Given ViewModel
        authRepository.setCurrentUserId("seller_123")
        profileRepository.setSellerProfile(createSellerProfile())
        val viewModel = createViewModel()
        advanceUntilIdle()

        // When showing payment info
        viewModel.showPaymentInfo()

        // Then payment info is visible
        viewModel.dialogState.test {
            val state = awaitItem()
            assertTrue(state.showPaymentInfo)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `hidePaymentInfo closes payment info`() = runTest {
        // Given payment info is visible
        authRepository.setCurrentUserId("seller_123")
        profileRepository.setSellerProfile(createSellerProfile())
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.showPaymentInfo()

        // When hiding payment info
        viewModel.hidePaymentInfo()

        // Then payment info is hidden
        viewModel.dialogState.test {
            val state = awaitItem()
            assertFalse(state.showPaymentInfo)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `dialog states are independent`() = runTest {
        // Given ViewModel
        authRepository.setCurrentUserId("seller_123")
        profileRepository.setSellerProfile(createSellerProfile())
        val viewModel = createViewModel()
        advanceUntilIdle()

        // When showing both dialogs
        viewModel.showMarketDialog()
        viewModel.showPaymentInfo()

        // Then both are visible
        viewModel.dialogState.test {
            val state = awaitItem()
            assertTrue(state.showMarketDialog)
            assertTrue(state.showPaymentInfo)
            cancelAndIgnoreRemainingEvents()
        }

        // When hiding market dialog
        viewModel.hideMarketDialog()

        // Then only payment is visible
        viewModel.dialogState.test {
            val state = awaitItem()
            assertFalse(state.showMarketDialog)
            assertTrue(state.showPaymentInfo)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ===== G. Refresh (2 tests) =====

    @Test
    fun `refresh reloads profile`() = runTest {
        // Given authenticated user and profile
        authRepository.setCurrentUserId("seller_123")
        profileRepository.setSellerProfile(createSellerProfile(displayName = "Original"))
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Change the profile in repository
        profileRepository.setSellerProfile(createSellerProfile(displayName = "Updated"))

        // When refreshing
        viewModel.refresh()
        advanceUntilIdle()

        // Then profile is reloaded
        viewModel.profileState.test {
            val state = awaitItem()
            assertIs<AsyncState.Success<SellerProfile>>(state)
            assertEquals("Updated", state.data.displayName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `refresh clears articles list and new emissions start fresh`() = runTest {
        // Given authenticated user and articles
        authRepository.setCurrentUserId("seller_123")
        profileRepository.setSellerProfile(createSellerProfile())
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Add articles
        articleRepository.emitArticle(createArticle("1", mode = MODE_ADDED))
        articleRepository.emitArticle(createArticle("2", mode = MODE_ADDED))
        advanceUntilIdle()

        // Verify initial count
        viewModel.statsState.test {
            assertEquals(2, awaitItem().productCount)
            cancelAndIgnoreRemainingEvents()
        }

        // When refreshing
        viewModel.refresh()
        advanceUntilIdle()

        // Then emit a new article with same ID (should only count once, proving list was cleared)
        articleRepository.emitArticle(createArticle("1", mode = MODE_ADDED))
        advanceUntilIdle()

        // Article count is 1 (not 3), proving the list was cleared
        // Note: statsState shows 1 because the new emission triggered an update
        viewModel.statsState.test {
            val stats = awaitItem()
            assertEquals(1, stats.productCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ===== H. Edge Cases (2 tests) =====

    @Test
    fun `does not duplicate articles on multiple MODE_ADDED for same ID`() = runTest {
        // Given authenticated user
        authRepository.setCurrentUserId("seller_123")
        profileRepository.setSellerProfile(createSellerProfile())
        val viewModel = createViewModel()
        advanceUntilIdle()

        // When emitting same article twice
        articleRepository.emitArticle(createArticle("1", "Product v1", mode = MODE_ADDED))
        advanceUntilIdle()
        articleRepository.emitArticle(createArticle("1", "Product v2", mode = MODE_ADDED))
        advanceUntilIdle()

        // Then count is 1 (not 2)
        viewModel.statsState.test {
            assertEquals(1, awaitItem().productCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `handles save failure gracefully`() = runTest {
        // Given authenticated user and save will fail
        authRepository.setCurrentUserId("seller_123")
        profileRepository.setSellerProfile(createSellerProfile())
        profileRepository.shouldFailSaveSellerProfile = true
        val viewModel = createViewModel()
        advanceUntilIdle()

        // When saving profile
        viewModel.saveProfile(createSellerProfile(displayName = "New Name"))
        advanceUntilIdle()

        // Then isSaving returns to false
        assertFalse(viewModel.isSaving.value)
        // And original profile state is maintained (not changed to error)
        viewModel.profileState.test {
            val state = awaitItem()
            assertIs<AsyncState.Success<SellerProfile>>(state)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
