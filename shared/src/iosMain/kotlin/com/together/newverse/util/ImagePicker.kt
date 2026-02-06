package com.together.newverse.util

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.NSUUID
import platform.UIKit.UIApplication
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerEditedImage
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.darwin.NSObject
import kotlin.coroutines.resume

/**
 * iOS implementation of ImagePicker
 * Uses UIImagePickerController for photo selection and camera capture
 */
actual class ImagePicker {

    companion object {
        private const val MAX_IMAGE_SIZE = 1920.0
        private const val JPEG_QUALITY = 0.8
    }

    /**
     * Pick an image from the photo library
     */
    @OptIn(ExperimentalForeignApi::class)
    actual suspend fun pickImage(): ImagePickerResult {
        return pickImageWithSource(UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypePhotoLibrary)
    }

    /**
     * Take a photo with the camera
     */
    @OptIn(ExperimentalForeignApi::class)
    actual suspend fun takePhoto(): ImagePickerResult {
        // Check if camera is available
        if (!UIImagePickerController.isSourceTypeAvailable(
                UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
            )
        ) {
            return ImagePickerResult.Error("Camera is not available on this device")
        }
        return pickImageWithSource(UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera)
    }

    @OptIn(ExperimentalForeignApi::class)
    private suspend fun pickImageWithSource(sourceType: UIImagePickerControllerSourceType): ImagePickerResult {
        return suspendCancellableCoroutine { continuation ->
            val delegate = ImagePickerDelegate { result ->
                continuation.resume(result)
            }

            val picker = UIImagePickerController()
            picker.sourceType = sourceType
            picker.delegate = delegate
            picker.allowsEditing = false

            // Present the picker
            val rootViewController = UIApplication.sharedApplication.keyWindow?.rootViewController
            if (rootViewController != null) {
                rootViewController.presentViewController(
                    picker,
                    animated = true,
                    completion = null
                )
            } else {
                continuation.resume(ImagePickerResult.Error("No root view controller available"))
            }

            continuation.invokeOnCancellation {
                picker.dismissViewControllerAnimated(true, completion = null)
            }
        }
    }
}

/**
 * Delegate for handling image picker callbacks
 */
@OptIn(ExperimentalForeignApi::class)
private class ImagePickerDelegate(
    private val onResult: (ImagePickerResult) -> Unit
) : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {

    override fun imagePickerController(
        picker: UIImagePickerController,
        didFinishPickingMediaWithInfo: Map<Any?, *>
    ) {
        picker.dismissViewControllerAnimated(true) {
            // Get the image (edited if available, otherwise original)
            val image = (didFinishPickingMediaWithInfo[UIImagePickerControllerEditedImage]
                ?: didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage]) as? UIImage

            if (image == null) {
                onResult(ImagePickerResult.Error("Failed to get image from picker"))
                return@dismissViewControllerAnimated
            }

            try {
                // Resize image if needed
                val resizedImage = resizeImageIfNeeded(image)

                // Convert to JPEG data
                val jpegData = UIImageJPEGRepresentation(resizedImage, 0.8)
                if (jpegData == null) {
                    onResult(ImagePickerResult.Error("Failed to convert image to JPEG"))
                    return@dismissViewControllerAnimated
                }

                // Convert NSData to ByteArray
                val byteArray = jpegData.toByteArray()
                val filename = "image_${NSUUID().UUIDString}.jpg"

                println("Image picked: $filename (${byteArray.size} bytes)")
                onResult(ImagePickerResult.Success(byteArray, filename))

            } catch (e: Exception) {
                println("Error processing image: ${e.message}")
                onResult(ImagePickerResult.Error("Failed to process image: ${e.message}"))
            }
        }
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true) {
            println("Image picker cancelled")
            onResult(ImagePickerResult.Cancelled)
        }
    }

    /**
     * Resize image to max 1920x1920 while maintaining aspect ratio
     */
    private fun resizeImageIfNeeded(image: UIImage): UIImage {
        val maxSize = 1920.0

        // Access CGSize using useContents for CValue
        val (width, height) = image.size.useContents { Pair(width, height) }

        // Check if resizing is needed
        if (width <= maxSize && height <= maxSize) {
            return image
        }

        // Calculate new size maintaining aspect ratio
        val ratio = minOf(maxSize / width, maxSize / height)
        val newWidth = width * ratio
        val newHeight = height * ratio

        // Create new context and draw resized image
        UIGraphicsBeginImageContextWithOptions(CGSizeMake(newWidth, newHeight), false, 1.0)
        image.drawInRect(CGRectMake(0.0, 0.0, newWidth, newHeight))
        val resizedImage = UIGraphicsGetImageFromCurrentImageContext()
        UIGraphicsEndImageContext()

        return resizedImage ?: image
    }
}

/**
 * Extension to convert NSData to ByteArray
 */
@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val length = this.length.toInt()
    val byteArray = ByteArray(length)

    if (length > 0) {
        byteArray.usePinned { pinned ->
            platform.posix.memcpy(
                pinned.addressOf(0),
                this.bytes,
                this.length
            )
        }
    }

    return byteArray
}
