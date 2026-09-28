package com.together.newverse.data.repository

import com.google.firebase.storage.FirebaseStorage
import com.together.newverse.domain.repository.StorageRepository
import kotlinx.coroutines.tasks.await
import com.together.newverse.util.Log

/**
 * Android-specific implementation of StorageRepository.
 * Uses native Firebase Storage SDK for reliable image uploads.
 */
class PlatformStorageRepository : StorageRepository {

    private val storage = FirebaseStorage.getInstance()
    private val storageRef = storage.reference

    companion object {
        private const val TAG = "PlatformStorage"
        private const val IMAGES_PATH = "images/"
    }

    override suspend fun uploadImage(
        imageData: ByteArray,
        filename: String,
        onProgress: ((Float) -> Unit)?
    ): Result<String> {
        return try {
            Log.d(TAG) { "uploadImage: START (${imageData.size} bytes)" }

            val path = filename.ifEmpty {
                generateImagePath()
            }

            val imageRef = storageRef.child(path)

            // Upload using putBytes (native Android Firebase SDK)
            val uploadTask = imageRef.putBytes(imageData)

            // Track progress if callback provided
            if (onProgress != null) {
                uploadTask.addOnProgressListener { taskSnapshot ->
                    val progress = taskSnapshot.bytesTransferred.toFloat() / taskSnapshot.totalByteCount.toFloat()
                    onProgress(progress)
                }
            }

            // Wait for upload to complete
            uploadTask.await()

            // Get download URL
            val downloadUrl = imageRef.downloadUrl.await().toString()

            Log.d(TAG) { "uploadImage: Success - $downloadUrl" }
            Result.success(downloadUrl)

        } catch (e: Exception) {
            Log.e(TAG) { "uploadImage: Error - ${e.message}" }
            e.printStackTrace()
            Result.failure(e)
        }
    }

    override suspend fun deleteImage(imageUrl: String): Result<Unit> {
        return try {
            if (imageUrl.isEmpty()) {
                return Result.success(Unit)
            }

            Log.d(TAG) { "deleteImage: START - $imageUrl" }

            // Get reference from URL
            val imageRef = storage.getReferenceFromUrl(imageUrl)
            imageRef.delete().await()

            Log.d(TAG) { "deleteImage: Success" }
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG) { "deleteImage: Error - ${e.message}" }
            e.printStackTrace()
            Result.failure(e)
        }
    }

    override fun generateImagePath(): String {
        val timestamp = System.currentTimeMillis()
        return "$IMAGES_PATH${timestamp}_ttt.jpeg"
    }
}
