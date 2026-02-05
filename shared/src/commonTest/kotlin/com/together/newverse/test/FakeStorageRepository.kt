package com.together.newverse.test

import com.together.newverse.domain.repository.StorageRepository

/**
 * Fake implementation of StorageRepository for testing.
 * Allows controlling upload results and tracking operations.
 */
class FakeStorageRepository : StorageRepository {

    // Track operations for verification
    private val _uploadedImages = mutableListOf<UploadedImage>()
    val uploadedImages: List<UploadedImage> get() = _uploadedImages.toList()

    private val _deletedImages = mutableListOf<String>()
    val deletedImages: List<String> get() = _deletedImages.toList()

    // Configuration for test scenarios
    var shouldFailUpload = false
    var shouldFailDelete = false
    var failureMessage = "Test error"
    var uploadedImageUrl = "https://test-storage.example.com/images/test-image.jpg"

    // Progress simulation
    var simulateProgress = false

    data class UploadedImage(
        val imageData: ByteArray,
        val filename: String
    )

    /**
     * Reset repository state for fresh test
     */
    fun reset() {
        _uploadedImages.clear()
        _deletedImages.clear()
        shouldFailUpload = false
        shouldFailDelete = false
        failureMessage = "Test error"
        uploadedImageUrl = "https://test-storage.example.com/images/test-image.jpg"
        simulateProgress = false
    }

    override suspend fun uploadImage(
        imageData: ByteArray,
        filename: String,
        onProgress: ((Float) -> Unit)?
    ): Result<String> {
        if (shouldFailUpload) {
            return Result.failure(Exception(failureMessage))
        }

        // Simulate progress if enabled
        if (simulateProgress && onProgress != null) {
            onProgress(0.25f)
            onProgress(0.5f)
            onProgress(0.75f)
            onProgress(1.0f)
        }

        _uploadedImages.add(UploadedImage(imageData, filename))
        return Result.success(uploadedImageUrl)
    }

    override suspend fun deleteImage(imageUrl: String): Result<Unit> {
        if (shouldFailDelete) {
            return Result.failure(Exception(failureMessage))
        }

        _deletedImages.add(imageUrl)
        return Result.success(Unit)
    }

    override fun generateImagePath(): String {
        return "images/test_${System.currentTimeMillis()}.jpg"
    }
}
