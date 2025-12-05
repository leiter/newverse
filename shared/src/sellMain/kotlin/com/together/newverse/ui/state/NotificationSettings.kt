package com.together.newverse.ui.state

/**
 * Notification settings for seller
 */
data class NotificationSettings(
    val newOrderNotifications: Boolean = true,
    val orderUpdateNotifications: Boolean = true,
    val lowStockNotifications: Boolean = true,
    val marketingNotifications: Boolean = false,
    val emailNotifications: Boolean = true,
    val pushNotifications: Boolean = true
)

/**
 * Notification actions for seller app
 */
sealed interface NotificationAction {
    data class UpdateNotificationSettings(val settings: NotificationSettings) : NotificationAction
    data object LoadNotifications : NotificationAction
    data class MarkNotificationAsRead(val notificationId: String) : NotificationAction
    data object ClearAllNotifications : NotificationAction
    data class ToggleNewOrders(val enabled: Boolean) : NotificationAction
    data class ToggleOrderUpdates(val enabled: Boolean) : NotificationAction
    data class ToggleLowStock(val enabled: Boolean) : NotificationAction
    data class ToggleMarketing(val enabled: Boolean) : NotificationAction
    data class ToggleEmail(val enabled: Boolean) : NotificationAction
    data class TogglePush(val enabled: Boolean) : NotificationAction
}
