package com.together.newverse.util

/**
 * Result of a document picking operation
 */
sealed class DocumentPickerResult {
    data class Success(val content: String, val filename: String) : DocumentPickerResult()
    data class Error(val message: String) : DocumentPickerResult()
    data object Cancelled : DocumentPickerResult()
}

/**
 * Platform-specific document picker for text files (BNN import files)
 * Expect/Actual pattern for Android and iOS implementations
 */
expect class DocumentPicker {
    /**
     * Pick a document/text file
     * @return DocumentPickerResult with file content or error
     */
    suspend fun pickDocument(): DocumentPickerResult
}
