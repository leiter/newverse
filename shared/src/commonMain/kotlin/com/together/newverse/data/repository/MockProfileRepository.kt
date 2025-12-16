package com.together.newverse.data.repository

import com.together.newverse.domain.model.BuyerProfile
import com.together.newverse.domain.model.SellerProfile
import com.together.newverse.domain.repository.ProfileRepository
import com.together.newverse.preview.PreviewData
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Mock implementation of ProfileRepository for development and testing
 */
class MockProfileRepository : ProfileRepository {

    private val _buyerProfile = MutableStateFlow<BuyerProfile?>(null)
    private val _sellerProfile = MutableStateFlow(PreviewData.sampleSellerProfile)

    override fun observeBuyerProfile(): Flow<BuyerProfile?> {
        return _buyerProfile.asStateFlow()
    }

    override suspend fun getBuyerProfile(): Result<BuyerProfile> {
        return try {
            delay(300)
            val profile = _buyerProfile.value
            if (profile != null) {
                Result.success(profile)
            } else {
                // Return a default profile if none exists
                val defaultProfile = PreviewData.sampleBuyerProfiles[0]
                _buyerProfile.value = defaultProfile
                Result.success(defaultProfile)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun saveBuyerProfile(profile: BuyerProfile): Result<BuyerProfile> {
        return try {
            delay(300)
            _buyerProfile.value = profile
            Result.success(profile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getSellerProfile(sellerId: String): Result<SellerProfile> {
        return try {
            delay(300)
            // For mock, always return the sample seller profile
            Result.success(PreviewData.sampleSellerProfile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun saveSellerProfile(profile: SellerProfile): Result<Unit> {
        return try {
            delay(300)
            _sellerProfile.value = profile
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun clearUserData(sellerId: String, buyerProfile: BuyerProfile): Result<Boolean> {
        return try {
            delay(300)
            // Clear buyer profile
            _buyerProfile.value = null
            // Reset seller profile to default
            _sellerProfile.value = PreviewData.sampleSellerProfile
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteBuyerProfile(userId: String): Result<Unit> {
        return try {
            delay(300)
            _buyerProfile.value = null
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}