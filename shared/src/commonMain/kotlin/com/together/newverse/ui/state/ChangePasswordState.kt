package com.together.newverse.ui.state

import org.jetbrains.compose.resources.StringResource

/**
 * State of the change-password dialog.
 *
 * Shared by both flavors: the dialog is the same three fields for a buyer and
 * for the seller, so the state, the dialog and the error mapping live in
 * `commonMain` and each flavor only maps its own actions onto them.
 *
 * The two error slots are separate on purpose. A wrong *current* password and a
 * rejected *new* one are different failures and belong under different fields;
 * collapsing them into one message would leave the user guessing which of the
 * two they got wrong.
 *
 * They hold a [StringResource] rather than a resolved string because a
 * ViewModel has no resource environment to resolve one in — `getString` needs
 * Android resources and throws in a plain JVM test. The dialog does the
 * resolving, where `stringResource` is available anyway.
 */
data class ChangePasswordState(
    val visible: Boolean = false,
    val currentPassword: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
    /** Rejected by re-authentication, i.e. the current password is wrong. */
    val currentPasswordError: StringResource? = null,
    /** Rejected when setting the new password, or a network or rate-limit error. */
    val newPasswordError: StringResource? = null,
    val isSubmitting: Boolean = false
) {
    /**
     * Closed and empty. Used both to dismiss the dialog and to clear it after a
     * successful change, so no password is left sitting in the state.
     */
    fun cleared(): ChangePasswordState = ChangePasswordState()
}
