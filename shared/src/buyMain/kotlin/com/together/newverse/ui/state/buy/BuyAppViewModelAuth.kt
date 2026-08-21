package com.together.newverse.ui.state.buy

import androidx.lifecycle.viewModelScope
import com.together.newverse.domain.repository.AuthUserInfo
import com.together.newverse.ui.navigation.NavRoutes
import com.together.newverse.ui.state.BasketState
import com.together.newverse.ui.state.BuyAppViewModel
import com.together.newverse.ui.state.CustomerProfileScreenState
import com.together.newverse.ui.state.InitializationStep
import com.together.newverse.ui.state.SnackbarType
import com.together.newverse.ui.state.BuyAccountAction
import com.together.newverse.domain.model.SellerEventType
import com.together.newverse.ui.state.UserRole
import com.together.newverse.ui.state.UserState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.account_deleted_success
import newverse.shared.generated.resources.account_deletion_incomplete
import newverse.shared.generated.resources.account_deleted_with_cancellations
import newverse.shared.generated.resources.error_email_in_use
import newverse.shared.generated.resources.error_email_invalid
import newverse.shared.generated.resources.error_login_failed
import newverse.shared.generated.resources.error_no_account
import newverse.shared.generated.resources.error_no_internet
import newverse.shared.generated.resources.error_registration_failed
import newverse.shared.generated.resources.error_too_many_attempts
import newverse.shared.generated.resources.error_weak_password
import newverse.shared.generated.resources.error_wrong_password
import newverse.shared.generated.resources.logout_error
import newverse.shared.generated.resources.logout_guest_success
import newverse.shared.generated.resources.password_reset_failed
import newverse.shared.generated.resources.password_reset_sent
import newverse.shared.generated.resources.snackbar_account_created
import newverse.shared.generated.resources.snackbar_login_success
import newverse.shared.generated.resources.snackbar_logout_failed
import newverse.shared.generated.resources.snackbar_logout_success
import newverse.shared.generated.resources.link_account_success
import org.jetbrains.compose.resources.getString

/**
 * Authentication and Account Management extension functions for BuyAppViewModel
 *
 * Handles login, logout, registration, password reset, and account operations.
 *
 * Extracted functions:
 * - login, loginWithGoogle, loginWithTwitter, logout
 * - register, sendPasswordResetEmail
 * - handleAccountAction
 * - Dialog management: showLogoutWarningDialog, showLinkAccountDialog, showDeleteAccountDialog (and dismiss variants)
 * - Account operations: confirmGuestLogout, linkWithGoogle, confirmDeleteAccount
 * - Helpers: getCurrentUserId
 *
 * Note: resetGoogleSignInTrigger, resetTwitterSignInTrigger, resetGoogleSignOutTrigger
 * remain in BuyAppViewModel.kt as override methods (required by AppViewModel interface)
 */

internal fun BuyAppViewModel.login(email: String, password: String) {
    viewModelScope.launch {
        // Clear any previous errors and set loading state
        _state.update { current ->
            current.copy(
                auth = current.auth.copy(
                    isLoading = true,
                    error = null,
                    isSuccess = false
                )
            )
        }

        // Attempt sign in with timeout (15 seconds) to prevent hanging on network issues
        val result = withTimeoutOrNull(15_000L) {
            authRepository.signInWithEmail(email, password)
        }

        if (result == null) {
            // Timed out — no internet or server unreachable
            val errorMessage = getString(Res.string.error_no_internet)
            println("⏱️ Login timeout - treating as network error")
            showSnackBar(errorMessage, SnackbarType.ERROR)
            _state.update { current ->
                current.copy(
                    auth = current.auth.copy(
                        isLoading = false,
                        error = errorMessage,
                        isSuccess = false
                    )
                )
            }
            return@launch
        }

        result
            .onSuccess { userId ->
                println("✅ [LOGIN SUCCESS] Buy App Login Success: userId=$userId")
                println("📋 [LOGIN STATE] Setting isLoading=false, error=null")

                // Clear auth screen loading state
                _state.update { current ->
                    current.copy(
                        auth = current.auth.copy(
                            isLoading = false,
                            isSuccess = true,
                            error = null
                        ),
                        requiresLogin = false // Clear forced login flag
                    )
                }

                // Show success message
                showSnackBar(getString(Res.string.snackbar_login_success), SnackbarType.SUCCESS)

                // Resume app initialization (load profile, order, articles)
                resumeInitializationAfterAuth()

                println("🎯 Login complete - resuming initialization")
            }
            .onFailure { error ->
                println("❌ [LOGIN ERROR] Login failed: ${error.message}")
                // Parse error message for user-friendly display
                val errorMessage = when {
                    error.message?.contains("No account found", true) == true ->
                        getString(Res.string.error_no_account)
                    error.message?.contains("Incorrect password", true) == true ->
                        getString(Res.string.error_wrong_password)
                    error.message?.contains("Invalid email", true) == true ->
                        getString(Res.string.error_email_invalid)
                    error.message?.contains("Network", true) == true ||
                    error.message?.contains("Unable to resolve host", true) == true ||
                    error.message?.contains("No address associated", true) == true ||
                    error.message?.contains("failed to connect", true) == true ||
                    error.message?.contains("timeout", true) == true ||
                    error.message?.contains("UnknownHostException", true) == true ->
                        getString(Res.string.error_no_internet)
                    error.message?.contains("too many", true) == true ->
                        getString(Res.string.error_too_many_attempts)
                    else -> error.message ?: getString(Res.string.error_login_failed)
                }

                // Show error snackbar
                showSnackBar(errorMessage, SnackbarType.ERROR)

                // Update state with error
                _state.update { current ->
                    current.copy(
                        auth = current.auth.copy(
                            isLoading = false,
                            error = errorMessage,
                            isSuccess = false
                        )
                    )
                }
            }
    }
}

