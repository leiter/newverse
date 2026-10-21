package com.together.newverse.test

import com.together.newverse.domain.model.SellerUiAction
import com.together.newverse.domain.repository.SellerUiActionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory published buttons.
 *
 * Enforces the per-slot cap the way [com.together.newverse.data.repository.GitLiveSellerUiActionRepository]
 * does, so a test that expects a refusal gets one here too — the cap lives in the
 * repository because RTDB rules cannot count children, which makes it exactly the kind
 * of rule a fake must reproduce rather than wave through.
 */
class FakeSellerUiActionRepository : SellerUiActionRepository {

    private val actions = MutableStateFlow<Map<String, List<SellerUiAction>>>(emptyMap())

    /** Seed what a seller has already published. */
    fun seed(sellerId: String, vararg published: SellerUiAction) {
        actions.value = actions.value + (sellerId to published.toList())
    }

    var shouldFailSave: Boolean = false

    fun reset() {
        actions.value = emptyMap()
        shouldFailSave = false
    }

    override fun observeActions(sellerId: String): Flow<List<SellerUiAction>> =
        actions.asStateFlow().map { it[sellerId].orEmpty() }

    override suspend fun getActions(sellerId: String): Result<List<SellerUiAction>> =
        Result.success(actions.value[sellerId].orEmpty())

    override suspend fun saveAction(
        sellerId: String,
        action: SellerUiAction
    ): Result<SellerUiAction> {
        if (shouldFailSave) return Result.failure(Exception("Test error"))
        if (!action.isUsable) {
            return Result.failure(IllegalArgumentException("an action needs a label and a target"))
        }

        val existing = actions.value[sellerId].orEmpty()
        val id = action.id.ifEmpty { "act_${existing.size + 1}" }
        val isNew = existing.none { it.id == id }
        val inSlot = existing.count { it.location == action.location && it.id != id }
        if (isNew && inSlot >= SellerUiAction.MAX_PER_LOCATION) {
            return Result.failure(IllegalStateException("${action.location} is full"))
        }

        val stored = action.copy(
            id = id,
            label = action.label.trim().take(SellerUiAction.MAX_LABEL_LENGTH),
            value = action.kind.normalize(action.value)
        )
        actions.value = actions.value + (
            sellerId to existing.filterNot { it.id == id } + stored
            )
        return Result.success(stored)
    }

    override suspend fun deleteAction(sellerId: String, actionId: String): Result<Unit> {
        val existing = actions.value[sellerId].orEmpty()
        actions.value = actions.value + (sellerId to existing.filterNot { it.id == actionId })
        return Result.success(Unit)
    }
}
