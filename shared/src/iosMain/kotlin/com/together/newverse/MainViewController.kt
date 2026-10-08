package com.together.newverse

import androidx.compose.ui.window.ComposeUIViewController
import com.together.newverse.ui.navigation.MainAppScaffold
import com.together.newverse.ui.navigation.PlatformAction
import com.together.newverse.ui.state.DeepLinkRouter
import com.together.newverse.ui.theme.NewverseTheme
import platform.UIKit.UIViewController
import com.together.newverse.util.Log

private const val TAG = "MainViewController"

/**
 * Called from Swift `.onOpenURL` to forward a deep link URL into the Kotlin layer.
 */
fun handleDeepLinkUrl(url: String) {
    Log.d(TAG) { "iOS Deep Link received: $url" }
    DeepLinkRouter.route(url)
}

/**
 * Creates the main UIViewController for iOS app.
 */
fun MainViewController(): UIViewController {
    return ComposeUIViewController {
        NewverseTheme {
            MainAppScaffold(
                onPlatformAction = { action ->
                    Log.d(TAG) { "iOS Platform Action: $action" }
                }
            )
        }
    }
}

/**
 * Creates the main UIViewController with callbacks for platform-specific actions.
 */
fun MainViewControllerWithCallback(
    onGoogleSignInRequested: () -> Unit,
    onAppleSignInRequested: () -> Unit,
    onTwitterSignInRequested: () -> Unit = {},
    onScanQrCodeRequested: () -> Unit = {},
    onShareRequested: (String) -> Unit = {}
): UIViewController {
    return ComposeUIViewController {
        NewverseTheme {
            MainAppScaffold(
                onPlatformAction = { action ->
                    Log.d(TAG) { "iOS Platform Action: $action" }
                    when (action) {
                        is PlatformAction.GoogleSignIn -> onGoogleSignInRequested()
                        is PlatformAction.AppleSignIn -> onAppleSignInRequested()
                        is PlatformAction.TwitterSignIn -> onTwitterSignInRequested()
                        is PlatformAction.ScanQrCode -> onScanQrCodeRequested()
                        is PlatformAction.ShareText -> onShareRequested(action.text)
                        is PlatformAction.GoogleSignOut -> { /* handled by Kotlin layer */ }
                        // Not wired to UNUserNotificationCenter yet; iOS shows no permission prompt.
                        is PlatformAction.RequestNotificationPermission -> { }
                    }
                }
            )
        }
    }
}