internal fun BuyAppViewModel.loginWithGoogle() {
    println("🔐 BuyAppViewModel.loginWithGoogle: Triggering Google Sign-In flow")
    _state.update { current ->
        current.copy(
            triggerGoogleSignIn = true
        )
    }
}

internal fun BuyAppViewModel.loginWithTwitter() {
    println("🔐 BuyAppViewModel.loginWithTwitter: Triggering Twitter Sign-In flow")
    _state.update { current ->
        current.copy(
            triggerTwitterSignIn = true
        )
    }
}

internal fun BuyAppViewModel.loginWithApple() {
    println("🔐 BuyAppViewModel.loginWithApple: Triggering Apple Sign-In flow")
    _state.update { current ->
        current.copy(
            triggerAppleSignIn = true
        )
    }
}

/**
 * Send password reset email to the specified email address.
 */
internal fun BuyAppViewModel.sendPasswordResetEmail(email: String) {
    viewModelScope.launch {
        // Set loading state
        _state.update { current ->
            current.copy(
                auth = current.auth.copy(
                    isLoading = true,
                    error = null,
                    passwordResetSent = false
                )
            )
        }

        authRepository.sendPasswordResetEmail(email)
            .onSuccess {
                println("✅ Password reset email sent to $email")
                _state.update { current ->
                    current.copy(
                        auth = current.auth.copy(
                            isLoading = false,
                            passwordResetSent = true,
                            showPasswordResetDialog = false,
                            error = null
                        )
                    )
                }
                showSnackBar(getString(Res.string.password_reset_sent), SnackbarType.SUCCESS)
            }
            .onFailure { error ->
                println("❌ Password reset failed: ${error.message}")
                val errorMessage = when {
                    error.message?.contains("No account found", true) == true ->
                        getString(Res.string.error_no_account)
                    error.message?.contains("Invalid email", true) == true ->
                        getString(Res.string.error_email_invalid)
                    else -> error.message ?: getString(Res.string.password_reset_failed)
                }
                _state.update { current ->
                    current.copy(
                        auth = current.auth.copy(
                            isLoading = false,
                            error = errorMessage,
                            passwordResetSent = false
                        )
                    )
                }
                showSnackBar(errorMessage, SnackbarType.ERROR)
            }
    }
}

internal fun BuyAppViewModel.logout() {
    viewModelScope.launch {
        authRepository.signOut()
            .onSuccess {
                // Clear per-user storage
                buyerUUIDStorage?.clearActiveUserId()
                (sellerConfig as? com.together.newverse.data.config.BuyerSellerConfig)?.clearActiveUser()

                // Clear basket and other user-specific data
                _state.update { current ->
                    current.copy(
                        user = UserState.Guest,
                        basket = BasketState(),
                        triggerGoogleSignOut = true
                    )
                }
                showSnackBar(getString(Res.string.snackbar_logout_success), SnackbarType.SUCCESS)
            }
            .onFailure { error ->
                showSnackBar(error.message ?: getString(Res.string.snackbar_logout_failed), SnackbarType.ERROR)
            }
    }
}

// ===== Account Management Handlers =====

