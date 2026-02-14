package com.together.newverse.ui.screens.buy

import app.cash.turbine.test
import com.together.newverse.domain.model.BuyerProfile
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CustomerProfileViewModelTest {

    private val dispatcherRule = MainDispatcherRule()
    private lateinit var profileRepository: FakeProfileRepository

    @BeforeTest
    fun setup() {
        dispatcherRule.setup()
        profileRepository = FakeProfileRepository()
    }

    @AfterTest
    fun tearDown() {
        dispatcherRule.tearDown()
    }

    private fun createViewModel(): CustomerProfileViewModel {
        return CustomerProfileViewModel(
            profileRepository = profileRepository
        )
    }

    private fun createTestProfile(
        id: String = "user_123",
        displayName: String = "John Doe",
        email: String = "john@example.com",
        phone: String = "+1 234 567 8901"
    ) = BuyerProfile(
        id = id,
        displayName = displayName,
        emailAddress = email,
        telephoneNumber = phone
    )

    // ===== Initial State Tests =====

    @Test
    fun `initial state has empty form data`() = runTest {
        val viewModel = createViewModel()

        viewModel.formState.test {
            val state = awaitItem()
            assertEquals("", state.data.displayName)
            assertEquals("", state.data.email)
            assertEquals("", state.data.phone)
            assertFalse(state.isDirty)
            assertFalse(state.isSubmitting)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `initial state is not editing`() = runTest {
        val viewModel = createViewModel()

        viewModel.isEditing.test {
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ===== Initialize From Profile Tests =====

    @Test
    fun `initializeFromProfile populates form with profile data`() = runTest {
        val viewModel = createViewModel()
        val profile = createTestProfile()

        viewModel.initializeFromProfile(profile)

        viewModel.formState.test {
            val state = awaitItem()
            assertEquals("John Doe", state.data.displayName)
            assertEquals("john@example.com", state.data.email)
            assertEquals("+1 234 567 8901", state.data.phone)
            assertFalse(state.isDirty)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ===== Editing State Tests =====

    @Test
    fun `startEditing sets isEditing to true`() = runTest {
        val viewModel = createViewModel()

        viewModel.startEditing()

        viewModel.isEditing.test {
            assertTrue(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `cancelEditing resets form and sets isEditing to false`() = runTest {
        val viewModel = createViewModel()
        val profile = createTestProfile()

        viewModel.initializeFromProfile(profile)
        viewModel.startEditing()
        viewModel.onDisplayNameChange("Changed Name")

        viewModel.cancelEditing()

        viewModel.formState.test {
            val state = awaitItem()
            assertEquals("John Doe", state.data.displayName)
            assertFalse(state.isDirty)
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.isEditing.test {
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ===== Field Update Tests =====

    @Test
    fun `onDisplayNameChange updates displayName and marks dirty`() = runTest {
        val viewModel = createViewModel()
        val profile = createTestProfile()
        viewModel.initializeFromProfile(profile)

        viewModel.onDisplayNameChange("Jane Doe")

        viewModel.formState.test {
            val state = awaitItem()
            assertEquals("Jane Doe", state.data.displayName)
            assertTrue(state.isDirty)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onEmailChange updates email and marks dirty`() = runTest {
        val viewModel = createViewModel()
        val profile = createTestProfile()
        viewModel.initializeFromProfile(profile)

        viewModel.onEmailChange("jane@example.com")

        viewModel.formState.test {
            val state = awaitItem()
            assertEquals("jane@example.com", state.data.email)
            assertTrue(state.isDirty)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onPhoneChange updates phone and marks dirty`() = runTest {
        val viewModel = createViewModel()
        val profile = createTestProfile()
        viewModel.initializeFromProfile(profile)

        viewModel.onPhoneChange("+1 999 888 7777")

        viewModel.formState.test {
            val state = awaitItem()
            assertEquals("+1 999 888 7777", state.data.phone)
            assertTrue(state.isDirty)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `field change clears field error`() = runTest {
        val viewModel = createViewModel()
        val profile = createTestProfile()
        viewModel.initializeFromProfile(profile)

        // Set invalid email to trigger error on save
        viewModel.onEmailChange("invalid-email")
        viewModel.saveProfile()
        advanceUntilIdle()

        // Now fix the email
        viewModel.onEmailChange("valid@example.com")

        viewModel.formState.test {
            val state = awaitItem()
            assertNull(state.getFieldError(ProfileValidation.FIELD_EMAIL))
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ===== Validation Tests =====

    @Test
    fun `isEmailValid returns true for valid email`() = runTest {
        val viewModel = createViewModel()
        viewModel.onEmailChange("test@example.com")

        assertTrue(viewModel.isEmailValid())
    }

    @Test
    fun `isEmailValid returns true for empty email`() = runTest {
        val viewModel = createViewModel()
        viewModel.onEmailChange("")

        assertTrue(viewModel.isEmailValid())
    }

    @Test
    fun `isEmailValid returns false for invalid email`() = runTest {
        val viewModel = createViewModel()
        viewModel.onEmailChange("invalid-email")

        assertFalse(viewModel.isEmailValid())
    }

    @Test
    fun `isPhoneValid returns true for valid phone`() = runTest {
        val viewModel = createViewModel()
        viewModel.onPhoneChange("+1 234 567 8901")

        assertTrue(viewModel.isPhoneValid())
    }

    @Test
    fun `isPhoneValid returns true for empty phone`() = runTest {
        val viewModel = createViewModel()
        viewModel.onPhoneChange("")

        assertTrue(viewModel.isPhoneValid())
    }

    @Test
    fun `isPhoneValid returns false for phone with invalid characters`() = runTest {
        val viewModel = createViewModel()
        viewModel.onPhoneChange("abc123")

        assertFalse(viewModel.isPhoneValid())
    }

    @Test
    fun `isPhoneValid returns false for too short phone`() = runTest {
        val viewModel = createViewModel()
        viewModel.onPhoneChange("12345")

        assertFalse(viewModel.isPhoneValid())
    }

    @Test
    fun `canSave returns true when all fields are valid`() = runTest {
        val viewModel = createViewModel()
        viewModel.onEmailChange("test@example.com")
        viewModel.onPhoneChange("+1 234 567 8901")

        assertTrue(viewModel.canSave())
    }

    @Test
    fun `canSave returns false when email is invalid`() = runTest {
        val viewModel = createViewModel()
        viewModel.onEmailChange("invalid-email")
        viewModel.onPhoneChange("+1 234 567 8901")

        assertFalse(viewModel.canSave())
    }

    @Test
    fun `canSave returns false when phone is invalid`() = runTest {
        val viewModel = createViewModel()
        viewModel.onEmailChange("test@example.com")
        viewModel.onPhoneChange("123")

        assertFalse(viewModel.canSave())
    }

    // ===== Save Profile Tests =====

    @Test
    fun `saveProfile with invalid email sets field error`() = runTest {
        val viewModel = createViewModel()
        val profile = createTestProfile()
        viewModel.initializeFromProfile(profile)
        viewModel.onEmailChange("invalid-email")

        viewModel.saveProfile()
        advanceUntilIdle()

        viewModel.formState.test {
            val state = awaitItem()
            assertEquals("email_format", state.getFieldError(ProfileValidation.FIELD_EMAIL))
            assertFalse(state.isSubmitting)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `saveProfile with invalid phone characters sets field error`() = runTest {
        val viewModel = createViewModel()
        val profile = createTestProfile()
        viewModel.initializeFromProfile(profile)
        viewModel.onPhoneChange("abc123def")

        viewModel.saveProfile()
        advanceUntilIdle()

        viewModel.formState.test {
            val state = awaitItem()
            assertEquals("phone_invalid_chars", state.getFieldError(ProfileValidation.FIELD_PHONE))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `saveProfile with too short phone sets field error`() = runTest {
        val viewModel = createViewModel()
        val profile = createTestProfile()
        viewModel.initializeFromProfile(profile)
        viewModel.onPhoneChange("12345")

        viewModel.saveProfile()
        advanceUntilIdle()

        viewModel.formState.test {
            val state = awaitItem()
            assertEquals("phone_format", state.getFieldError(ProfileValidation.FIELD_PHONE))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `saveProfile without initialized profile fails`() = runTest {
        val viewModel = createViewModel()
        // Don't initialize from profile

        viewModel.saveProfile()
        advanceUntilIdle()

        viewModel.formState.test {
            val state = awaitItem()
            assertEquals("No profile loaded", state.submitError)
            assertFalse(state.isSubmitting)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `saveProfile with valid data calls repository`() = runTest {
        val viewModel = createViewModel()
        val profile = createTestProfile()
        viewModel.initializeFromProfile(profile)
        viewModel.onDisplayNameChange("Updated Name")

        viewModel.saveProfile()
        advanceUntilIdle()

        assertTrue(profileRepository.saveBuyerProfileCalled)
        assertEquals("Updated Name", profileRepository.lastSavedBuyerProfile?.displayName)
    }

    @Test
    fun `saveProfile success updates state and exits editing`() = runTest {
        val viewModel = createViewModel()
        val profile = createTestProfile()
        viewModel.initializeFromProfile(profile)
        viewModel.startEditing()
        viewModel.onDisplayNameChange("Updated Name")

        viewModel.saveProfile()
        advanceUntilIdle()

        viewModel.formState.test {
            val state = awaitItem()
            assertFalse(state.isSubmitting)
            assertFalse(state.isDirty)
            assertNull(state.submitError)
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.isEditing.test {
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `saveProfile failure sets submit error`() = runTest {
        val viewModel = createViewModel()
        val profile = createTestProfile()
        viewModel.initializeFromProfile(profile)
        profileRepository.shouldFailSaveBuyerProfile = true
        profileRepository.failureMessage = "Network error"

        viewModel.saveProfile()
        advanceUntilIdle()

        viewModel.formState.test {
            val state = awaitItem()
            assertEquals("Network error", state.submitError)
            assertFalse(state.isSubmitting)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `saveProfile completes successfully and stops submitting`() = runTest {
        val viewModel = createViewModel()
        val profile = createTestProfile()
        viewModel.initializeFromProfile(profile)

        // Start save
        viewModel.saveProfile()
        advanceUntilIdle()

        // After completion, should not be submitting
        viewModel.formState.test {
            val finalState = awaitItem()
            assertFalse(finalState.isSubmitting)
            assertNull(finalState.submitError)
            cancelAndIgnoreRemainingEvents()
        }

        // Verify profile was saved
        assertTrue(profileRepository.saveBuyerProfileCalled)
    }
}
