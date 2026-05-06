package com.together.newverse

import androidx.compose.ui.window.ComposeUIViewController
import com.together.newverse.ui.navigation.AppScaffold
import com.together.newverse.ui.navigation.PlatformAction
import com.together.newverse.ui.state.DeepLinkRouter
import com.together.newverse.ui.theme.NewverseTheme
import platform.UIKit.UIViewController

/**
 * Called from Swift `.onOpenURL` to forward a deep link URL into the Kotlin layer.
 * The URL is picked up by [AppScaffold] via [DeepLinkRouter].
 */
fun handleDeepLinkUrl(url: String) {
    println("iOS Deep Link received: $url")
    DeepLinkRouter.route(url)
}

/**
 * Creates the main UIViewController for iOS app
 * This is called from SwiftUI to display the Compose UI
 */
fun MainViewController(): UIViewController {
    return ComposeUIViewController {
        NewverseTheme {
            // Use the same AppScaffold as Android for full navigation support
            AppScaffold(
                onPlatformAction = { action ->
                    // Handle platform-specific actions (Google Sign-In, etc.)
                    // TODO: Implement iOS-specific platform actions
                    println("iOS Platform Action: $action")
                }
            )
        }
    }
}

/**
 * Creates the main UIViewController with callbacks for platform-specific actions.
 * This version allows Swift code to handle native sign-in flows.
 *
 * @param onGoogleSignInRequested Called when Google Sign-In is requested
 * @param onAppleSignInRequested Called when Apple Sign-In is requested
 * @param onTwitterSignInRequested Called when Twitter Sign-In is requested
 */
fun MainViewControllerWithCallback(
    onGoogleSignInRequested: () -> Unit,
    onAppleSignInRequested: () -> Unit,
    onTwitterSignInRequested: () -> Unit = {}
): UIViewController {
    return ComposeUIViewController {
        NewverseTheme {
            AppScaffold(
                onPlatformAction = { action ->
                    println("iOS Platform Action: $action")
                    when (action) {
                        is PlatformAction.GoogleSignIn -> {
                            println("iOS: Invoking Google Sign-In callback")
                            onGoogleSignInRequested()
                        }
                        is PlatformAction.AppleSignIn -> {
                            println("iOS: Invoking Apple Sign-In callback")
                            onAppleSignInRequested()
                        }
                        is PlatformAction.TwitterSignIn -> {
                            println("iOS: Invoking Twitter Sign-In callback")
                            onTwitterSignInRequested()
                        }
                        is PlatformAction.GoogleSignOut -> {
                            println("iOS: Google Sign-Out requested")
                            // Sign-out is handled by Firebase auth state
                        }
                        is PlatformAction.ScanQrCode -> {
                            println("iOS: QR Code scanning not yet implemented")
                        }
                        is PlatformAction.ShareText -> {
                            println("iOS: Share text not yet implemented: ${action.text}")
                        }
                    }
                }
            )
        }
    }
}
