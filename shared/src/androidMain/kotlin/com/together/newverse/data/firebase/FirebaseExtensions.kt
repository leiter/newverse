package com.together.newverse.data.firebase

import com.google.android.gms.tasks.Task
import com.google.firebase.database.*
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Extension functions to convert Firebase callbacks to Kotlin Coroutines Flow and suspend functions
 */

/**
 * Observe child events as a Flow
 * Emits snapshots for ADDED, CHANGED, MOVED, and REMOVED events
 */
fun DatabaseReference.observeChildEvents(): Flow<DataSnapshot> = callbackFlow {
    val listener = object : ChildEventListener {
        override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
            trySend(snapshot)
        }

        override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
            trySend(snapshot)
        }

        override fun onChildRemoved(snapshot: DataSnapshot) {
            trySend(snapshot)
        }

        override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {
            trySend(snapshot)
        }

        override fun onCancelled(error: DatabaseError) {
            cancel("Firebase error", error.toException())
        }
    }

    addChildEventListener(listener)
    awaitClose { removeEventListener(listener) }
}

/**
 * Observe value changes as a Flow
 */
fun DatabaseReference.observeValue(): Flow<DataSnapshot> = callbackFlow {
    val listener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            trySend(snapshot)
        }

        override fun onCancelled(error: DatabaseError) {
            cancel("Firebase error", error.toException())
        }
    }

    addValueEventListener(listener)
    awaitClose { removeEventListener(listener) }
}

/**
 * Observe value changes as a Flow with type mapping
 */
inline fun <reified T : Any> DatabaseReference.observeValueAs(): Flow<T?> = callbackFlow {
    val listener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            val value = snapshot.getValue(T::class.java)
            trySend(value)
        }

        override fun onCancelled(error: DatabaseError) {
            cancel("Firebase error", error.toException())
        }
    }

    addValueEventListener(listener)
    awaitClose { removeEventListener(listener) }
}

/**
 * Observe list of children as a Flow with type mapping
 */
inline fun <reified T : Any> DatabaseReference.observeListAs(
    crossinline idMapper: (DataSnapshot) -> String = { it.key ?: "" }
): Flow<List<T>> = callbackFlow {
    val listener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            val list = snapshot.children.mapNotNull { childSnapshot ->
                childSnapshot.getValue(T::class.java)?.also { item ->
                    // Try to set the ID if the object has an 'id' field
                    try {
                        val field = T::class.java.getDeclaredField("id")
                        field.isAccessible = true
                        if (field.type == String::class.java) {
                            field.set(item, idMapper(childSnapshot))
                        }
                    } catch (e: Exception) {
                        // Field doesn't exist or isn't accessible, ignore
                    }
                }
            }
            trySend(list)
        }

        override fun onCancelled(error: DatabaseError) {
            cancel("Firebase error", error.toException())
        }
    }

    addValueEventListener(listener)
    awaitClose { removeEventListener(listener) }
}

/**
 * Get a single value as suspend function
 */
suspend inline fun <reified T : Any> DatabaseReference.getValueAs(): T? {
    val snapshot = getSingleValue()
    return snapshot.getValue(T::class.java)
}

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
 * Get a single value snapshot for Query
 */
suspend fun Query.getSingleValue(): DataSnapshot = suspendCoroutine { continuation ->
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
 * Get list of children as suspend function
 */
suspend inline fun <reified T : Any> DatabaseReference.getListAs(
    crossinline idMapper: (DataSnapshot) -> String = { it.key ?: "" }
): List<T> {
    val snapshot = getSingleValue()
    return snapshot.children.mapNotNull { childSnapshot ->
        childSnapshot.getValue(T::class.java)?.also { item ->
            // Try to set the ID if the object has an 'id' field
            try {
                val field = T::class.java.getDeclaredField("id")
                field.isAccessible = true
                if (field.type == String::class.java) {
                    field.set(item, idMapper(childSnapshot))
                }
            } catch (e: Exception) {
                // Field doesn't exist or isn't accessible, ignore
            }
        }
    }
}

/**
 * Check if a reference exists
 */
suspend fun DatabaseReference.exists(): Boolean {
    val snapshot = getSingleValue()
    return snapshot.exists()
}

/**
 * Convert Task to suspend function
 */
suspend fun <T> Task<T>.awaitResult(): T {
    return this.await()
}

/**
 * Check connection status
 */
suspend fun checkConnected(): Boolean {
    return try {
        Database.connectedStatus().getValueAs<Boolean>() ?: false
    } catch (e: Exception) {
        false
    }
}

/**
 * Data class to represent Firebase child events with metadata
 */
data class FirebaseChildEvent<T>(
    val data: T,
    val id: String,
    val eventType: EventType
)

enum class EventType {
    ADDED,
    CHANGED,
    REMOVED,
    MOVED
}

/**
 * Observe child events with type information
 */
inline fun <reified T : Any> DatabaseReference.observeChildEventsAs(): Flow<FirebaseChildEvent<T>> = callbackFlow {
    val listener = object : ChildEventListener {
        override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
            snapshot.getValue(T::class.java)?.let { data ->
                trySend(FirebaseChildEvent(data, snapshot.key ?: "", EventType.ADDED))
            }
        }

        override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
            snapshot.getValue(T::class.java)?.let { data ->
                trySend(FirebaseChildEvent(data, snapshot.key ?: "", EventType.CHANGED))
            }
        }

        override fun onChildRemoved(snapshot: DataSnapshot) {
            snapshot.getValue(T::class.java)?.let { data ->
                trySend(FirebaseChildEvent(data, snapshot.key ?: "", EventType.REMOVED))
            }
        }

        override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {
            snapshot.getValue(T::class.java)?.let { data ->
                trySend(FirebaseChildEvent(data, snapshot.key ?: "", EventType.MOVED))
            }
        }

        override fun onCancelled(error: DatabaseError) {
            cancel("Firebase error", error.toException())
        }
    }

    addChildEventListener(listener)
    awaitClose { removeEventListener(listener) }
}
