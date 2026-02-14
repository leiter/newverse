package com.together.newverse.data.repository

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for InMemoryAuthRepository.
 *
 * Tests cover:
 * - Sign in with email/password
 * - Sign up with email/password
 * - Sign out
 * - Anonymous sign in
 * - Account linking
 * - Delete account
 * - Password reset
 * - Auth state observation
 */
class InMemoryAuthRepositoryTest {

    private lateinit var repository: InMemoryAuthRepository

    @BeforeTest
    fun setup() {
        repository = InMemoryAuthRepository()
    }

    // ===== A. Initial State (3 tests) =====

    @Test
    fun `initial state has no current user`() = runTest {
        assertNull(repository.getCurrentUserId())
    }

    @Test
    fun `initial state is not anonymous`() = runTest {
        assertFalse(repository.isAnonymous())
    }

    @Test
    fun `initial getCurrentUserInfo returns null`() = runTest {
        assertNull(repository.getCurrentUserInfo())
    }

    // ===== B. Sign In with Email (6 tests) =====

    @Test
    fun `signInWithEmail succeeds for test buyer user`() = runTest {
        val result = repository.signInWithEmail("test@buyer.com", "password123")

        assertTrue(result.isSuccess)
        assertEquals("buyer_001", result.getOrNull())
        assertEquals("buyer_001", repository.getCurrentUserId())
    }

    @Test
    fun `signInWithEmail succeeds for test seller user`() = runTest {
        val result = repository.signInWithEmail("test@seller.com", "password123")

        assertTrue(result.isSuccess)
        assertEquals("seller_001", result.getOrNull())
    }

