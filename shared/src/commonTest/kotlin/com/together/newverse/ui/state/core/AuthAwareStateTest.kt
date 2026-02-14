package com.together.newverse.ui.state.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthAwareStateTest {

    // ===== Basic State Properties =====

    @Test
    fun `AwaitingAuth state properties`() {
        val state: AuthAwareState<String> = AuthAwareState.AwaitingAuth

        assertNull(state.userIdOrNull)
        assertFalse(state.isAuthenticated)
        assertFalse(state.isAnonymousUser)
        assertTrue(state.isAwaitingAuth)
        assertFalse(state.requiresAuth)
        assertNull(state.asyncStateOrNull)
    }

    @Test
    fun `AuthRequired state properties`() {
        val state: AuthAwareState<String> = AuthAwareState.AuthRequired

        assertNull(state.userIdOrNull)
        assertFalse(state.isAuthenticated)
        assertFalse(state.isAnonymousUser)
        assertFalse(state.isAwaitingAuth)
        assertTrue(state.requiresAuth)
        assertNull(state.asyncStateOrNull)
    }

    @Test
    fun `Authenticated state with Success data`() {
        val state = AuthAwareState.Authenticated(
            userId = "user123",
            isAnonymous = false,
            data = AsyncState.Success("profile data")
        )

        assertEquals("user123", state.userIdOrNull)
        assertTrue(state.isAuthenticated)
        assertFalse(state.isAnonymousUser)
        assertFalse(state.isAwaitingAuth)
        assertFalse(state.requiresAuth)
        assertEquals("profile data", state.getDataOrNull())
    }

    @Test
    fun `Authenticated anonymous user`() {
        val state = AuthAwareState.Authenticated(
            userId = "guest123",
            isAnonymous = true,
            data = AsyncState.Success("guest data")
        )

        assertEquals("guest123", state.userIdOrNull)
        assertTrue(state.isAuthenticated)
        assertTrue(state.isAnonymousUser)
    }

    @Test
    fun `Authenticated state with Loading data`() {
        val state = AuthAwareState.Authenticated<String>(
            userId = "user123",
            isAnonymous = false,
            data = AsyncState.Loading
        )

        assertEquals("user123", state.userIdOrNull)
        assertTrue(state.isAuthenticated)
        assertNull(state.getDataOrNull()) // Loading has no data
    }

    @Test
    fun `Authenticated state with Error data`() {
        val state = AuthAwareState.Authenticated<String>(
            userId = "user123",
            isAnonymous = false,
            data = AsyncState.Error("failed to load")
        )

        assertEquals("user123", state.userIdOrNull)
        assertTrue(state.isAuthenticated)
        assertNull(state.getDataOrNull()) // Error has no data
    }

    // ===== MapData Operator =====

    @Test
    fun `mapData transforms Authenticated Success data`() {
        val state = AuthAwareState.Authenticated(
            userId = "user123",
            isAnonymous = false,
            data = AsyncState.Success(5)
        )

        val mapped = state.mapData { it * 2 }

        assertIs<AuthAwareState.Authenticated<Int>>(mapped)
        assertEquals("user123", mapped.userId)
        assertIs<AsyncState.Success<Int>>(mapped.data)
        assertEquals(10, mapped.data.data)
    }

    @Test
    fun `mapData preserves Authenticated Loading`() {
        val state = AuthAwareState.Authenticated<Int>(
            userId = "user123",
            isAnonymous = false,
            data = AsyncState.Loading
        )

        val mapped = state.mapData { it * 2 }

        assertIs<AuthAwareState.Authenticated<Int>>(mapped)
        assertIs<AsyncState.Loading>(mapped.data)
    }

    @Test
    fun `mapData preserves Authenticated Error`() {
        val state = AuthAwareState.Authenticated<Int>(
            userId = "user123",
            isAnonymous = false,
            data = AsyncState.Error("error")
        )

        val mapped = state.mapData { it * 2 }

        assertIs<AuthAwareState.Authenticated<Int>>(mapped)
        assertIs<AsyncState.Error>(mapped.data)
    }

    @Test
    fun `mapData preserves AwaitingAuth`() {
        val state: AuthAwareState<Int> = AuthAwareState.AwaitingAuth

        val mapped = state.mapData { it * 2 }

        assertIs<AuthAwareState.AwaitingAuth>(mapped)
    }

    @Test
    fun `mapData preserves AuthRequired`() {
        val state: AuthAwareState<Int> = AuthAwareState.AuthRequired

        val mapped = state.mapData { it * 2 }

        assertIs<AuthAwareState.AuthRequired>(mapped)
    }

    // ===== Combine Auth Aware States =====

    @Test
    fun `combineAuthAwareStates returns Success when both authenticated with Success`() {
        val state1 = AuthAwareState.Authenticated(
            userId = "user123",
            isAnonymous = false,
            data = AsyncState.Success(5)
        )
        val state2 = AuthAwareState.Authenticated(
            userId = "user123",
            isAnonymous = false,
            data = AsyncState.Success(3)
        )

        val combined = combineAuthAwareStates(state1, state2) { a, b -> a + b }

        assertIs<AuthAwareState.Authenticated<Int>>(combined)
        assertIs<AsyncState.Success<Int>>(combined.data)
        assertEquals(8, combined.data.data)
    }

    @Test
    fun `combineAuthAwareStates returns AwaitingAuth if any is AwaitingAuth`() {
        val authenticated = AuthAwareState.Authenticated(
            userId = "user123",
            isAnonymous = false,
            data = AsyncState.Success(5)
        )
        val awaiting: AuthAwareState<Int> = AuthAwareState.AwaitingAuth

        val combined1 = combineAuthAwareStates(authenticated, awaiting) { a, b -> a + b }
        val combined2 = combineAuthAwareStates(awaiting, authenticated) { a, b -> a + b }

        assertIs<AuthAwareState.AwaitingAuth>(combined1)
        assertIs<AuthAwareState.AwaitingAuth>(combined2)
    }

    @Test
    fun `combineAuthAwareStates returns AuthRequired if any is AuthRequired`() {
        val authenticated = AuthAwareState.Authenticated(
            userId = "user123",
            isAnonymous = false,
            data = AsyncState.Success(5)
        )
        val authRequired: AuthAwareState<Int> = AuthAwareState.AuthRequired

        val combined1 = combineAuthAwareStates(authenticated, authRequired) { a, b -> a + b }
        val combined2 = combineAuthAwareStates(authRequired, authenticated) { a, b -> a + b }

        assertIs<AuthAwareState.AuthRequired>(combined1)
        assertIs<AuthAwareState.AuthRequired>(combined2)
    }

    @Test
    fun `combineAuthAwareStates returns Loading if any data is Loading`() {
        val success = AuthAwareState.Authenticated(
            userId = "user123",
            isAnonymous = false,
            data = AsyncState.Success(5)
        )
        val loading = AuthAwareState.Authenticated<Int>(
            userId = "user123",
            isAnonymous = false,
            data = AsyncState.Loading
        )

        val combined = combineAuthAwareStates(success, loading) { a, b -> a + b }

        assertIs<AuthAwareState.Authenticated<Int>>(combined)
        assertIs<AsyncState.Loading>(combined.data)
    }

    @Test
    fun `combineAuthAwareStates returns Error if any data is Error`() {
        val success = AuthAwareState.Authenticated(
            userId = "user123",
            isAnonymous = false,
            data = AsyncState.Success(5)
        )
        val error = AuthAwareState.Authenticated<Int>(
            userId = "user123",
            isAnonymous = false,
            data = AsyncState.Error("failed")
        )

        val combined = combineAuthAwareStates(success, error) { a, b -> a + b }

        assertIs<AuthAwareState.Authenticated<Int>>(combined)
        assertIs<AsyncState.Error>(combined.data)
    }

    // ===== GetDataOrDefault =====

    @Test
    fun `getDataOrDefault returns data for authenticated Success`() {
        val state = AuthAwareState.Authenticated(
            userId = "user123",
            isAnonymous = false,
            data = AsyncState.Success("data")
        )

        assertEquals("data", state.getDataOrDefault("default"))
    }

    @Test
    fun `getDataOrDefault returns default for non-authenticated states`() {
        assertEquals("default", AuthAwareState.AwaitingAuth.getDataOrDefault("default"))
        assertEquals("default", AuthAwareState.AuthRequired.getDataOrDefault("default"))
    }

    @Test
    fun `getDataOrDefault returns default for authenticated non-Success data`() {
        val loading = AuthAwareState.Authenticated<String>(
            userId = "user123",
            isAnonymous = false,
            data = AsyncState.Loading
        )
        val error = AuthAwareState.Authenticated<String>(
            userId = "user123",
            isAnonymous = false,
            data = AsyncState.Error("error")
        )

        // Test that getDataOrNull returns null for non-Success
        assertNull(loading.getDataOrNull())
        assertNull(error.getDataOrNull())

        // Test asyncStateOrNull returns the inner state
        assertIs<AsyncState.Loading>(loading.asyncStateOrNull)
        assertIs<AsyncState.Error>(error.asyncStateOrNull)
    }

    // ===== Callback Operators =====

    @Test
    fun `onAuthenticatedSuccess executes block for authenticated Success`() {
        var capturedUserId: String? = null
        var capturedData: String? = null

        val state = AuthAwareState.Authenticated(
            userId = "user123",
            isAnonymous = false,
            data = AsyncState.Success("data")
        )

        state.onAuthenticatedSuccess { userId, data ->
            capturedUserId = userId
            capturedData = data
        }

        assertEquals("user123", capturedUserId)
        assertEquals("data", capturedData)
    }

    @Test
    fun `onAuthenticatedSuccess does not execute for Loading`() {
        var executed = false

        val state = AuthAwareState.Authenticated<String>(
            userId = "user123",
            isAnonymous = false,
            data = AsyncState.Loading
        )

        state.onAuthenticatedSuccess { _, _ -> executed = true }

        assertFalse(executed)
    }

    @Test
    fun `onAuthRequired executes block for AuthRequired state`() {
        var executed = false

        AuthAwareState.AuthRequired.onAuthRequired { executed = true }

        assertTrue(executed)
    }

    @Test
    fun `onAwaitingAuth executes block for AwaitingAuth state`() {
        var executed = false

        AuthAwareState.AwaitingAuth.onAwaitingAuth { executed = true }

        assertTrue(executed)
    }
}
