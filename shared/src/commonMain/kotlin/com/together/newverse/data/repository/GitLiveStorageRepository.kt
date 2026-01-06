package com.together.newverse.data.repository

import com.together.newverse.domain.repository.StorageRepository
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.storage.storage
import dev.gitlive.firebase.storage.StorageReference
import kotlinx.coroutines.flow.first

/**
 * GitLive implementation of StorageRepository for cross-platform storage
 * Handles image upload/download using GitLive Firebase Storage SDK
 */
class GitLiveStorageRepository : StorageRepository {

    private val storage = Firebase.storage
    private val storageRef = storage.reference

    companion object {
        private const val IMAGES_PATH = "images/"
    }

    /**
     * Upload an image to Firebase Storage using GitLive SDK
     * @param imageData The image data as ByteArray
     * @param filename Optional filename (will be auto-generated if empty)
     * @param onProgress Optional callback for upload progress (GitLive SDK has limited progress support)
     * @return Result with download URL on success
     */
    override suspend fun uploadImage(
        imageData: ByteArray,
        filename: String,
        onProgress: ((Float) -> Unit)?
    ): Result<String> {
        return try {
            println("🔐 GitLiveStorageRepository.uploadImage: START")

            val path = if (filename.isEmpty()) {
                generateImagePath()
            } else {
                filename
            }

            val imageRef = storageRef.child(path)

            // Note: GitLive SDK has limited progress tracking support
            // For now, we upload without progress monitoring
            if (onProgress != null) {
                onProgress(0.5f) // Report halfway through upload
            }

            // Upload the image data
            // GitLive storage uses putFile which requires a file path
            // For now, use a workaround or fallback to error
            // TODO: Implement proper file upload for GitLive
            throw UnsupportedOperationException("GitLive storage upload from ByteArray not yet implemented. Please use Firebase storage.")

            if (onProgress != null) {
                onProgress(1.0f) // Report complete
            }

            // Get download URL
            val downloadUrl = imageRef.getDownloadUrl()

            println("✅ GitLiveStorageRepository.uploadImage: Success - $downloadUrl")
            Result.success(downloadUrl)

        } catch (e: Exception) {
            println("❌ GitLiveStorageRepository.uploadImage: Error - ${e.message}")
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

            println("🔐 GitLiveStorageRepository.deleteImage: START - $imageUrl")

            // Extract the path from the URL
            // GitLive doesn't have getReferenceFromUrl, so we need to parse manually
            val storageReference = parseStorageReference(imageUrl)
            if (storageReference != null) {
                storageReference.delete()
                println("✅ GitLiveStorageRepository.deleteImage: Success")
                Result.success(Unit)
            } else {
                println("⚠️ GitLiveStorageRepository.deleteImage: Could not parse URL, skipping delete")
                Result.success(Unit)
            }

        } catch (e: Exception) {
            println("❌ GitLiveStorageRepository.deleteImage: Error - ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Generate a unique image path/filename
     * @return Generated path in format "images/{timestamp}_ttt.jpeg"
     */
    override fun generateImagePath(): String {
        val timestamp = kotlin.time.Clock.System.now().toEpochMilliseconds()
        return "$IMAGES_PATH${timestamp}_ttt.jpeg"
    }

    /**
     * Parse a Firebase Storage URL to get the StorageReference
     * This is a workaround since GitLive doesn't have getReferenceFromUrl
     */
    private fun parseStorageReference(imageUrl: String): StorageReference? {
        return try {
            // Expected format: https://firebasestorage.googleapis.com/v0/b/{bucket}/o/{path}?{params}
            // We need to extract the path

            // Simple approach: check if URL contains our images path
            if (imageUrl.contains(IMAGES_PATH)) {
                val pathStart = imageUrl.indexOf(IMAGES_PATH)
                val pathEnd = imageUrl.indexOf("?", pathStart).takeIf { it > 0 } ?: imageUrl.length
                val path = imageUrl.substring(pathStart, pathEnd)
                    .replace("%2F", "/")  // URL decode forward slashes

                storageRef.child(path)
            } else {
                null
            }
        } catch (e: Exception) {
            println("⚠️ GitLiveStorageRepository.parseStorageReference: Error parsing URL - ${e.message}")
            null
        }
    }
}
