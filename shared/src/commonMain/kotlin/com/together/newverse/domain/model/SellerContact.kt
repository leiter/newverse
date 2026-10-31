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
    /**
     * Whether this row is worth showing a buyer.
     *
     * Blank is the ordinary case — an editor row the seller started and abandoned.
     * [ContactChannel.LOCATION] adds a second one: a pair that does not parse, or
     * sits off the globe, is a pin that would drop the buyer in the ocean, so it is
     * withheld exactly like a blank number rather than offered and apologised for.
     */
    val isUsable: Boolean
        get() = value.isNotBlank() && (channel != ContactChannel.LOCATION || latLng != null)

    /**
     * The coordinates this contact carries, or null when it is not a location or
     * does not parse. Reading the stored string rather than extra fields keeps every
     * channel one row of `{channel, value, label}` — see [ContactChannel.LOCATION].
     */
    val latLng: LatLng? get() = LatLng.parse(value)

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
    INSTAGRAM("https://instagram.com/"),

    /**
     * Where the stall actually stands, as `latitude,longitude`.
     *
     * A web map link rather than the `geo:` URI the coordinates deserve: `geo:` is
     * the standard and Android honours it, but iOS does not register a handler for
     * it, so a seller's pin would simply do nothing on half the installs. The https
     * form opens the maps app on Android, Apple Maps or Safari on iOS, and a browser
     * on anything else — the one shape that works everywhere this app runs.
     *
     * The pair lives in the same `value` string as every other channel's, so a
     * location is stored, validated and rendered as one more contact row and needs
     * no schema change, no rules change and no second code path.
     */
    LOCATION("https://www.google.com/maps/search/?api=1&query=");

    /**
     * Strip what the scheme cannot carry. Applied when the seller saves, so the
     * stored value is already dialable, and again on read so older records behave.
     */
    fun normalize(raw: String): String = when (this) {
        PHONE, WHATSAPP, SIGNAL -> raw.filter { it.isDigit() || it == '+' }
        EMAIL -> raw.trim()
        WEB -> raw.trim().removePrefix("https://").removePrefix("http://")
        INSTAGRAM -> raw.trim().removePrefix("@")
        // Kept as text rather than round-tripped through Double: re-emitting a
        // parsed number can produce "1.0E-5", which no map will read back. Strips
        // whatever a keyboard or a paste added around the two figures -- degree
        // signs, spaces, N/E letters -- and leaves the digits the seller meant.
        LOCATION -> raw.split(',')
            .let { if (it.size < 2) return@let emptyList<String>() else it.take(2) }
            .map { part -> part.trim().filter { it.isDigit() || it == '-' || it == '.' } }
            .let { parts -> if (parts.size == 2) parts.joinToString(",") else "" }
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

/**
 * A point on the globe, as a [SellerContact] of channel [ContactChannel.LOCATION]
 * carries it.
 *
 * Parsing is total and never throws: the value is whatever a seller typed into two
 * text fields, and a half-finished pair is an ordinary state during editing, not an
 * error. Out-of-range figures are rejected rather than clamped — a latitude of 95 is
 * a typo, and clamping it to 90 would send the buyer somewhere confidently wrong
 * instead of showing nothing.
 */
data class LatLng(val latitude: Double, val longitude: Double) {

    /** The stored form, which is also what [ContactChannel.LOCATION] appends to its scheme. */
    fun stored(): String = "$latitude,$longitude"

    companion object {
        /** Null unless [raw] holds two in-range figures separated by a comma. */
        fun parse(raw: String): LatLng? {
            val parts = raw.split(',')
            if (parts.size != 2) return null
            val lat = parts[0].trim().toDoubleOrNull() ?: return null
            val lng = parts[1].trim().toDoubleOrNull() ?: return null
            if (lat.isNaN() || lng.isNaN()) return null
            if (lat < -90.0 || lat > 90.0) return null
            if (lng < -180.0 || lng > 180.0) return null
            return LatLng(lat, lng)
        }
    }
}
