package com.together.newverse.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.together.newverse.ui.state.ChangePasswordState
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.a11y_changing_password
import newverse.shared.generated.resources.button_cancel
import newverse.shared.generated.resources.change_password_confirm
import newverse.shared.generated.resources.change_password_confirm_label
import newverse.shared.generated.resources.change_password_current_label
import newverse.shared.generated.resources.change_password_message
import newverse.shared.generated.resources.change_password_new_label
import newverse.shared.generated.resources.change_password_same_as_current
import newverse.shared.generated.resources.change_password_title
import newverse.shared.generated.resources.error_password_length
import newverse.shared.generated.resources.error_password_no_letter
import newverse.shared.generated.resources.error_password_no_number
import newverse.shared.generated.resources.error_passwords_mismatch
import newverse.shared.generated.resources.register_password_hint
import newverse.shared.generated.resources.register_password_match_desc
import newverse.shared.generated.resources.toggle_hide
import newverse.shared.generated.resources.toggle_show
import org.jetbrains.compose.resources.stringResource

/**
 * Dialog for changing the password of a signed-in email/password account.
 *
 * The current password is asked for first because it is doing two jobs: it is
 * the credential the re-authentication needs (Firebase refuses a password
 * change on a session more than a few minutes old) and it is what makes the
 * change safe on an unlocked phone someone else is holding.
 *
 * Local validation lives here, as it does in `EmailLinkingDialog`, so the rules
 * stay next to the fields they describe and can use `stringResource`. Anything
 * only the server can know — a wrong current password, a rate limit — comes
 * back through [state]'s two error slots.
 *
 * Shown for both flavors; only offer it when the account actually has a
 * password provider.
 */
