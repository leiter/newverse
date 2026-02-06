package com.together.newverse.ui.state.buy

import app.cash.turbine.test
import com.together.newverse.domain.model.Article
import com.together.newverse.test.FakeArticleRepository
import com.together.newverse.test.FakeAuthRepository
import com.together.newverse.test.FakeBasketRepository
import com.together.newverse.test.FakeOrderRepository
import com.together.newverse.test.FakeProfileRepository
import com.together.newverse.test.MainDispatcherRule
import com.together.newverse.test.TestData
import com.together.newverse.ui.state.AuthMode
import com.together.newverse.ui.state.BottomSheetState
import com.together.newverse.ui.state.BuyAppViewModel
import com.together.newverse.ui.state.DialogState
import com.together.newverse.ui.state.SnackbarType
import com.together.newverse.ui.state.BuyUiAction
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
 * Unit tests for BuyAppViewModel UI management extension functions.
 *
 * Tests cover:
 * - Snackbar display and dismissal
 * - Dialog display and dismissal
 * - Bottom sheet display and dismissal
 * - Refresh state management
 * - Password reset dialog
 * - Auth mode switching
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BuyAppViewModelUiTest {

    private val dispatcherRule = MainDispatcherRule()
    private lateinit var articleRepository: FakeArticleRepository
    private lateinit var orderRepository: FakeOrderRepository
    private lateinit var profileRepository: FakeProfileRepository
    private lateinit var authRepository: FakeAuthRepository
    private lateinit var basketRepository: FakeBasketRepository

    @BeforeTest
    fun setup() {
        dispatcherRule.setup()
        articleRepository = FakeArticleRepository()
        orderRepository = FakeOrderRepository()
        profileRepository = FakeProfileRepository()
        authRepository = FakeAuthRepository()
        basketRepository = FakeBasketRepository()
    }

    @AfterTest
    fun tearDown() {
        dispatcherRule.tearDown()
    }

    private fun createViewModel(): BuyAppViewModel {
        return BuyAppViewModel(
            articleRepository = articleRepository,
            orderRepository = orderRepository,
            profileRepository = profileRepository,
            authRepository = authRepository,
            basketRepository = basketRepository
        )
    }

    // ===== Snackbar Tests =====

    @Test
    fun `showSnackbar sets snackbar state with SUCCESS type`() = runTest {
        // Given
        authRepository.setCurrentUserId("buyer_123")
        profileRepository.setBuyerProfile(TestData.sampleBuyerProfiles[0].copy(id = "buyer_123"))
        val viewModel = createViewModel()
        advanceUntilIdle()

        // When
        viewModel.dispatch(BuyUiAction.ShowSnackbar("Success message", SnackbarType.SUCCESS))
        advanceUntilIdle()

        // Then
        viewModel.state.test {
            val state = awaitItem()
            assertNotNull(state.common.ui.snackbar)
            assertEquals("Success message", state.common.ui.snackbar?.message)
            assertEquals(SnackbarType.SUCCESS, state.common.ui.snackbar?.type)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `showSnackbar sets snackbar state with ERROR type`() = runTest {
        // Given
        authRepository.setCurrentUserId("buyer_123")
        profileRepository.setBuyerProfile(TestData.sampleBuyerProfiles[0].copy(id = "buyer_123"))
        val viewModel = createViewModel()
        advanceUntilIdle()

        // When
        viewModel.dispatch(BuyUiAction.ShowSnackbar("Error message", SnackbarType.ERROR))
        advanceUntilIdle()

        // Then
        viewModel.state.test {
            val state = awaitItem()
            assertNotNull(state.common.ui.snackbar)
            assertEquals("Error message", state.common.ui.snackbar?.message)
            assertEquals(SnackbarType.ERROR, state.common.ui.snackbar?.type)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `showSnackbar sets snackbar state with INFO type`() = runTest {
        // Given
        authRepository.setCurrentUserId("buyer_123")
        profileRepository.setBuyerProfile(TestData.sampleBuyerProfiles[0].copy(id = "buyer_123"))
        val viewModel = createViewModel()
        advanceUntilIdle()

        // When
        viewModel.dispatch(BuyUiAction.ShowSnackbar("Info message", SnackbarType.INFO))
        advanceUntilIdle()

        // Then
        viewModel.state.test {
            val state = awaitItem()
            assertNotNull(state.common.ui.snackbar)
            assertEquals("Info message", state.common.ui.snackbar?.message)
            assertEquals(SnackbarType.INFO, state.common.ui.snackbar?.type)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `hideSnackbar clears snackbar state`() = runTest {
        // Given snackbar is shown
        authRepository.setCurrentUserId("buyer_123")
        profileRepository.setBuyerProfile(TestData.sampleBuyerProfiles[0].copy(id = "buyer_123"))
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.dispatch(BuyUiAction.ShowSnackbar("Test", SnackbarType.SUCCESS))
        advanceUntilIdle()

        // Verify snackbar is shown
        viewModel.state.test {
            val state = awaitItem()
            assertNotNull(state.common.ui.snackbar)
            cancelAndIgnoreRemainingEvents()
        }

        // When
        viewModel.dispatch(BuyUiAction.HideSnackbar)
        advanceUntilIdle()

        // Then
        viewModel.state.test {
            val state = awaitItem()
            assertNull(state.common.ui.snackbar)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ===== Dialog Tests =====

    @Test
    fun `showDialog sets dialog state with Confirmation type`() = runTest {
        // Given
        authRepository.setCurrentUserId("buyer_123")
        profileRepository.setBuyerProfile(TestData.sampleBuyerProfiles[0].copy(id = "buyer_123"))
        val viewModel = createViewModel()
        advanceUntilIdle()

        // When
        val dialog = DialogState.Confirmation(
            title = "Confirm Action",
            message = "Are you sure you want to proceed?"
        )
        viewModel.dispatch(BuyUiAction.ShowDialog(dialog))
        advanceUntilIdle()

        // Then
        viewModel.state.test {
            val state = awaitItem()
            assertNotNull(state.common.ui.dialog)
            assertIs<DialogState.Confirmation>(state.common.ui.dialog)
            val confirmation = state.common.ui.dialog as DialogState.Confirmation
            assertEquals("Confirm Action", confirmation.title)
            assertEquals("Are you sure you want to proceed?", confirmation.message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `showDialog sets dialog state with Error type`() = runTest {
        // Given
        authRepository.setCurrentUserId("buyer_123")
        profileRepository.setBuyerProfile(TestData.sampleBuyerProfiles[0].copy(id = "buyer_123"))
        val viewModel = createViewModel()
        advanceUntilIdle()

        // When
        val dialog = DialogState.Error(
            title = "Error",
            message = "Something went wrong"
        )
        viewModel.dispatch(BuyUiAction.ShowDialog(dialog))
        advanceUntilIdle()

        // Then
        viewModel.state.test {
            val state = awaitItem()
            assertNotNull(state.common.ui.dialog)
            assertIs<DialogState.Error>(state.common.ui.dialog)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `hideDialog clears dialog state`() = runTest {
        // Given dialog is shown
        authRepository.setCurrentUserId("buyer_123")
        profileRepository.setBuyerProfile(TestData.sampleBuyerProfiles[0].copy(id = "buyer_123"))
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.dispatch(BuyUiAction.ShowDialog(DialogState.Confirmation("Test", "Test")))
        advanceUntilIdle()

        // When
        viewModel.dispatch(BuyUiAction.HideDialog)
        advanceUntilIdle()

        // Then
        viewModel.state.test {
            val state = awaitItem()
            assertNull(state.common.ui.dialog)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ===== Bottom Sheet Tests =====

    @Test
    fun `showBottomSheet sets bottom sheet state with ProductDetail`() = runTest {
        // Given
        authRepository.setCurrentUserId("buyer_123")
        profileRepository.setBuyerProfile(TestData.sampleBuyerProfiles[0].copy(id = "buyer_123"))
        val viewModel = createViewModel()
        advanceUntilIdle()

        // When
        val product = TestData.sampleArticles[0]
        val sheet = BottomSheetState.ProductDetail(product)
        viewModel.dispatch(BuyUiAction.ShowBottomSheet(sheet))
        advanceUntilIdle()

        // Then
        viewModel.state.test {
            val state = awaitItem()
            assertNotNull(state.common.ui.bottomSheet)
            assertIs<BottomSheetState.ProductDetail>(state.common.ui.bottomSheet)
            val productDetail = state.common.ui.bottomSheet as BottomSheetState.ProductDetail
            assertEquals(product.id, productDetail.product.id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `showBottomSheet sets bottom sheet state with CartSummary`() = runTest {
        // Given
        authRepository.setCurrentUserId("buyer_123")
        profileRepository.setBuyerProfile(TestData.sampleBuyerProfiles[0].copy(id = "buyer_123"))
        val viewModel = createViewModel()
        advanceUntilIdle()

        // When
        val items = TestData.sampleOrderedProducts
        val sheet = BottomSheetState.CartSummary(items)
        viewModel.dispatch(BuyUiAction.ShowBottomSheet(sheet))
        advanceUntilIdle()

        // Then
        viewModel.state.test {
            val state = awaitItem()
            assertNotNull(state.common.ui.bottomSheet)
            assertIs<BottomSheetState.CartSummary>(state.common.ui.bottomSheet)
            val cartSummary = state.common.ui.bottomSheet as BottomSheetState.CartSummary
            assertEquals(items.size, cartSummary.items.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `hideBottomSheet clears bottom sheet state`() = runTest {
        // Given bottom sheet is shown
        authRepository.setCurrentUserId("buyer_123")
        profileRepository.setBuyerProfile(TestData.sampleBuyerProfiles[0].copy(id = "buyer_123"))
        val viewModel = createViewModel()
        advanceUntilIdle()
        val sheet = BottomSheetState.ProductDetail(TestData.sampleArticles[0])
        viewModel.dispatch(BuyUiAction.ShowBottomSheet(sheet))
        advanceUntilIdle()

        // When
        viewModel.dispatch(BuyUiAction.HideBottomSheet)
        advanceUntilIdle()

        // Then
        viewModel.state.test {
            val state = awaitItem()
            assertNull(state.common.ui.bottomSheet)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ===== Refresh State Tests =====

    @Test
    fun `setRefreshing updates isRefreshing to true`() = runTest {
        // Given
        authRepository.setCurrentUserId("buyer_123")
        profileRepository.setBuyerProfile(TestData.sampleBuyerProfiles[0].copy(id = "buyer_123"))
        val viewModel = createViewModel()
        advanceUntilIdle()

        // When
        viewModel.dispatch(BuyUiAction.SetRefreshing(true))
        advanceUntilIdle()

        // Then
        viewModel.state.test {
            val state = awaitItem()
            assertTrue(state.common.ui.isRefreshing)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setRefreshing updates isRefreshing to false`() = runTest {
        // Given refreshing is true
        authRepository.setCurrentUserId("buyer_123")
        profileRepository.setBuyerProfile(TestData.sampleBuyerProfiles[0].copy(id = "buyer_123"))
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.dispatch(BuyUiAction.SetRefreshing(true))
        advanceUntilIdle()

        // When
        viewModel.dispatch(BuyUiAction.SetRefreshing(false))
        advanceUntilIdle()

        // Then
        viewModel.state.test {
            val state = awaitItem()
            assertFalse(state.common.ui.isRefreshing)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ===== Password Reset Dialog Tests =====

    @Test
    fun `showPasswordResetDialog sets showPasswordResetDialog true`() = runTest {
        // Given
        authRepository.setCurrentUserId(null)
        val viewModel = createViewModel()
        advanceUntilIdle()

        // When
        viewModel.dispatch(BuyUiAction.ShowPasswordResetDialog)
        advanceUntilIdle()

        // Then
        viewModel.state.test {
            val state = awaitItem()
            assertTrue(state.screens.auth.showPasswordResetDialog)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `hidePasswordResetDialog sets showPasswordResetDialog false`() = runTest {
        // Given dialog is shown
        authRepository.setCurrentUserId(null)
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.dispatch(BuyUiAction.ShowPasswordResetDialog)
        advanceUntilIdle()

        // When
        viewModel.dispatch(BuyUiAction.HidePasswordResetDialog)
        advanceUntilIdle()

        // Then
        viewModel.state.test {
            val state = awaitItem()
            assertFalse(state.screens.auth.showPasswordResetDialog)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ===== Auth Mode Tests =====

    @Test
    fun `setAuthMode updates auth mode to LOGIN`() = runTest {
        // Given
        authRepository.setCurrentUserId(null)
        val viewModel = createViewModel()
        advanceUntilIdle()

        // When
        viewModel.dispatch(BuyUiAction.SetAuthMode(AuthMode.LOGIN))
        advanceUntilIdle()

        // Then
        viewModel.state.test {
            val state = awaitItem()
            assertEquals(AuthMode.LOGIN, state.screens.auth.mode)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setAuthMode updates auth mode to REGISTER`() = runTest {
        // Given
        authRepository.setCurrentUserId(null)
        val viewModel = createViewModel()
        advanceUntilIdle()

        // When
        viewModel.dispatch(BuyUiAction.SetAuthMode(AuthMode.REGISTER))
        advanceUntilIdle()

        // Then
        viewModel.state.test {
            val state = awaitItem()
            assertEquals(AuthMode.REGISTER, state.screens.auth.mode)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setAuthMode clears error state`() = runTest {
        // Given auth has an error
        authRepository.setCurrentUserId(null)
        val viewModel = createViewModel()
        advanceUntilIdle()

        // First set some mode
        viewModel.dispatch(BuyUiAction.SetAuthMode(AuthMode.LOGIN))
        advanceUntilIdle()

        // When switching mode
        viewModel.dispatch(BuyUiAction.SetAuthMode(AuthMode.REGISTER))
        advanceUntilIdle()

        // Then error should be cleared
        viewModel.state.test {
            val state = awaitItem()
            assertNull(state.screens.auth.error)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
