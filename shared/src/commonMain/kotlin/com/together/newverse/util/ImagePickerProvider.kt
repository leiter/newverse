package com.together.newverse.util

import androidx.compose.runtime.compositionLocalOf

/**
 * CompositionLocal for providing ImagePicker down the composition tree
 * This allows platform-specific ImagePicker implementation to be provided at the root
 * and accessed anywhere in the UI hierarchy
 */
val LocalImagePicker = compositionLocalOf<ImagePicker?> {
    null // Default to null, will be provided by platform-specific code
}
