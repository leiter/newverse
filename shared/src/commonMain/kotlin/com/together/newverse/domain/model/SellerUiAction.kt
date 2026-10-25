package com.together.newverse.domain.model

/**
 * A button the seller publishes into the buy app, as the seller entered it.
 *
 * Stored at `seller_ui_actions/{sellerId}/{actionId}`: the seller writes, every
 * signed-in buyer reads. Its own node rather than a field on `seller_profile`, for the
 * reason [Market] has one — a thing with rules of its own needs somewhere to hang them
 * — and because the profile's `$field` read map would publish it to buyers either way.
 *
 * Deliberately *not* carried on `seller_events`. That log reads `auth.uid === $sellerId`,
 * so buyers cannot read it at all, and it records facts that happened rather than state
 * that is; answering "which buttons exist now" from an append-only log means replaying
 * it with tombstones. A button is current configuration, so it is stored as current
 * configuration and removed by deleting the row.
 *
 * See `doc/guides/seller-ui-actions.md`.
 */
data class SellerUiAction(
    /** The node key. Empty on a row the seller is still filling in. */
    val id: String = "",
    val kind: ActionKind = ActionKind.OPEN_URL,
    /** What the button says, e.g. "Wünsche". The seller's own words, so unlocalized. */
    val label: String = "",
    /**
     * The target, without a scheme — "example.org/wuensche", never
     * "https://example.org/wuensche". [ActionKind] supplies the scheme; see [toUri].
     */
    val value: String = "",
    val location: ActionLocation = ActionLocation.TOPBAR_PROFILE,
    /**
     * Who sees it. [ActionAudience.EVERYONE] unless the seller narrowed it, which is
     * also what an older record with no stored audience reads as.
     */
    val audience: ActionAudience = ActionAudience.EVERYONE,
) {
    /**
     * A button with nothing to say or nowhere to go is not shown, and neither is a
     * row whose [kind] does not belong in its [location].
     *
     * The slot decides the kind — see [ActionLocation.kind]. An `OPEN_URL` in the
     * title slot has no button to be tapped, and an `ANNOUNCE` in a button slot has
     * nothing to open. Both are nonsense rather than a degraded rendering, so both
     * are dropped here as well as refused by the rules.
     */
    val isUsable: Boolean
        get() = value.isNotBlank() && kind == location.kind &&
            // An announcement has no button, so there is nothing for the seller to
            // label and nothing to withhold the row for. [readyToStore] derives the
            // `label` the rules still require from the text itself.
            (label.isNotBlank() || kind == ActionKind.ANNOUNCE)

    /**
     * This row as it should be written: the slot's kind, and a label the rules accept.
     *
     * Both derivations live here rather than at either call site so that the editor
     * and the repository cannot disagree about what a saved row looks like. The two
     * disagreeing is how this feature's second bug happened.
     *
     * The kind comes from the slot, never from the caller — an `ANNOUNCE` in a button
     * slot has nothing to open and an `OPEN_URL` in the title slot has no button to be
     * tapped. The label of an announcement is its opening words, because the rules
     * require a non-empty `label` and asking a market trader to write a sentence *and*
     * name it would be one field too many; it is what the row is recognised by in the
     * seller's own list, and buyers never see it.
     */
    fun readyToStore(): SellerUiAction {
        val storedKind = location.kind
        val storedLabel = when {
            label.isNotBlank() -> label
            storedKind == ActionKind.ANNOUNCE -> value.trim().firstWords(MAX_LABEL_LENGTH)
            else -> label
        }
        return copy(kind = storedKind, label = storedLabel)
    }

    /**
     * What a buyer's dismissal of this row is remembered by.
     *
     * The id *and* a hash of the text, deliberately. Keyed on the id alone, a seller
     * who corrects the wording of an announcement in place would never reach a buyer
     * who had dismissed the previous one — the silent-stale-configuration failure this
     * feature has already been bitten by twice. Keyed on the text alone, two sellers'
     * identical announcements would share one dismissal. Together: a re-read of the
     * same words stays gone, and new words come back.
     */
    val dismissalKey: String get() = "$id:${value.stableHash()}"

    /**
     * The URI handed to the platform's URI handler.
     *
     * The scheme comes from [kind], never from stored data — the same rule as
     * [SellerContact.toUri], and for the same reason. A seller who could store a raw
     * URI could make the buy app open an arbitrary scheme: `intent://` into another
     * installed app, `market://`, or a credential-phishing page wearing the app's own
     * chrome, where the buyer's trust cue is the top bar and not the address. Storing
     * only the remainder means the worst a compromised seller account can do is point
     * at the wrong https page.
     */
    fun toUri(): String = kind.scheme + kind.normalize(value)

    companion object {
        /**
         * How many actions one surface will show.
         *
         * A phone top bar holds a title and about two actions before it starts eating
         * the title. Enforced where actions are saved and where they are rendered,
         * because RTDB rules cannot count children.
         */
        const val MAX_PER_LOCATION = 3

        /** What [label] is allowed to be. Longer than this does not fit a top bar. */
        const val MAX_LABEL_LENGTH = 24

        /** What [value] is allowed to be, matching the rules' own cap. */
        const val MAX_VALUE_LENGTH = 500
    }
}

