package com.together.newverse.test

import com.together.newverse.domain.model.AccessRequest
import com.together.newverse.domain.model.AccessStatus
import com.together.newverse.domain.model.BuyerProfile
import com.together.newverse.domain.model.CleanUpResult
import com.together.newverse.domain.model.DraftBasket
import com.together.newverse.domain.model.SellerProfile
import com.together.newverse.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf

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
    var shouldFailSaveBuyerProfile = false
    var failureMessage = "Test error"

    // Track buyer profile operations
    var saveBuyerProfileCalled = false
        private set
    var lastSavedBuyerProfile: BuyerProfile? = null
        private set

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
        saveBuyerProfileCalled = false
        lastSavedBuyerProfile = null
        shouldFailGetSellerProfile = false
        shouldFailSaveSellerProfile = false
        shouldFailGetBuyerProfile = false
        shouldFailSaveBuyerProfile = false
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
        saveBuyerProfileCalled = true
        lastSavedBuyerProfile = profile

        if (shouldFailSaveBuyerProfile) {
            return Result.failure(Exception(failureMessage))
        }

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

    private val knownClients = mutableMapOf<String, MutableSet<String>>()
    private val blockedClients = mutableMapOf<String, MutableSet<String>>()

    override suspend fun addKnownClient(sellerId: String, buyerId: String): Result<Unit> {
        knownClients.getOrPut(sellerId) { mutableSetOf() }.add(buyerId)
        return Result.success(Unit)
    }

    override suspend fun blockClient(sellerId: String, buyerId: String): Result<Unit> {
        knownClients[sellerId]?.remove(buyerId)
        blockedClients.getOrPut(sellerId) { mutableSetOf() }.add(buyerId)
        return Result.success(Unit)
    }

    override suspend fun unblockClient(sellerId: String, buyerId: String): Result<Unit> {
        blockedClients[sellerId]?.remove(buyerId)
        knownClients.getOrPut(sellerId) { mutableSetOf() }.add(buyerId)
        return Result.success(Unit)
    }

    override suspend fun isClientBlocked(sellerId: String, buyerId: String): Boolean {
        return blockedClients[sellerId]?.contains(buyerId) == true
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

    // Access request state for tests
    private val _accessRequests = MutableStateFlow<List<AccessRequest>>(emptyList())
    private val _accessStatus = mutableMapOf<String, AccessStatus>()

    fun setAccessStatus(buyerUUID: String, sellerId: String, status: AccessStatus) {
        _accessStatus["$buyerUUID/$sellerId"] = status
    }

    override suspend fun saveBuyerUUID(uuid: String): Result<Unit> {
        return Result.success(Unit)
    }

    override suspend fun submitAccessRequest(sellerId: String, buyerUUID: String, displayName: String): Result<Unit> {
        _accessStatus["$buyerUUID/$sellerId"] = AccessStatus.PENDING
        return Result.success(Unit)
    }

    override suspend fun getAccessStatus(buyerUUID: String, sellerId: String): AccessStatus {
        return _accessStatus["$buyerUUID/$sellerId"] ?: AccessStatus.NONE
    }

    override fun observeAccessStatus(buyerUUID: String, sellerId: String): Flow<AccessStatus> {
        return flowOf(_accessStatus["$buyerUUID/$sellerId"] ?: AccessStatus.NONE)
    }

    override fun observeAccessRequests(sellerId: String): Flow<List<AccessRequest>> {
        return _accessRequests.asStateFlow()
    }

    override suspend fun approveAccessRequest(sellerId: String, buyerUUID: String): Result<Unit> {
        _accessStatus["$buyerUUID/$sellerId"] = AccessStatus.APPROVED
        _accessRequests.value = _accessRequests.value.filter { it.buyerUUID != buyerUUID }
        return Result.success(Unit)
    }

    override suspend fun blockBuyer(sellerId: String, buyerUUID: String): Result<Unit> {
        _accessStatus["$buyerUUID/$sellerId"] = AccessStatus.BLOCKED
        _accessRequests.value = _accessRequests.value.filter { it.buyerUUID != buyerUUID }
        approvedBuyers.getOrPut(sellerId) { mutableMapOf() }.remove(buyerUUID)
        blockedClients.getOrPut(sellerId) { mutableSetOf() }.add(buyerUUID)
        return Result.success(Unit)
    }

    private val approvedBuyers = mutableMapOf<String, MutableMap<String, String>>()

    override suspend fun approveAccessRequestWithTracking(sellerId: String, buyerUUID: String, displayName: String): Result<Unit> {
        _accessStatus["$buyerUUID/$sellerId"] = AccessStatus.APPROVED
        _accessRequests.value = _accessRequests.value.filter { it.buyerUUID != buyerUUID }
        approvedBuyers.getOrPut(sellerId) { mutableMapOf() }[buyerUUID] = displayName
        return Result.success(Unit)
    }

    override suspend fun unblockApprovedBuyer(sellerId: String, buyerUUID: String): Result<Unit> {
        _accessStatus["$buyerUUID/$sellerId"] = AccessStatus.APPROVED
        blockedClients[sellerId]?.remove(buyerUUID)
        approvedBuyers.getOrPut(sellerId) { mutableMapOf() }[buyerUUID] = ""
        return Result.success(Unit)
    }

    override suspend fun updateApprovedBuyerDisplayName(sellerId: String, buyerUUID: String, displayName: String): Result<Unit> {
        approvedBuyers[sellerId]?.put(buyerUUID, displayName)
        return Result.success(Unit)
    }

    override fun observeApprovedBuyerIds(sellerId: String): Flow<Map<String, String>> {
        return flowOf(approvedBuyers[sellerId]?.toMap() ?: emptyMap())
    }

    override suspend fun cancelAccessRequest(sellerId: String, buyerUUID: String): Result<Unit> =
        Result.success(Unit)

    override suspend fun getBuyerDisplayName(sellerId: String, buyerUUID: String): String = ""
}
