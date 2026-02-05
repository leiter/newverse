package com.together.newverse.data.repository

import com.together.newverse.domain.repository.AuthRepository
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.FirebaseUser
import dev.gitlive.firebase.auth.EmailAuthProvider
import dev.gitlive.firebase.auth.GoogleAuthProvider
import dev.gitlive.firebase.auth.TwitterAuthProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * GitLive implementation of AuthRepository for cross-platform authentication.
 *
 * This is the REAL implementation using GitLive Firebase SDK.
 */
class GitLiveAuthRepository : AuthRepository {

    // GitLive Firebase Auth instance
    private val auth: FirebaseAuth = Firebase.auth

    /**
     * Check if user has a persisted session and restore it.
     */
    override suspend fun checkPersistedAuth(): Result<String?> {
        return try {
            println("🔐 GitLiveAuthRepository.checkPersistedAuth: Checking persisted auth")

            val currentUser = auth.currentUser
            if (currentUser != null) {
                // Refresh token to ensure validity
                currentUser.getIdToken(true)
                println("✅ GitLiveAuthRepository.checkPersistedAuth: Found user ${currentUser.uid}")
                Result.success(currentUser.uid)
            } else {
                println("✅ GitLiveAuthRepository.checkPersistedAuth: No persisted user")
                Result.success(null)
            }
        } catch (e: Exception) {
            println("❌ GitLiveAuthRepository.checkPersistedAuth: Error - ${e.message}")
            Result.failure(Exception("Failed to check auth status: ${e.message}"))
        }
    }

    /**
     * Observe authentication state changes.
     */
    override fun observeAuthState(): Flow<String?> {
        println("🔐 GitLiveAuthRepository.observeAuthState: Setting up auth state observer")

        // GitLive provides authStateChanged as a Flow
        return auth.authStateChanged.map { user ->
            println("🔐 GitLiveAuthRepository.observeAuthState: Auth state changed - userId=${user?.uid}")
            user?.uid
        }
    }

    /**
     * Get current user ID.
     */
    override suspend fun getCurrentUserId(): String? {
        return auth.currentUser?.uid
    }

    /**
     * Sign in with email and password.
     */
    override suspend fun signInWithEmail(email: String, password: String): Result<String> {
        return try {
            // Input validation
            if (email.isBlank() || password.isBlank()) {
                return Result.failure(Exception("Email and password cannot be empty"))
            }

            println("🔐 GitLiveAuthRepository.signInWithEmail: Attempting sign in for $email")

            // Sign in with GitLive Firebase Auth
            val authResult = auth.signInWithEmailAndPassword(email, password)
            val user = authResult.user

            if (user != null) {
                println("✅ GitLiveAuthRepository.signInWithEmail: Success - userId=${user.uid}")
                Result.success(user.uid)
            } else {
                Result.failure(Exception("Sign in failed: User is null"))
            }

        } catch (e: Exception) {
            handleAuthException(e, "Sign in")
        }
    }

    /**
     * Sign up with email and password.
     */
    override suspend fun signUpWithEmail(email: String, password: String): Result<String> {
        return try {
            // Input validation
            if (email.isBlank() || password.isBlank()) {
                return Result.failure(Exception("Email and password cannot be empty"))
            }

            println("🔐 GitLiveAuthRepository.signUpWithEmail: Creating account for $email")

            // Create user with GitLive Firebase Auth
            val authResult = auth.createUserWithEmailAndPassword(email, password)
            val user = authResult.user

            if (user != null) {
                println("✅ GitLiveAuthRepository.signUpWithEmail: Success - userId=${user.uid}")
                Result.success(user.uid)
            } else {
                Result.failure(Exception("Sign up failed: User creation failed"))
            }

        } catch (e: Exception) {
            handleAuthException(e, "Sign up")
        }
    }

    /**
     * Sign out current user.
     */
    override suspend fun signOut(): Result<Unit> {
        return try {
            val currentUser = auth.currentUser
            println("🔐 GitLiveAuthRepository.signOut: Signing out user ${currentUser?.uid}")

            auth.signOut()

            println("✅ GitLiveAuthRepository.signOut: Success")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Sign out failed: ${e.message}"))
        }
    }

    /**
     * Delete current user account.
     */
    override suspend fun deleteAccount(): Result<Unit> {
        return try {
            val currentUser = auth.currentUser
            if (currentUser == null) {
                return Result.failure(Exception("No user is currently signed in"))
            }

            println("🔐 GitLiveAuthRepository.deleteAccount: Deleting account for user ${currentUser.uid}")

            currentUser.delete()

            println("✅ GitLiveAuthRepository.deleteAccount: Success")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Account deletion failed: ${e.message}"))
        }
    }

    /**
     * Sign in anonymously as a guest.
     */
    override suspend fun signInAnonymously(): Result<String> {
        return try {
            println("🔐 GitLiveAuthRepository.signInAnonymously: Creating anonymous session")

            val authResult = auth.signInAnonymously()
            val user = authResult.user

            if (user != null) {
                println("✅ GitLiveAuthRepository.signInAnonymously: Success - userId=${user.uid}")
                Result.success(user.uid)
            } else {
                Result.failure(Exception("Anonymous sign in failed"))
            }

        } catch (e: Exception) {
            Result.failure(Exception("Anonymous sign in failed: ${e.message}"))
        }
    }

    /**
     * Check if current user is anonymous.
     */
    override suspend fun isAnonymous(): Boolean {
        return auth.currentUser?.isAnonymous ?: false
    }