/**
 * What a published button does.
 *
 * One entry today. The enum exists so the stored record names a behaviour the buy app
 * already implements, rather than carrying a URI the app would have to trust: adding
 * `CALL` or `OPEN_SCREEN` later is a new entry here and a new branch where actions are
 * rendered, not a change to what sellers may store.
 *
 * Values are stored by [name]. An unknown name read back is skipped rather than
 * crashed on, so a record written by a newer app version simply does not appear in an
 * older one — the same degradation as [ContactChannel] and `storedOrderOrigin`.
 */
enum class ActionKind(val scheme: String) {
    OPEN_URL("https://"),

    /**
     * Text the buy app shows, rather than a target it opens.
     *
     * Its scheme is empty and its [normalize] is the identity, because nothing about
     * an announcement is ever handed to a URI handler. That is also why the rules may
     * safely let an `ANNOUNCE` value carry a colon while an `OPEN_URL` value may not:
     * the no-colon check exists to stop a stored scheme reaching `openUri`, and an
     * announcement has no path to `openUri` at all. "Heute ab 9: Bodenschätze" is an
     * ordinary sentence, and running it through the URL normalizer would quietly
     * mangle it.
     */
    ANNOUNCE("");

    /**
     * Strip what the scheme must not carry. Applied when the seller saves, so the
     * stored value is already openable, and again on read so older records behave.
     *
     * `http://` is stripped along with `https://` rather than preserved: this is the
     * one place that decides the scheme, and it decides on the secure one.
     */
    fun normalize(raw: String): String = when (this) {
        OPEN_URL -> raw.trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .trimStart('/')
        // Trimmed only. The seller's own sentence, kept as written.
        ANNOUNCE -> raw.trim()
    }

    companion object {
        /** The stored name, or null when written by a version that knows more kinds. */
        fun fromStored(raw: String?): ActionKind? = entries.firstOrNull { it.name == raw }
    }
}

/**
 * Where a published button appears.
 *
 * One enum naming whole slots, rather than a free-form surface and screen pair. A
 * typo in `"topbar,profileScreen"` either renders nowhere and looks like data loss, or
 * renders everywhere; an unknown enum name is skipped by [fromStored] and the row is
 * simply not shown. Each entry is mapped to a concrete route where actions are
 * rendered — the mapping lives in the UI layer, since a domain model has no business
 * knowing `NavRoutes`.
 */
enum class ActionLocation(
    /**
     * The one [ActionKind] this slot carries, so the seller never has to pick a kind
     * and an impossible pairing cannot be stored. See [SellerUiAction.isUsable].
     */
    val kind: ActionKind,
    /** How many rows this slot will show. */
    val maxActions: Int,
) {
    TOPBAR_HOME(ActionKind.OPEN_URL, SellerUiAction.MAX_PER_LOCATION),
    TOPBAR_PROFILE(ActionKind.OPEN_URL, SellerUiAction.MAX_PER_LOCATION),

    /**
     * The home screen's title, replaced by the seller's announcement while one is
     * published and the buyer has not dismissed it.
     *
     * Not a button slot, and so not reachable through [forLocation] — read it with
     * [announcementFor], which returns at most one. A title is one thing: two of
     * them cannot collapse into an overflow menu the way two buttons can, and
     * concatenating them would make one buyer's dismissal apply to a sentence the
     * seller never wrote. The cap is 1 here, enforced where actions are saved.
     */
    TOPBAR_HOME_TITLE(ActionKind.ANNOUNCE, maxActions = 1);

    companion object {
        /** The stored name, or null when written by a version that knows more slots. */
        fun fromStored(raw: String?): ActionLocation? = entries.firstOrNull { it.name == raw }
    }
}

