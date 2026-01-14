package com.together.newverse.ui.state.buy

import androidx.lifecycle.viewModelScope
import com.together.newverse.data.repository.GitLiveArticleRepository
import com.together.newverse.domain.model.BuyerProfile
import com.together.newverse.ui.navigation.NavRoutes
import com.together.newverse.ui.state.BasketState
import com.together.newverse.ui.state.BuyAppViewModel
import com.together.newverse.ui.state.CustomerProfileScreenState
import com.together.newverse.ui.state.InitializationStep
import com.together.newverse.ui.state.SnackbarType
import com.together.newverse.ui.state.UnifiedAccountAction
import com.together.newverse.ui.state.UserRole
import com.together.newverse.ui.state.UserState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.account_deleted_success
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
                screens = current.screens.copy(
                    auth = current.screens.auth.copy(
                        isLoading = true,
                        error = null,
                        isSuccess = false
                    )
                )
            )
        }

        // Attempt sign in
        authRepository.signInWithEmail(email, password)
            .onSuccess { userId ->
                println("✅ Buy App Login Success: userId=$userId")

                // Success - just clear the forced login flag and let the app naturally navigate
                // Don't try to navigate manually - let AppScaffold handle it
                _state.update { current ->
                    current.copy(
                        screens = current.screens.copy(
                            auth = current.screens.auth.copy(
                                isLoading = false,
                                isSuccess = true,
                                error = null
                            )
                        ),
                        common = current.common.copy(
                            requiresLogin = false // Clear forced login flag - this will hide ForcedLoginScreen
                        ),
                        meta = current.meta.copy(
                            isInitializing = false,
                            isInitialized = true,
                            initializationStep = InitializationStep.Complete
                        )
                    )
                }

                // Show success message
                showSnackbar(getString(Res.string.snackbar_login_success), SnackbarType.SUCCESS)

                println("🎯 Login complete - requiresLogin cleared, app will show main UI")
            }
            .onFailure { error ->
                // Parse error message for user-friendly display
                val errorMessage = when {
                    error.message?.contains("No account found", true) == true ->
                        getString(Res.string.error_no_account)
                    error.message?.contains("Incorrect password", true) == true ->
                        getString(Res.string.error_wrong_password)
                    error.message?.contains("Invalid email", true) == true ->
                        getString(Res.string.error_email_invalid)
                    error.message?.contains("Network", true) == true ->
                        getString(Res.string.error_no_internet)
                    error.message?.contains("too many", true) == true ->
                        getString(Res.string.error_too_many_attempts)
                    else -> error.message ?: getString(Res.string.error_login_failed)
                }

                // Show error snackbar
                showSnackbar(errorMessage, SnackbarType.ERROR)

                // Update state with error
                _state.update { current ->
                    current.copy(
                        screens = current.screens.copy(
                            auth = current.screens.auth.copy(
                                isLoading = false,
                                error = errorMessage,
                                isSuccess = false
                            )
                        )
                    )
                }
            }
    }
}

internal fun BuyAppViewModel.loginWithGoogle() {
    println("🔐 UnifiedAppViewModel.loginWithGoogle: Triggering Google Sign-In flow")
    _state.update { current ->
        current.copy(
            common = current.common.copy(
                triggerGoogleSignIn = true
            )
        )
    }
}

internal fun BuyAppViewModel.loginWithTwitter() {
    println("🔐 UnifiedAppViewModel.loginWithTwitter: Triggering Twitter Sign-In flow")
    _state.update { current ->
        current.copy(
            common = current.common.copy(
                triggerTwitterSignIn = true
            )
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
                screens = current.screens.copy(
                    auth = current.screens.auth.copy(
                        isLoading = true,
                        error = null,
                        passwordResetSent = false
                    )
                )
            )
        }

        authRepository.sendPasswordResetEmail(email)
            .onSuccess {
                println("✅ Password reset email sent to $email")
                _state.update { current ->
                    current.copy(
                        screens = current.screens.copy(
                            auth = current.screens.auth.copy(
                                isLoading = false,
                                passwordResetSent = true,
                                showPasswordResetDialog = false,
                                error = null
                            )
                        )
                    )
                }
                showSnackbar(getString(Res.string.password_reset_sent), SnackbarType.SUCCESS)
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
                        screens = current.screens.copy(
                            auth = current.screens.auth.copy(
                                isLoading = false,
                                error = errorMessage,
                                passwordResetSent = false
                            )
                        )
                    )
                }
                showSnackbar(errorMessage, SnackbarType.ERROR)
            }
    }
}

