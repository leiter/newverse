package com.together.newverse.util

import androidx.compose.runtime.compositionLocalOf

/**
 * CompositionLocal for providing DocumentPicker down the composition tree
 * This allows platform-specific DocumentPicker implementation to be provided at the root
 * and accessed anywhere in the UI hierarchy
 */
val LocalDocumentPicker = compositionLocalOf<DocumentPicker?> {
    null // Default to null, will be provided by platform-specific code
}
