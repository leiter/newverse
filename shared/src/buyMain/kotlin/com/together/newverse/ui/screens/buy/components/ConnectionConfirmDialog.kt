package com.together.newverse.ui.screens.buy.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.together.newverse.ui.state.ConnectionConfirmation
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.invitation_accept
import newverse.shared.generated.resources.invitation_confirm_message
import newverse.shared.generated.resources.invitation_confirm_title
import newverse.shared.generated.resources.button_cancel
import org.jetbrains.compose.resources.stringResource

@Composable
fun ConnectionConfirmDialog(
    confirmation: ConnectionConfirmation,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(Res.string.invitation_confirm_title),
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Text(
                text = stringResource(
                    Res.string.invitation_confirm_message,
                    confirmation.sellerDisplayName
                )
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(Res.string.invitation_accept))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.button_cancel))
            }
        }
    )
}