/**
 * Which buyers a published button is for.
 *
 * The seller's own words for these are "alle", "Demo" and "freigeschaltet". A seller
 * running an onboarding link wants it in front of people who have not been approved
 * yet; a link to this week's list is for the customers who can actually order.
 *
 * Both narrowed audiences wait for the access status to be *known*, never guessing
 * from its default. [BuyerProfile] access loads a moment after the UI does, so
 * deciding on an unloaded status would show a button at launch and take it away
 * immediately — the same flash `canContactSeller` documents and avoids.
 *
 * Stored by [name]; an unknown value reads as null and the row is skipped, like
 * [ActionKind] and [ActionLocation]. An *absent* value is different and reads as
 * [EVERYONE]: records written before audiences existed were shown to everyone, and
 * that is what they should go on doing.
 */
enum class ActionAudience {
    EVERYONE,
    /** Buyers the seller has not approved — the ones still in demo mode. */
    DEMO_ONLY,
    /** Buyers who can actually place a real order. */
    APPROVED_ONLY;

    /**
     * Whether this audience includes the buyer looking at the screen.
     *
     * [isApproved] and [isDemo] are passed separately rather than derived from one
     * another precisely because both are false while the status is still loading,
     * which is what keeps a narrowed button from flashing on at launch.
     */
    fun includes(isApproved: Boolean, isDemo: Boolean): Boolean = when (this) {
        EVERYONE -> true
        DEMO_ONLY -> isDemo
        APPROVED_ONLY -> isApproved
    }

    companion object {
        /** Absent means [EVERYONE]; an unknown name means null, and the row is skipped. */
        fun stored(raw: String?): ActionAudience? =
            if (raw == null) EVERYONE else entries.firstOrNull { it.name == raw }
    }
}

/**
 * The buttons for one slot, in a stable order and already capped.
 *
 * Sorted by label so the order does not depend on RTDB key iteration — two buttons
 * that swap places between launches read as a bug. The cap is applied *after* the
 * audience filter, so a button hidden from this buyer does not use up one of the
 * three places.
 */
fun List<SellerUiAction>.forLocation(
    location: ActionLocation,
    isApproved: Boolean,
    isDemo: Boolean,
): List<SellerUiAction> =
    filter { it.location == location && it.isUsable && it.audience.includes(isApproved, isDemo) }
        .sortedBy { it.label.lowercase() }
        .take(SellerUiAction.MAX_PER_LOCATION)

/**
 * The one announcement for a slot, or null.
 *
 * Audience-filtered like [forLocation], and for the same reason: a narrowed
 * announcement stays absent until the app knows which side of the approval line this
 * buyer is on. The winner is picked by id rather than by label so that a seller who
 * has somehow published two — by hand, or from two devices — gets the same one on
 * every buyer's phone and on every launch, instead of whichever RTDB iterated first.
 */
fun List<SellerUiAction>.announcementFor(
    location: ActionLocation,
    isApproved: Boolean,
    isDemo: Boolean,
): SellerUiAction? =
    filter { it.location == location && it.isUsable && it.audience.includes(isApproved, isDemo) }
        .minByOrNull { it.id }

/**
 * The opening words of a string, within [limit] characters.
 *
 * Cut at a space rather than mid-character, because a seller reading
 * "Heute ab 9 Uhr gibt es f" in their own list of rows would reasonably think
 * something had gone wrong. Falls back to a hard cut for a single word longer than
 * the limit, where there is no boundary to find.
 */
private fun String.firstWords(limit: Int): String {
    if (length <= limit) return this
    val cut = take(limit + 1)
    val lastSpace = cut.lastIndexOf(' ')
    return if (lastSpace > 0) cut.take(lastSpace) else take(limit)
}

/**
 * A short, stable digest of a string — FNV-1a, 32-bit, lowercase hex.
 *
 * Hand-rolled because this has to agree across launches, platforms and app versions,
 * which `String.hashCode()` does not promise, and because it is written into device
 * storage where a changed algorithm would silently un-dismiss every announcement.
 * Not a security property: it keys a UI preference, and a collision costs one buyer
 * one unseen announcement.
 */
internal fun String.stableHash(): String {
    var hash = 2166136261u
    for (char in this) {
        hash = hash xor char.code.toUInt()
        hash *= 16777619u
    }
    return hash.toString(16).padStart(8, '0')
}
