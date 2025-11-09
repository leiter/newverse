package com.together.newverse.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.TwitterAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.together.newverse.domain.repository.AuthRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Firebase implementation of AuthRepository for production use.
 *
 * This implementation uses Firebase Authentication to manage user sessions
 * and provides real authentication features including:
 * - Email/password authentication
 * - Session persistence across app restarts
 * - Secure token management
 * - Real-time auth state monitoring
 */
class FirebaseAuthRepository : AuthRepository {

    private val auth: FirebaseAuth = Firebase.auth

    /**
     * Check if user has a persisted session and restore it
     * This is automatically handled by Firebase Auth
     */
    override suspend fun checkPersistedAuth(): Result<String?> {
        return try {
            val currentUser = auth.currentUser
            if (currentUser != null) {
                // Refresh the token to ensure it's still valid
                currentUser.getIdToken(true).await()
                Result.success(currentUser.uid)
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Result.failure(Exception("Failed to check auth status: ${e.message}"))
        }
    }

    override fun observeAuthState(): Flow<String?> = callbackFlow {
        println("🔐 FirebaseAuthRepository.observeAuthState: Setting up auth state listener")

        val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            println("🔐 FirebaseAuthRepository.observeAuthState: Auth state changed - userId=${user?.uid}, isAnonymous=${user?.isAnonymous}")
            trySend(user?.uid)
        }

        auth.addAuthStateListener(authStateListener)
        println("🔐 FirebaseAuthRepository.observeAuthState: Auth state listener added")

        awaitClose {
            println("🔐 FirebaseAuthRepository.observeAuthState: Removing auth state listener")
            auth.removeAuthStateListener(authStateListener)
        }
    }

    override suspend fun getCurrentUserId(): String? {
        return auth.currentUser?.uid
    }

    override suspend fun signInWithEmail(email: String, password: String): Result<String> {
        return try {
            // Validate input
            if (email.isBlank() || password.isBlank()) {
                return Result.failure(Exception("Email and password cannot be empty"))
            }

            // Sign in with Firebase
            val authResult = auth.signInWithEmailAndPassword(email, password).await()
            val user = authResult.user

            if (user != null) {
                Result.success(user.uid)
            } else {
                Result.failure(Exception("Sign in failed: User is null"))
            }
        } catch (e: Exception) {
            // Handle Firebase-specific exceptions
            val errorMessage = when {
                e.message?.contains("INVALID_EMAIL") == true -> "Invalid email format"
                e.message?.contains("USER_DISABLED") == true -> "This account has been disabled"
                e.message?.contains("USER_NOT_FOUND") == true -> "No account found with this email"
                e.message?.contains("WRONG_PASSWORD") == true -> "Incorrect password"
                e.message?.contains("TOO_MANY_REQUESTS") == true -> "Too many failed attempts. Please try again later"
                e.message?.contains("NETWORK_ERROR") == true -> "Network error. Please check your connection"
                else -> e.message ?: "Sign in failed"
            }
            Result.failure(Exception(errorMessage))
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

            // Create user with Firebase
            val authResult = auth.createUserWithEmailAndPassword(email, password).await()
            val user = authResult.user

            if (user != null) {
                // Send email verification (optional)
                user.sendEmailVerification().await()
                Result.success(user.uid)
            } else {
                Result.failure(Exception("Sign up failed: User is null"))
            }
        } catch (e: Exception) {
            // Handle Firebase-specific exceptions
            val errorMessage = when {
                e.message?.contains("EMAIL_EXISTS") == true -> "An account already exists with this email"
                e.message?.contains("INVALID_EMAIL") == true -> "Invalid email format"
                e.message?.contains("WEAK_PASSWORD") == true -> "Password is too weak. Please use at least 6 characters"
                e.message?.contains("OPERATION_NOT_ALLOWED") == true -> "Email/password sign up is not enabled"
                e.message?.contains("TOO_MANY_REQUESTS") == true -> "Too many requests. Please try again later"
                e.message?.contains("NETWORK_ERROR") == true -> "Network error. Please check your connection"
                else -> e.message ?: "Sign up failed"
            }
            Result.failure(Exception(errorMessage))
        }
    }

    override suspend fun signOut(): Result<Unit> {
        return try {
            auth.signOut()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Sign out failed: ${e.message}"))
        }
    }

