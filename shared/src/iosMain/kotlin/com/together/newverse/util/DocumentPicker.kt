package com.together.newverse.util

/**
 * iOS implementation of DocumentPicker
 * TODO: Implement using UIDocumentPickerViewController
 */
actual class DocumentPicker {
    /**
     * Pick a document file
     * TODO: Implement iOS document picker
     */
    actual suspend fun pickDocument(): DocumentPickerResult {
        return DocumentPickerResult.Error("iOS document picker not yet implemented")
    }
}
