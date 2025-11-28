package com.together.newverse.data.repository

import com.together.newverse.domain.repository.StorageRepository

/**
 * Android-specific implementation of StorageRepository.
 * Uses GitLive for cross-platform Firebase support.
 */
class PlatformStorageRepository : StorageRepository {

    private val implementation: StorageRepository by lazy {
        println("🏭 PlatformStorageRepository: Using GitLive (cross-platform)")
        GitLiveStorageRepository()
    }

    override suspend fun uploadImage(
        imageData: ByteArray,
        filename: String,
        onProgress: ((Float) -> Unit)?
    ): Result<String> {
        return implementation.uploadImage(imageData, filename, onProgress)
    }

    override suspend fun deleteImage(imageUrl: String): Result<Unit> {
        return implementation.deleteImage(imageUrl)
    }

    override fun generateImagePath(): String {
        return implementation.generateImagePath()
    }
}
