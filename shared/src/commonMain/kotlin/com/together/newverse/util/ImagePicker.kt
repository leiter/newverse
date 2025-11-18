package com.together.newverse.util

/**
 * Result of an image picking operation
 */
sealed class ImagePickerResult {
    data class Success(val imageData: ByteArray, val filename: String) : ImagePickerResult() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as Success

            if (!imageData.contentEquals(other.imageData)) return false
            if (filename != other.filename) return false

            return true
        }

        override fun hashCode(): Int {
            var result = imageData.contentHashCode()
            result = 31 * result + filename.hashCode()
            return result
        }
    }
    data class Error(val message: String) : ImagePickerResult()
    object Cancelled : ImagePickerResult()
}

/**
 * Platform-specific image picker
 * Expect/Actual pattern for Android and iOS implementations
 */
expect class ImagePicker {
    /**
     * Pick an image from the gallery
     * @return ImagePickerResult with image data or error
     */
    suspend fun pickImage(): ImagePickerResult

    /**
     * Take a photo with the camera
     * @return ImagePickerResult with image data or error
     */
    suspend fun takePhoto(): ImagePickerResult
}
