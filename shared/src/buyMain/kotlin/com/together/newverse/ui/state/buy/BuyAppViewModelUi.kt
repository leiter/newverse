package com.together.newverse.ui.state.buy

import com.together.newverse.ui.state.AuthMode
import com.together.newverse.ui.state.BuyAppViewModel
import com.together.newverse.ui.state.BottomSheetState
import com.together.newverse.ui.state.DialogState
import com.together.newverse.ui.state.SnackbarState
import com.together.newverse.ui.state.SnackbarType
import com.together.newverse.util.Log
import kotlinx.coroutines.flow.update

/**
 * UI management extension functions for BuyAppViewModel
 *
 * Handles snackbars, dialogs, bottom sheets, and refresh state.
 *
 * Extracted functions:
 * - showSnackbar
 * - hideSnackbar
 * - showDialog
 * - hideDialog
 * - showBottomSheet
 * - hideBottomSheet
 * - setRefreshing
 * - showPasswordResetDialog
 * - hidePasswordResetDialog
 */

internal fun BuyAppViewModel.showSnackBar(message: String, type: SnackbarType) {
    _state.update { current ->
        current.copy(
            ui = current.ui.copy(
                snackbar = SnackbarState(message = message, type = type)
            )
        )
    }
}

internal fun BuyAppViewModel.hideSnackBar() {
    _state.update { current ->
        current.copy(
            ui = current.ui.copy(snackbar = null)
        )
    }
}

internal fun BuyAppViewModel.showDialog(dialog: DialogState) {
    _state.update { current ->
        current.copy(
            ui = current.ui.copy(dialog = dialog)
        )
    }
}

internal fun BuyAppViewModel.hideDialog() {
    _state.update { current ->
        current.copy(
            ui = current.ui.copy(dialog = null)
        )
    }
}

internal fun BuyAppViewModel.showBottomSheet(sheet: BottomSheetState) {
    _state.update { current ->
        current.copy(
            ui = current.ui.copy(bottomSheet = sheet)
        )
    }
}

internal fun BuyAppViewModel.hideBottomSheet() {
    _state.update { current ->
        current.copy(
            ui = current.ui.copy(bottomSheet = null)
        )
    }
}

internal fun BuyAppViewModel.setRefreshing(isRefreshing: Boolean) {
    _state.update { current ->
        current.copy(
            ui = current.ui.copy(isRefreshing = isRefreshing)
        )
    }
}

/**
 * Show password reset dialog
 */
internal fun BuyAppViewModel.showPasswordResetDialog() {
    _state.update { current ->
        current.copy(
            auth = current.auth.copy(
                showPasswordResetDialog = true,
                passwordResetSent = false,
                error = null
            )
        )
    }
}

/**
 * Hide password reset dialog
 */
internal fun BuyAppViewModel.hidePasswordResetDialog() {
    _state.update { current ->
        current.copy(
            auth = current.auth.copy(
                showPasswordResetDialog = false,
                error = null
            )
        )
    }
}

/**
 * Set auth screen mode (LOGIN, REGISTER, FORGOT_PASSWORD)
 * Used to switch between login and register screens when shown from AppScaffold
 */
internal fun BuyAppViewModel.setAuthMode(mode: AuthMode) {
    _state.update { current ->
        current.copy(
            auth = current.auth.copy(
                mode = mode,
                error = null,
                isSuccess = false
            )
        )
    }
}

/**
 * Set auth error message to be displayed on login screen
 * Error persists until user dismisses it or tries again
 */
internal fun BuyAppViewModel.setAuthError(error: String) {
    _state.update { current ->
        current.copy(
            auth = current.auth.copy(
                error = error,
                isLoading = false,
                isSuccess = false  // Ensure success state is cleared
            )
        )
    }
}

// ===== The seller's home-title announcement ================================

/**
 * Read this device's dismissed announcement keys into state.
 *
 * Called once from `init`. Synchronous because the underlying storage is
 * SharedPreferences or NSUserDefaults rather than a network call, and because the
 * alternative — rendering before the read lands — would flash an announcement the
 * buyer closed on a previous launch.
 *
 * A failure is swallowed to an empty set. The cost is one announcement shown again;
 * the cost of failing startup over a read of a UI preference is the whole app.
 */
internal fun BuyAppViewModel.loadDismissedAnnouncements() {
    val storage = announcementDismissalStorage ?: return
    val dismissed = try {
        storage.dismissed()
    } catch (e: Exception) {
        Log.w("BuyVMUi") { "loadDismissedAnnouncements: ${e.message}" }
        return
    }
    _state.update { it.copy(dismissedAnnouncements = it.dismissedAnnouncements + dismissed) }
}

/**
 * Stop showing the announcement currently in the home title.
 *
 * The key is taken from state rather than from the caller, so a tap can only ever
 * dismiss the sentence that was on screen. State is updated whether or not the write
 * succeeds: the buyer asked for the banner to go away, and it going away is the part
 * they can see. A failed write means it comes back next launch, which is a far smaller
 * surprise than a close button that does nothing.
 */
internal fun BuyAppViewModel.dismissHomeAnnouncement() {
    val key = _state.value.homeTitleAnnouncement?.dismissalKey ?: return
    _state.update { it.copy(dismissedAnnouncements = it.dismissedAnnouncements + key) }
    try {
        announcementDismissalStorage?.dismiss(key)
    } catch (e: Exception) {
        Log.w("BuyVMUi") { "dismissHomeAnnouncement: $key not persisted - ${e.message}" }
    }
}