internal fun BuyAppViewModel.handleAccountAction(action: BuyAccountAction) {
    when (action) {
        is BuyAccountAction.ShowLogoutWarning -> showLogoutWarningDialog()
        is BuyAccountAction.DismissLogoutWarning -> dismissLogoutWarningDialog()
        is BuyAccountAction.ShowLinkAccountDialog -> showLinkAccountDialog()
        is BuyAccountAction.DismissLinkAccountDialog -> dismissLinkAccountDialog()
        is BuyAccountAction.ShowDeleteAccountDialog -> showDeleteAccountDialog()
        is BuyAccountAction.DismissDeleteAccountDialog -> dismissDeleteAccountDialog()
        is BuyAccountAction.ConfirmGuestLogout -> confirmGuestLogout()
        is BuyAccountAction.LinkWithGoogle -> linkWithGoogle()
        is BuyAccountAction.LinkWithEmail -> linkWithEmail(action.email, action.password)
        is BuyAccountAction.ShowEmailLinkingDialog -> showEmailLinkingDialog()
        is BuyAccountAction.DismissEmailLinkingDialog -> dismissEmailLinkingDialog()
        is BuyAccountAction.UpdateEmailLinkingEmail -> updateEmailLinkingEmail(action.email)
        is BuyAccountAction.UpdateEmailLinkingPassword -> updateEmailLinkingPassword(action.password)
        is BuyAccountAction.UpdateEmailLinkingConfirmPassword -> updateEmailLinkingConfirmPassword(action.confirmPassword)
        is BuyAccountAction.ConfirmDeleteAccount -> confirmDeleteAccount()
    }
}

internal fun BuyAppViewModel.showLogoutWarningDialog() {
    _state.update { current ->
        current.copy(
            customerProfile = current.customerProfile.copy(
                showLogoutWarningDialog = true
            )
        )
    }
}

internal fun BuyAppViewModel.dismissLogoutWarningDialog() {
    _state.update { current ->
        current.copy(
            customerProfile = current.customerProfile.copy(
                showLogoutWarningDialog = false
            )
        )
    }
}

internal fun BuyAppViewModel.showLinkAccountDialog() {
    _state.update { current ->
        current.copy(
            customerProfile = current.customerProfile.copy(
                showLinkAccountDialog = true
            )
        )
    }
}

internal fun BuyAppViewModel.dismissLinkAccountDialog() {
    _state.update { current ->
        current.copy(
            customerProfile = current.customerProfile.copy(
                showLinkAccountDialog = false
            )
        )
    }
}

internal fun BuyAppViewModel.showDeleteAccountDialog() {
    _state.update { current ->
        current.copy(
            customerProfile = current.customerProfile.copy(
                showDeleteAccountDialog = true
            )
        )
    }
}

internal fun BuyAppViewModel.dismissDeleteAccountDialog() {
    _state.update { current ->
        current.copy(
            customerProfile = current.customerProfile.copy(
                showDeleteAccountDialog = false
            )
        )
    }
}

/**
 * Confirm guest logout with immediate data deletion.
 * Deletes buyer profile from Firebase, clears local basket, signs out.
 */
internal fun BuyAppViewModel.confirmGuestLogout() {
    viewModelScope.launch {
        try {
            // Close dialogs first
            _state.update { current ->
                current.copy(
                    customerProfile = current.customerProfile.copy(
                        showLogoutWarningDialog = false,
                        isLinkingAccount = true // Use as loading state
                    )
                )
            }

            val userId = getCurrentUserId()

            // Capture identity before anything is deleted - the seller's log needs it.
            val audit = captureBuyerAudit(userId)

            // Step 1: Delete buyer profile from Firebase
            if (userId != null) {
                profileRepository.deleteBuyerProfile(userId)
                println("🗑️ Deleted buyer profile for: $userId")
            }

            // Step 2: Clear local basket
            basketRepository.clearBasket()
            println("🗑️ Cleared local basket")

            // Step 3: Clear per-user storage
            buyerUUIDStorage?.clearActiveUserId()
            (sellerConfig as? com.together.newverse.data.config.BuyerSellerConfig)?.clearActiveUser()
            println("🗑️ Cleared per-user storage")

            // Step 4: Record the wipe in the seller's book keeping log.
            // Written before auth deletion, while the session can still write.
            logSellerEvent(
                type = SellerEventType.GUEST_DATA_DELETED,
                audit = audit,
                details = "Guest logged out; profile, basket and favourites deleted"
            )

            // Step 5: Delete Firebase Auth account (this also signs out)
            val deletionError = deleteAuthAccountOrSignOut(audit)

            // Step 6: Clear all local state
            _state.update { current ->
                current.copy(
                    user = UserState.Guest,
                    basket = BasketState(),
                    triggerGoogleSignOut = true,
                    requiresLogin = true, // Show login screen
                    customerProfile = CustomerProfileScreenState(),
                    mainScreen = current.mainScreen.copy(
                        favouriteArticles = emptyList()
                    )
                )
            }

            if (deletionError == null) {
                showSnackBar(getString(Res.string.logout_guest_success), SnackbarType.INFO)
            } else {
                showSnackBar(getString(Res.string.account_deletion_incomplete), SnackbarType.ERROR)
            }

        } catch (e: Exception) {
            println("❌ Error during guest logout: ${e.message}")
            _state.update { current ->
                current.copy(
                    customerProfile = current.customerProfile.copy(
                        isLinkingAccount = false
                    )
                )
            }
            showSnackBar(getString(Res.string.logout_error, e.message ?: "Unknown error"), SnackbarType.ERROR)
        }
    }
}

