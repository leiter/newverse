package com.together.newverse.data.repository

import com.together.newverse.data.config.FeatureFlags
import com.together.newverse.data.config.AuthProvider
import com.together.newverse.domain.model.BuyerProfile
import com.together.newverse.domain.model.SellerProfile
import com.together.newverse.domain.repository.ProfileRepository
import com.together.newverse.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow

/**
 * Android-specific implementation of ProfileRepository that properly handles
 * switching between Firebase and GitLive implementations.
 */
class PlatformProfileRepository(
    private val authRepository: AuthRepository
) : ProfileRepository {

    private val actualRepository: ProfileRepository by lazy {
        when (FeatureFlags.authProvider) {
            AuthProvider.FIREBASE -> {
                println("🏭 PlatformProfileRepository: Using Firebase (Android native)")
                FirebaseProfileRepository()
            }
            AuthProvider.GITLIVE -> {
                println("🏭 PlatformProfileRepository: Using GitLive (cross-platform)")
                GitLiveProfileRepository(authRepository)
            }
            AuthProvider.AUTO -> {
                // For Android, match auth provider selection
                if (FeatureFlags.gitLiveRolloutPercentage >= 100) {
                    println("🏭 PlatformProfileRepository: Using GitLive (100% rollout)")
                    GitLiveProfileRepository(authRepository)
                } else {
                    println("🏭 PlatformProfileRepository: Using Firebase (Android default)")
                    FirebaseProfileRepository()
                }
            }
        }
    }

    override fun observeBuyerProfile(): Flow<BuyerProfile?> {
        return actualRepository.observeBuyerProfile()
    }

    override suspend fun getBuyerProfile(): Result<BuyerProfile> {
        return actualRepository.getBuyerProfile()
    }

    override suspend fun saveBuyerProfile(profile: BuyerProfile): Result<BuyerProfile> {
        return actualRepository.saveBuyerProfile(profile)
    }

    override suspend fun getSellerProfile(sellerId: String): Result<SellerProfile> {
        return actualRepository.getSellerProfile(sellerId)
    }

    override suspend fun saveSellerProfile(profile: SellerProfile): Result<Unit> {
        return actualRepository.saveSellerProfile(profile)
    }

    override suspend fun clearUserData(sellerId: String, buyerProfile: BuyerProfile): Result<Boolean> {
        return actualRepository.clearUserData(sellerId, buyerProfile)
    }
}