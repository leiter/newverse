package com.together.newverse.data.config

/**
 * How many dismissal keys one device keeps.
 *
 * Bounded because nothing ever removes one: a seller publishing a fresh announcement
 * every week would otherwise grow this set forever. The oldest are dropped first, and
 * the worst an evicted key can do is show one very old announcement again — which
 * cannot happen in practice, because the seller deleted that row long ago.
 *
 * A top-level constant rather than a member of the expect class, so each platform
 * implementation does not have to restate a companion object to get at it.
 */
const val MAX_DISMISSAL_KEYS = 50

/** Separates the stored keys. Not a character a [dismissalKey] can contain. */
internal const val DISMISSAL_SEPARATOR = "\n"

/**
 * Marks a key as answered rather than merely collapsed. Not a character an id starts
 * with, so the two meanings cannot be confused on read.
 */
internal const val ANSWERED_PREFIX = "!"

/**
 * Which seller rows this device has stopped showing in full.
 *
 * **Per-install, not per-user** — unlike [DemoOrderStorage] and
 * [PendingInviteTokenStorage], which give every userId its own container. Those hold
 * the buyer's own data, which must not leak between accounts on a shared phone. A
 * collapsed announcement is not the buyer's data; it is this screen's answer to "I
 * have read this". Scoping it per user would bring the banner back when a guest links
 * their account, which is the moment they are least interested in reading it again.
 *
 * Keys are [com.together.newverse.domain.model.SellerUiAction.dismissalKey] — the row
 * id and a hash of the text — so re-wording an announcement brings it back and
 * re-reading the same one does not.
 *
 * ### Two meanings, one file
 *
 * A key stored plain means **collapsed**: the buyer closed the row, it is drawn as an
 * icon in the top bar instead of in the title, and tapping that icon removes the key
 * again. Closing is reversible, because it used not to be — a buyer who caught the ×
 * by accident had lost that announcement until the seller reworded it, and nothing in
 * this set was ever removed.
 *
 * A key stored behind [ANSWERED_PREFIX] means **answered**: the row was
 * [com.together.newverse.domain.model.ActionPersistence.HIDE_ON_ACTION] and the buyer
 * acted on it. That is not reversible and must not be drawn as an icon — the seller
 * asked a question once and got their answer, and an icon that reopened it would
 * invite a second.
 *
 * One file and a prefix rather than two storages, so there is still one place a
 * device records what it has stopped showing, and one eviction policy over it.
 *
 * Android: SharedPreferences file `newverse_announcements`, iOS: standard NSUserDefaults.
 */
expect class AnnouncementDismissalStorage {
    /** Every key dismissed on this device. Empty, never null, on a fresh install. */
    fun dismissed(): Set<String>

    /** Remember that this key is dismissed. Idempotent. */
    fun dismiss(key: String)

    /**
     * Forget a key, so the row it names is shown in full again. Idempotent.
     *
     * The half that never existed: this set only ever grew, which is what made
     * closing an announcement permanent. Only a collapsed key is ever restored — an
     * answered one is stored behind [ANSWERED_PREFIX] and nothing offers to undo it.
     */
    fun restore(key: String)
}