/**
 * Link anonymous account with Google credentials.
 * Triggers platform-specific Google Sign-In for linking.
 */
internal fun BuyAppViewModel.linkWithGoogle() {
    viewModelScope.launch {
        _state.update { current ->
            current.copy(
                customerProfile = current.customerProfile.copy(
                    isLinkingAccount = true,
                    linkAccountError = null
                )
            )
        }

        // Trigger Google Sign-In for linking
        // The platform layer will handle this and call back with the ID token
        _state.update { current ->
            current.copy(
                triggerGoogleSignIn = true,
                customerProfile = current.customerProfile.copy(
                    showLinkAccountDialog = false
                )
            )
        }
    }
}

/**
 * Link anonymous account with email and password credentials.
 * This preserves all guest data (favorites, basket, profile) while upgrading to a permanent account.
 */
internal fun BuyAppViewModel.linkWithEmail(email: String, password: String) {
    viewModelScope.launch {
        // Set loading state
        _state.update { current ->
            current.copy(
                customerProfile = current.customerProfile.copy(
                    isLinkingAccount = true,
                    emailLinkingError = null
                )
            )
        }

        // Get profile BEFORE linking to capture its state as an anonymous user.
        val anonymousProfile = profileRepository.getBuyerProfile().getOrNull()

        // Attempt to link the account
        authRepository.linkWithEmail(email, password)
            .onSuccess { userId ->
                println("✅ BuyAppViewModel.linkWithEmail: Success - userId=$userId")

                // Update profile with the linked email.
                val updatedProfile = (anonymousProfile ?: _state.value.customerProfile.profile)?.copy(
                    emailAddress = email
                )
                if (updatedProfile != null) {
                    profileRepository.saveBuyerProfile(updatedProfile)
                }

                // Migrate anonymous user storage to real account
                val previousUserId = (_state.value.user as? UserState.LoggedIn)?.id
                if (previousUserId != null && previousUserId != userId) {
                    buyerUUIDStorage?.renameUserId(previousUserId, userId)
                    buyerUUIDStorage?.setActiveUserId(userId)
                    (sellerConfig as? com.together.newverse.data.config.BuyerSellerConfig)?.migrateAnonymousUser(previousUserId, userId)
                    println("🔑 Migrated per-user storage from anonymous $previousUserId to real $userId")
                }

                // Update state: close dialog, update user state
                _state.update { current ->
                    val currentUser = current.user
                    val newUserState = if (currentUser is UserState.LoggedIn) {
                        currentUser.copy(email = email)
                    } else {
                        UserState.LoggedIn(
                            id = userId,
                            name = updatedProfile?.displayName ?: "",
                            email = email,
                            role = UserRole.CUSTOMER
                        )
                    }

                    current.copy(
                        user = newUserState,
                        customerProfile = current.customerProfile.copy(
                            isLinkingAccount = false,
                            showEmailLinkingDialog = false,
                            showLinkAccountDialog = false,
                            emailLinkingEmail = "",
                            emailLinkingPassword = "",
                            emailLinkingConfirmPassword = "",
                            emailLinkingError = null,
                            profile = updatedProfile
                        )
                    )
                }

                // Record the upgrade in the seller's book keeping log
                logSellerEvent(
                    type = SellerEventType.ACCOUNT_LINKED,
                    audit = BuyerAuditSnapshot(
                        buyerId = userId,
                        firebaseUserId = userId,
                        buyerUUID = updatedProfile?.buyerUUID ?: "",
                        buyerName = updatedProfile?.displayName ?: "",
                        buyerEmail = email
                    ),
                    details = "Guest upgraded to permanent account via email"
                )

                // Show success message
                showSnackBar(getString(Res.string.link_account_success), SnackbarType.SUCCESS)
            }
            .onFailure { error ->
                println("❌ BuyAppViewModel.linkWithEmail: Error - ${error.message}")

                // Update state with error
                _state.update { current ->
                    current.copy(
                        customerProfile = current.customerProfile.copy(
                            isLinkingAccount = false,
                            emailLinkingError = error.message
                        )
                    )
                }
            }
    }
}
internal fun BuyAppViewModel.showEmailLinkingDialog() {
    _state.update { current ->
        current.copy(
            customerProfile = current.customerProfile.copy(
                showEmailLinkingDialog = true,
                showLinkAccountDialog = false, // Close the provider selection dialog
                emailLinkingEmail = "",
                emailLinkingPassword = "",
                emailLinkingConfirmPassword = "",
                emailLinkingError = null
            )
        )
    }
}

