package com.together.newverse.data.repository

import com.together.newverse.domain.repository.AuthRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Clock

/**
 * In-memory implementation of AuthRepository for testing and development.
 *
 * This implementation stores user credentials in memory and does not persist across app restarts.
 * Replace with FirebaseAuthRepository or other backend implementation for production.
 */
class InMemoryAuthRepository : AuthRepository {

    // In-memory storage for user credentials
    private val users = mutableMapOf<String, UserCredentials>()

    // Current authenticated user ID
    private val _currentUserId = MutableStateFlow<String?>(null)

    // Simulated persistent storage (like SharedPreferences/UserDefaults)
    private var persistedUserId: String? = null

    // Flag to track if we've checked persisted auth
    private var hasCheckedPersistedAuth = false

    init {
        // Add some test users for development
        users["test@buyer.com"] = UserCredentials(
            userId = "buyer_001",
            email = "test@buyer.com",
            password = "password123"
        )
        users["test@seller.com"] = UserCredentials(
            userId = "seller_001",
            email = "test@seller.com",
            password = "password123"
        )

        // Simulate auto-login for development - comment this out to test guest flow
        // Uncomment the line below to test auto-login on app start
        // persistedUserId = "buyer_001"  // TEST: Auto-login as buyer (disabled for Firebase testing)
    }

    /**
     * Check if user has a persisted session and restore it
     * Simulates checking stored tokens/credentials on app startup
     */
    override suspend fun checkPersistedAuth(): Result<String?> {
        return try {
            // Simulate network/storage delay
            delay(500)

            if (!hasCheckedPersistedAuth) {
                hasCheckedPersistedAuth = true

                // Check if we have a persisted user ID (simulating token validation)
                persistedUserId?.let { userId ->
                    // Validate the persisted session is still valid
                    if (users.values.any { it.userId == userId }) {
                        _currentUserId.value = userId
                        Result.success(userId)
                    } else {
                        // Session invalid, clear it
                        persistedUserId = null
                        _currentUserId.value = null
                        Result.success(null)
                    }
                } ?: Result.success(null)
            } else {
                // Already checked, return current state
                Result.success(_currentUserId.value)
            }
        } catch (e: Exception) {
            Result.failure(Exception("Failed to check auth status: ${e.message}"))
        }
    }

    override fun observeAuthState(): Flow<String?> {
        return _currentUserId.asStateFlow()
    }

    override suspend fun getCurrentUserId(): String? {
        return _currentUserId.value
    }

    override suspend fun signInWithEmail(email: String, password: String): Result<String> {
        return try {
            // Validate input
            if (email.isBlank() || password.isBlank()) {
                return Result.failure(Exception("Email and password cannot be empty"))
            }

            // Check if user exists and password matches
            val user = users[email]
            if (user == null) {
                return Result.failure(Exception("User not found"))
            }

            if (user.password != password) {
                return Result.failure(Exception("Invalid password"))
            }

            // Set current user
            _currentUserId.value = user.userId

            // Persist the user ID (simulating storing a token)
            persistedUserId = user.userId

            Result.success(user.userId)
        } catch (e: Exception) {
            Result.failure(Exception("Sign in failed: ${e.message}"))
        }
    }

    override suspend fun signUpWithEmail(email: String, password: String): Result<String> {
        return try {
            // Validate input
            if (email.isBlank() || password.isBlank()) {
                return Result.failure(Exception("Email and password cannot be empty"))
            }

            if (!email.contains("@")) {
                return Result.failure(Exception("Invalid email format"))
            }

            if (password.length < 6) {
                return Result.failure(Exception("Password must be at least 6 characters"))
            }

            // Check if user already exists
            if (users.containsKey(email)) {
                return Result.failure(Exception("User already exists"))
            }

            // Generate new user ID
            val userId = "user_${Clock.System.now().toEpochMilliseconds()}"

            // Create new user
            users[email] = UserCredentials(
                userId = userId,
                email = email,
                password = password
            )

            // Set current user
            _currentUserId.value = userId

            // Persist the user ID (simulating storing a token)
            persistedUserId = userId

            Result.success(userId)
        } catch (e: Exception) {
            Result.failure(Exception("Sign up failed: ${e.message}"))
        }
    }

    override suspend fun signOut(): Result<Unit> {
        return try {
            _currentUserId.value = null
            // Clear persisted session
            persistedUserId = null
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Sign out failed: ${e.message}"))
        }
    }

    override suspend fun deleteAccount(): Result<Unit> {
        return try {
            val userId = _currentUserId.value
                ?: return Result.failure(Exception("No user logged in"))

            // Find and remove user by userId
            val emailToRemove = users.entries.find { it.value.userId == userId }?.key
            if (emailToRemove != null) {
                users.remove(emailToRemove)
            }

            // Sign out
            _currentUserId.value = null

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Delete account failed: ${e.message}"))
        }
    }

    override suspend fun signInAnonymously(): Result<String> {
        return try {
            // Simulate network delay
            delay(300)

            // Generate anonymous user ID
            val userId = "guest_${Clock.System.now().toEpochMilliseconds()}"

            // Set current user as anonymous
            _currentUserId.value = userId

            // Note: We don't persist anonymous sessions
            // They're meant to be temporary

            Result.success(userId)
        } catch (e: Exception) {
            Result.failure(Exception("Anonymous sign in failed: ${e.message}"))
        }
    }

    override suspend fun isAnonymous(): Boolean {
        // In this mock implementation, if user ID starts with "guest_", it's anonymous
        return _currentUserId.value?.startsWith("guest_") ?: false
    }

    override suspend fun signInWithGoogle(idToken: String): Result<String> {
        // Mock implementation - not supported in InMemoryAuthRepository
        return Result.failure(Exception("Google Sign-In is only available with Firebase authentication"))
    }

    override suspend fun signInWithTwitter(token: String, secret: String): Result<String> {
        // Mock implementation - not supported in InMemoryAuthRepository
        return Result.failure(Exception("Twitter Sign-In is only available with Firebase authentication"))
    }

    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
        return try {
            // Simulate network delay
            delay(500)

            if (email.isBlank()) {
                return Result.failure(Exception("Email cannot be empty"))
            }

            // Check if user exists
            if (!users.containsKey(email)) {
                return Result.failure(Exception("No account found with this email"))
            }

            // In mock implementation, just simulate success
            println("📧 InMemoryAuthRepository: Password reset email sent to $email (simulated)")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to send reset email: ${e.message}"))
        }
    }

    override suspend fun linkWithEmail(email: String, password: String): Result<String> {
        return try {
            // Simulate network delay
            delay(500)

            val currentUserId = _currentUserId.value
                ?: return Result.failure(Exception("No user is currently signed in"))

            // Check if current user is anonymous
            if (!isAnonymous()) {
                return Result.failure(Exception("Current user is not anonymous"))
            }

            // Check if email is already in use
            if (users.containsKey(email)) {
                return Result.failure(Exception("An account already exists with this email"))
            }

            // Create permanent account with the same user ID
            users[email] = UserCredentials(
                userId = currentUserId,
                email = email,
                password = password
            )

            // Persist the session
            persistedUserId = currentUserId

            println("🔗 InMemoryAuthRepository: Linked anonymous account with email $email")
            Result.success(currentUserId)
        } catch (e: Exception) {
            Result.failure(Exception("Account linking failed: ${e.message}"))
        }
    }

    /**
     * Internal data class for storing user credentials
     */
    private data class UserCredentials(
        val userId: String,
        val email: String,
        val password: String
    )
}
