package com.together.newverse.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.together.newverse.ui.state.DialogState

/**
 * App dialog component for seller app
 */
@Composable
fun AppDialog(
    dialog: DialogState?,
    onDismiss: () -> Unit,
    onConfirm: (() -> Unit)? = null
) {
    when (dialog) {
        is DialogState.Confirmation -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = {
                    Text(text = dialog.title)
                },
                text = {
                    Text(text = dialog.message)
                },
                confirmButton = {
                    TextButton(onClick = { onConfirm?.invoke() ?: onDismiss() }) {
                        Text(dialog.confirmLabel)
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text(dialog.cancelLabel)
                    }
                }
            )
        }
        is DialogState.Information -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = {
                    Text(text = dialog.title)
                },
                text = {
                    Text(text = dialog.message)
                },
                confirmButton = {
                    TextButton(onClick = onDismiss) {
                        Text(dialog.dismissLabel)
                    }
                }
            )
        }
        is DialogState.Error -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = {
                    Text(text = dialog.title)
                },
                text = {
                    Text(text = dialog.message)
                },
                confirmButton = {
                    TextButton(onClick = { onConfirm?.invoke() ?: onDismiss() }) {
                        Text(dialog.retryLabel)
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text(dialog.dismissLabel)
                    }
                }
            )
        }
        null -> {
            // No dialog to show
        }
    }
}
