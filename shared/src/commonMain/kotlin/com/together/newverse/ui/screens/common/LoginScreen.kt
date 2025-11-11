package com.together.newverse.ui.screens.common

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.together.newverse.ui.state.AuthScreenState
import com.together.newverse.ui.state.UnifiedAppAction
import com.together.newverse.ui.state.UnifiedNavigationAction
import com.together.newverse.ui.state.UnifiedUserAction
import com.together.newverse.ui.navigation.NavRoutes
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    authState: AuthScreenState = AuthScreenState(),
    onAction: (UnifiedAppAction) -> Unit = {}
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }

    val errorEmailRequired = stringResource(Res.string.error_email_required)
    val errorEmailInvalid = stringResource(Res.string.error_email_invalid)
    val errorPasswordRequired = stringResource(Res.string.error_password_required)
    val errorPasswordLength = stringResource(Res.string.error_password_length)

    // Validate email format
    fun validateEmail(): Boolean {
        emailError = when {
            email.isBlank() -> errorEmailRequired
            !email.contains("@") -> errorEmailInvalid
            else -> null
        }
        return emailError == null
    }

    // Validate password
    fun validatePassword(): Boolean {
        passwordError = when {
            password.isBlank() -> errorPasswordRequired
            password.length < 6 -> errorPasswordLength
            else -> null
        }
        return passwordError == null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Logo or App Name
        Card(
            modifier = Modifier.padding(bottom = 32.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text(
                text = stringResource(Res.string.app_leaf_icon),
                style = MaterialTheme.typography.displayLarge,
                modifier = Modifier.padding(16.dp),
                textAlign = TextAlign.Center
            )
        }

        Text(
            text = stringResource(Res.string.login_title),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary
        )

        Text(
            text = stringResource(Res.string.login_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 32.dp)
        )

        // Email Field
        OutlinedTextField(
            value = email,
            onValueChange = {
                email = it
                emailError = null
            },
            label = { Text(stringResource(Res.string.label_email)) },
            placeholder = { Text(stringResource(Res.string.login_email_placeholder)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !authState.isLoading,
            isError = emailError != null,
            supportingText = emailError?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Password Field
        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                passwordError = null
            },
            label = { Text(stringResource(Res.string.label_password)) },
            placeholder = { Text(stringResource(Res.string.login_password_placeholder)) },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            singleLine = true,
            enabled = !authState.isLoading,
            isError = passwordError != null,
            supportingText = passwordError?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                TextButton(
                    onClick = { passwordVisible = !passwordVisible },
                    enabled = !authState.isLoading
                ) {
                    Text(if (passwordVisible) stringResource(Res.string.toggle_hide) else stringResource(Res.string.toggle_show))
                }
            }
        )

        // Forgot Password Link
        TextButton(
            onClick = {
                // TODO: Navigate to password reset
            },
            modifier = Modifier
                .align(Alignment.End)
                .padding(top = 8.dp),
            enabled = !authState.isLoading
        ) {
            Text(stringResource(Res.string.login_forgot_password))
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Sign In Button
        Button(
            onClick = {
                val emailValid = validateEmail()
                val passwordValid = validatePassword()

                if (emailValid && passwordValid) {
                    onAction(UnifiedUserAction.Login(email, password))
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = !authState.isLoading
        ) {
            if (authState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text(stringResource(Res.string.button_sign_in), style = MaterialTheme.typography.labelLarge)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Divider with "OR"
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HorizontalDivider(modifier = Modifier.weight(1f))
            Text(
                text = stringResource(Res.string.divider_or),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider(modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Google Sign-In Button
        OutlinedButton(
            onClick = {
                onAction(UnifiedUserAction.LoginWithGoogle)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = !authState.isLoading,
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            // Google logo placeholder
            Text(
                text = stringResource(Res.string.login_google_icon),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 12.dp)
            )
            Text(stringResource(Res.string.login_sign_in_google), style = MaterialTheme.typography.labelLarge)
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Twitter Sign-In Button
        OutlinedButton(
            onClick = {
                onAction(UnifiedUserAction.LoginWithTwitter)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = !authState.isLoading,
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            // Twitter logo placeholder
            Text(
                text = stringResource(Res.string.login_twitter_icon),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 12.dp)
            )
            Text(stringResource(Res.string.login_sign_in_twitter), style = MaterialTheme.typography.labelLarge)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Continue as Guest Button
        OutlinedButton(
            onClick = {
                // Sign in anonymously or just navigate to home
                onAction(UnifiedNavigationAction.NavigateTo(NavRoutes.Home))
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = !authState.isLoading
        ) {
            Text(stringResource(Res.string.button_continue_guest), style = MaterialTheme.typography.labelLarge)
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Sign Up Link
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(Res.string.login_no_account),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(
                onClick = {
                    // Navigate to register screen
                    onAction(UnifiedNavigationAction.NavigateTo(NavRoutes.Register))
                },
                enabled = !authState.isLoading
            ) {
                Text(stringResource(Res.string.login_sign_up_link))
            }
        }

        // Error Message Display
        authState.error?.let { errorMessage ->
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // Success Message (if needed)
        if (authState.isSuccess) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(Res.string.login_success),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}


