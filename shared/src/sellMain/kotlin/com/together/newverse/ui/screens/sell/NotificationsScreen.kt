package com.together.newverse.ui.screens.sell

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.together.newverse.ui.adaptive.constrainedContentWidth
import com.together.newverse.ui.state.NotificationAction
import com.together.newverse.ui.state.NotificationSettings
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/**
 * Notifications settings screen for sellers
 * @param platformContent Optional platform-specific content (e.g., Android service controls)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    notificationSettings: NotificationSettings,
    onAction: (NotificationAction) -> Unit,
    platformContent: @Composable (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .constrainedContentWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Platform-specific content (e.g., Android notification service controls)
        platformContent?.invoke()

        Spacer(modifier = Modifier.height(8.dp))

        // Order Notifications Card
        NotificationCard(title = stringResource(Res.string.seller_notif_section_orders)) {
            NotificationToggleItem(
                title = stringResource(Res.string.seller_notif_new_orders),
                description = stringResource(Res.string.seller_notif_new_orders_desc),
                checked = notificationSettings.newOrderNotifications,
                onCheckedChange = { onAction(NotificationAction.ToggleNewOrders(it)) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            NotificationToggleItem(
                title = stringResource(Res.string.seller_notif_order_updates),
                description = stringResource(Res.string.seller_notif_order_updates_desc),
                checked = notificationSettings.orderUpdateNotifications,
                onCheckedChange = { onAction(NotificationAction.ToggleOrderUpdates(it)) }
            )
        }

        // Inventory Notifications Card
        NotificationCard(title = stringResource(Res.string.seller_notif_section_stock)) {
            NotificationToggleItem(
                title = stringResource(Res.string.seller_notif_low_stock),
                description = stringResource(Res.string.seller_notif_low_stock_desc),
                checked = notificationSettings.lowStockNotifications,
                onCheckedChange = { onAction(NotificationAction.ToggleLowStock(it)) }
            )
        }

        // Marketing Notifications Card
        NotificationCard(title = stringResource(Res.string.seller_notif_section_marketing)) {
            NotificationToggleItem(
                title = stringResource(Res.string.seller_notif_marketing),
                description = stringResource(Res.string.seller_notif_marketing_desc),
                checked = notificationSettings.marketingNotifications,
                onCheckedChange = { onAction(NotificationAction.ToggleMarketing(it)) }
            )
        }

        // Delivery Methods Card
        NotificationCard(
            title = stringResource(Res.string.seller_notif_section_delivery),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            NotificationToggleItem(
                title = stringResource(Res.string.seller_notif_email),
                description = stringResource(Res.string.seller_notif_email_desc),
                checked = notificationSettings.emailNotifications,
                icon = Icons.Default.Email,
                onCheckedChange = { onAction(NotificationAction.ToggleEmail(it)) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            NotificationToggleItem(
                title = stringResource(Res.string.seller_notif_push),
                description = stringResource(Res.string.seller_notif_push_desc),
                checked = notificationSettings.pushNotifications,
                icon = Icons.Default.Notifications,
                onCheckedChange = { onAction(NotificationAction.TogglePush(it)) }
            )
        }
    }
}

@Composable
private fun NotificationCard(
    title: String,
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceVariant,
    titleColor: androidx.compose.ui.graphics.Color = Color.Unspecified,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = titleColor,
                // Let TalkBack's next-heading gesture land on each section
                modifier = Modifier.semantics { heading() }
            )

            Spacer(modifier = Modifier.height(12.dp))

            content()
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
    // The whole row is one switch so a screen reader hears the label, the
    // description and the on/off state in a single stop.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange
            ),
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
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.surface,
                checkedTrackColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.clearAndSetSemantics { }
        )
    }
}
