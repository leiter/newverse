package com.together.newverse.data.repository

import com.together.newverse.domain.model.BuyerProfile
import com.together.newverse.domain.model.SellerProfile
import com.together.newverse.domain.repository.ProfileRepository
import com.together.newverse.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow

/**
 * Android-specific implementation of ProfileRepository.
 * Uses GitLive for cross-platform Firebase support.
 */
class PlatformProfileRepository(
    private val authRepository: AuthRepository
) : ProfileRepository {

    private val actualRepository: ProfileRepository by lazy {
        println("🏭 PlatformProfileRepository: Using GitLive (cross-platform)")
        GitLiveProfileRepository(authRepository)
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

    override suspend fun deleteBuyerProfile(userId: String): Result<Unit> {
        return actualRepository.deleteBuyerProfile(userId)
    }
}