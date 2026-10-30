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
    /**
     * Whether buyers see it at all.
     *
     * The seller's off switch, beside the delete. Both reach a buyer with the app
     * already open — the buy app watches this node — but they mean different things
     * to the seller: a deleted row is gone and has to be retyped, a deactivated one
     * is parked. A wishbox that only makes sense in spring, a link to a list that is
     * out of date for a fortnight: neither is worth losing the wording of.
     *
     * Absent on a row written before the flag existed, which reads as **true**: those
     * rows were being shown, and they go on being shown. The same rule
     * [ActionAudience.stored] applies to a missing audience, and for the same reason.
     *
     * A deactivated row still holds its slot, deliberately — it is parked *there*,
     * and a seller who turns the announcement off for a week does not expect to find
     * something else in its place.
     */
    val isActive: Boolean = true,
    /**
     * Whether acting on it takes it away, or only the buyer closing it does.
     *
     * The seller's call, per row, because both are right for different rows: a
     * one-off survey should go once it is answered, while a standing wishbox should
     * stay so a buyer with a second wish can send it. See [ActionPersistence].
     */
    val persistence: ActionPersistence = ActionPersistence.UNTIL_CANCELLED,
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
        get() = isActive && isComplete

    /**
     * Whether the seller has finished filling this row in — regardless of whether it
     * is switched on.
     *
     * Separate from [isUsable] because the two questions have different askers. A
     * renderer wants to know whether to draw the row, and a row switched off must not
     * be drawn. The seller's editor wants to know whether the row is worth *saving*,
     * and a complete row the seller has just switched off is the whole point of
     * having a switch — filtering the save on [isUsable] would have made deactivating
     * a row impossible to save.
     */
    val isComplete: Boolean
        get() = value.isNotBlank() && kind == location.kind &&
            // An announcement and a message box have no button, so there is nothing
            // for the seller to label and nothing to withhold the row for.
            // [readyToStore] derives the `label` the rules still require from the
            // text itself.
            (label.isNotBlank() || kind.labelIsDerived)

    /**
     * This row as it should be written: the slot's kind, and a label the rules accept.
     *
     * Both derivations live here rather than at either call site so that the editor
     * and the repository cannot disagree about what a saved row looks like. The two
     * disagreeing is how this feature's second bug happened.
     *
     * The kind comes from the slot, never from the caller — an `ANNOUNCE` in a button
     * slot has nothing to open and an `OPEN_URL` in the title slot has no button to be
     * tapped. The label of an announcement or a message box is its opening words,
     * because the rules require a non-empty `label` and asking a market trader to
     * write a sentence *and* name it would be one field too many; it is what the row
     * is recognised by in the seller's own list, and buyers never see it.
     */
    fun readyToStore(): SellerUiAction {
        val storedKind = location.kind
        val storedLabel = when {
            label.isNotBlank() -> label
            storedKind.labelIsDerived -> value.trim().firstWords(MAX_LABEL_LENGTH)
            else -> label
        }
        // A row with nothing to act on cannot hide when acted on, so it is not stored
        // claiming it will. Settled here rather than refused, because the seller never
        // chose it: the kind did, and an announcement whose picker was never shown
        // should not carry whatever the field happened to default to.
        val storedPersistence =
            if (storedKind.hasAction) persistence else ActionPersistence.UNTIL_CANCELLED
        return copy(kind = storedKind, label = storedLabel, persistence = storedPersistence)
    }

    /**
     * Whether this row should be hidden now that the buyer has acted on it — tapped
     * the button, sent the message.
     *
     * Asked at the moment of the action rather than decided in the renderer, so the
     * one place that knows the act succeeded is the one place that hides the row.
     */
    val hidesAfterUse: Boolean
        get() = kind.hasAction && persistence == ActionPersistence.HIDE_ON_ACTION

    /**
     * What a buyer's dismissal of this row is remembered by.
     *
     * The id *and* a hash of the text, deliberately. Keyed on the id alone, a seller
     * who corrects the wording of an announcement in place would never reach a buyer
     * who had dismissed the previous one — the silent-stale-configuration failure this
     * feature has already been bitten by twice. Keyed on the text alone, two sellers'
     * identical announcements would share one dismissal. Together: a re-read of the
     * same words stays gone, and new words come back.
     *
     * The same key carries a dismissed message box. A buyer who has closed "Was fehlt
     * euch?" has said they do not want to be asked; the seller asking a *different*
     * question is a new question, and should reach them.
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
 * The enum exists so the stored record names a behaviour the buy app already
 * implements, rather than carrying a URI the app would have to trust: adding `CALL` or
 * `OPEN_SCREEN` later is a new entry here and a new branch where actions are rendered,
 * not a change to what sellers may store.
 *
 * Values are stored by [name]. An unknown name read back is skipped rather than
 * crashed on, so a record written by a newer app version simply does not appear in an
 * older one — the same degradation as [ContactChannel] and `storedOrderOrigin`.
 */
