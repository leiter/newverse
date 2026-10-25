package com.together.newverse.data.repository

import com.together.newverse.domain.model.ActionAudience
import com.together.newverse.domain.model.ActionKind
import com.together.newverse.domain.model.ActionLocation
import com.together.newverse.domain.model.SellerUiAction
import com.together.newverse.domain.repository.SellerUiActionRepository
import com.together.newverse.util.Log
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.database.database
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

/**
 * Published buttons at `seller_ui_actions/{sellerId}/{actionId}`.
 *
 * Reads never throw: a seller who has published nothing and a node a buyer cannot
 * reach are the same thing to the buy app — no buttons — and neither is worth failing
 * startup over. Writes report their failure, because the seller is standing there
 * having just pressed save.
 */
class GitLiveSellerUiActionRepository : SellerUiActionRepository {

    private companion object {
        private const val TAG = "UiActionRepo"
    }

    private val database = Firebase.database
    private val actionsRef = database.reference("seller_ui_actions")

    override fun observeActions(sellerId: String): Flow<List<SellerUiAction>> {
        if (sellerId.isEmpty()) return flowOf(emptyList())
        return actionsRef.child(sellerId).valueEvents
            .map { snapshot -> parseActions(snapshot.value) }
            .catch { e ->
                Log.w(TAG) { "observeActions: $sellerId unreadable - ${e.message}" }
                emit(emptyList())
            }
    }

    override suspend fun getActions(sellerId: String): Result<List<SellerUiAction>> {
        return try {
            if (sellerId.isEmpty()) return Result.success(emptyList())
            Result.success(parseActions(actionsRef.child(sellerId).valueEvents.first().value))
        } catch (e: Exception) {
            Log.w(TAG) { "getActions: $sellerId unreadable - ${e.message}" }
            // Not a failure the caller can act on, and the buy app's startup path
            // treats an empty list the same way. Reported as success-with-nothing.
            Result.success(emptyList())
        }
    }

    override suspend fun saveAction(
        sellerId: String,
        action: SellerUiAction
    ): Result<SellerUiAction> {
        return try {
            // The slot's kind and an announcement's derived label are settled before
            // anything is checked against them — see [SellerUiAction.readyToStore],
            // which the seller's editor calls too so the two cannot disagree.
            val typed = action.readyToStore()
            if (!typed.isUsable) {
                return Result.failure(IllegalArgumentException("an action needs a label and a target"))
            }

            val existing = parseActions(actionsRef.child(sellerId).valueEvents.first().value)
            val id = action.id.ifEmpty { "act_${Clock.System.now().toEpochMilliseconds()}" }

            // Counted here because RTDB rules cannot count children. Replacing one of
            // the existing actions is always allowed; it is only a *new* one that can
            // push a slot over the cap.
            val isNew = existing.none { it.id == id }
            val inSlot = existing.count { it.location == typed.location && it.id != id }
            // Per slot, not one global cap: the home title holds exactly one, since a
            // title cannot collapse into an overflow menu the way buttons can.
            if (isNew && inSlot >= typed.location.maxActions) {
                return Result.failure(
                    IllegalStateException(
                        "${typed.location} already holds ${typed.location.maxActions} actions"
                    )
                )
            }

            val stored = typed.copy(
                id = id,
                label = typed.label.trim().take(SellerUiAction.MAX_LABEL_LENGTH),
                // Normalized on save as well as on read, so an OPEN_URL sitting in
                // the database is already scheme-less and the rules' no-colon check
                // can hold. For an ANNOUNCE this only trims.
                value = typed.kind.normalize(typed.value).take(SellerUiAction.MAX_VALUE_LENGTH)
            )
            actionsRef.child(sellerId).child(id).setValue(actionToMap(stored))
            Log.d(TAG) { "saveAction: $id -> ${stored.location} for $sellerId" }
            Result.success(stored)
        } catch (e: Exception) {
            Log.e(TAG) { "saveAction: ${e.message}" }
            Result.failure(e)
        }
    }

    override suspend fun deleteAction(sellerId: String, actionId: String): Result<Unit> {
        return try {
            require(actionId.isNotEmpty()) { "an action needs an id before it can be removed" }
            actionsRef.child(sellerId).child(actionId).removeValue()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG) { "deleteAction: ${e.message}" }
            Result.failure(e)
        }
    }

    /**
     * Read stored actions, skipping any the app cannot render.
     *
     * A kind or a location this version does not know is dropped rather than guessed
     * at: a button whose behaviour is unknown cannot be given a sensible default, and
     * one placed in an unknown slot would have to land somewhere arbitrary. Both would
     * be worse than not appearing. Same rule as `ContactChannel.fromStored`.
     */
    private fun parseActions(raw: Any?): List<SellerUiAction> {
        val rows: List<Pair<String?, Any?>> = when (raw) {
            is Map<*, *> -> raw.entries.map { (key, value) -> (key as? String) to value }
            is List<*> -> raw.map { null to it }
            else -> return emptyList()
        }
        return rows.mapNotNull { (key, row) ->
            val fields = row as? Map<*, *> ?: return@mapNotNull null
            val id = (fields["id"] as? String)?.ifEmpty { null } ?: key ?: return@mapNotNull null
            val kind = ActionKind.fromStored(fields["kind"] as? String) ?: return@mapNotNull null
            val location = ActionLocation.fromStored(fields["location"] as? String)
                ?: return@mapNotNull null
            // Absent reads as EVERYONE; an unknown name skips the row.
            val audience = ActionAudience.stored(fields["audience"] as? String)
                ?: return@mapNotNull null
            val value = fields["value"] as? String ?: return@mapNotNull null
            val label = fields["label"] as? String ?: return@mapNotNull null
            if (value.isBlank() || label.isBlank()) return@mapNotNull null
            // A kind that does not belong in this slot is a row this app cannot
            // render either way, so it is dropped here rather than halfway down the
            // UI. The rules refuse the pairing too; this covers a hand-written row.
            if (kind != location.kind) return@mapNotNull null
            SellerUiAction(
                id = id,
                kind = kind,
                label = label.take(SellerUiAction.MAX_LABEL_LENGTH),
                // Normalized again on read: a record written before this ran on save,
                // or written straight into the database by hand, still opens safely.
                value = kind.normalize(value),
                location = location,
                audience = audience
            )
        }
    }

    private fun actionToMap(action: SellerUiAction): Map<String, Any?> = mapOf(
        "id" to action.id,
        "kind" to action.kind.name,
        "label" to action.label,
        "value" to action.value,
        "location" to action.location.name,
        "audience" to action.audience.name
    )
}
