package com.together.newverse.data.repository

import com.together.newverse.data.config.FeatureFlags
import com.together.newverse.data.config.AuthProvider
import com.together.newverse.domain.model.BuyerProfile
import com.together.newverse.domain.model.SellerProfile
import com.together.newverse.domain.repository.ProfileRepository
import com.together.newverse.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow

/**
 * iOS-specific implementation of ProfileRepository.
 *
 * Currently iOS uses GitLive exclusively for cross-platform Firebase support.
 */
class PlatformProfileRepository(
    private val authRepository: AuthRepository
) : ProfileRepository {

    private val actualRepository: ProfileRepository by lazy {
        when (FeatureFlags.authProvider) {
            AuthProvider.FIREBASE -> {
                // iOS doesn't have native Firebase wrapper yet, fall back to GitLive
                println("🏭 PlatformProfileRepository (iOS): Firebase requested but using GitLive")
                GitLiveProfileRepository(authRepository)
            }
            AuthProvider.GITLIVE -> {
                println("🏭 PlatformProfileRepository (iOS): Using GitLive (cross-platform)")
                GitLiveProfileRepository(authRepository)
            }
            AuthProvider.AUTO -> {
                // iOS defaults to GitLive
                println("🏭 PlatformProfileRepository (iOS): Using GitLive (iOS default)")
                GitLiveProfileRepository(authRepository)
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