    @Test
    fun `signInWithEmail fails for nonexistent user`() = runTest {
        val result = repository.signInWithEmail("nonexistent@test.com", "password123")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("not found") == true)
    }

    @Test
    fun `signInWithEmail fails for wrong password`() = runTest {
        val result = repository.signInWithEmail("test@buyer.com", "wrongpassword")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Invalid password") == true)
    }

    @Test
    fun `signInWithEmail fails for empty email`() = runTest {
        val result = repository.signInWithEmail("", "password123")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("cannot be empty") == true)
    }

    @Test
    fun `signInWithEmail fails for empty password`() = runTest {
        val result = repository.signInWithEmail("test@buyer.com", "")

        assertTrue(result.isFailure)
    }

    // ===== C. Sign Up (6 tests) =====

    @Test
    fun `signUpWithEmail creates new user`() = runTest {
        val result = repository.signUpWithEmail("newuser@test.com", "password123")

        assertTrue(result.isSuccess)
        assertNotNull(result.getOrNull())
        assertTrue(result.getOrNull()!!.startsWith("user_"))
    }

    @Test
    fun `signUpWithEmail sets current user`() = runTest {
        val result = repository.signUpWithEmail("newuser@test.com", "password123")

        assertEquals(result.getOrNull(), repository.getCurrentUserId())
    }

    @Test
    fun `signUpWithEmail fails for existing email`() = runTest {
        val result = repository.signUpWithEmail("test@buyer.com", "password123")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("already exists") == true)
    }

    @Test
    fun `signUpWithEmail fails for invalid email format`() = runTest {
        val result = repository.signUpWithEmail("invalidemail", "password123")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Invalid email") == true)
    }

    @Test
    fun `signUpWithEmail fails for short password`() = runTest {
        val result = repository.signUpWithEmail("new@test.com", "12345")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("at least 6") == true)
    }

    @Test
    fun `signUpWithEmail allows new user to sign in`() = runTest {
        repository.signUpWithEmail("newuser@test.com", "mypassword")
        repository.signOut()

        val result = repository.signInWithEmail("newuser@test.com", "mypassword")

        assertTrue(result.isSuccess)
    }

    // ===== D. Sign Out (3 tests) =====

    @Test
    fun `signOut clears current user`() = runTest {
        repository.signInWithEmail("test@buyer.com", "password123")
        assertNotNull(repository.getCurrentUserId())

        repository.signOut()

        assertNull(repository.getCurrentUserId())
    }

    @Test
    fun `signOut succeeds when already signed out`() = runTest {
        val result = repository.signOut()

        assertTrue(result.isSuccess)
    }

    @Test
    fun `signOut clears anonymous user`() = runTest {
        repository.signInAnonymously()
        assertTrue(repository.isAnonymous())

        repository.signOut()

        assertNull(repository.getCurrentUserId())
        assertFalse(repository.isAnonymous())
    }

    // ===== E. Anonymous Sign In (4 tests) =====

    @Test
    fun `signInAnonymously creates guest user`() = runTest {
        val result = repository.signInAnonymously()

        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull()!!.startsWith("guest_"))
    }

    @Test
    fun `signInAnonymously sets current user`() = runTest {
        repository.signInAnonymously()

        assertNotNull(repository.getCurrentUserId())
        assertTrue(repository.getCurrentUserId()!!.startsWith("guest_"))
    }

    @Test
    fun `isAnonymous returns true for anonymous user`() = runTest {
        repository.signInAnonymously()

        assertTrue(repository.isAnonymous())
    }

    @Test
    fun `isAnonymous returns false for email user`() = runTest {
        repository.signInWithEmail("test@buyer.com", "password123")

        assertFalse(repository.isAnonymous())
    }

    // ===== F. Delete Account (3 tests) =====

    @Test
    fun `deleteAccount removes user and signs out`() = runTest {
        repository.signUpWithEmail("todelete@test.com", "password123")

        val result = repository.deleteAccount()

        assertTrue(result.isSuccess)
        assertNull(repository.getCurrentUserId())
    }

    @Test
    fun `deleteAccount prevents future sign in`() = runTest {
        repository.signUpWithEmail("todelete@test.com", "password123")
        repository.deleteAccount()

        val result = repository.signInWithEmail("todelete@test.com", "password123")

        assertTrue(result.isFailure)
    }

    @Test
    fun `deleteAccount fails when not signed in`() = runTest {
        val result = repository.deleteAccount()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("No user") == true)
    }

    // ===== G. Account Linking (4 tests) =====

    @Test
    fun `linkWithEmail converts anonymous to permanent`() = runTest {
        repository.signInAnonymously()
        val anonymousId = repository.getCurrentUserId()

        val result = repository.linkWithEmail("linked@test.com", "password123")

        assertTrue(result.isSuccess)
        // User ID should remain the same
        assertEquals(anonymousId, result.getOrNull())
    }

    @Test
    fun `linkWithEmail allows sign in with email after linking`() = runTest {
        repository.signInAnonymously()
        repository.linkWithEmail("linked@test.com", "password123")
        repository.signOut()

        val result = repository.signInWithEmail("linked@test.com", "password123")

        assertTrue(result.isSuccess)
    }

    @Test
    fun `linkWithEmail fails for non-anonymous user`() = runTest {
        repository.signInWithEmail("test@buyer.com", "password123")

        val result = repository.linkWithEmail("new@test.com", "password123")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("not anonymous") == true)
    }

    @Test
    fun `linkWithEmail fails for existing email`() = runTest {
        repository.signInAnonymously()

        val result = repository.linkWithEmail("test@buyer.com", "password123")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("already exists") == true)
    }

    // ===== H. Password Reset (3 tests) =====

    @Test
    fun `sendPasswordResetEmail succeeds for existing user`() = runTest {
        val result = repository.sendPasswordResetEmail("test@buyer.com")

        assertTrue(result.isSuccess)
    }

    @Test
    fun `sendPasswordResetEmail fails for nonexistent user`() = runTest {
        val result = repository.sendPasswordResetEmail("nonexistent@test.com")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("No account") == true)
    }

    @Test
    fun `sendPasswordResetEmail fails for empty email`() = runTest {
        val result = repository.sendPasswordResetEmail("")

        assertTrue(result.isFailure)
    }

    // ===== I. Social Sign In (3 tests) =====

    @Test
    fun `signInWithGoogle returns failure for in-memory repo`() = runTest {
        val result = repository.signInWithGoogle("fake_token")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Firebase") == true)
    }

    @Test
    fun `signInWithTwitter returns failure for in-memory repo`() = runTest {
        val result = repository.signInWithTwitter("token", "secret")

        assertTrue(result.isFailure)
    }

    @Test
    fun `signInWithApple returns failure for in-memory repo`() = runTest {
        val result = repository.signInWithApple("idToken", "nonce")

        assertTrue(result.isFailure)
    }

    // ===== J. Observable Auth State (3 tests) =====

    @Test
    fun `observeAuthState emits null initially`() = runTest {
        repository.observeAuthState().test {
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeAuthState emits userId after sign in`() = runTest {
        repository.observeAuthState().test {
            // Initial state
            assertNull(awaitItem())

            // Sign in
            repository.signInWithEmail("test@buyer.com", "password123")
            assertEquals("buyer_001", awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeAuthState emits null after sign out`() = runTest {
        repository.signInWithEmail("test@buyer.com", "password123")

        repository.observeAuthState().test {
            // Current signed in state
            assertEquals("buyer_001", awaitItem())

            // Sign out
            repository.signOut()
            assertNull(awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ===== K. User Info (3 tests) =====

    @Test
    fun `getCurrentUserInfo returns info for signed in user`() = runTest {
        repository.signInWithEmail("test@buyer.com", "password123")

        val userInfo = repository.getCurrentUserInfo()

        assertNotNull(userInfo)
        assertEquals("buyer_001", userInfo.id)
        assertEquals("test@buyer.com", userInfo.email)
        assertFalse(userInfo.isAnonymous)
    }

    @Test
    fun `getCurrentUserInfo returns info for anonymous user`() = runTest {
        repository.signInAnonymously()

        val userInfo = repository.getCurrentUserInfo()

        assertNotNull(userInfo)
        assertTrue(userInfo.id.startsWith("guest_"))
        assertNull(userInfo.email)
        assertTrue(userInfo.isAnonymous)
    }

    @Test
    fun `getCurrentUserInfo returns null when signed out`() = runTest {
        repository.signInWithEmail("test@buyer.com", "password123")
        repository.signOut()

        assertNull(repository.getCurrentUserInfo())
    }
}
