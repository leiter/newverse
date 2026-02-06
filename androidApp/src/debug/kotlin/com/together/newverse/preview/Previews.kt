package com.together.newverse.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.together.newverse.ui.screens.common.AboutScreenModern
import com.together.newverse.ui.screens.common.LoginScreen
import com.together.newverse.ui.theme.NewverseTheme

// =====================================================================
// Common Screens (shared between buy and sell flavors)
// =====================================================================

@Preview(name = "About Screen", showBackground = true)
@Composable
fun AboutScreenPreview() {
    NewverseTheme {
        AboutScreenModern()
    }
}

@Preview(name = "Login Screen", showBackground = true)
@Composable
fun LoginScreenPreview() {
    NewverseTheme {
        LoginScreen()
    }
}
