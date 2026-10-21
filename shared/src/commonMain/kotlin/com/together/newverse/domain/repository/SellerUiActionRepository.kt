package com.together.newverse.domain.repository

import com.together.newverse.domain.model.SellerUiAction
import kotlinx.coroutines.flow.Flow

/**
 * The buttons a seller publishes into the buy app, at
 * `seller_ui_actions/{sellerId}/{actionId}`.
 *
 * Seller-writable, readable by every signed-in user — the same shape as `markets`,
 * because it answers the same need: the seller configures something and buyers render
 * it. See `doc/guides/seller-ui-actions.md`.
 *
 * Removal is a delete, not a flag. Nothing references an action by id the way an
 * `Order` references a market, so there is no dangling reference to protect and no
 * reason to keep a retired button readable.
 */
interface SellerUiActionRepository {

    /** Live actions for one seller. Empty while the node is absent or unreadable. */
    fun observeActions(sellerId: String): Flow<List<SellerUiAction>>

    /** One read, for the startup path that cannot wait on a subscription. */
    suspend fun getActions(sellerId: String): Result<List<SellerUiAction>>

    /**
     * Create or replace one action. The id is minted here when absent.
     *
     * Refuses an action whose location already holds [SellerUiAction.MAX_PER_LOCATION],
     * since the rules cannot count children — see that constant.
     */
    suspend fun saveAction(sellerId: String, action: SellerUiAction): Result<SellerUiAction>

    /** Remove one action. */
    suspend fun deleteAction(sellerId: String, actionId: String): Result<Unit>
}
