package com.together.newverse

import androidx.compose.ui.window.ComposeUIViewController
import com.together.newverse.ui.navigation.AppScaffold
import com.together.newverse.ui.navigation.PlatformAction
import com.together.newverse.ui.theme.NewverseTheme
import platform.UIKit.UIViewController

/**
 * Creates the main UIViewController for iOS app
 * This is called from SwiftUI to display the Compose UI
 */
fun MainViewController(): UIViewController {
    return ComposeUIViewController {
        NewverseTheme {
            AppScaffold(
                onPlatformAction = { action ->
                    // Handle platform-specific actions on iOS
                    when (action) {
                        is PlatformAction.GoogleSignIn -> {
                            // TODO: Implement iOS Google Sign-In
                            println("🔐 iOS: Google Sign-In requested")
                        }
                        is PlatformAction.TwitterSignIn -> {
                            // TODO: Implement iOS Twitter Sign-In
                            println("🔐 iOS: Twitter Sign-In requested")
                        }
                    }
                }
            )
        }
    }
}
