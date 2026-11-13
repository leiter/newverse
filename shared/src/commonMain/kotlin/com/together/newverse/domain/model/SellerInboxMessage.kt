package com.together.newverse.domain.model

/**
 * One thing a buyer typed into a message box the seller published, as it was sent.
 *
 * Stored at `seller_inbox/{sellerId}/{messageId}`: the buyer may create a row and
 * nothing else, the seller may read and delete. The mirror image of
 * [SellerUiAction], which runs seller→buyer; this is the answer coming back.
 *
 * Add-only for the buyer, deliberately. A buyer who could edit a sent message could
 * rewrite a wish the seller has already acted on, and one who could read the node
 * would read every other buyer's. Both are enforced in `database.rules.json`, not
 * here — see `doc/guides/quick-message-wishbox.md`.
 *
 * ### Why the row repeats what the seller could look up
 *
 * [buyerName], [prompt] and [requestKind] are all derivable at write time and none of
 * them is derivable later. A buyer renames themselves; the seller rewords the
 * question; a future app version retires a kind of box. A wish filed under a question
 * the seller no longer asks means nothing, and a seller reading a list of answers has
 * to see what was asked. So each message snapshots what it was an answer to, the same
 * reason [OrderedProduct] snapshots the price it was charged rather than pointing at a
 * price that moves.
 */
data class SellerInboxMessage(
    /** The node key. Empty on a message not yet sent. */
    val id: String = "",
    /** Whose inbox. Checked against the path by the rules. */
    val sellerId: String = "",
    /**
     * Who sent it — the buyer's auth uid.
     *
     * The rules require this to equal `auth.uid` on create, so it is an identity the
     * seller can act on (approve, block, look up in their customer list) rather than
     * a name anyone could type.
     */
    val buyerId: String = "",
    /**
     * What the buyer calls themselves, as their profile read at send time.
     *
     * Unverified by nature and may be empty — a guest has no name to give. The seller
     * sees it beside [buyerId], which is the part that is true.
     */
    val buyerName: String = "",
    /**
     * Which kind of box this came through.
     *
     * [ActionKind.QUICK_MESSAGE] for every message today. Stored so that a seller
     * reading their inbox after a second kind of box exists — feedback, a rating —
     * does not have to infer which from the wording, and so one list can show both.
     * An unknown name read back is skipped, like everywhere else a kind is stored.
     */
    val requestKind: ActionKind = ActionKind.QUICK_MESSAGE,
    /**
     * The id of the [SellerUiAction] that asked. Empty on a row written before the
     * published box it came from was deleted — which is most of them, eventually.
     *
     * Kept for grouping rather than for lookup: the action it names may well be gone,
     * which is why [prompt] is snapshotted beside it instead of being read through it.
     */
    val requestId: String = "",
    /** The question as the buyer saw it, snapshotted. See the note on this class. */
    val prompt: String = "",
    /** What the buyer typed, or the answers they picked, joined. See [selections]. */
    val text: String = "",
    /**
     * The answers a buyer picked from a [ActionKind.CHOICE], as **labels**.
     *
     * Empty for every other kind, and the rules refuse the child there.
     *
     * Labels rather than positions into [SellerUiAction.options], deliberately and
     * at some cost in bytes. A position is only meaningful beside the list it indexes,
     * and that list lives on a row which — as [requestId] says plainly — is usually
     * gone by the time anyone reads the message. Worse, it moves: a seller inserting
     * an answer would silently re-map every reply already filed, with no error and no
     * way to notice. The same reason [OrderedProduct] snapshots the price it was
     * charged instead of pointing at one that moves.
     *
     * The cost is that rewording an answer splits its tally. That is the honest
     * result — a reworded question is a different question, and
     * [SellerUiAction.dismissalKey] already re-asks everyone when the options change,
     * so the split is the intended behaviour rather than an accident of storage.
     *
     * [text] carries the same labels joined, so a seller's inbox that knows nothing
     * about pickers still shows something a human can read.
     */
    val selections: List<String> = emptyList(),
    /**
     * Everything the picker offered, snapshotted at send time. Empty for other kinds.
     *
     * Stored beside [selections] so the seller can see what the buyer chose *from*,
     * which is what makes a tally mean anything: three people picking "Eier" out of
     * three options is not the same finding as three out of eight. Snapshotted for
     * the reason [prompt] is — the row it came from may be gone.
     */
    val options: List<String> = emptyList(),
    /** When it was sent, epoch millis, from the sending device's clock. */
    val timestamp: Long = 0L,
    /**
     * The same instant as an ISO-8601 string.
     *
     * Both are stored for the reason `seller_events` stores both: the number sorts and
     * the string is readable in the Firebase console, where this node will mostly be
     * looked at before any seller-side screen is polished.
     */
    val timestampIso: String = "",
) {
    /**
     * A message with nothing in it is not sent.
     *
     * A choice answers with [selections], not with typing, but [text] carries those
     * same labels joined — so one check covers both kinds, and a picker that somehow
     * sent an empty selection is caught by the same gate as an empty wish.
     */
    val isSendable: Boolean get() = text.isNotBlank() && buyerId.isNotEmpty()

    companion object {
        /**
         * What [text] is allowed to be, matching the rules' own cap.
         *
         * A wish is a sentence, not an essay. The cap is the same 500 characters
         * [SellerUiAction.MAX_VALUE_LENGTH] uses, so neither side of the exchange can
         * outgrow the other.
         */
        const val MAX_TEXT_LENGTH = 500

        /** What [buyerName] is allowed to be. Longer than this is not a name. */
        const val MAX_NAME_LENGTH = 120
    }
}
