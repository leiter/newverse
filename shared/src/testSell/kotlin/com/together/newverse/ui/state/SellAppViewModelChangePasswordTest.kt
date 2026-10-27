package com.together.newverse.ui.state

import com.together.newverse.test.FakeAuthRepository
import com.together.newverse.test.FakeProfileRepository
import com.together.newverse.test.MainDispatcherRule
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

/**
 * Changing the signed-in seller's password.
 *
 * The two repository calls are separate so their failures can be told apart:
 * a rejected re-authentication means the *current* password was wrong, a
 * rejected update means the *new* one was. These tests pin that split, and that
 * nothing is left in state once a change succeeds.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SellAppViewModelChangePasswordTest {

    private val dispatcherRule = MainDispatcherRule()
    private lateinit var authRepository: FakeAuthRepository

    @BeforeTest
    fun setup() {
        dispatcherRule.setup()
        authRepository = FakeAuthRepository()
    }

    @AfterTest
    fun tearDown() {
        authRepository.reset()
        dispatcherRule.tearDown()
    }

    private fun createViewModel() = SellAppViewModel(
        authRepository = authRepository,
        profileRepository = FakeProfileRepository()
    )

    private fun signedIn(): SellAppViewModel {
        authRepository.setCurrentUserId("seller_001")
        return createViewModel()
    }

    private fun SellAppViewModel.fillIn(
        current: String = "oldpass1",
        new: String = "newpass1",
        confirm: String = "newpass1"
    ) {
        dispatch(SellUserAction.ShowChangePasswordDialog)
        dispatch(SellUserAction.UpdateChangePasswordCurrent(current))
        dispatch(SellUserAction.UpdateChangePasswordNew(new))
        dispatch(SellUserAction.UpdateChangePasswordConfirm(confirm))
    }

    @Test
    fun `dialog starts hidden and empty`() = runTest {
        val viewModel = signedIn()
        advanceUntilIdle()

        val state = viewModel.state.value.changePassword
        assertFalse(state.visible)
        assertEquals("", state.currentPassword)
    }

    @Test
    fun `showing the dialog makes it visible`() = runTest {
        val viewModel = signedIn()
        advanceUntilIdle()

        viewModel.dispatch(SellUserAction.ShowChangePasswordDialog)

        assertTrue(viewModel.state.value.changePassword.visible)
    }

    @Test
    fun `showing the dialog again does not reuse the last attempt's input`() = runTest {
        val viewModel = signedIn()
        advanceUntilIdle()
        viewModel.fillIn()

        viewModel.dispatch(SellUserAction.DismissChangePasswordDialog)
        viewModel.dispatch(SellUserAction.ShowChangePasswordDialog)

        val state = viewModel.state.value.changePassword
        assertEquals("", state.currentPassword)
        assertEquals("", state.newPassword)
        assertEquals("", state.confirmPassword)
    }

    @Test
    fun `field updates land in state`() = runTest {
        val viewModel = signedIn()
        advanceUntilIdle()

        viewModel.fillIn(current = "oldpass1", new = "newpass1", confirm = "newpass1")

        val state = viewModel.state.value.changePassword
        assertEquals("oldpass1", state.currentPassword)
        assertEquals("newpass1", state.newPassword)
        assertEquals("newpass1", state.confirmPassword)
    }

    @Test
    fun `submitting reauthenticates with the current password, then sets the new one`() = runTest {
        val viewModel = signedIn()
        advanceUntilIdle()
        viewModel.fillIn(current = "oldpass1", new = "newpass1")

        viewModel.dispatch(SellUserAction.SubmitChangePassword)
        advanceUntilIdle()

        assertTrue(authRepository.reauthenticateCalled)
        assertEquals("oldpass1", authRepository.lastReauthenticatePassword)
        assertTrue(authRepository.updatePasswordCalled)
        assertEquals("newpass1", authRepository.lastUpdatedPassword)
    }

    @Test
    fun `a successful change clears the dialog and the typed passwords`() = runTest {
        val viewModel = signedIn()
        advanceUntilIdle()
        viewModel.fillIn()

        viewModel.dispatch(SellUserAction.SubmitChangePassword)
        advanceUntilIdle()

        val state = viewModel.state.value.changePassword
        assertFalse(state.visible)
        assertEquals("", state.currentPassword)
        assertEquals("", state.newPassword)
        assertEquals("", state.confirmPassword)
        assertFalse(state.isSubmitting)
    }

    @Test
    fun `a wrong current password reports under the current password field`() = runTest {
        val viewModel = signedIn()
        advanceUntilIdle()
        authRepository.shouldFailReauthenticate = true
        authRepository.failureMessage = "Incorrect password"
        viewModel.fillIn()

        viewModel.dispatch(SellUserAction.SubmitChangePassword)
        advanceUntilIdle()

        val state = viewModel.state.value.changePassword
        assertNotNull(state.currentPasswordError)
        assertNull(state.newPasswordError)
        // The dialog stays open so the password can be corrected in place.
        assertTrue(state.visible)
        assertFalse(state.isSubmitting)
    }

    @Test
    fun `a wrong current password never reaches updatePassword`() = runTest {
        val viewModel = signedIn()
        advanceUntilIdle()
        authRepository.shouldFailReauthenticate = true
        viewModel.fillIn()

        viewModel.dispatch(SellUserAction.SubmitChangePassword)
        advanceUntilIdle()

        assertFalse(authRepository.updatePasswordCalled)
    }

    @Test
    fun `a rejected new password reports under the new password field`() = runTest {
        val viewModel = signedIn()
        advanceUntilIdle()
        authRepository.shouldFailUpdatePassword = true
        authRepository.failureMessage = "Password is too weak"
        viewModel.fillIn()

        viewModel.dispatch(SellUserAction.SubmitChangePassword)
        advanceUntilIdle()

        val state = viewModel.state.value.changePassword
        assertNotNull(state.newPasswordError)
        assertNull(state.currentPasswordError)
        assertTrue(state.visible)
    }

    @Test
    fun `typing in the current password field clears its error`() = runTest {
        val viewModel = signedIn()
        advanceUntilIdle()
        authRepository.shouldFailReauthenticate = true
        viewModel.fillIn()
        viewModel.dispatch(SellUserAction.SubmitChangePassword)
        advanceUntilIdle()

        viewModel.dispatch(SellUserAction.UpdateChangePasswordCurrent("another1"))

        assertNull(viewModel.state.value.changePassword.currentPasswordError)
    }

    @Test
    fun `dismissing clears the typed passwords`() = runTest {
        val viewModel = signedIn()
        advanceUntilIdle()
        viewModel.fillIn()

        viewModel.dispatch(SellUserAction.DismissChangePasswordDialog)

        val state = viewModel.state.value.changePassword
        assertFalse(state.visible)
        assertEquals("", state.currentPassword)
    }
}
