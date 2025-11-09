package com.together.newverse.data.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference

/**
 * Firebase Database helper object
 * Provides centralized access to Firebase Realtime Database paths
 */
object Database {

    private const val ARTICLES = "articles"
    private const val SELLER_PROFILE = "seller_profile"
    private const val ORDERS = "orders"
    private const val CLIENTS = "buyer_profile"
    private const val STORAGE_PREFIX = "images/"

    private var isPersistenceEnabled = false

    private fun fire(): FirebaseDatabase = FirebaseDatabase.getInstance()

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

    /**
     * Get articles reference for the current authenticated user
     */
    fun articles(): DatabaseReference {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
            ?: throw IllegalStateException("User not authenticated")
        return fire().reference.child(ARTICLES).child(uid)
    }

    /**
     * Get articles reference for a specific provider/seller
     */
    fun providerArticles(providerId: String): DatabaseReference =
        fire().reference.child(ARTICLES).child(providerId)

    /**
     * Get seller profile reference
     */
    fun sellerProfile(sellerId: String = "", seller: Boolean = false): DatabaseReference {
        val result = fire().reference.child(SELLER_PROFILE)
        if (sellerId.isNotEmpty()) {
            return result.child(sellerId)
        }
        return if (seller) {
            val uid = FirebaseAuth.getInstance().currentUser?.uid
                ?: throw IllegalStateException("User not authenticated")
            result.child(uid)
        } else {
            result
        }
    }

    /**
     * Get connection status reference
     */
    fun connectedStatus(): DatabaseReference =
        fire().getReference(".info/connected")

    /**
     * Get orders reference for seller
     */
    fun orderSeller(sellerId: String = ""): DatabaseReference {
        return if (sellerId.isEmpty()) {
            val uid = FirebaseAuth.getInstance().currentUser?.uid
                ?: throw IllegalStateException("User not authenticated")
            fire().reference.child(ORDERS).child(uid)
        } else {
            fire().reference.child(ORDERS).child(sellerId)
        }
    }

    /**
     * Get next orders for a specific date
     */
    fun nextOrders(date: String): com.google.firebase.database.Query {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
            ?: throw IllegalStateException("User not authenticated")
        return fire().reference.child(ORDERS)
            .child(uid)
            .child(date)
            .orderByChild("pickUpDate")
    }

    /**
     * Get buyer profile reference
     */
    fun buyer(): DatabaseReference {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
            ?: throw IllegalStateException("User not authenticated")
        return fire().reference.child(CLIENTS).child(uid)
    }

    /**
     * Get storage reference
     */
    fun storage(filename: String = ""): StorageReference {
        val path = if (filename.isEmpty()) {
            "$STORAGE_PREFIX${System.currentTimeMillis()}_ttt.jpeg"
        } else {
            filename
        }
        return FirebaseStorage.getInstance().reference.child(path)
    }
}
