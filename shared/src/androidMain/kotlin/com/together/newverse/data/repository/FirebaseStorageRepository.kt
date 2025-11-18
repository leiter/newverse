package com.together.newverse.data.repository

import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.together.newverse.data.firebase.awaitResult
import com.together.newverse.domain.repository.StorageRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await

/**
 * Firebase implementation of StorageRepository
 * Handles image upload/download using Firebase Storage
 */
class FirebaseStorageRepository : StorageRepository {

    private val storage = FirebaseStorage.getInstance()
    private val storageRef = storage.reference

    companion object {
        private const val IMAGES_PATH = "images/"
    }

    /**
     * Upload an image to Firebase Storage
     * @param imageData The image data as ByteArray
     * @param filename Optional filename (will be auto-generated if empty)
     * @param onProgress Optional callback for upload progress
     * @return Result with download URL on success
     */
    override suspend fun uploadImage(
        imageData: ByteArray,
        filename: String,
        onProgress: ((Float) -> Unit)?
    ): Result<String> {
        return try {
            val path = if (filename.isEmpty()) {
                generateImagePath()
            } else {
                filename
            }

            val imageRef = storageRef.child(path)

            // Upload with progress tracking if callback provided
            if (onProgress != null) {
                val uploadFlow = callbackFlow {
                    val uploadTask = imageRef.putBytes(imageData)

                    uploadTask.addOnProgressListener { taskSnapshot ->
                        val progress = taskSnapshot.bytesTransferred.toFloat() / taskSnapshot.totalByteCount.toFloat()
                        trySend(progress)
                    }

                    uploadTask.addOnSuccessListener {
                        close()
                    }

                    uploadTask.addOnFailureListener { exception ->
                        close(exception)
                    }

                    awaitClose { }
                }

                // Collect progress
                uploadFlow.collect { progress ->
                    onProgress(progress)
                }
            } else {
                // Upload without progress tracking
                imageRef.putBytes(imageData).await()
            }

            // Get download URL
            val downloadUrl = imageRef.downloadUrl.await()
            Result.success(downloadUrl.toString())

        } catch (e: Exception) {
            println("❌ FirebaseStorageRepository.uploadImage: Error - ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Delete an image from Firebase Storage
     * @param imageUrl The full URL of the image to delete
     * @return Result indicating success or failure
     */
    override suspend fun deleteImage(imageUrl: String): Result<Unit> {
        return try {
            if (imageUrl.isEmpty()) {
                return Result.success(Unit)
            }

            // Extract the path from the URL
            val storageReference = storage.getReferenceFromUrl(imageUrl)
            storageReference.delete().await()

            println("✅ FirebaseStorageRepository.deleteImage: Successfully deleted $imageUrl")
            Result.success(Unit)

        } catch (e: Exception) {
            println("❌ FirebaseStorageRepository.deleteImage: Error - ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Generate a unique image path/filename
     * @return Generated path in format "images/{timestamp}_ttt.jpeg"
     */
    override fun generateImagePath(): String {
        return "$IMAGES_PATH${System.currentTimeMillis()}_ttt.jpeg"
    }
}
