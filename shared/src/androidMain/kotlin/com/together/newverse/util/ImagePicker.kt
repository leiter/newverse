package com.together.newverse.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.coroutines.resume
import androidx.core.graphics.scale

/**
 * Android implementation of ImagePicker
 * Uses Activity Result API for image selection and camera capture
 */
actual class ImagePicker(private val activity: ComponentActivity) {

    private var pickImageLauncher: ActivityResultLauncher<String>? = null
    private var takePhotoLauncher: ActivityResultLauncher<Uri>? = null
    private var cameraPermissionLauncher: ActivityResultLauncher<String>? = null
    private var photoUri: Uri? = null

    init {
        setupLaunchers()
    }

    /**
     * Setup Activity Result launchers
     * Must be called before activity is started
     */
    private fun setupLaunchers() {
        // Launcher for picking from gallery
        pickImageLauncher = activity.registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            pendingImageContinuation?.resume(uri)
            pendingImageContinuation = null
        }

        // Launcher for taking photo with camera
        takePhotoLauncher = activity.registerForActivityResult(
            ActivityResultContracts.TakePicture()
        ) { success: Boolean ->
            pendingPhotoContinuation?.resume(if (success) photoUri else null)
            pendingPhotoContinuation = null
        }

        // Launcher for requesting camera permission
        cameraPermissionLauncher = activity.registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted: Boolean ->
            pendingPermissionContinuation?.resume(granted)
            pendingPermissionContinuation = null
        }
    }

    companion object {
        private var pendingImageContinuation: kotlin.coroutines.Continuation<Uri?>? = null
        private var pendingPhotoContinuation: kotlin.coroutines.Continuation<Uri?>? = null
        private var pendingPermissionContinuation: kotlin.coroutines.Continuation<Boolean>? = null

        // JPEG compression quality
        private const val JPEG_QUALITY = 85

        // Max image dimensions to reduce file size
        private const val MAX_IMAGE_WIDTH = 1920
        private const val MAX_IMAGE_HEIGHT = 1920
    }

    /**
     * Pick an image from the gallery
     */
    actual suspend fun pickImage(): ImagePickerResult {
        return try {
            val uri = suspendCancellableCoroutine<Uri?> { continuation ->
                pendingImageContinuation = continuation
                pickImageLauncher?.launch("image/*")
            }

            if (uri != null) {
                processImageUri(activity, uri)
            } else {
                ImagePickerResult.Cancelled
            }
        } catch (e: Exception) {
            ImagePickerResult.Error("Failed to open image picker: ${e.message}")
        }
    }

    /**
     * Take a photo with the camera
     */
    actual suspend fun takePhoto(): ImagePickerResult {
        return try {
            // Check and request camera permission
            val hasPermission = if (ContextCompat.checkSelfPermission(
                    activity, Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                true
            } else {
                suspendCancellableCoroutine { continuation ->
                    pendingPermissionContinuation = continuation
                    cameraPermissionLauncher?.launch(Manifest.permission.CAMERA)
                }
            }

            if (!hasPermission) {
                return ImagePickerResult.Error("Kameraberechtigung wird benötigt, um Fotos aufzunehmen")
            }

            // Create temporary file for photo
            val photoFile = createImageFile(activity)
            photoUri = FileProvider.getUriForFile(
                activity,
                "${activity.packageName}.fileprovider",
                photoFile
            )

            val uri = suspendCancellableCoroutine<Uri?> { continuation ->
                pendingPhotoContinuation = continuation
                photoUri?.let { takePhotoLauncher?.launch(it) }
            }

            if (uri != null) {
                processImageUri(activity, uri)
            } else {
                ImagePickerResult.Cancelled
            }
        } catch (e: Exception) {
            ImagePickerResult.Error("Failed to take photo: ${e.message}")
        }
    }

    /**
     * Process image URI and convert to ByteArray
     */
    private fun processImageUri(context: Context, uri: Uri): ImagePickerResult {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return ImagePickerResult.Error("Failed to open image stream")

            // Decode bitmap
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            if (originalBitmap == null) {
                return ImagePickerResult.Error("Failed to decode image")
            }

            // Correct EXIF rotation
            val rotatedBitmap = correctExifRotation(context, uri, originalBitmap)

            // Resize if necessary
            val resizedBitmap = resizeBitmap(rotatedBitmap, MAX_IMAGE_WIDTH, MAX_IMAGE_HEIGHT)

            // Convert to JPEG ByteArray
            val outputStream = ByteArrayOutputStream()
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
            val imageData = outputStream.toByteArray()
            outputStream.close()

            // Clean up
            if (resizedBitmap != rotatedBitmap) {
                resizedBitmap.recycle()
            }
            if (rotatedBitmap != originalBitmap) {
                rotatedBitmap.recycle()
            }
            originalBitmap.recycle()

            // Generate filename
            val filename = "image_${System.currentTimeMillis()}.jpg"

            ImagePickerResult.Success(imageData, filename)

        } catch (e: Exception) {
            ImagePickerResult.Error("Failed to process image: ${e.message}")
        }
    }

    /**
     * Resize bitmap to fit within max dimensions while maintaining aspect ratio
     */
    private fun resizeBitmap(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        if (width <= maxWidth && height <= maxHeight) {
            return bitmap
        }

        val ratio = minOf(
            maxWidth.toFloat() / width,
            maxHeight.toFloat() / height
        )

        val newWidth = (width * ratio).toInt()
        val newHeight = (height * ratio).toInt()

        return bitmap.scale(newWidth, newHeight)
    }

    /**
     * Read EXIF orientation from the image URI and rotate the bitmap accordingly
     */
    private fun correctExifRotation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        val exifStream = context.contentResolver.openInputStream(uri) ?: return bitmap
        val exif = ExifInterface(exifStream)
        exifStream.close()

        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.preScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.preScale(-1f, 1f)
            }
            else -> return bitmap
        }

        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        return rotated
    }

    /**
     * Create a temporary file for camera photos
     */
    private fun createImageFile(context: Context): File {
        val timestamp = System.currentTimeMillis()
        val imageFileName = "JPEG_${timestamp}_"
        val storageDir = context.cacheDir
        return File.createTempFile(imageFileName, ".jpg", storageDir)
    }
}
