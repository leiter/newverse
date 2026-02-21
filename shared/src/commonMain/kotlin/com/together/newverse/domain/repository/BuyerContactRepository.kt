package com.together.newverse.domain.repository

import com.together.newverse.domain.model.BuyerContact
import kotlinx.coroutines.flow.Flow

interface BuyerContactRepository {
    fun observeContacts(buyerId: String): Flow<List<BuyerContact>>
    suspend fun addContact(buyerId: String, contact: BuyerContact): Result<Unit>
    suspend fun removeContact(buyerId: String, contactUserId: String): Result<Unit>
    suspend fun blockContact(buyerId: String, contactUserId: String): Result<Unit>
    suspend fun unblockContact(buyerId: String, contactUserId: String): Result<Unit>
    fun observeBlockedContacts(buyerId: String): Flow<List<String>>
}
