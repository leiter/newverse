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
    /** A button with nothing to say or nowhere to go is not shown. */
    val isUsable: Boolean get() = label.isNotBlank() && value.isNotBlank()

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
    OPEN_URL("https://");

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
enum class ActionLocation {
    TOPBAR_HOME,
    TOPBAR_PROFILE;

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
