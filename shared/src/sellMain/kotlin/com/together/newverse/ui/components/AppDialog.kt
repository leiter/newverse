package com.together.newverse.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.together.newverse.ui.state.DialogState
import com.together.newverse.ui.state.localizedCancelLabel
import com.together.newverse.ui.state.localizedConfirmLabel
import com.together.newverse.ui.state.localizedDismissLabel
import com.together.newverse.ui.state.localizedErrorMessage
import com.together.newverse.ui.state.localizedRetryLabel
import com.together.newverse.ui.state.localizedTitle

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
                    Text(text = dialog.title, modifier = Modifier.semantics { heading() })
                },
                text = {
                    Text(text = dialog.message)
                },
                confirmButton = {
                    TextButton(onClick = { onConfirm?.invoke() ?: onDismiss() }) {
                        Text(dialog.localizedConfirmLabel())
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text(dialog.localizedCancelLabel())
                    }
                }
            )
        }
        is DialogState.Information -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = {
                    Text(text = dialog.title, modifier = Modifier.semantics { heading() })
                },
                text = {
                    Text(text = dialog.message)
                },
                confirmButton = {
                    TextButton(onClick = onDismiss) {
                        Text(dialog.localizedDismissLabel())
                    }
                }
            )
        }
        is DialogState.Error -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = {
                    Text(text = dialog.localizedTitle(), modifier = Modifier.semantics { heading() })
                },
                text = {
                    Text(text = dialog.message)
                },
                confirmButton = {
                    TextButton(onClick = { onConfirm?.invoke() ?: onDismiss() }) {
                        Text(dialog.localizedRetryLabel())
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text(dialog.localizedDismissLabel())
                    }
                }
            )
        }
        is DialogState.DraftBasketWarning -> {
            // Not used in seller app
        }
        null -> {
            // No dialog to show
        }
    }
}
