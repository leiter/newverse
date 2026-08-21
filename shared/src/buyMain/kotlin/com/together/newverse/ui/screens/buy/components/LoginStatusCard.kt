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
import com.together.newverse.ui.state.localizedDisplayName
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
    authProviders: List<AuthProvider> = emptyList(),
    isLinkingAccount: Boolean = false,
    // Unused while account linking is hidden; kept so restoring it is a UI-only change.
    @Suppress("UNUSED_PARAMETER") onLinkWithGoogle: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onLinkWithEmail: () -> Unit,
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
                // For a guest, signing out *is* a full wipe - the anonymous
                // account and everything on it goes - so the button says so.
                GuestStatus(
                    isLinkingAccount = isLinkingAccount,
                    onDeleteGuestAccount = onLogout
                )
            } else {
                // Authenticated user status
                AuthenticatedStatus(
                    userEmail = userEmail,
                    authProviders = authProviders.ifEmpty { listOf(authProvider) },
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
    onDeleteGuestAccount: () -> Unit
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

    // Account linking is deliberately absent for now. The link dialogs and the
    // actions behind them are still wired up in CustomerProfileScreenModern, so
    // restoring the buttons here is all it takes to bring the option back.
    OutlinedButton(
        onClick = onDeleteGuestAccount,
        enabled = !isLinkingAccount,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = stringResource(Res.string.action_delete_account),
            color = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun AuthenticatedStatus(
    userEmail: String?,
    authProviders: List<AuthProvider>,
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

    // Format: "Angemeldet mit Google, Apple" (Compose Resources doesn't support %s
    // format). An account can be linked to several providers, so all are listed.
    // map is inline, so the @Composable call is legal inside it; joinToString's
    // transform is not, which is why the two steps are separate.
    val providerNames = authProviders.map { it.localizedDisplayName() }.joinToString(", ")
    val authenticatedText = stringResource(Res.string.account_status_authenticated)
        .replace("%s", providerNames)
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
