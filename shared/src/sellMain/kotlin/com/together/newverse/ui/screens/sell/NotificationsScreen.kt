package com.together.newverse.ui.screens.sell

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.together.newverse.ui.state.NotificationAction
import com.together.newverse.ui.state.NotificationSettings

/**
 * Notifications settings screen for sellers
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    notificationSettings: NotificationSettings,
    onAction: (NotificationAction) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Benachrichtigungseinstellungen",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Order Notifications Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Bestellungen",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                NotificationToggleItem(
                    title = "Neue Bestellungen",
                    description = "Benachrichtigung bei neuen Kundenbestellungen",
                    checked = notificationSettings.newOrderNotifications,
                    onCheckedChange = {
                        onAction(NotificationAction.ToggleNewOrders(it))
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                NotificationToggleItem(
                    title = "Bestellungs-Updates",
                    description = "Benachrichtigung bei Änderungen an Bestellungen",
                    checked = notificationSettings.orderUpdateNotifications,
                    onCheckedChange = {
                        onAction(NotificationAction.ToggleOrderUpdates(it))
                    }
                )
            }
        }

        // Inventory Notifications Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Lagerbestand",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                NotificationToggleItem(
                    title = "Niedriger Lagerbestand",
                    description = "Benachrichtigung wenn Produkte zur Neige gehen",
                    checked = notificationSettings.lowStockNotifications,
                    onCheckedChange = {
                        onAction(NotificationAction.ToggleLowStock(it))
                    }
                )
            }
        }

        // Marketing Notifications Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Marketing",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                NotificationToggleItem(
                    title = "Marketing-Nachrichten",
                    description = "Tipps und Neuigkeiten für Verkäufer",
                    checked = notificationSettings.marketingNotifications,
                    onCheckedChange = {
                        onAction(NotificationAction.ToggleMarketing(it))
                    }
                )
            }
        }

        // Delivery Methods Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Zustellungsmethoden",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )

                Spacer(modifier = Modifier.height(12.dp))

                NotificationToggleItem(
                    title = "E-Mail-Benachrichtigungen",
                    description = "Benachrichtigungen per E-Mail erhalten",
                    checked = notificationSettings.emailNotifications,
                    icon = Icons.Default.Email,
                    onCheckedChange = {
                        onAction(NotificationAction.ToggleEmail(it))
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                NotificationToggleItem(
                    title = "Push-Benachrichtigungen",
                    description = "Benachrichtigungen auf diesem Gerät",
                    checked = notificationSettings.pushNotifications,
                    icon = Icons.Default.Notifications,
                    onCheckedChange = {
                        onAction(NotificationAction.TogglePush(it))
                    }
                )
            }
        }
    }
}

@Composable
private fun NotificationToggleItem(
    title: String,
    description: String,
    checked: Boolean,
    icon: ImageVector = Icons.Default.Notifications,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.surface,
                checkedTrackColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}
