package com.together.newverse.data.firebase

import com.google.android.gms.tasks.Task
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Extension functions to convert Firebase callbacks to Kotlin Coroutines Flow and suspend functions
 */

/**
 * Get a single value snapshot
 */
suspend fun DatabaseReference.getSingleValue(): DataSnapshot = suspendCoroutine { continuation ->
    addListenerForSingleValueEvent(object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            continuation.resume(snapshot)
        }

        override fun onCancelled(error: DatabaseError) {
            continuation.resumeWithException(error.toException())
        }
    })
}

/**
 * Convert Task to suspend function
 */
suspend fun <T> Task<T>.awaitResult(): T {
    return this.await()
}