internal fun BuyAppViewModel.dismissEmailLinkingDialog() {
    _state.update { current ->
        current.copy(
            customerProfile = current.customerProfile.copy(
                showEmailLinkingDialog = false,
                emailLinkingEmail = "",
                emailLinkingPassword = "",
                emailLinkingConfirmPassword = "",
                emailLinkingError = null
            )
        )
    }
}

internal fun BuyAppViewModel.updateEmailLinkingEmail(email: String) {
    _state.update { current ->
        current.copy(
            customerProfile = current.customerProfile.copy(
                emailLinkingEmail = email,
                emailLinkingError = null // Clear error when user types
            )
        )
    }
}

internal fun BuyAppViewModel.updateEmailLinkingPassword(password: String) {
    _state.update { current ->
        current.copy(
            customerProfile = current.customerProfile.copy(
                emailLinkingPassword = password,
                emailLinkingError = null // Clear error when user types
            )
        )
    }
}

internal fun BuyAppViewModel.updateEmailLinkingConfirmPassword(confirmPassword: String) {
    _state.update { current ->
        current.copy(
            customerProfile = current.customerProfile.copy(
                emailLinkingConfirmPassword = confirmPassword,
                emailLinkingError = null // Clear error when user types
            )
        )
    }
}

/**
 * Confirm account deletion for authenticated users.
 * - Future orders (pickup date > now) are CANCELLED
 * - Past orders are kept for seller records
 * - Buyer profile is deleted
 * - Firebase Auth account is deleted
 */
internal fun BuyAppViewModel.confirmDeleteAccount() {
    viewModelScope.launch {
        try {
            // Set loading state (keep dialog visible to show progress)
            _state.update { current ->
                current.copy(
                    customerProfile = current.customerProfile.copy(
                        isLoading = true
                    )
                )
            }

            val userId = getCurrentUserId()
            var cancelledOrderCount = 0

            // Capture identity before anything is deleted - the seller's log needs it.
            val audit = captureBuyerAudit(userId)

            if (userId != null) {
                // Get buyer profile to access placedOrderIds
                val profileResult = profileRepository.getBuyerProfile()
                val buyerProfile = profileResult.getOrNull()

                if (buyerProfile != null) {
                    // Clear user data: cancel future orders, keep past orders, delete profile
                    val cleanUpResult = profileRepository.clearUserData(
                        sellerId = sellerConfig.sellerId,
                        buyerProfile = buyerProfile
                    )

                    cleanUpResult.onSuccess { result ->
                        cancelledOrderCount = result.cancelledOrders.size
                        println("🔐 confirmDeleteAccount: Cleanup complete - cancelled=${result.cancelledOrders.size}, skipped=${result.skippedOrders.size}, profileDeleted=${result.profileDeleted}")
                        if (result.errors.isNotEmpty()) {
                            println("⚠️ confirmDeleteAccount: Cleanup had errors: ${result.errors}")
                        }
                    }.onFailure { e ->
                        println("⚠️ confirmDeleteAccount: Cleanup failed - ${e.message}")
                    }
                } else {
                    // No profile found, just delete auth
                    println("🔐 confirmDeleteAccount: No buyer profile found, proceeding with auth deletion only")
                }
            }

            // Clear local basket
            basketRepository.clearBasket()

            // Clear per-user storage, same as the guest wipe
            buyerUUIDStorage?.clearActiveUserId()
            (sellerConfig as? com.together.newverse.data.config.BuyerSellerConfig)?.clearActiveUser()

            // Record the deletion in the seller's book keeping log.
            // Written before auth deletion, while the session can still write.
            logSellerEvent(
                type = SellerEventType.ACCOUNT_DELETED,
                audit = audit,
                cancelledOrderCount = cancelledOrderCount,
                details = "Account deleted by buyer; past orders kept for seller records"
            )

            // Delete Firebase Auth account (this also signs out)
            val deletionError = deleteAuthAccountOrSignOut(audit)

            // Reset state and hide dialog
            _state.update { current ->
                current.copy(
                    user = UserState.Guest,
                    basket = BasketState(),
                    triggerGoogleSignOut = true,
                    requiresLogin = true,
                    customerProfile = CustomerProfileScreenState(),
                    mainScreen = current.mainScreen.copy(
                        favouriteArticles = emptyList()
                    )
                )
            }

            // Show success message with cancelled order count
            if (deletionError != null) {
                showSnackBar(getString(Res.string.account_deletion_incomplete), SnackbarType.ERROR)
            } else {
                val message = if (cancelledOrderCount > 0) {
                    getString(Res.string.account_deleted_with_cancellations, cancelledOrderCount)
                } else {
                    getString(Res.string.account_deleted_success)
                }
                showSnackBar(message, SnackbarType.INFO)
            }

        } catch (e: Exception) {
            // Hide loading and dialog on error
            _state.update { current ->
                current.copy(
                    customerProfile = current.customerProfile.copy(
                        isLoading = false,
                        showDeleteAccountDialog = false
                    )
                )
            }
            showSnackBar("Fehler beim Löschen: ${e.message}", SnackbarType.ERROR)
        }
    }
}

