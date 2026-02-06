package com.together.newverse.ui.screens.buy.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.button_cancel
import newverse.shared.generated.resources.link_account_email
import newverse.shared.generated.resources.link_account_google
import newverse.shared.generated.resources.link_account_message
import newverse.shared.generated.resources.link_account_title
import org.jetbrains.compose.resources.stringResource

/**
 * Dialog offering options to link a guest account with a permanent account.
 * Provides Google and Email linking options.
 */
@Composable
fun LinkAccountDialog(
    onDismiss: () -> Unit,
    onLinkWithGoogle: () -> Unit,
    onLinkWithEmail: () -> Unit,
    isLinking: Boolean = false
) {
    AlertDialog(
        onDismissRequest = { if (!isLinking) onDismiss() },
        title = {
            Text(
                text = stringResource(Res.string.link_account_title),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = stringResource(Res.string.link_account_message),
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Google link button
                Button(
                    onClick = onLinkWithGoogle,
                    enabled = !isLinking,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(Res.string.link_account_google))
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Email link button
                OutlinedButton(
                    onClick = onLinkWithEmail,
                    enabled = !isLinking,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(Res.string.link_account_email))
                }
            }
        },
        confirmButton = {},
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
