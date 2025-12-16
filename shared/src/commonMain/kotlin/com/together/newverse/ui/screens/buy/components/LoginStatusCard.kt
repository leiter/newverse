package com.together.newverse.ui.screens.buy.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.together.newverse.ui.state.AuthProvider
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.account_status_authenticated
import newverse.shared.generated.resources.account_status_guest
import newverse.shared.generated.resources.account_status_guest_warning
import newverse.shared.generated.resources.account_status_title
import newverse.shared.generated.resources.action_delete_account
import newverse.shared.generated.resources.action_link_email
import newverse.shared.generated.resources.action_link_google
import newverse.shared.generated.resources.action_logout
import org.jetbrains.compose.resources.stringResource

/**
 * Card displaying the user's login status and available auth actions.
 *
 * For guest users:
 * - Shows warning that data is not permanently saved
 * - Offers options to link with Google or Email
 * - Shows logout button (which triggers data loss warning)
 *
 * For authenticated users:
 * - Shows email and auth provider
 * - Shows logout button
 * - Shows delete account option
 */
@Composable
fun LoginStatusCard(
    isAnonymous: Boolean,
    userEmail: String?,
    authProvider: AuthProvider,
    isLinkingAccount: Boolean = false,
    onLinkWithGoogle: () -> Unit,
    onLinkWithEmail: () -> Unit,
    onLogout: () -> Unit,
    onDeleteAccount: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header with lock icon
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(Res.string.account_status_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isAnonymous) {
                // Guest user status
                GuestStatus(
                    isLinkingAccount = isLinkingAccount,
                    onLinkWithGoogle = onLinkWithGoogle,
                    onLinkWithEmail = onLinkWithEmail,
                    onLogout = onLogout
                )
            } else {
                // Authenticated user status
                AuthenticatedStatus(
                    userEmail = userEmail,
                    authProvider = authProvider,
                    onLogout = onLogout,
                    onDeleteAccount = onDeleteAccount
                )
            }
        }
    }
}

@Composable
private fun GuestStatus(
    isLinkingAccount: Boolean,
    onLinkWithGoogle: () -> Unit,
    onLinkWithEmail: () -> Unit,
    onLogout: () -> Unit
) {
    // Warning status
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(Res.string.account_status_guest),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error
        )
    }

    Spacer(modifier = Modifier.height(4.dp))

    Text(
        text = stringResource(Res.string.account_status_guest_warning),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 28.dp)
    )

    Spacer(modifier = Modifier.height(16.dp))

    // Link account buttons
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = onLinkWithGoogle,
            enabled = !isLinkingAccount,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(Res.string.action_link_google))
        }

        OutlinedButton(
            onClick = onLinkWithEmail,
            enabled = !isLinkingAccount,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(Res.string.action_link_email))
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    // Logout button
    TextButton(
        onClick = onLogout,
        enabled = !isLinkingAccount,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = stringResource(Res.string.action_logout),
            color = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun AuthenticatedStatus(
    userEmail: String?,
    authProvider: AuthProvider,
    onLogout: () -> Unit,
    onDeleteAccount: () -> Unit
) {
    // Verified status
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = userEmail ?: "Angemeldet",
            style = MaterialTheme.typography.bodyLarge
        )
    }

    Spacer(modifier = Modifier.height(4.dp))

    // Format: "Angemeldet mit Google" (Compose Resources doesn't support %s format)
    val authenticatedText = stringResource(Res.string.account_status_authenticated)
        .replace("%s", authProvider.displayName)
    Text(
        text = authenticatedText,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 28.dp)
    )

    Spacer(modifier = Modifier.height(16.dp))

    // Action buttons
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        TextButton(onClick = onLogout) {
            Text(stringResource(Res.string.action_logout))
        }

        TextButton(onClick = onDeleteAccount) {
            Text(
                text = stringResource(Res.string.action_delete_account),
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}
