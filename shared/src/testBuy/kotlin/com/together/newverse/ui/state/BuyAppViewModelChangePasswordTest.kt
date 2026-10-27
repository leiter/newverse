package com.together.newverse.ui.state

import com.together.newverse.test.FakeArticleRepository
import com.together.newverse.test.FakeAuthRepository
import com.together.newverse.test.FakeBasketRepository
import com.together.newverse.test.FakeMarketRepository
import com.together.newverse.test.FakeOrderRepository
import com.together.newverse.test.FakeProfileRepository
import com.together.newverse.test.FakeSellerConfig
import com.together.newverse.test.FakeSellerUiActionRepository
import com.together.newverse.test.MainDispatcherRule
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

/**
 * Changing the signed-in buyer's password.
 *
 * The buy flavor drives the same shared dialog as the seller one, through its own
 * actions and its own nested state. These tests pin the same split the seller tests
 * pin — a rejected re-authentication is reported under the *current* password, a
 * rejected update under the *new* one — so the two flavors cannot drift apart.
 */
class BuyAppViewModelChangePasswordTest {

    private val dispatcherRule = MainDispatcherRule()

    private lateinit var articleRepository: FakeArticleRepository
    private lateinit var orderRepository: FakeOrderRepository
    private lateinit var profileRepository: FakeProfileRepository
    private lateinit var authRepository: FakeAuthRepository
    private lateinit var basketRepository: FakeBasketRepository
    private lateinit var marketRepository: FakeMarketRepository
    private lateinit var uiActionRepository: FakeSellerUiActionRepository

    @BeforeTest
    fun setUp() {
        dispatcherRule.setup()
        articleRepository = FakeArticleRepository()
        orderRepository = FakeOrderRepository()
        profileRepository = FakeProfileRepository()
        authRepository = FakeAuthRepository()
        basketRepository = FakeBasketRepository()
        marketRepository = FakeMarketRepository()
        uiActionRepository = FakeSellerUiActionRepository()

        authRepository.setCurrentUserId("buyer_1")
    }

    @AfterTest
    fun tearDown() {
        articleRepository.reset()
        orderRepository.reset()
        profileRepository.reset()
        authRepository.reset()
        basketRepository.reset()
        uiActionRepository.reset()
        dispatcherRule.tearDown()
    }

    private fun createViewModel() = BuyAppViewModel(
        articleRepository = articleRepository,
        orderRepository = orderRepository,
        profileRepository = profileRepository,
        authRepository = authRepository,
        basketRepository = basketRepository,
        sellerConfig = FakeSellerConfig(),
        marketRepository = marketRepository,
        sellerUiActionRepository = uiActionRepository
    )

    private val BuyAppViewModel.changePassword: ChangePasswordState
        get() = state.value.customerProfile.changePassword

    private fun BuyAppViewModel.fillIn(
        current: String = "oldpass1",
        new: String = "newpass1",
        confirm: String = "newpass1"
    ) {
        dispatch(BuyAccountAction.ShowChangePasswordDialog)
        dispatch(BuyAccountAction.UpdateChangePasswordCurrent(current))
        dispatch(BuyAccountAction.UpdateChangePasswordNew(new))
        dispatch(BuyAccountAction.UpdateChangePasswordConfirm(confirm))
    }

    @Test
    fun `dialog starts hidden and empty`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.changePassword
        assertFalse(state.visible)
        assertEquals("", state.currentPassword)
    }

    @Test
    fun `showing the dialog makes it visible`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.dispatch(BuyAccountAction.ShowChangePasswordDialog)

        assertTrue(viewModel.changePassword.visible)
    }

    @Test
    fun `showing the dialog again does not reuse the last attempt's input`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.fillIn()

        viewModel.dispatch(BuyAccountAction.DismissChangePasswordDialog)
        viewModel.dispatch(BuyAccountAction.ShowChangePasswordDialog)

        val state = viewModel.changePassword
        assertEquals("", state.currentPassword)
        assertEquals("", state.newPassword)
        assertEquals("", state.confirmPassword)
    }

    @Test
    fun `field updates land in state`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.fillIn(current = "oldpass1", new = "newpass1", confirm = "newpass1")

        val state = viewModel.changePassword
        assertEquals("oldpass1", state.currentPassword)
        assertEquals("newpass1", state.newPassword)
        assertEquals("newpass1", state.confirmPassword)
    }

    @Test
    fun `submitting reauthenticates with the current password, then sets the new one`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.fillIn(current = "oldpass1", new = "newpass1")

        viewModel.dispatch(BuyAccountAction.SubmitChangePassword)
        advanceUntilIdle()

        assertTrue(authRepository.reauthenticateCalled)
        assertEquals("oldpass1", authRepository.lastReauthenticatePassword)
        assertTrue(authRepository.updatePasswordCalled)
        assertEquals("newpass1", authRepository.lastUpdatedPassword)
    }

    @Test
    fun `a successful change clears the dialog and the typed passwords`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.fillIn()

        viewModel.dispatch(BuyAccountAction.SubmitChangePassword)
        advanceUntilIdle()

        val state = viewModel.changePassword
        assertFalse(state.visible)
        assertEquals("", state.currentPassword)
        assertEquals("", state.newPassword)
        assertEquals("", state.confirmPassword)
        assertFalse(state.isSubmitting)
    }

    @Test
    fun `a wrong current password reports under the current password field`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        authRepository.shouldFailReauthenticate = true
        authRepository.failureMessage = "Incorrect password"
        viewModel.fillIn()

        viewModel.dispatch(BuyAccountAction.SubmitChangePassword)
        advanceUntilIdle()

        val state = viewModel.changePassword
        assertNotNull(state.currentPasswordError)
        assertNull(state.newPasswordError)
        // The dialog stays open so the password can be corrected in place.
        assertTrue(state.visible)
        assertFalse(state.isSubmitting)
    }

    @Test
    fun `a wrong current password never reaches updatePassword`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        authRepository.shouldFailReauthenticate = true
        viewModel.fillIn()

        viewModel.dispatch(BuyAccountAction.SubmitChangePassword)
        advanceUntilIdle()

        assertFalse(authRepository.updatePasswordCalled)
    }

    @Test
    fun `a rejected new password reports under the new password field`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        authRepository.shouldFailUpdatePassword = true
        authRepository.failureMessage = "Password is too weak"
        viewModel.fillIn()

        viewModel.dispatch(BuyAccountAction.SubmitChangePassword)
        advanceUntilIdle()

        val state = viewModel.changePassword
        assertNotNull(state.newPasswordError)
        assertNull(state.currentPasswordError)
        assertTrue(state.visible)
    }

    @Test
    fun `typing in the current password field clears its error`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        authRepository.shouldFailReauthenticate = true
        viewModel.fillIn()
        viewModel.dispatch(BuyAccountAction.SubmitChangePassword)
        advanceUntilIdle()

        viewModel.dispatch(BuyAccountAction.UpdateChangePasswordCurrent("another1"))

        assertNull(viewModel.changePassword.currentPasswordError)
    }

    @Test
    fun `dismissing clears the typed passwords`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.fillIn()

        viewModel.dispatch(BuyAccountAction.DismissChangePasswordDialog)

        val state = viewModel.changePassword
        assertFalse(state.visible)
        assertEquals("", state.currentPassword)
    }
}