    /**
     * Sign in with Google.
     */
    override suspend fun signInWithGoogle(idToken: String): Result<String> {
        return try {
            println("🔐 GitLiveAuthRepository.signInWithGoogle: Authenticating with Google")

            val credential = GoogleAuthProvider.credential(idToken, null)
            val authResult = auth.signInWithCredential(credential)
            val user = authResult.user

            if (user != null) {
                println("✅ GitLiveAuthRepository.signInWithGoogle: Success - userId=${user.uid}")
                Result.success(user.uid)
            } else {
                Result.failure(Exception("Google sign in failed"))
            }

        } catch (e: Exception) {
            Result.failure(Exception("Google sign in failed: ${e.message}"))
        }
    }

    /**
     * Sign in with Twitter.
     */
    override suspend fun signInWithTwitter(token: String, secret: String): Result<String> {
        return try {
            println("🔐 GitLiveAuthRepository.signInWithTwitter: Authenticating with Twitter")

            val credential = TwitterAuthProvider.credential(token, secret)
            val authResult = auth.signInWithCredential(credential)
            val user = authResult.user

            if (user != null) {
                println("✅ GitLiveAuthRepository.signInWithTwitter: Success - userId=${user.uid}")
                Result.success(user.uid)
            } else {
                Result.failure(Exception("Twitter sign in failed"))
            }

        } catch (e: Exception) {
            Result.failure(Exception("Twitter sign in failed: ${e.message}"))
        }
    }

    /**
     * Send password reset email.
     */
    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
        return try {
            if (email.isBlank()) {
                return Result.failure(Exception("Email cannot be empty"))
            }

            println("🔐 GitLiveAuthRepository.sendPasswordResetEmail: Sending reset email to $email")

            auth.sendPasswordResetEmail(email)

            println("✅ GitLiveAuthRepository.sendPasswordResetEmail: Success")
            Result.success(Unit)
        } catch (e: Exception) {
            val errorMessage = when {
                e.message?.contains("USER_NOT_FOUND") == true -> "No account found with this email"
                e.message?.contains("INVALID_EMAIL") == true -> "Invalid email format"
                else -> e.message ?: "Failed to send reset email"
            }
            println("❌ GitLiveAuthRepository.sendPasswordResetEmail: Error - $errorMessage")
            Result.failure(Exception(errorMessage))
        }
    }

    /**
     * Link anonymous account with email and password credentials.
     * Converts a guest account to a permanent email-authenticated account.
     */
    override suspend fun linkWithEmail(email: String, password: String): Result<String> {
        return try {
            // Input validation
            if (email.isBlank() || password.isBlank()) {
                return Result.failure(Exception("Email and password cannot be empty"))
            }

            val currentUser = auth.currentUser
            if (currentUser == null) {
                return Result.failure(Exception("No user is currently signed in"))
            }

            if (!currentUser.isAnonymous) {
                return Result.failure(Exception("Current user is not anonymous"))
            }

            println("🔐 GitLiveAuthRepository.linkWithEmail: Linking anonymous account with email $email")

            // Create email credential and link with current user
            val credential = EmailAuthProvider.credential(email, password)
            val authResult = currentUser.linkWithCredential(credential)
            val linkedUser = authResult.user

            if (linkedUser != null) {
                println("✅ GitLiveAuthRepository.linkWithEmail: Success - userId=${linkedUser.uid}")
                Result.success(linkedUser.uid)
            } else {
                Result.failure(Exception("Account linking failed: User is null"))
            }

        } catch (e: Exception) {
            handleLinkingException(e)
        }
    }

    // Helper functions

    /**
     * Handle authentication exceptions with user-friendly error messages.
     */
    private fun handleAuthException(exception: Exception, operation: String): Result<String> {
        val errorMessage = when {
            exception.message?.contains("INVALID_EMAIL") == true -> "Invalid email format"
            exception.message?.contains("EMAIL_ALREADY_IN_USE") == true -> "An account already exists with this email"
            exception.message?.contains("WEAK_PASSWORD") == true -> "Password is too weak"
            exception.message?.contains("USER_DISABLED") == true -> "This account has been disabled"
            exception.message?.contains("USER_NOT_FOUND") == true -> "No account found with this email"
            exception.message?.contains("WRONG_PASSWORD") == true -> "Incorrect password"
            exception.message?.contains("TOO_MANY_REQUESTS") == true -> "Too many failed attempts. Please try again later"
            exception.message?.contains("NETWORK_ERROR") == true -> "Network error. Please check your connection"
            exception.message?.contains("OPERATION_NOT_ALLOWED") == true -> "This sign-in method is not enabled"
            else -> exception.message ?: "$operation failed"
        }

        println("❌ GitLiveAuthRepository: $operation error - $errorMessage")
        return Result.failure(Exception(errorMessage))
    }

    /**
     * Handle account linking exceptions with user-friendly error messages.
     */
    private fun handleLinkingException(exception: Exception): Result<String> {
        val errorMessage = when {
            exception.message?.contains("CREDENTIAL_ALREADY_IN_USE") == true -> "This email is already linked to another account"
            exception.message?.contains("EMAIL_ALREADY_IN_USE") == true -> "An account already exists with this email"
            exception.message?.contains("INVALID_EMAIL") == true -> "Invalid email format"
            exception.message?.contains("WEAK_PASSWORD") == true -> "Password is too weak (minimum 6 characters)"
            exception.message?.contains("REQUIRES_RECENT_LOGIN") == true -> "Please sign out and sign in again before linking"
            exception.message?.contains("PROVIDER_ALREADY_LINKED") == true -> "An email account is already linked"
            exception.message?.contains("NETWORK_ERROR") == true -> "Network error. Please check your connection"
            else -> exception.message ?: "Account linking failed"
        }

        println("❌ GitLiveAuthRepository.linkWithEmail: Error - $errorMessage")
        return Result.failure(Exception(errorMessage))
    }
}