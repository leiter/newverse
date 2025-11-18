package com.together.newverse.data.repository

import com.together.newverse.data.config.FeatureFlags
import com.together.newverse.domain.repository.StorageRepository

/**
 * Platform-specific Storage Repository that switches between
 * Firebase and GitLive implementations based on feature flags
 */
class PlatformStorageRepository : StorageRepository {

    private val implementation: StorageRepository by lazy {
        if (FeatureFlags.useGitLiveStorage) {
            println("🔐 PlatformStorageRepository: Using GitLive Storage implementation")
            GitLiveStorageRepository()
        } else {
            println("🔥 PlatformStorageRepository: Using Firebase Storage implementation")
            FirebaseStorageRepository()
        }
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
