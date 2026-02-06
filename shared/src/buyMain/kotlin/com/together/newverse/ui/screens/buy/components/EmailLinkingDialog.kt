package com.together.newverse.ui.screens.buy.components

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.button_cancel
import newverse.shared.generated.resources.error_confirm_password
import newverse.shared.generated.resources.error_email_invalid
import newverse.shared.generated.resources.error_password_length
import newverse.shared.generated.resources.error_password_no_letter
import newverse.shared.generated.resources.error_password_no_number
import newverse.shared.generated.resources.error_passwords_mismatch
import newverse.shared.generated.resources.label_email
import newverse.shared.generated.resources.label_password
import newverse.shared.generated.resources.link_email_dialog_confirm
import newverse.shared.generated.resources.link_email_dialog_message
import newverse.shared.generated.resources.link_email_dialog_title
import newverse.shared.generated.resources.register_confirm_password
import newverse.shared.generated.resources.register_password_hint
import newverse.shared.generated.resources.register_password_match_desc
import newverse.shared.generated.resources.toggle_hide
import newverse.shared.generated.resources.toggle_show
import org.jetbrains.compose.resources.stringResource

/**
 * Dialog for linking a guest account with email and password credentials.
 * Includes validation for email format and password requirements.
 */
@Composable
fun EmailLinkingDialog(
    email: String,
    password: String,
    confirmPassword: String,
    error: String?,
    isLinking: Boolean,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    // Validation error messages
    val errorEmailInvalid = stringResource(Res.string.error_email_invalid)
    val errorPasswordLength = stringResource(Res.string.error_password_length)
    val errorPasswordNoNumber = stringResource(Res.string.error_password_no_number)
    val errorPasswordNoLetter = stringResource(Res.string.error_password_no_letter)
    val errorConfirmPassword = stringResource(Res.string.error_confirm_password)
    val errorPasswordsMismatch = stringResource(Res.string.error_passwords_mismatch)

    // Local validation state
    var emailError by remember(email) { mutableStateOf<String?>(null) }
    var passwordError by remember(password) { mutableStateOf<String?>(null) }
    var confirmPasswordError by remember(confirmPassword) { mutableStateOf<String?>(null) }

    // Validation functions
    fun validateEmail(): Boolean {
        emailError = when {
            email.isBlank() -> null // Don't show error for empty field until submit
            !email.contains("@") || !email.contains(".") -> errorEmailInvalid
            else -> null
        }
        return email.isNotBlank() && email.contains("@") && email.contains(".")
    }

    fun validatePassword(): Boolean {
        passwordError = when {
            password.isBlank() -> null // Don't show error for empty field until submit
            password.length < 6 -> errorPasswordLength
            !password.any { it.isDigit() } -> errorPasswordNoNumber
            !password.any { it.isLetter() } -> errorPasswordNoLetter
            else -> null
        }
        return password.length >= 6 && password.any { it.isDigit() } && password.any { it.isLetter() }
    }

    fun validateConfirmPassword(): Boolean {
        confirmPasswordError = when {
            confirmPassword.isBlank() -> null // Don't show error for empty field until submit
            confirmPassword != password -> errorPasswordsMismatch
            else -> null
        }
        return confirmPassword.isNotBlank() && confirmPassword == password
    }

    // Check if form is valid for enabling submit button
    val isFormValid = email.isNotBlank() &&
            email.contains("@") && email.contains(".") &&
            password.length >= 6 &&
            password.any { it.isDigit() } &&
            password.any { it.isLetter() } &&
            confirmPassword == password

    // Track if any field has focus (keyboard is likely showing)
    var anyFieldHasFocus by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = { if (!isLinking) onDismiss() },
        title = {
            Text(
                text = stringResource(Res.string.link_email_dialog_title),
                fontWeight = FontWeight.Bold
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
                            text = stringResource(Res.string.link_email_dialog_message),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                // Email field
                OutlinedTextField(
                    value = email,
                    onValueChange = {
                        onEmailChange(it)
                        emailError = null
                    },
                    label = { Text(stringResource(Res.string.label_email)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) anyFieldHasFocus = true
                        },
                    singleLine = true,
                    enabled = !isLinking,
                    isError = emailError != null,
                    supportingText = emailError?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Password field
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        onPasswordChange(it)
                        passwordError = null
                        // Also clear confirm password error if passwords now match
                        if (confirmPassword == it) {
                            confirmPasswordError = null
                        }
                    },
                    label = { Text(stringResource(Res.string.label_password)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) anyFieldHasFocus = true
                        },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    enabled = !isLinking,
                    isError = passwordError != null,
                    supportingText = passwordError?.let { { Text(it) } } ?: {
                        Text(
                            stringResource(Res.string.register_password_hint),
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        TextButton(
                            onClick = { passwordVisible = !passwordVisible },
                            enabled = !isLinking
                        ) {
                            Text(
                                if (passwordVisible) stringResource(Res.string.toggle_hide)
                                else stringResource(Res.string.toggle_show)
                            )
                        }
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Confirm password field
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = {
                        onConfirmPasswordChange(it)
                        confirmPasswordError = null
                    },
                    label = { Text(stringResource(Res.string.register_confirm_password)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) anyFieldHasFocus = true
                        },
                    visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    enabled = !isLinking,
                    isError = confirmPasswordError != null,
                    supportingText = confirmPasswordError?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        if (confirmPassword.isNotEmpty() && confirmPassword == password) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = stringResource(Res.string.register_password_match_desc),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            TextButton(
                                onClick = { confirmPasswordVisible = !confirmPasswordVisible },
                                enabled = !isLinking
                            ) {
                                Text(
                                    if (confirmPasswordVisible) stringResource(Res.string.toggle_hide)
                                    else stringResource(Res.string.toggle_show)
                                )
                            }
                        }
                    }
                )

                // Server error display
                if (error != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    // Validate all fields before submitting
                    val emailValid = validateEmail()
                    val passwordValid = validatePassword()
                    val confirmValid = validateConfirmPassword()

                    if (emailValid && passwordValid && confirmValid) {
                        onConfirm()
                    }
                },
                enabled = !isLinking && isFormValid
            ) {
                if (isLinking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(stringResource(Res.string.link_email_dialog_confirm))
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isLinking
            ) {
                Text(stringResource(Res.string.button_cancel))
            }
        }
    )
}
