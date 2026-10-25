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
 * Which seller announcements this device has been told to stop showing.
 *
 * **Per-install, not per-user** — unlike [DemoOrderStorage] and
 * [PendingInviteTokenStorage], which give every userId its own container. Those hold
 * the buyer's own data, which must not leak between accounts on a shared phone. A
 * dismissed announcement is not the buyer's data; it is this screen's answer to "I
 * have read this". Scoping it per user would bring the banner back when a guest links
 * their account, which is the moment they are least interested in reading it again.
 *
 * Keys are [com.together.newverse.domain.model.SellerUiAction.dismissalKey] — the row
 * id and a hash of the text — so re-wording an announcement brings it back and
 * re-reading the same one does not. Dismissal does not expire: a buyer who has said
 * they are done with a sentence is done with it.
 *
 * Android: SharedPreferences file `newverse_announcements`, iOS: standard NSUserDefaults.
 */
expect class AnnouncementDismissalStorage {
    /** Every key dismissed on this device. Empty, never null, on a fresh install. */
    fun dismissed(): Set<String>

    /** Remember that this key is dismissed. Idempotent. */
    fun dismiss(key: String)
}
