package com.together.newverse.ui.screens.buy.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.button_cancel
import newverse.shared.generated.resources.logout_warning_confirm
import newverse.shared.generated.resources.logout_warning_item_basket
import newverse.shared.generated.resources.logout_warning_item_favorites
import newverse.shared.generated.resources.logout_warning_item_profile
import newverse.shared.generated.resources.logout_warning_link
import newverse.shared.generated.resources.logout_warning_message
import newverse.shared.generated.resources.logout_warning_orders_preserved
import newverse.shared.generated.resources.logout_warning_prompt
import newverse.shared.generated.resources.logout_warning_title
import org.jetbrains.compose.resources.stringResource

/**
 * Warning dialog shown when a guest user attempts to logout.
 * Informs them that their data will be deleted and offers
 * the option to link their account instead.
 */
@Composable
fun LogoutWarningDialog(
    onDismiss: () -> Unit,
    onLinkAccount: () -> Unit,
    onConfirmLogout: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
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
                text = stringResource(Res.string.logout_warning_title),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = stringResource(Res.string.logout_warning_message),
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Bullet list of data that will be deleted
                BulletItem(stringResource(Res.string.logout_warning_item_favorites))
                BulletItem(stringResource(Res.string.logout_warning_item_profile))
                BulletItem(stringResource(Res.string.logout_warning_item_basket))

                Spacer(modifier = Modifier.height(12.dp))

                // Orders preserved notice
                Text(
                    text = stringResource(Res.string.logout_warning_orders_preserved),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Prompt to link account
                Text(
                    text = stringResource(Res.string.logout_warning_prompt),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            Button(onClick = onLinkAccount) {
                Text(stringResource(Res.string.logout_warning_link))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(Res.string.button_cancel))
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = onConfirmLogout) {
                    Text(
                        text = stringResource(Res.string.logout_warning_confirm),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    )
}

@Composable
private fun BulletItem(text: String) {
    Row(
        modifier = Modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "•",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
