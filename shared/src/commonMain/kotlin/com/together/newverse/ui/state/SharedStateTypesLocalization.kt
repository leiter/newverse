package com.together.newverse.ui.state

import androidx.compose.runtime.Composable
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.auth_provider_anonymous
import newverse.shared.generated.resources.auth_provider_apple
import newverse.shared.generated.resources.auth_provider_email
import newverse.shared.generated.resources.auth_provider_google
import newverse.shared.generated.resources.auth_provider_twitter
import newverse.shared.generated.resources.dialog_button_cancel
import newverse.shared.generated.resources.dialog_button_confirm
import newverse.shared.generated.resources.dialog_button_dismiss
import newverse.shared.generated.resources.dialog_button_ok
import newverse.shared.generated.resources.dialog_button_retry
import newverse.shared.generated.resources.dialog_error_title
import newverse.shared.generated.resources.init_checking_auth
import newverse.shared.generated.resources.init_complete
import newverse.shared.generated.resources.init_failed
import newverse.shared.generated.resources.init_loading_articles
import newverse.shared.generated.resources.init_loading_order
import newverse.shared.generated.resources.init_loading_profile
import newverse.shared.generated.resources.init_starting
import org.jetbrains.compose.resources.stringResource

/**
 * Localization extension for InitializationStep
 * Returns a localized message for the current initialization step
 */
@Composable
fun InitializationStep.localizedMessage(): String = stringResource(
    when (this) {
        is InitializationStep.NotStarted -> Res.string.init_starting
        is InitializationStep.CheckingAuth -> Res.string.init_checking_auth
        is InitializationStep.LoadingProfile -> Res.string.init_loading_profile
        is InitializationStep.LoadingOrder -> Res.string.init_loading_order
        is InitializationStep.LoadingArticles -> Res.string.init_loading_articles
        is InitializationStep.Complete -> Res.string.init_complete
        is InitializationStep.Failed -> Res.string.init_failed
    }
)

/**
 * Localization extension for InitializationStep.Failed
 * Returns a localized error message with the error details
 */
@Composable
fun InitializationStep.Failed.localizedErrorMessage(): String =
    stringResource(Res.string.init_failed, this.message)

/**
 * Localization extension for AuthProvider
 * Returns a localized display name for the authentication provider
 */
@Composable
fun AuthProvider.localizedDisplayName(): String = stringResource(
    when (this) {
        AuthProvider.ANONYMOUS -> Res.string.auth_provider_anonymous
        AuthProvider.GOOGLE -> Res.string.auth_provider_google
        AuthProvider.EMAIL -> Res.string.auth_provider_email
        AuthProvider.TWITTER -> Res.string.auth_provider_twitter
        AuthProvider.APPLE -> Res.string.auth_provider_apple
    }
)

/**
 * Localization extension for DialogState.Confirmation
 * Provides localized button labels with fallbacks to resource strings
 */
@Composable
fun DialogState.Confirmation.localizedConfirmLabel(): String =
    if (confirmLabel == "Confirm") stringResource(Res.string.dialog_button_confirm) else confirmLabel

@Composable
fun DialogState.Confirmation.localizedCancelLabel(): String =
    if (cancelLabel == "Cancel") stringResource(Res.string.dialog_button_cancel) else cancelLabel

/**
 * Localization extension for DialogState.Information
 * Provides localized button labels with fallbacks
 */
@Composable
fun DialogState.Information.localizedDismissLabel(): String =
    if (dismissLabel == "OK") stringResource(Res.string.dialog_button_ok) else dismissLabel

/**
 * Localization extension for DialogState.Error
 * Provides localized title and button labels
 */
@Composable
fun DialogState.Error.localizedTitle(): String =
    if (title == "Error") stringResource(Res.string.dialog_error_title) else title

@Composable
fun DialogState.Error.localizedRetryLabel(): String =
    if (retryLabel == "Retry") stringResource(Res.string.dialog_button_retry) else retryLabel

@Composable
fun DialogState.Error.localizedDismissLabel(): String =
    if (dismissLabel == "Dismiss") stringResource(Res.string.dialog_button_dismiss) else dismissLabel
