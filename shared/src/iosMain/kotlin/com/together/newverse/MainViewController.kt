package com.together.newverse

import androidx.compose.ui.window.ComposeUIViewController
import com.together.newverse.ui.navigation.MainAppScaffold
import com.together.newverse.ui.navigation.PlatformAction
import com.together.newverse.ui.state.DeepLinkRouter
import com.together.newverse.ui.theme.NewverseTheme
import platform.UIKit.UIViewController

/**
 * Called from Swift `.onOpenURL` to forward a deep link URL into the Kotlin layer.
 */
fun handleDeepLinkUrl(url: String) {
    println("iOS Deep Link received: $url")
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
                    println("iOS Platform Action: $action")
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
    onTwitterSignInRequested: () -> Unit = {}
): UIViewController {
    return ComposeUIViewController {
        NewverseTheme {
            MainAppScaffold(
                onPlatformAction = { action ->
                    println("iOS Platform Action: $action")
                    when (action) {
                        is PlatformAction.GoogleSignIn -> onGoogleSignInRequested()
                        is PlatformAction.AppleSignIn -> onAppleSignInRequested()
                        is PlatformAction.TwitterSignIn -> onTwitterSignInRequested()
                        else -> {
                            // Other actions like ShareText or ScanQrCode 
                            // will need additional callbacks in the future
                        }
                    }
                }
            )
        }
    }
}
