package com.together.newverse

import androidx.compose.ui.window.ComposeUIViewController
import com.together.newverse.ui.navigation.AppScaffold
import com.together.newverse.ui.theme.NewverseTheme
import platform.UIKit.UIViewController

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