/**
 * Buyer identity captured *before* a destructive operation, because the profile
 * is deleted by the time the book keeping event is written.
 */
internal data class BuyerAuditSnapshot(
    val buyerId: String = "",
    /** Firebase Auth uid, read from the auth session rather than UI state. */
    val firebaseUserId: String = "",
    val buyerUUID: String = "",
    val buyerName: String = "",
    val buyerEmail: String = ""
)

/**
 * Snapshot the buyer identity for the seller's event log.
 * Falls back to whatever is in state when no profile could be loaded.
 */
internal suspend fun BuyAppViewModel.captureBuyerAudit(userId: String?): BuyerAuditSnapshot {
    val profile = _state.value.customerProfile.profile
    // The auth session is the authority on the uid; UI state can lag behind it.
    val firebaseUserId = authRepository.getCurrentUserId() ?: userId ?: profile?.id ?: ""
    return BuyerAuditSnapshot(
        buyerId = userId ?: profile?.id ?: firebaseUserId,
        firebaseUserId = firebaseUserId,
        buyerUUID = profile?.buyerUUID ?: "",
        buyerName = profile?.displayName ?: "",
        buyerEmail = profile?.emailAddress ?: ""
    )
}

/**
 * Append a book keeping event to the seller's log.
 * Failures are swallowed: logging must never break the operation it records.
 */
internal suspend fun BuyAppViewModel.logSellerEvent(
    type: SellerEventType,
    audit: BuyerAuditSnapshot,
    cancelledOrderCount: Int = 0,
    details: String = ""
) {
    val repository = sellerEventRepository ?: return
    if (sellerConfig.isDemoMode) {
        // Not connected to a real seller - there are no books to keep.
        println("📒 logSellerEvent: Skipping ${type.name} (demo mode)")
        return
    }
    repository.logEvent(
        sellerId = sellerConfig.sellerId,
        type = type,
        buyerId = audit.buyerId,
        firebaseUserId = audit.firebaseUserId,
        buyerUUID = audit.buyerUUID,
        buyerName = audit.buyerName,
        buyerEmail = audit.buyerEmail,
        cancelledOrderCount = cancelledOrderCount,
        details = details
    ).onFailure { e ->
        println("⚠️ logSellerEvent: Could not record ${type.name} - ${e.message}")
    }
}

/**
 * Delete the Firebase Auth account after the buyer's data has been removed.
 *
 * Auth deletion has to happen last, because deleting the buyer data requires an
 * authenticated session. If it fails anyway (Firebase demands a recent login for
 * this operation), the account would be left behind holding no data. Rather than
 * swallow that, we retry once, force a sign-out so no session survives on wiped
 * data, and record the orphaned uid in the seller log so it can be cleaned up.
 *
 * @return null on success, or the error message when the account was orphaned.
 */
internal suspend fun BuyAppViewModel.deleteAuthAccountOrSignOut(
    audit: BuyerAuditSnapshot
): String? {
    authRepository.deleteAccount()
        .onSuccess {
            println("🔐 Deleted Firebase Auth account ${audit.firebaseUserId}")
            return null
        }

    // Retry once: transient network failures are the common case here.
    val retry = authRepository.deleteAccount()
    retry.onSuccess {
        println("🔐 Deleted Firebase Auth account ${audit.firebaseUserId} (retry)")
        return null
    }

    val reason = retry.exceptionOrNull()?.message ?: "Unknown error"
    println("⚠️ Auth account ${audit.firebaseUserId} could not be deleted - $reason")

    // Record the orphan while still authenticated: the append rule requires a
    // session whose uid matches buyerId, so signing out first would block it.
    logSellerEvent(
        type = SellerEventType.ACCOUNT_DELETION_INCOMPLETE,
        audit = audit,
        details = "Auth account $reason - uid ${audit.firebaseUserId} needs manual removal"
    )

    // Data is already gone; make sure no signed-in session outlives it.
    authRepository.signOut()

    return reason
}

