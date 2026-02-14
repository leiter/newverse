package com.together.newverse.ui.state.core

import app.cash.turbine.test
import com.together.newverse.test.FakeAuthRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AuthFlowCoordinatorTest {

    private lateinit var authRepository: FakeAuthRepository
    private lateinit var coordinator: AuthFlowCoordinator

    @BeforeTest
    fun setup() {
        authRepository = FakeAuthRepository()
        coordinator = AuthFlowCoordinator(authRepository)
    }

    // ===== Initialization =====

    @Test
    fun `initial state is Initializing`() {
        assertEquals(AuthState.Initializing, coordinator.authState.value)
    }

    // ===== Auth State Updates =====

    @Test
    fun `updateAuthState changes state`() {
        val newState = AuthState.Authenticated(
            userId = "user123",
            email = "test@example.com",
            displayName = "Test User",
            photoUrl = null,
            isAnonymous = false
        )

        coordinator.updateAuthState(newState)

        assertEquals(newState, coordinator.authState.value)
    }

    // ===== Helper Methods =====

    @Test
    fun `getCurrentUserId returns userId when authenticated`() {
        coordinator.updateAuthState(
            AuthState.Authenticated(
                userId = "user123",
                email = null,
                displayName = null,
                photoUrl = null,
                isAnonymous = false
            )
        )

        assertEquals("user123", coordinator.getCurrentUserId())
    }

    @Test
    fun `getCurrentUserId returns null when not authenticated`() {
        coordinator.updateAuthState(AuthState.NotAuthenticated)

        assertNull(coordinator.getCurrentUserId())
    }

    @Test
    fun `isAuthenticated returns true when authenticated`() {
        coordinator.updateAuthState(
            AuthState.Authenticated(
                userId = "user123",
                email = null,
                displayName = null,
                photoUrl = null,
                isAnonymous = false
            )
        )

        assertTrue(coordinator.isAuthenticated())
    }

    @Test
    fun `isAuthenticated returns false when not authenticated`() {
        coordinator.updateAuthState(AuthState.NotAuthenticated)

        assertFalse(coordinator.isAuthenticated())
    }

    @Test
    fun `isAnonymous returns true for anonymous user`() {
        coordinator.updateAuthState(
            AuthState.Authenticated(
                userId = "guest123",
                email = null,
                displayName = null,
                photoUrl = null,
                isAnonymous = true
            )
        )

        assertTrue(coordinator.isAnonymous())
    }

    @Test
    fun `isAnonymous returns false for regular user`() {
        coordinator.updateAuthState(
            AuthState.Authenticated(
                userId = "user123",
                email = "test@example.com",
                displayName = null,
                photoUrl = null,
                isAnonymous = false
            )
        )

        assertFalse(coordinator.isAnonymous())
    }

    // ===== whenAuthenticated Operator =====

    @Test
    fun `whenAuthenticated emits AwaitingAuth when Initializing`() = runTest {
        // State is Initializing by default

        coordinator.whenAuthenticated { userId ->
            flowOf("data for $userId")
        }.test {
            val item = awaitItem()
            assertIs<AuthAwareState.AwaitingAuth>(item)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `whenAuthenticated emits AuthRequired when NotAuthenticated`() = runTest {
        coordinator.updateAuthState(AuthState.NotAuthenticated)

        coordinator.whenAuthenticated { userId ->
            flowOf("data for $userId")
        }.test {
            val item = awaitItem()
            assertIs<AuthAwareState.AuthRequired>(item)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ===== withAuthSuspend =====

    @Test
    fun `withAuthSuspend returns Success when authenticated`() = runTest {
        coordinator.updateAuthState(
            AuthState.Authenticated(
                userId = "user123",
                email = null,
                displayName = null,
                photoUrl = null,
                isAnonymous = false
            )
        )

        val result = coordinator.withAuthSuspend { userId ->
            "Result for $userId"
        }

        assertTrue(result.isSuccess)
        assertEquals("Result for user123", result.getOrNull())
    }

    @Test
    fun `withAuthSuspend returns Failure when not authenticated`() = runTest {
        coordinator.updateAuthState(AuthState.NotAuthenticated)

        val result = coordinator.withAuthSuspend { userId ->
            "Result for $userId"
        }

        assertTrue(result.isFailure)
    }

    @Test
    fun `withAuthSuspend returns Failure when initializing`() = runTest {
        // State is Initializing by default

        val result = coordinator.withAuthSuspend { userId ->
            "Result for $userId"
        }

        assertTrue(result.isFailure)
    }

    // ===== AuthState Properties =====

    @Test
    fun `AuthState userIdOrNull returns userId for Authenticated`() {
        val state = AuthState.Authenticated(
            userId = "user123",
            email = null,
            displayName = null,
            photoUrl = null,
            isAnonymous = false
        )

        assertEquals("user123", state.userIdOrNull)
    }

    @Test
    fun `AuthState userIdOrNull returns null for non-Authenticated`() {
        assertNull(AuthState.Initializing.userIdOrNull)
        assertNull(AuthState.NotAuthenticated.userIdOrNull)
    }

    @Test
    fun `AuthState isAuthenticated returns correct values`() {
        assertTrue(
            AuthState.Authenticated(
                userId = "user123",
                email = null,
                displayName = null,
                photoUrl = null,
                isAnonymous = false
            ).isAuthenticated
        )
        assertFalse(AuthState.Initializing.isAuthenticated)
        assertFalse(AuthState.NotAuthenticated.isAuthenticated)
    }

    @Test
    fun `AuthState isInitializing returns correct values`() {
        assertTrue(AuthState.Initializing.isInitializing)
        assertFalse(AuthState.NotAuthenticated.isInitializing)
        assertFalse(
            AuthState.Authenticated(
                userId = "user123",
                email = null,
                displayName = null,
                photoUrl = null,
                isAnonymous = false
            ).isInitializing
        )
    }
}
