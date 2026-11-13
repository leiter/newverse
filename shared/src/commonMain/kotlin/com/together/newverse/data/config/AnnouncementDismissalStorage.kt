package com.together.newverse.data.config

/**
 * How many keys one device keeps.
 *
 * Bounded because a seller publishing a fresh announcement every week would otherwise
 * grow this set forever. The oldest are dropped first.
 *
 * **What eviction costs is no longer nothing.** While a key could only mean "dismissed
 * for good", losing the oldest was harmless — the row it named had been deleted by the
 * seller long before. Now a plain key means *collapsed*, and a collapsed row is
 * usually still published: evicting its key puts it back in the title, where the buyer
 * has to collapse it again. So this is a **soft** guarantee. Collapse is sticky, not
 * permanent, and a buyer who wants a row gone for good long-presses its icon, which
 * stores an [ANSWERED_PREFIX] key that eviction treats no differently but which is
 * only reached deliberately.
 *
 * 50 is chosen against one seller publishing a handful of rows: reaching it means
 * fifty distinct wordings on one device, which is years of weekly announcements. If a
 * seller ever churns rows fast enough to matter, the fix is to evict *collapsed* keys
 * before answered ones rather than to raise the number — an answered row is a promise
 * to the seller, a collapsed one is a convenience for the buyer.
 *
 * A top-level constant rather than a member of the expect class, so each platform
 * implementation does not have to restate a companion object to get at it.
 */
const val MAX_DISMISSAL_KEYS = 50

/**
 * How many times a buyer is told that long-pressing a collapsed row's icon hides it
 * for good.
 *
 * Shown on the first few collapses and then never again. A gesture with no visible
 * affordance is a gesture nobody finds, and the alternative — a permanent "hide
 * forever" button beside every collapsed icon — would put the destructive option back
 * one stray tap away, which is the problem collapsing was introduced to fix.
 *
 * Five rather than once: the first collapse is also the moment the buyer is working out
 * what just happened to the announcement, and a hint read while surprised is a hint
 * unread.
 */
const val MAX_COLLAPSE_HINTS = 5

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

    /** How many times the long-press hint has been shown on this device. */
    fun collapseHintsShown(): Int

    /** Remember that the long-press hint has been shown once more. */
    fun noteCollapseHintShown()
}