enum class ActionKind(
    val scheme: String,
    /**
     * Whether the seller is spared the `label` field because it can be taken from
     * [SellerUiAction.value].
     *
     * True for the kinds that have no button to label — an announcement and a message
     * box both render the seller's own text, and the stored `label` exists only so
     * the rules' non-empty check holds and the row is recognisable in the seller's own
     * list. See [SellerUiAction.readyToStore].
     */
    val labelIsDerived: Boolean = false,
    /**
     * Whether a buyer can *do* something with this row, as opposed to only read it.
     *
     * Tapping a button and sending a message are acts; an announcement is not acted
     * on, only read and closed. What this decides is whether
     * [ActionPersistence.HIDE_ON_ACTION] means anything for the row — see
     * [SellerUiAction.readyToStore], which will not store a persistence the kind
     * cannot honour.
     */
    val hasAction: Boolean = true,
) {
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
    ANNOUNCE("", labelIsDerived = true, hasAction = false),

    /**
     * A one-line text field with a send button, whose [SellerUiAction.value] is the
     * question the buyer is answering — "Was fehlt euch im Angebot?".
     *
     * What the buyer types goes to `seller_inbox/{sellerId}`, never to a URI handler,
     * so this kind has an empty scheme and an identity [normalize] for the reason
     * [ANNOUNCE] does, and the rules let its value carry a colon for the same reason:
     * the question is a sentence.
     *
     * The *kind* is what the seller is asking for, and it travels with every message
     * sent through this box — see [SellerInboxMessage.requestKind]. A later
     * `FEEDBACK` or `RATING` box is a new entry here and a new branch where the input
     * is rendered; the stored messages then say which box each one came from without
     * the seller having to guess from the wording.
     */
    QUICK_MESSAGE("", labelIsDerived = true);

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
        ANNOUNCE, QUICK_MESSAGE -> raw.trim()
    }

    companion object {
        /** The stored name, or null when written by a version that knows more kinds. */
        fun fromStored(raw: String?): ActionKind? = entries.firstOrNull { it.name == raw }
    }
}

