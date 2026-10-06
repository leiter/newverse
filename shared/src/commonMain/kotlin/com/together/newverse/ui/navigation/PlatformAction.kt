package com.together.newverse.ui.navigation

/**
 * Platform-specific actions that need to be handled by the platform layer
 */
sealed interface PlatformAction {
    data object GoogleSignIn : PlatformAction
    data object TwitterSignIn : PlatformAction
    data object AppleSignIn : PlatformAction
    data object GoogleSignOut : PlatformAction
    data object ScanQrCode : PlatformAction
    data class ShareText(val text: String) : PlatformAction

    /**
     * Ask for permission to post notifications, needed from Android 13 on before the
     * order reminder can be shown. A no-op where the platform grants it at install time
     * or has no such permission.
     */
    data object RequestNotificationPermission : PlatformAction
}
