package com.together.newverse.data.firebase

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.database.DatabaseReference
import dev.gitlive.firebase.database.database
import dev.gitlive.firebase.storage.storage
import dev.gitlive.firebase.storage.StorageReference
import kotlinx.datetime.Clock

/**
 * Firebase Database helper object (GitLive cross-platform)
 * Provides centralized access to Firebase Realtime Database paths
 */
object Database {

    private const val ARTICLES = "articles"
    private const val SELLER_PROFILE = "seller_profile"
    private const val ORDERS = "orders"
    private const val CLIENTS = "buyer_profile"
    private const val STORAGE_PREFIX = "images/"

    private var isPersistenceEnabled = false

    private fun fire() = Firebase.database

    /**
     * Initialize Firebase Database with persistence
     */
    fun initialize() {
        if (!isPersistenceEnabled) {
            try {
                fire().setPersistenceEnabled(true)
                isPersistenceEnabled = true
            } catch (e: Exception) {
                // Already enabled or error
            }
        }
    }

    private fun requireUserId(): String {
        return Firebase.auth.currentUser?.uid
            ?: throw IllegalStateException("User not authenticated")
    }

    /**
     * Get articles reference for the current authenticated user
     */
    fun articles(): DatabaseReference {
        val uid = requireUserId()
        return fire().reference(ARTICLES).child(uid)
    }

    /**
     * Get articles reference for a specific provider/seller
     */
    fun providerArticles(providerId: String): DatabaseReference =
        fire().reference(ARTICLES).child(providerId)

    /**
     * Get seller profile reference
     */
    fun sellerProfile(sellerId: String = "", seller: Boolean = false): DatabaseReference {
        val result = fire().reference(SELLER_PROFILE)
        if (sellerId.isNotEmpty()) {
            return result.child(sellerId)
        }
        return if (seller) {
            val uid = requireUserId()
            result.child(uid)
        } else {
            result
        }
    }

    /**
     * Get connection status reference
     */
    fun connectedStatus(): DatabaseReference =
        fire().reference(".info/connected")

    /**
     * Get orders reference for seller
     */
    fun orderSeller(sellerId: String = ""): DatabaseReference {
        return if (sellerId.isEmpty()) {
            val uid = requireUserId()
            fire().reference(ORDERS).child(uid)
        } else {
            fire().reference(ORDERS).child(sellerId)
        }
    }

    /**
     * Get next orders for a specific date
     */
    fun nextOrders(date: String): DatabaseReference {
        val uid = requireUserId()
        return fire().reference(ORDERS)
            .child(uid)
            .child(date)
    }

    /**
     * Get buyer profile reference
     */
    fun buyer(): DatabaseReference {
        val uid = requireUserId()
        return fire().reference(CLIENTS).child(uid)
    }

    /**
     * Get storage reference
     */
    fun storage(filename: String = ""): StorageReference {
        val path = if (filename.isEmpty()) {
            "$STORAGE_PREFIX${Clock.System.now().toEpochMilliseconds()}_ttt.jpeg"
        } else {
            filename
        }
        return Firebase.storage.reference(path)
    }

    /**
     * Get seller profile reference for listing
     */
    fun sellerProfileList(): DatabaseReference =
        fire().reference(SELLER_PROFILE)
}