internal fun BuyAppViewModel.getCurrentUserId(): String? {
    return when (val user = _state.value.user) {
        is UserState.LoggedIn -> user.id
        else -> null
    }
}

/**
 * Continue as guest - creates an anonymous Firebase user and resumes app initialization.
 * Called when user taps "Continue as Guest" on the login screen.
 */
internal fun BuyAppViewModel.continueAsGuest() {
    viewModelScope.launch {
        // Set loading state
        _state.update { current ->
            current.copy(
                auth = current.auth.copy(
                    isLoading = true,
                    error = null
                )
            )
        }

        // Attempt guest sign-in with timeout (15 seconds) to prevent hanging on network issues
        val result = withTimeoutOrNull(15_000L) {
            authRepository.signInAnonymously()
        }

        if (result == null) {
            // Timed out — no internet or server unreachable
            val errorMessage = getString(Res.string.error_no_internet)
            println("⏱️ Guest sign-in timeout - treating as network error")
            _state.update { current ->
                current.copy(
                    auth = current.auth.copy(
                        isLoading = false,
                        error = errorMessage
                    )
                )
            }
            return@launch
        }

        result.fold(
            onSuccess = { userId ->
                println("✅ Buy App: Guest sign-in successful, user ID: $userId")
                _state.update { current ->
                    current.copy(
                        auth = current.auth.copy(
                            isLoading = false,
                            error = null
                        )
                    )
                }
                // Resume app initialization (load profile, order, articles)
                resumeInitializationAfterAuth()
            },
            onFailure = { error ->
                println("❌ Buy App: Guest sign-in failed - ${error.message}")
                // Parse error message for user-friendly display
                val errorMessage = when {
                    error.message?.contains("Network", true) == true ||
                    error.message?.contains("Unable to resolve host", true) == true ||
                    error.message?.contains("No address associated", true) == true ||
                    error.message?.contains("failed to connect", true) == true ||
                    error.message?.contains("timeout", true) == true ||
                    error.message?.contains("UnknownHostException", true) == true ->
                        getString(Res.string.error_no_internet)
                    else -> error.message ?: "Failed to continue as guest"
                }
                _state.update { current ->
                    current.copy(
                        auth = current.auth.copy(
                            isLoading = false,
                            error = errorMessage
                        )
                    )
                }
            }
        )
    }
}

/**
 * Resume app initialization after successful authentication.
 * Loads user profile, current order, and products.
 * Called after login, registration, Google sign-in, or continue as guest.
 *
 * @param authUserInfo Optional user info from auth provider (e.g., Google) to populate profile
 */
