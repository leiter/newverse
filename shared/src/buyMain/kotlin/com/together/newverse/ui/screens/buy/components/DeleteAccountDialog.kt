package com.together.newverse.ui.screens.buy.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.a11y_deleting_account
import newverse.shared.generated.resources.button_cancel
import newverse.shared.generated.resources.delete_account_apple_notice
import newverse.shared.generated.resources.delete_account_confirm
import newverse.shared.generated.resources.delete_account_message
import newverse.shared.generated.resources.delete_account_title
import org.jetbrains.compose.resources.stringResource

/**
 * Confirmation dialog shown when a user attempts to delete their account.
 * Warns about data deletion and order cancellation.
 *
 * @param requiresAppleConfirmation when true, deletion will present Apple's
 *   sign-in sheet - the re-authorisation needed to revoke the Sign in with
 *   Apple token. Announcing it here keeps the sheet from arriving unexplained
 *   after the user has already confirmed.
 */
@Composable
fun DeleteAccountDialog(
    isLoading: Boolean = false,
    requiresAppleConfirmation: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = stringResource(Res.string.delete_account_title),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() }
            )
        },
        text = {
            Column {
                Text(
                    text = stringResource(Res.string.delete_account_message),
                    style = MaterialTheme.typography.bodyMedium
                )

                if (requiresAppleConfirmation) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = stringResource(Res.string.delete_account_apple_notice),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                if (isLoading) {
                    Spacer(modifier = Modifier.height(16.dp))
                    val deletingLabel = stringResource(Res.string.a11y_deleting_account)
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(24.dp)
                            .semantics {
                                liveRegion = LiveRegionMode.Polite
                                contentDescription = deletingLabel
                            },
                        strokeWidth = 2.dp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !isLoading
            ) {
                Text(
                    text = stringResource(Res.string.delete_account_confirm),
                    color = if (isLoading)
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    else
                        MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isLoading
            ) {
                Text(stringResource(Res.string.button_cancel))
            }
        }
    )
}