@Composable
fun ChangePasswordDialog(
    state: ChangePasswordState,
    onCurrentPasswordChange: (String) -> Unit,
    onNewPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var currentVisible by remember { mutableStateOf(false) }
    var newVisible by remember { mutableStateOf(false) }
    var confirmVisible by remember { mutableStateOf(false) }

    val errorPasswordLength = stringResource(Res.string.error_password_length)
    val errorPasswordNoNumber = stringResource(Res.string.error_password_no_number)
    val errorPasswordNoLetter = stringResource(Res.string.error_password_no_letter)
    val errorPasswordsMismatch = stringResource(Res.string.error_passwords_mismatch)
    val errorSameAsCurrent = stringResource(Res.string.change_password_same_as_current)

    // The two server-side errors arrive as resources, because the ViewModel that
    // set them had no resource environment to resolve them in.
    val currentPasswordError = state.currentPasswordError?.let { stringResource(it) }
    val serverNewPasswordError = state.newPasswordError?.let { stringResource(it) }

    // Local validation, cleared whenever the field it belongs to changes.
    var newPasswordError by remember(state.newPassword) { mutableStateOf<String?>(null) }
    var confirmPasswordError by remember(state.confirmPassword) { mutableStateOf<String?>(null) }

    // Same rules the registration and linking flows apply, so a password that
    // was accepted at sign-up is not rejected here.
    fun newPasswordIsValid(): Boolean =
        state.newPassword.length >= MIN_PASSWORD_LENGTH &&
            state.newPassword.any { it.isDigit() } &&
            state.newPassword.any { it.isLetter() } &&
            state.newPassword != state.currentPassword

    fun validateNewPassword(): Boolean {
        newPasswordError = when {
            state.newPassword.isBlank() -> null
            state.newPassword.length < MIN_PASSWORD_LENGTH -> errorPasswordLength
            !state.newPassword.any { it.isDigit() } -> errorPasswordNoNumber
            !state.newPassword.any { it.isLetter() } -> errorPasswordNoLetter
            state.newPassword == state.currentPassword -> errorSameAsCurrent
            else -> null
        }
        return newPasswordIsValid()
    }

    fun validateConfirmPassword(): Boolean {
        confirmPasswordError = when {
            state.confirmPassword.isBlank() -> null
            state.confirmPassword != state.newPassword -> errorPasswordsMismatch
            else -> null
        }
        return state.confirmPassword.isNotBlank() && state.confirmPassword == state.newPassword
    }

    val isFormValid = state.currentPassword.isNotBlank() &&
        newPasswordIsValid() &&
        state.confirmPassword == state.newPassword

    // Track if any field has focus (keyboard is likely showing)
    var anyFieldHasFocus by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = { if (!state.isSubmitting) onDismiss() },
        title = {
            Text(
                text = stringResource(Res.string.change_password_title),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() }
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 400.dp)
                    .verticalScroll(scrollState)
            ) {
                // Hide description when keyboard is showing to save space
                AnimatedVisibility(visible = !anyFieldHasFocus) {
                    Column {
                        Text(
                            text = stringResource(Res.string.change_password_message),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                // Current password - the re-authentication credential
                OutlinedTextField(
                    value = state.currentPassword,
                    onValueChange = onCurrentPasswordChange,
                    label = { Text(stringResource(Res.string.change_password_current_label)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) anyFieldHasFocus = true
                        },
                    visualTransformation = if (currentVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    enabled = !state.isSubmitting,
                    isError = currentPasswordError != null,
                    supportingText = currentPasswordError?.let {
                        {
                            Text(
                                text = it,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        TextButton(
                            onClick = { currentVisible = !currentVisible },
                            enabled = !state.isSubmitting
                        ) {
                            Text(
                                if (currentVisible) stringResource(Res.string.toggle_hide)
                                else stringResource(Res.string.toggle_show)
                            )
                        }
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // New password
                OutlinedTextField(
                    value = state.newPassword,
                    onValueChange = onNewPasswordChange,
                    label = { Text(stringResource(Res.string.change_password_new_label)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) anyFieldHasFocus = true
                        },
                    visualTransformation = if (newVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    enabled = !state.isSubmitting,
                    isError = newPasswordError != null || serverNewPasswordError != null,
                    supportingText = (newPasswordError ?: serverNewPasswordError)?.let {
                        {
                            Text(
                                text = it,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                            )
                        }
                    } ?: {
                        Text(
                            stringResource(Res.string.register_password_hint),
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        TextButton(
                            onClick = { newVisible = !newVisible },
                            enabled = !state.isSubmitting
                        ) {
                            Text(
                                if (newVisible) stringResource(Res.string.toggle_hide)
                                else stringResource(Res.string.toggle_show)
                            )
                        }
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Confirm new password
                OutlinedTextField(
                    value = state.confirmPassword,
                    onValueChange = onConfirmPasswordChange,
                    label = { Text(stringResource(Res.string.change_password_confirm_label)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) anyFieldHasFocus = true
                        },
                    visualTransformation = if (confirmVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    enabled = !state.isSubmitting,
                    isError = confirmPasswordError != null,
                    supportingText = confirmPasswordError?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        if (state.confirmPassword.isNotEmpty() && state.confirmPassword == state.newPassword) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = stringResource(Res.string.register_password_match_desc),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            TextButton(
                                onClick = { confirmVisible = !confirmVisible },
                                enabled = !state.isSubmitting
                            ) {
                                Text(
                                    if (confirmVisible) stringResource(Res.string.toggle_hide)
                                    else stringResource(Res.string.toggle_show)
                                )
                            }
                        }
                    }
                )
            }
        },
        confirmButton = {
            val submittingLabel = stringResource(Res.string.a11y_changing_password)
            Button(
                onClick = {
                    val newValid = validateNewPassword()
                    val confirmValid = validateConfirmPassword()

                    if (newValid && confirmValid) {
                        onConfirm()
                    }
                },
                enabled = !state.isSubmitting && isFormValid,
                // While submitting the label is a spinner; announce the progress instead.
                modifier = if (state.isSubmitting) {
                    Modifier.semantics {
                        contentDescription = submittingLabel
                        liveRegion = LiveRegionMode.Polite
                    }
                } else {
                    Modifier
                }
            ) {
                if (state.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(stringResource(Res.string.change_password_confirm))
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !state.isSubmitting
            ) {
                Text(stringResource(Res.string.button_cancel))
            }
        }
    )
}

/**
 * The floor Firebase itself enforces, and what `InMemoryAuthRepository` checks.
 */
private const val MIN_PASSWORD_LENGTH = 6