internal fun BuyAppViewModel.resumeInitializationAfterAuth(authUserInfo: AuthUserInfo? = null) {
    viewModelScope.launch {
        try {
            println("🚀 Resuming initialization after auth...")
            if (authUserInfo != null) {
                println("📧 Auth user info: email=${authUserInfo.email}, name=${authUserInfo.displayName}")
            }

            // Activate per-user storage
            val userId = authUserInfo?.id ?: authRepository.getCurrentUserId()
            if (userId != null) {
                buyerUUIDStorage?.setActiveUserId(userId)
                (sellerConfig as? com.together.newverse.data.config.BuyerSellerConfig)?.setActiveUserId(userId)
                println("🔑 Activated per-user storage for userId=$userId")
            }

            // Set initializing state
            _state.update { current ->
                current.copy(
                    meta = current.meta.copy(
                        isInitializing = true,
                        initializationStep = InitializationStep.LoadingProfile
                    )
                )
            }

            // Load user profile and update with auth provider info if available
            loadUserProfile(authUserInfo)

            // Restore buyerUUID from Firebase profile if local storage is empty.
            // On iOS, NSUserDefaults is wiped on uninstall/reinstall, so the UUID is lost
            // locally even though it still exists in Firebase. Without it,
            // startObservingAccessStatus() immediately marks the buyer as demo mode,
            // causing loadCurrentOrder() to look in demo_orders/ and miss the real order.
            val localUUID = buyerUUIDStorage?.get()
            if (localUUID == null) {
                val profileUUID = _state.value.customerProfile.profile?.buyerUUID
                if (!profileUUID.isNullOrBlank()) {
                    println("🔑 resumeInitializationAfterAuth: Restoring buyerUUID from Firebase profile after reinstall")
                    buyerUUIDStorage?.set(profileUUID)
                }
            }

            // Load seller display name if connected to a seller
            val currentSellerId = sellerConfig.sellerId
            if (currentSellerId.isNotEmpty()) {
                try {
                    val sellerDisplayName = profileRepository.getSellerProfile(currentSellerId)
                        .getOrNull()?.displayName ?: ""
                    _state.update { it.copy(
                        connectedSellerId = currentSellerId,
                        connectedSellerDisplayName = sellerDisplayName
                    )}
                } catch (e: Exception) {
                    println("[NV_BuyAppVM] resumeInitializationAfterAuth: getSellerProfile failed (non-fatal) - ${e.message}")
                }
            }

            // Start observing access status if we have a stored UUID
            // Called before loadCurrentOrder so isDemoMode is as accurate as possible
            startObservingAccessStatus()

            // Load current order
            _state.update { current ->
                current.copy(
                    meta = current.meta.copy(
                        initializationStep = InitializationStep.LoadingOrder
                    )
                )
            }
            loadCurrentOrder()

            // Load articles
            _state.update { current ->
                current.copy(
                    meta = current.meta.copy(
                        initializationStep = InitializationStep.LoadingArticles
                    )
                )
            }
            loadProducts()
            loadMainScreenArticles()

            // Load persisted demo orders on startup
            if (sellerConfig.isDemoMode) {
                loadOrderHistory()
            }

            // Mark initialization complete
            _state.update { current ->
                current.copy(
                    meta = current.meta.copy(
                        isInitializing = false,
                        isInitialized = true,
                        initializationStep = InitializationStep.Complete
                    )
                )
            }

            println("✅ Initialization resumed successfully!")

        } catch (e: Exception) {
            println("❌ Error resuming initialization: ${e.message}")
            _state.update { current ->
                current.copy(
                    meta = current.meta.copy(
                        isInitializing = false,
                        isInitialized = false,
                        initializationStep = InitializationStep.Failed(
                            step = "resume",
                            message = e.message ?: "Unknown error"
                        )
                    )
                )
            }
        }
    }
}

internal fun BuyAppViewModel.register(email: String, password: String, name: String) {
    viewModelScope.launch {
        // Clear any previous errors and set loading state
        _state.update { current ->
            current.copy(
                auth = current.auth.copy(
                    isLoading = true,
                    error = null,
                    isSuccess = false
                )
            )
        }

        // Attempt sign up with timeout (15 seconds) to prevent hanging on network issues
        val result = withTimeoutOrNull(15_000L) {
            authRepository.signUpWithEmail(email, password)
        }

        if (result == null) {
            // Timed out — no internet or server unreachable
            val errorMessage = getString(Res.string.error_no_internet)
            println("⏱️ Registration timeout - treating as network error")
            showSnackBar(errorMessage, SnackbarType.ERROR)
            _state.update { current ->
                current.copy(
                    auth = current.auth.copy(
                        isLoading = false,
                        error = errorMessage,
                        isSuccess = false
                    )
                )
            }
            return@launch
        }

        result
            .onSuccess { userId ->
                // Update user state with name
                _state.update { current ->
                    current.copy(
                        user = UserState.LoggedIn(
                            id = userId,
                            name = name,
                            email = email,
                            role = UserRole.CUSTOMER // Default to customer for new registrations
                        ),
                        auth = current.auth.copy(
                            isLoading = false,
                            error = null,
                            isSuccess = true
                        )
                    )
                }

                // Show success message
                showSnackBar(getString(Res.string.snackbar_account_created), SnackbarType.SUCCESS)

                // Navigate to login after a short delay
                delay(1500)
                navigateTo(NavRoutes.Login)
            }
            .onFailure { error ->
                // Provide user-friendly error messages
                val errorMessage = when {
                    error.message?.contains("email-already-in-use") == true ->
                        getString(Res.string.error_email_in_use)
                    error.message?.contains("weak-password") == true ->
                        getString(Res.string.error_weak_password)
                    error.message?.contains("invalid-email") == true ->
                        getString(Res.string.error_email_invalid)
                    error.message?.contains("Network", true) == true ||
                    error.message?.contains("Unable to resolve host", true) == true ||
                    error.message?.contains("No address associated", true) == true ||
                    error.message?.contains("failed to connect", true) == true ||
                    error.message?.contains("timeout", true) == true ||
                    error.message?.contains("UnknownHostException", true) == true ->
                        getString(Res.string.error_no_internet)
                    else ->
                        getString(Res.string.error_registration_failed)
                }

                _state.update { current ->
                    current.copy(
                        auth = current.auth.copy(
                            isLoading = false,
                            error = errorMessage,
                            isSuccess = false
                        )
                    )
                }

                showSnackBar(errorMessage, SnackbarType.ERROR)
            }
    }
}