internal fun BuyAppViewModel.logout() {
    viewModelScope.launch {
        authRepository.signOut()
            .onSuccess {
                // Clear basket and other user-specific data
                _state.update { current ->
                    current.copy(
                        common = current.common.copy(
                            user = UserState.Guest,
                            basket = BasketState(),
                            triggerGoogleSignOut = true
                        )
                    )
                }
                showSnackbar(getString(Res.string.snackbar_logout_success), SnackbarType.SUCCESS)
            }
            .onFailure { error ->
                showSnackbar(error.message ?: getString(Res.string.snackbar_logout_failed), SnackbarType.ERROR)
            }
    }
}

// ===== Account Management Handlers =====

internal fun BuyAppViewModel.handleAccountAction(action: UnifiedAccountAction) {
    when (action) {
        is UnifiedAccountAction.ShowLogoutWarning -> showLogoutWarningDialog()
        is UnifiedAccountAction.DismissLogoutWarning -> dismissLogoutWarningDialog()
        is UnifiedAccountAction.ShowLinkAccountDialog -> showLinkAccountDialog()
        is UnifiedAccountAction.DismissLinkAccountDialog -> dismissLinkAccountDialog()
        is UnifiedAccountAction.ShowDeleteAccountDialog -> showDeleteAccountDialog()
        is UnifiedAccountAction.DismissDeleteAccountDialog -> dismissDeleteAccountDialog()
        is UnifiedAccountAction.ConfirmGuestLogout -> confirmGuestLogout()
        is UnifiedAccountAction.LinkWithGoogle -> linkWithGoogle()
        is UnifiedAccountAction.LinkWithEmail -> navigateTo(NavRoutes.Register) // Email linking redirects to registration
        is UnifiedAccountAction.ConfirmDeleteAccount -> confirmDeleteAccount()
    }
}

internal fun BuyAppViewModel.showLogoutWarningDialog() {
    _state.update { current ->
        current.copy(
            screens = current.screens.copy(
                customerProfile = current.screens.customerProfile.copy(
                    showLogoutWarningDialog = true
                )
            )
        )
    }
}

internal fun BuyAppViewModel.dismissLogoutWarningDialog() {
    _state.update { current ->
        current.copy(
            screens = current.screens.copy(
                customerProfile = current.screens.customerProfile.copy(
                    showLogoutWarningDialog = false
                )
            )
        )
    }
}

internal fun BuyAppViewModel.showLinkAccountDialog() {
    _state.update { current ->
        current.copy(
            screens = current.screens.copy(
                customerProfile = current.screens.customerProfile.copy(
                    showLinkAccountDialog = true
                )
            )
        )
    }
}

internal fun BuyAppViewModel.dismissLinkAccountDialog() {
    _state.update { current ->
        current.copy(
            screens = current.screens.copy(
                customerProfile = current.screens.customerProfile.copy(
                    showLinkAccountDialog = false
                )
            )
        )
    }
}

internal fun BuyAppViewModel.showDeleteAccountDialog() {
    _state.update { current ->
        current.copy(
            screens = current.screens.copy(
                customerProfile = current.screens.customerProfile.copy(
                    showDeleteAccountDialog = true
                )
            )
        )
    }
}

