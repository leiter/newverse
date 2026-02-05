package com.together.newverse.test

import com.together.newverse.domain.model.BuyerProfile
import com.together.newverse.domain.model.CleanUpResult
import com.together.newverse.domain.model.DraftBasket
import com.together.newverse.domain.model.SellerProfile
import com.together.newverse.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Fake implementation of ProfileRepository for testing.
 * Allows controlling profile data and tracking operations.
 */
class FakeProfileRepository : ProfileRepository {

    private val _buyerProfile = MutableStateFlow<BuyerProfile?>(null)
    private val _sellerProfile = MutableStateFlow<SellerProfile?>(null)

    // Track operations for verification
    var saveSellerProfileCalled = false
        private set
    var lastSavedSellerProfile: SellerProfile? = null
        private set

    // Configuration for test scenarios
    var shouldFailGetSellerProfile = false
    var shouldFailSaveSellerProfile = false
    var shouldFailGetBuyerProfile = false
    var failureMessage = "Test error"

    /**
     * Set the seller profile to be returned
     */
    fun setSellerProfile(profile: SellerProfile) {
        _sellerProfile.value = profile
    }

    /**
     * Set the buyer profile to be returned
     */
    fun setBuyerProfile(profile: BuyerProfile?) {
        _buyerProfile.value = profile
    }

    /**
     * Reset repository state for fresh test
     */
    fun reset() {
        _buyerProfile.value = null
        _sellerProfile.value = null
        saveSellerProfileCalled = false
        lastSavedSellerProfile = null
        shouldFailGetSellerProfile = false
        shouldFailSaveSellerProfile = false
        shouldFailGetBuyerProfile = false
        failureMessage = "Test error"
    }

    override fun observeBuyerProfile(): Flow<BuyerProfile?> {
        return _buyerProfile.asStateFlow()
    }

    override suspend fun getBuyerProfile(): Result<BuyerProfile> {
        if (shouldFailGetBuyerProfile) {
            return Result.failure(Exception(failureMessage))
        }

        val profile = _buyerProfile.value
        return if (profile != null) {
            Result.success(profile)
        } else {
            Result.failure(Exception("Buyer profile not found"))
        }
    }

    override suspend fun saveBuyerProfile(profile: BuyerProfile): Result<BuyerProfile> {
        _buyerProfile.value = profile
        return Result.success(profile)
    }

    override suspend fun getSellerProfile(sellerId: String): Result<SellerProfile> {
        if (shouldFailGetSellerProfile) {
            return Result.failure(Exception(failureMessage))
        }

        val profile = _sellerProfile.value
        return if (profile != null) {
            Result.success(profile)
        } else {
            Result.failure(Exception("Seller profile not found"))
        }
    }

    override suspend fun saveSellerProfile(profile: SellerProfile): Result<Unit> {
        saveSellerProfileCalled = true
        lastSavedSellerProfile = profile

        if (shouldFailSaveSellerProfile) {
            return Result.failure(Exception(failureMessage))
        }

        _sellerProfile.value = profile
        return Result.success(Unit)
    }

    override suspend fun clearUserData(
        sellerId: String,
        buyerProfile: BuyerProfile
    ): Result<CleanUpResult> {
        _buyerProfile.value = null
        return Result.success(
            CleanUpResult(
                started = true,
                profileDeleted = true
            )
        )
    }

    override suspend fun deleteBuyerProfile(userId: String): Result<Unit> {
        _buyerProfile.value = null
        return Result.success(Unit)
    }

    override suspend fun saveDraftBasket(draftBasket: DraftBasket): Result<Unit> {
        val currentProfile = _buyerProfile.value
        if (currentProfile != null) {
            _buyerProfile.value = currentProfile.copy(draftBasket = draftBasket)
        }
        return Result.success(Unit)
    }

    override suspend fun clearDraftBasket(): Result<Unit> {
        val currentProfile = _buyerProfile.value
        if (currentProfile != null) {
            _buyerProfile.value = currentProfile.copy(draftBasket = null)
        }
        return Result.success(Unit)
    }
}