    override suspend fun deleteAccount(): Result<Unit> {
        return try {
            val user = auth.currentUser
                ?: return Result.failure(Exception("No user logged in"))

            // Re-authenticate if necessary (for sensitive operations)
            // Note: In production, you might want to prompt for password again

            // Delete the user account
            user.delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            val errorMessage = when {
                e.message?.contains("REQUIRES_RECENT_LOGIN") == true ->
                    "This operation requires recent authentication. Please sign in again"
                else -> e.message ?: "Failed to delete account"
            }
            Result.failure(Exception(errorMessage))
        }
    }

    /**
     * Send a password reset email to the user
     */
    suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
        return try {
            if (email.isBlank()) {
                return Result.failure(Exception("Email cannot be empty"))
            }

            auth.sendPasswordResetEmail(email).await()
            Result.success(Unit)
        } catch (e: Exception) {
            val errorMessage = when {
                e.message?.contains("USER_NOT_FOUND") == true -> "No account found with this email"
                e.message?.contains("INVALID_EMAIL") == true -> "Invalid email format"
                else -> e.message ?: "Failed to send reset email"
            }
            Result.failure(Exception(errorMessage))
        }
    }

    /**
     * Update the user's email address
     */
    suspend fun updateEmail(newEmail: String): Result<Unit> {
        return try {
            val user = auth.currentUser
                ?: return Result.failure(Exception("No user logged in"))

            user.updateEmail(newEmail).await()
            Result.success(Unit)
        } catch (e: Exception) {
            val errorMessage = when {
                e.message?.contains("REQUIRES_RECENT_LOGIN") == true ->
                    "This operation requires recent authentication. Please sign in again"
                e.message?.contains("EMAIL_EXISTS") == true -> "This email is already in use"
                e.message?.contains("INVALID_EMAIL") == true -> "Invalid email format"
                else -> e.message ?: "Failed to update email"
            }
            Result.failure(Exception(errorMessage))
        }
    }

    /**
     * Update the user's password
     */
    suspend fun updatePassword(newPassword: String): Result<Unit> {
        return try {
            val user = auth.currentUser
                ?: return Result.failure(Exception("No user logged in"))

            if (newPassword.length < 6) {
                return Result.failure(Exception("Password must be at least 6 characters"))
            }

            user.updatePassword(newPassword).await()
            Result.success(Unit)
        } catch (e: Exception) {
            val errorMessage = when {
                e.message?.contains("REQUIRES_RECENT_LOGIN") == true ->
                    "This operation requires recent authentication. Please sign in again"
                e.message?.contains("WEAK_PASSWORD") == true -> "Password is too weak"
                else -> e.message ?: "Failed to update password"
            }
            Result.failure(Exception(errorMessage))
        }
    }

    /**
     * Re-authenticate the user (for sensitive operations)
     */
    suspend fun reauthenticate(email: String, password: String): Result<Unit> {
        return try {
            val user = auth.currentUser
                ?: return Result.failure(Exception("No user logged in"))

            val credential = com.google.firebase.auth.EmailAuthProvider
                .getCredential(email, password)

            user.reauthenticate(credential).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Re-authentication failed: ${e.message}"))
        }
    }

    /**
     * Check if the user's email is verified
     */
    fun isEmailVerified(): Boolean {
        return auth.currentUser?.isEmailVerified ?: false
    }

    /**
     * Resend email verification
     */
    suspend fun resendEmailVerification(): Result<Unit> {
        return try {
            val user = auth.currentUser
                ?: return Result.failure(Exception("No user logged in"))

            user.sendEmailVerification().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to send verification email: ${e.message}"))
        }
    }

    /**
     * Get the current user's email
     */
    fun getCurrentUserEmail(): String? {
        return auth.currentUser?.email
    }

    /**
     * Sign in anonymously (for guest users)
     */
    override suspend fun signInAnonymously(): Result<String> {
        return try {
            println("🔐 FirebaseAuthRepository.signInAnonymously: Starting anonymous sign in...")
            val authResult = auth.signInAnonymously().await()
            val user = authResult.user

            if (user != null) {
                println("🔐 FirebaseAuthRepository.signInAnonymously: SUCCESS - userId=${user.uid}, isAnonymous=${user.isAnonymous}")
                Result.success(user.uid)
            } else {
                println("❌ FirebaseAuthRepository.signInAnonymously: FAILED - user is null")
                Result.failure(Exception("Anonymous sign in failed"))
            }
        } catch (e: Exception) {
            println("❌ FirebaseAuthRepository.signInAnonymously: EXCEPTION - ${e.message}")
            e.printStackTrace()
            Result.failure(Exception("Anonymous sign in failed: ${e.message}"))
        }
    }

    /**
     * Check if current user is anonymous/guest
     */
    override suspend fun isAnonymous(): Boolean {
        return auth.currentUser?.isAnonymous ?: false
    }

    /**
     * Link anonymous account to email/password
     */
    suspend fun linkAnonymousAccount(email: String, password: String): Result<String> {
        return try {
            val user = auth.currentUser
                ?: return Result.failure(Exception("No user logged in"))

            if (!user.isAnonymous) {
                return Result.failure(Exception("Current user is not anonymous"))
            }

            val credential = com.google.firebase.auth.EmailAuthProvider
                .getCredential(email, password)

            val authResult = user.linkWithCredential(credential).await()
            val linkedUser = authResult.user

            if (linkedUser != null) {
                Result.success(linkedUser.uid)
            } else {
                Result.failure(Exception("Account linking failed"))
            }
        } catch (e: Exception) {
            val errorMessage = when {
                e.message?.contains("EMAIL_EXISTS") == true -> "This email is already in use"
                e.message?.contains("CREDENTIAL_ALREADY_IN_USE") == true -> "This credential is already linked to another account"
                else -> e.message ?: "Account linking failed"
            }
            Result.failure(Exception(errorMessage))
        }
    }

    /**
     * Sign in with Google using ID token
     */
    override suspend fun signInWithGoogle(idToken: String): Result<String> {
        return try {
            println("🔐 FirebaseAuthRepository.signInWithGoogle: Starting Google sign in...")

            // Create Google credential with the ID token
            val credential = GoogleAuthProvider.getCredential(idToken, null)

            // Sign in with the credential
            val authResult = auth.signInWithCredential(credential).await()
            val user = authResult.user

            if (user != null) {
                println("🔐 FirebaseAuthRepository.signInWithGoogle: SUCCESS - userId=${user.uid}, email=${user.email}")
                Result.success(user.uid)
            } else {
                println("❌ FirebaseAuthRepository.signInWithGoogle: FAILED - user is null")
                Result.failure(Exception("Google sign in failed: User is null"))
            }
        } catch (e: Exception) {
            println("❌ FirebaseAuthRepository.signInWithGoogle: EXCEPTION - ${e.message}")
            e.printStackTrace()
            val errorMessage = when {
                e.message?.contains("ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL") == true ->
                    "An account already exists with the same email but different sign-in credentials"
                e.message?.contains("INVALID_CREDENTIAL") == true ->
                    "Invalid Google credentials"
                e.message?.contains("USER_DISABLED") == true ->
                    "This account has been disabled"
                e.message?.contains("NETWORK_ERROR") == true ->
                    "Network error. Please check your connection"
                else -> e.message ?: "Google sign in failed"
            }
            Result.failure(Exception(errorMessage))
        }
    }

    /**
     * Sign in with Twitter using OAuth token and secret
     */
    override suspend fun signInWithTwitter(token: String, secret: String): Result<String> {
        return try {
            println("🔐 FirebaseAuthRepository.signInWithTwitter: Starting Twitter sign in...")

            // Create Twitter credential with the OAuth token and secret
            val credential = TwitterAuthProvider.getCredential(token, secret)

            // Sign in with the credential
            val authResult = auth.signInWithCredential(credential).await()
            val user = authResult.user

            if (user != null) {
                println("🔐 FirebaseAuthRepository.signInWithTwitter: SUCCESS - userId=${user.uid}, displayName=${user.displayName}")
                Result.success(user.uid)
            } else {
                println("❌ FirebaseAuthRepository.signInWithTwitter: FAILED - user is null")
                Result.failure(Exception("Twitter sign in failed: User is null"))
            }
        } catch (e: Exception) {
            println("❌ FirebaseAuthRepository.signInWithTwitter: EXCEPTION - ${e.message}")
            e.printStackTrace()
            val errorMessage = when {
                e.message?.contains("ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL") == true ->
                    "An account already exists with the same email but different sign-in credentials"
                e.message?.contains("INVALID_CREDENTIAL") == true ->
                    "Invalid Twitter credentials"
                e.message?.contains("USER_DISABLED") == true ->
                    "This account has been disabled"
                e.message?.contains("NETWORK_ERROR") == true ->
                    "Network error. Please check your connection"
                else -> e.message ?: "Twitter sign in failed"
            }
            Result.failure(Exception(errorMessage))
        }
    }
}