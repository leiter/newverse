package com.together.newverse.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Displays a QR code for the given content string.
 * Platform-specific implementations use ZXing (Android) or CoreImage (iOS).
 */
@Composable
expect fun QrCodeImage(
    content: String,
    modifier: Modifier = Modifier,
    sizeDp: Int = 200
)
