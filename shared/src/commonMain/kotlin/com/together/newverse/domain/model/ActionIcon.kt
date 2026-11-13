package com.together.newverse.domain.model

/**
 * Which icon a published row is drawn with, from a set the app already ships.
 *
 * The same discipline [ActionKind.scheme] follows: the stored record names an asset
 * the buy app **has**, rather than carrying something the app would have to trust. A
 * seller who could store a URL, a drawable name or a raw vector could put an
 * arbitrary image in the buy app's top bar — the one piece of chrome a buyer reads as
 * the app's own. Storing a name from a closed list means the worst a compromised
 * seller account can do is pick a misleading icon from a set we chose, which is the
 * same latitude they already have with a misleading label.
 *
 * ### A name, not a number
 *
 * Stored by [name], like every other enum in this file. A number would be a position
 * in a list somebody can renumber: delete the fifth icon, add another later, reuse
 * the five, and every row already stored means something else — with no error and no
 * way to notice. That is the failure [SellerInboxMessage.selections] rejected indices
 * over, and it is no less real for being cosmetic. A name either resolves or it does
 * not; it can never quietly become a different icon.
 *
 * ### An unknown icon degrades, it does not skip the row
 *
 * [ActionKind] and [ActionLocation] skip a row whose stored name they do not know,
 * because unknown *behaviour* cannot be guessed at safely. An icon is cosmetic, so
 * the opposite call is the right one: a button vanishing because a newer app version
 * knew one more icon would be a far worse trade than drawing the default. So
 * [stored] returns null for an absent *and* for an unknown name, and null means "the
 * one this kind would have had anyway" — see `ActionIcon.vector` in the UI layer.
 *
 * ### Adding one
 *
 * A new entry here, a new branch in the resolver, and a seller-facing name. No data
 * migration, because nothing stored refers to an icon by position.
 */
enum class ActionIcon {
    /** A question mark. What a choice picker is drawn with unless the seller says otherwise. */
    HELP,
    INFO,
    CART,
    BASKET,
    STORE,
    LEAF,
    BREAD,
    EGG,
    DRINK,
    CLOCK,
    CALENDAR,
    DELIVERY,
    GIFT,
    STAR,
    HEART,
    MESSAGE,
    LIST,
    WARNING;

    companion object {
        /**
         * The stored name, or null for "use the default".
         *
         * Null for an absent value and null for a name this version does not know —
         * see the note on this class. Both mean the row is drawn with the icon its
         * kind would have had, which is always something.
         */
        fun stored(raw: String?): ActionIcon? =
            if (raw == null) null else entries.firstOrNull { it.name == raw }
    }
}
