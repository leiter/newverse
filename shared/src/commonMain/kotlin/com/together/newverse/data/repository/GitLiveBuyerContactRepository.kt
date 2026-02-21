package com.together.newverse.data.repository

import com.together.newverse.domain.model.BuyerContact
import com.together.newverse.domain.repository.BuyerContactRepository
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.database.database
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

/**
 * Firebase Realtime Database implementation of BuyerContactRepository.
 *
 * Firebase structure:
 * - buyer_contacts/{buyerId}/{contactBuyerId}/ - contact data
 * - buyer_blocked/{buyerId}/{blockedUserId}: true - block list
 */
class GitLiveBuyerContactRepository : BuyerContactRepository {

    private val database = Firebase.database
    private val contactsRef = database.reference("buyer_contacts")
    private val blockedRef = database.reference("buyer_blocked")

    override fun observeContacts(buyerId: String): Flow<List<BuyerContact>> {
        return contactsRef.child(buyerId).valueEvents.map { snapshot ->
            if (!snapshot.exists) return@map emptyList()

            val contacts = mutableListOf<BuyerContact>()
            snapshot.children.forEach { child ->
                try {
                    contacts.add(
                        BuyerContact(
                            userId = try { child.child("userId").value<String>() } catch (e: Exception) { child.key ?: "" },
                            displayName = try { child.child("displayName").value<String>() } catch (e: Exception) { "" },
                            addedAt = try { child.child("addedAt").value<Long>() } catch (e: Exception) { 0L }
                        )
                    )
                } catch (e: Exception) {
                    println("Failed to parse contact: ${e.message}")
                }
            }
            contacts.sortedByDescending { it.addedAt }
        }
    }

    override suspend fun addContact(buyerId: String, contact: BuyerContact): Result<Unit> {
        return try {
            val ref = contactsRef.child(buyerId).child(contact.userId)
            ref.child("userId").setValue(contact.userId)
            ref.child("displayName").setValue(contact.displayName)
            ref.child("addedAt").setValue(
                if (contact.addedAt > 0) contact.addedAt
                else Clock.System.now().toEpochMilliseconds()
            )
            println("Contact added: ${contact.userId} for buyer $buyerId")
            Result.success(Unit)
        } catch (e: Exception) {
            println("Failed to add contact: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun removeContact(buyerId: String, contactUserId: String): Result<Unit> {
        return try {
            contactsRef.child(buyerId).child(contactUserId).removeValue()
            println("Contact removed: $contactUserId for buyer $buyerId")
            Result.success(Unit)
        } catch (e: Exception) {
            println("Failed to remove contact: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun blockContact(buyerId: String, contactUserId: String): Result<Unit> {
        return try {
            blockedRef.child(buyerId).child(contactUserId).setValue(true)
            println("Contact blocked: $contactUserId by buyer $buyerId")
            Result.success(Unit)
        } catch (e: Exception) {
            println("Failed to block contact: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun unblockContact(buyerId: String, contactUserId: String): Result<Unit> {
        return try {
            blockedRef.child(buyerId).child(contactUserId).removeValue()
            println("Contact unblocked: $contactUserId by buyer $buyerId")
            Result.success(Unit)
        } catch (e: Exception) {
            println("Failed to unblock contact: ${e.message}")
            Result.failure(e)
        }
    }

    override fun observeBlockedContacts(buyerId: String): Flow<List<String>> {
        return blockedRef.child(buyerId).valueEvents.map { snapshot ->
            if (!snapshot.exists) return@map emptyList()

            val blocked = mutableListOf<String>()
            snapshot.children.forEach { child ->
                child.key?.let { blocked.add(it) }
            }
            blocked
        }
    }
}
