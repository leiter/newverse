package com.together.newverse.domain.repository

/**
 * Repository for managing file storage (images, documents, etc.)
 */
interface StorageRepository {
    /**
     * Upload an image file to storage
     * @param imageData The image data as ByteArray
     * @param filename Optional filename (will be auto-generated if empty)
     * @param onProgress Optional callback for upload progress (0.0 to 1.0)
     * @return Result with the download URL on success
     */
    suspend fun uploadImage(
        imageData: ByteArray,
        filename: String = "",
        onProgress: ((Float) -> Unit)? = null
    ): Result<String>

    /**
     * Delete an image from storage
     * @param imageUrl The full URL of the image to delete
     * @return Result indicating success or failure
     */
    suspend fun deleteImage(imageUrl: String): Result<Unit>

    /**
     * Get a reference path for a new image
     * @return Generated filename with path
     */
    fun generateImagePath(): String
}