/**
 * Where a published row appears — a button slot, the title, or a message box.
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
    TOPBAR_HOME_TITLE(ActionKind.ANNOUNCE, maxActions = 1),

    /**
     * The home screen's title, replaced by a message box the seller has published —
     * a question, a field and a send button.
     *
     * Takes the title slot rather than sitting among the actions, for the reason
     * [TOPBAR_HOME_TITLE] does: a text field the buyer is meant to type a sentence
     * into needs the width, and a field squeezed beside the app name fits nothing
     * worth sending. Not a button slot, so not reachable through [forLocation] —
     * read it with [promptFor].
     *
     * One per slot, like the announcement, and for a sharper reason: two questions in
     * one field cannot both be answered, and a buyer's answer would not say which it
     * replied to.
     */
    TOPBAR_HOME_INPUT(ActionKind.QUICK_MESSAGE, maxActions = 1),

    /**
     * The profile screen's title, replaced by a message box.
     *
     * The second input slot exists because the home screen's title is contended —
     * an announcement and a question cannot both have it — while the profile screen
     * has a title the buyer is not acting on. A seller running both puts the
     * announcement on home and the wishbox on profile.
     */
    TOPBAR_PROFILE_INPUT(ActionKind.QUICK_MESSAGE, maxActions = 1);

    /** Whether this slot renders a message box rather than a button or a title. */
    val isInput: Boolean get() = kind == ActionKind.QUICK_MESSAGE

    /**
     * Whether this slot draws buttons, as opposed to replacing a title.
     *
     * [forLocation] returns nothing for a slot where this is false, so a title or a
     * message box cannot be rendered as a button even by a caller that asks for one.
     * Convention used to carry that — the title slot was simply never passed to
     * `forLocation` — and a convention is one careless call site away from drawing an
     * announcement as a button nobody can tap.
     */
    val isButtonSlot: Boolean get() = kind == ActionKind.OPEN_URL

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
 * What makes a published row go away for one buyer.
 *
 * Both options exist because the seller's rows are not all the same kind of thing,
 * and the app cannot tell which from the outside. A one-off question — "kommt ihr am
 * Samstag?" — is answered once and should then be gone; a standing wishbox should
 * stay, because a buyer who sends one wish this week has another next week. Guessing
 * either way is wrong for half the rows.
 *
 * Stored by [name] and **optional**, like [ActionAudience]: absent reads as
 * [UNTIL_CANCELLED], which is what every row written before the field existed was
 * doing. An unknown name reads as null and the row is skipped, for the reason an
 * unknown audience skips it — a row whose disappearing condition this app version
 * does not understand is one it cannot render honestly.
 */
enum class ActionPersistence {
    /**
     * Stays until the buyer closes it, and then stays closed.
     *
     * The default, and the safe one: the worst it does is leave something on screen
     * that the buyer can remove themselves.
     */
    UNTIL_CANCELLED,

    /**
     * Goes once the buyer has acted on it — tapped the button, sent the message.
     *
     * Remembered the same way a closed row is, through
     * [SellerUiAction.dismissalKey], so it is the *same* row that stays gone: the
     * seller rewording the question asks again, which is almost always what they
     * meant by rewording it.
     *
     * Only honoured for a kind that can be acted on. See [ActionKind.hasAction].
     */
    HIDE_ON_ACTION;

    companion object {
        /** Absent means [UNTIL_CANCELLED]; an unknown name means null, and the row is skipped. */
        fun stored(raw: String?): ActionPersistence? =
            if (raw == null) UNTIL_CANCELLED else entries.firstOrNull { it.name == raw }
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
): List<SellerUiAction> {
    // Nothing is ever drawn as a button in a slot that has no buttons. See
    // [ActionLocation.isButtonSlot] for why this is checked rather than trusted.
    if (!location.isButtonSlot) return emptyList()
    return filter { it.location == location && it.isUsable && it.audience.includes(isApproved, isDemo) }
        .sortedBy { it.label.lowercase() }
        .take(SellerUiAction.MAX_PER_LOCATION)
}

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
): SellerUiAction? = singleFor(location, isApproved, isDemo)

/**
 * The one message box for a slot, or null.
 *
 * The same resolution as [announcementFor] — these are the two slots that hold
 * exactly one row — under its own name, because the two are read in different places
 * and a call site asking for a prompt should say so.
 */
fun List<SellerUiAction>.promptFor(
    location: ActionLocation,
    isApproved: Boolean,
    isDemo: Boolean,
): SellerUiAction? = singleFor(location, isApproved, isDemo)

/**
 * The winner for a slot that holds exactly one row.
 *
 * Picked by id rather than by label so that a seller who has somehow published two —
 * by hand, or from two devices — gets the same one on every buyer's phone and on
 * every launch, instead of whichever RTDB iterated first.
 */
private fun List<SellerUiAction>.singleFor(
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
