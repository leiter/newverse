package com.together.newverse.domain.repository

import com.together.newverse.domain.model.SellerInboxMessage
import kotlinx.coroutines.flow.Flow

/**
 * What buyers have sent the seller through a published message box, at
 * `seller_inbox/{sellerId}/{messageId}`.
 *
 * The return channel for [com.together.newverse.domain.model.ActionKind.QUICK_MESSAGE],
 * and the mirror of `SellerUiActionRepository`: that one is seller-writable and
 * world-readable, this one is buyer-writable and seller-readable. Neither direction
 * can be served by one node, which is the same conclusion `seller_ui_actions` reached
 * about `seller_events`.
 *
 * Not carried on `seller_events` either, and this time the reasons are different.
 * That log's write rule requires an APPROVED buyer, while a wishbox is deliberately
 * open to every signed-in one; and its rows are facts about accounts
 * (`ACCOUNT_LINKED`), where these are free text a person wrote and the seller will
 * read, delete, and want capped in length.
 *
 * See `doc/guides/quick-message-wishbox.md`.
 */
interface SellerInboxRepository {

    /**
     * Send one message. The id is minted here when absent.
     *
     * Buy side. Reports its failure rather than swallowing it: the buyer is standing
     * there having just pressed send, and a wish that silently went nowhere is worse
     * than a wish that visibly did not.
     */
    suspend fun send(message: SellerInboxMessage): Result<SellerInboxMessage>

    /**
     * Live messages for one seller, newest first. Empty while the node is absent.
     *
     * Sell side only — a buyer cannot read this node at all, by rule.
     */
    fun observeMessages(sellerId: String): Flow<List<SellerInboxMessage>>

    /**
     * Remove one message once the seller is done with it.
     *
     * A delete rather than a handled flag, for the reason a retired published button
     * is deleted: nothing references a message by id, so there is no dangling
     * reference to protect. The seller clearing their inbox is the whole lifecycle.
     */
    suspend fun deleteMessage(sellerId: String, messageId: String): Result<Unit>
}
