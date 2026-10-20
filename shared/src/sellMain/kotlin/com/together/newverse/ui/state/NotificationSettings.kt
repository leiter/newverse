package com.together.newverse.ui.state

/**
 * Notification settings for seller
 */
data class NotificationSettings(
    val newOrderNotifications: Boolean = true,
    val orderUpdateNotifications: Boolean = true,
    /**
     * The refill reminder before each market day — the one switch here that is
     * connected to something. It is stored in `StockReminderStorage` and arms or
     * cancels the real reminder; `StockViewModel.setReminderEnabled` is the writer,
     * and this field mirrors it for the UI.
     */
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