internal fun BuyAppViewModel.dismissDeleteAccountDialog() {
    _state.update { current ->
        current.copy(
            screens = current.screens.copy(
                customerProfile = current.screens.customerProfile.copy(
                    showDeleteAccountDialog = false
                )
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
                    screens = current.screens.copy(
                        customerProfile = current.screens.customerProfile.copy(
                            showLogoutWarningDialog = false,
                            isLinkingAccount = true // Use as loading state
                        )
                    )
                )
            }

            val userId = getCurrentUserId()

            // Step 1: Delete buyer profile from Firebase
            if (userId != null) {
                profileRepository.deleteBuyerProfile(userId)
                println("🗑️ Deleted buyer profile for: $userId")
            }

            // Step 2: Clear local basket
            basketRepository.clearBasket()
            println("🗑️ Cleared local basket")

            // Step 3: Delete Firebase Auth account (this also signs out)
            authRepository.deleteAccount()
                .onSuccess { println("🔐 Deleted Firebase Auth account") }
                .onFailure { e -> println("⚠️ Failed to delete auth account: ${e.message}") }

            // Step 4: Clear all local state
            _state.update { current ->
                current.copy(
                    common = current.common.copy(
                        user = UserState.Guest,
                        basket = BasketState(),
                        triggerGoogleSignOut = true,
                        requiresLogin = true // Show login screen
                    ),
                    screens = current.screens.copy(
                        customerProfile = CustomerProfileScreenState(),
                        mainScreen = current.screens.mainScreen.copy(
                            favouriteArticles = emptyList()
                        )
                    )
                )
            }

            showSnackbar(getString(Res.string.logout_guest_success), SnackbarType.INFO)

        } catch (e: Exception) {
            println("❌ Error during guest logout: ${e.message}")
            _state.update { current ->
                current.copy(
                    screens = current.screens.copy(
                        customerProfile = current.screens.customerProfile.copy(
                            isLinkingAccount = false
                        )
                    )
                )
            }
            showSnackbar(getString(Res.string.logout_error, e.message ?: "Unknown error"), SnackbarType.ERROR)
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
                screens = current.screens.copy(
                    customerProfile = current.screens.customerProfile.copy(
                        isLinkingAccount = true,
                        linkAccountError = null
                    )
                )
            )
        }

        // Trigger Google Sign-In for linking
        // The platform layer will handle this and call back with the ID token
        _state.update { current ->
            current.copy(
                common = current.common.copy(triggerGoogleSignIn = true),
                screens = current.screens.copy(
                    customerProfile = current.screens.customerProfile.copy(
                        showLinkAccountDialog = false
                    )
                )
            )
        }
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
                    screens = current.screens.copy(
                        customerProfile = current.screens.customerProfile.copy(
                            isLoading = true
                        )
                    )
                )
            }

            val userId = getCurrentUserId()
            var cancelledOrderCount = 0

            if (userId != null) {
                // Get buyer profile to access placedOrderIds
                val profileResult = profileRepository.getBuyerProfile()
                val buyerProfile = profileResult.getOrNull()

                if (buyerProfile != null) {
                    // Clear user data: cancel future orders, keep past orders, delete profile
                    val cleanUpResult = profileRepository.clearUserData(
                        sellerId = GitLiveArticleRepository.DEFAULT_SELLER_ID,
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

            // Delete Firebase Auth account (this also signs out)
            authRepository.deleteAccount()
                .onSuccess { println("🔐 Deleted Firebase Auth account for authenticated user") }
                .onFailure { e -> println("⚠️ Failed to delete auth account: ${e.message}") }

            // Reset state and hide dialog
            _state.update { current ->
                current.copy(
                    common = current.common.copy(
                        user = UserState.Guest,
                        basket = BasketState(),
                        triggerGoogleSignOut = true,
                        requiresLogin = true
                    ),
                    screens = current.screens.copy(
                        customerProfile = CustomerProfileScreenState()
                    )
                )
            }

            // Show success message with cancelled order count
            val message = if (cancelledOrderCount > 0) {
                getString(Res.string.account_deleted_with_cancellations, cancelledOrderCount)
            } else {
                getString(Res.string.account_deleted_success)
            }
            showSnackbar(message, SnackbarType.INFO)

        } catch (e: Exception) {
            // Hide loading and dialog on error
            _state.update { current ->
                current.copy(
                    screens = current.screens.copy(
                        customerProfile = current.screens.customerProfile.copy(
                            isLoading = false,
                            showDeleteAccountDialog = false
                        )
                    )
                )
            }
            showSnackbar("Fehler beim Löschen: ${e.message}", SnackbarType.ERROR)
        }
    }
}

internal fun BuyAppViewModel.getCurrentUserId(): String? {
    return when (val user = _state.value.common.user) {
        is UserState.LoggedIn -> user.id
        else -> null
    }
}

internal fun BuyAppViewModel.register(email: String, password: String, name: String) {
    viewModelScope.launch {
        // Clear any previous errors and set loading state
        _state.update { current ->
            current.copy(
                screens = current.screens.copy(
                    auth = current.screens.auth.copy(
                        isLoading = true,
                        error = null,
                        isSuccess = false
                    )
                )
            )
        }

        // Attempt sign up
        authRepository.signUpWithEmail(email, password)
            .onSuccess { userId ->
                // Update user state with name
                _state.update { current ->
                    current.copy(
                        common = current.common.copy(
                            user = UserState.LoggedIn(
                                id = userId,
                                name = name,
                                email = email,
                                role = UserRole.CUSTOMER // Default to customer for new registrations
                            )
                        ),
                        screens = current.screens.copy(
                            auth = current.screens.auth.copy(
                                isLoading = false,
                                error = null,
                                isSuccess = true
                            )
                        )
                    )
                }

                // Show success message
                showSnackbar(getString(Res.string.snackbar_account_created), SnackbarType.SUCCESS)

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
                    error.message?.contains("network") == true ->
                        getString(Res.string.error_no_internet)
                    else ->
                        getString(Res.string.error_registration_failed)
                }

                _state.update { current ->
                    current.copy(
                        screens = current.screens.copy(
                            auth = current.screens.auth.copy(
                                isLoading = false,
                                error = errorMessage,
                                isSuccess = false
                            )
                        )
                    )
                }

                showSnackbar(errorMessage, SnackbarType.ERROR)
            }
    }
}
