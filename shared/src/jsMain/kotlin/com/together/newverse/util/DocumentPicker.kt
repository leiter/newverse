package com.together.newverse.util

actual class DocumentPicker {
    actual suspend fun pickDocument(): DocumentPickerResult =
        DocumentPickerResult.Error("Document picker not supported on web")
}
