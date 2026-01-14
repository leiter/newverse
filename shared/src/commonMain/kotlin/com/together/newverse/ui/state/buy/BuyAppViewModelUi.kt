package com.together.newverse.ui.state.buy

import com.together.newverse.ui.state.BuyAppViewModel
import com.together.newverse.ui.state.BottomSheetState
import com.together.newverse.ui.state.DialogState
import com.together.newverse.ui.state.SnackbarState
import com.together.newverse.ui.state.SnackbarType
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

internal fun BuyAppViewModel.showSnackbar(message: String, type: SnackbarType) {
    _state.update { current ->
        current.copy(
            common = current.common.copy(
                ui = current.common.ui.copy(
                    snackbar = SnackbarState(message = message, type = type)
                )
            )
        )
    }
}

internal fun BuyAppViewModel.hideSnackbar() {
    _state.update { current ->
        current.copy(
            common = current.common.copy(
                ui = current.common.ui.copy(snackbar = null)
            )
        )
    }
}

internal fun BuyAppViewModel.showDialog(dialog: DialogState) {
    _state.update { current ->
        current.copy(
            common = current.common.copy(
                ui = current.common.ui.copy(dialog = dialog)
            )
        )
    }
}

internal fun BuyAppViewModel.hideDialog() {
    _state.update { current ->
        current.copy(
            common = current.common.copy(
                ui = current.common.ui.copy(dialog = null)
            )
        )
    }
}

internal fun BuyAppViewModel.showBottomSheet(sheet: BottomSheetState) {
    _state.update { current ->
        current.copy(
            common = current.common.copy(
                ui = current.common.ui.copy(bottomSheet = sheet)
            )
        )
    }
}

internal fun BuyAppViewModel.hideBottomSheet() {
    _state.update { current ->
        current.copy(
            common = current.common.copy(
                ui = current.common.ui.copy(bottomSheet = null)
            )
        )
    }
}

internal fun BuyAppViewModel.setRefreshing(isRefreshing: Boolean) {
    _state.update { current ->
        current.copy(
            common = current.common.copy(
                ui = current.common.ui.copy(isRefreshing = isRefreshing)
            )
        )
    }
}

/**
 * Show password reset dialog
 */
internal fun BuyAppViewModel.showPasswordResetDialog() {
    _state.update { current ->
        current.copy(
            screens = current.screens.copy(
                auth = current.screens.auth.copy(
                    showPasswordResetDialog = true,
                    passwordResetSent = false,
                    error = null
                )
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
            screens = current.screens.copy(
                auth = current.screens.auth.copy(
                    showPasswordResetDialog = false,
                    error = null
                )
            )
        )
    }
}
