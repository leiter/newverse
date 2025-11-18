package com.together.newverse.util

/**
 * iOS implementation of ImagePicker
 * TODO: Implement using UIImagePickerController
 */
actual class ImagePicker {
    /**
     * Pick an image from the gallery
     * TODO: Implement iOS image picker
     */
    actual suspend fun pickImage(): ImagePickerResult {
        return ImagePickerResult.Error("iOS image picker not yet implemented")
    }

    /**
     * Take a photo with the camera
     * TODO: Implement iOS camera capture
     */
    actual suspend fun takePhoto(): ImagePickerResult {
        return ImagePickerResult.Error("iOS camera not yet implemented")
    }
}
