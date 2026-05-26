package com.together.newverse.util

actual class ImagePicker {
    actual suspend fun pickImage(): ImagePickerResult =
        ImagePickerResult.Error("Image picker not supported on web")

    actual suspend fun takePhoto(): ImagePickerResult =
        ImagePickerResult.Error("Camera not supported on web")
}
