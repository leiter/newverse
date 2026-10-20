package com.together.newverse.domain.model

/**
 * A way to reach the seller, as the seller entered it.
 *
 * Contacts live on the thing they describe — [SellerProfile.contacts] for the
 * business, [Market.contacts] for a single stall — and are edited there. There is
 * no contact node and no market id on the record: the owner is whatever holds the
 * list. See `doc/guides/seller-contact-design.md`.
 */
data class SellerContact(
    val channel: ContactChannel = ContactChannel.PHONE,
    /** The bare number, address or handle. Never a full URI: [ContactChannel] supplies the scheme. */
    val value: String = "",
    /** Optional override for the menu entry, e.g. "Bestellhotline". Empty means the channel's own label. */
    val label: String = "",
) {
    val isUsable: Boolean get() = value.isNotBlank()

    /**
     * The URI handed to the platform's URI handler.
     *
     * The scheme comes from [channel], never from stored data: a seller who could
     * store a raw URI could make the buy app open an arbitrary scheme.
     */
    fun toUri(): String = channel.scheme + channel.normalize(value)
}

/**
 * The kinds of contact a seller can offer. The channel decides the scheme the buy
 * app opens and the icon it draws.
 *
 * Values are stored by [name]. An unknown name read back from the database is
 * skipped rather than crashed on, so a record written by a newer app version simply
 * does not appear in an older one — the same degradation as `storedOrderOrigin`.
 */
enum class ContactChannel(val scheme: String) {
    PHONE("tel:"),
    EMAIL("mailto:"),
    WHATSAPP("https://wa.me/"),
    SIGNAL("https://signal.me/#p/"),
    WEB("https://"),
    INSTAGRAM("https://instagram.com/");

    /**
     * Strip what the scheme cannot carry. Applied when the seller saves, so the
     * stored value is already dialable, and again on read so older records behave.
     */
    fun normalize(raw: String): String = when (this) {
        PHONE, WHATSAPP, SIGNAL -> raw.filter { it.isDigit() || it == '+' }
        EMAIL -> raw.trim()
        WEB -> raw.trim().removePrefix("https://").removePrefix("http://")
        INSTAGRAM -> raw.trim().removePrefix("@")
    }

    companion object {
        /** The stored name, or null when it was written by a version that knows more channels. */
        fun fromStored(raw: String?): ContactChannel? =
            entries.firstOrNull { it.name == raw }
    }
}

/**
 * Everything a buyer may be shown about how to reach one seller: the business's own
 * contacts, plus each market's.
 *
 * Kept separate from [SellerProfile] because a buyer may not read that node — only
 * its individual fields — so this is what the buy app actually loads.
 */
data class SellerContactDirectory(
    val sellerContacts: List<SellerContact> = emptyList(),
    val marketContacts: Map<String, List<SellerContact>> = emptyMap(),
    val marketNames: Map<String, String> = emptyMap(),
) {
    val isEmpty: Boolean
        get() = sellerContacts.none { it.isUsable } && marketContacts.values.none { list -> list.any { it.isUsable } }

    /**
     * What the top bar shows: the business's contacts plus one market's, with
     * duplicates collapsed.
     *
     * Union rather than override — a stall mobile answered only on market day adds
     * to the office line, it does not replace it. Deduplicating on channel and value
     * means a seller who repeats the same number on a market is not punished with a
     * doubled row.
     */
    fun resolveFor(marketId: String?): List<SellerContact> {
        val market = marketId?.let { marketContacts[it] }.orEmpty()
        return (sellerContacts + market)
            .filter { it.isUsable }
            .distinctBy { it.channel to it.channel.normalize(it.value) }
    }
}

/**
 * The seller's own view of their contacts, shaped the way a buyer would read them.
 * Used when the seller profile is already in hand, so the two fields need not be
 * fetched again.
 */
fun SellerProfile.toContactDirectory(): SellerContactDirectory = SellerContactDirectory(
    sellerContacts = contacts,
    marketContacts = markets.filter { it.contacts.isNotEmpty() }.associate { it.id to it.contacts },
    marketNames = markets.associate { it.id to it.name }
)
