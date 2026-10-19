package com.together.newverse.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * A day on which a market does not take place: a public holiday, the seller's
 * vacation, a cancelled stall.
 *
 * Closed days are filtered out of the pickup dates offered to a buyer. See
 * `doc/market-pricing-and-access-design.md`.
 *
 * [marketId] empty means **every** market — the seller is away entirely. A named
 * market closes only that stall. The same convention as a contact without a
 * market belonging to the business rather than a stall.
 */
data class MarketClosure(
    /** `yyyyMMdd`, the key format `/orders` already uses: no timezone, sorts, readable. */
    val date: String = "",
    val marketId: String = "",
    /** Shown to the buyer when a closed day is explained: "Feiertag", "Urlaub". */
    val reason: String = "",
) {
    val isSellerWide: Boolean get() = marketId.isEmpty()

    fun appliesTo(marketId: String?): Boolean =
        isSellerWide || (marketId != null && this.marketId == marketId)

    companion object {
        /** `yyyyMMdd` for a local date, zero-padded. */
        fun dateKey(date: LocalDate): String =
            buildString {
                append(date.year.toString().padStart(4, '0'))
                append(date.monthNumber.toString().padStart(2, '0'))
                append(date.dayOfMonth.toString().padStart(2, '0'))
            }

        fun dateKey(instant: Instant, timeZone: TimeZone): String =
            dateKey(instant.toLocalDateTime(timeZone).date)
    }
}

/**
 * The closed days of one seller, indexed for lookup.
 *
 * Built once and asked per candidate date, because the pickup-date generator tests
 * every candidate it walks past.
 */
class MarketClosures(closures: List<MarketClosure> = emptyList()) {
    private val byDate: Map<String, List<MarketClosure>> = closures
        .filter { it.date.isNotBlank() }
        .groupBy { it.date }

    val isEmpty: Boolean get() = byDate.isEmpty()

    /** Whether [marketId] — or the whole business, when null — is shut on that day. */
    fun isClosed(dateKey: String, marketId: String? = null): Boolean =
        byDate[dateKey]?.any { it.appliesTo(marketId) } == true

    fun isClosed(instant: Instant, timeZone: TimeZone, marketId: String? = null): Boolean =
        isClosed(MarketClosure.dateKey(instant, timeZone), marketId)

    /**
     * Every closed date key that applies to [marketId] — seller-wide closures plus
     * that market's own. Used to hand the reminder worker a flat snapshot it can
     * evaluate offline.
     */
    fun closedCycles(marketId: String? = null): Set<String> =
        byDate.filterValues { closures -> closures.any { it.appliesTo(marketId) } }.keys

    /** Why the day is shut, for the buyer. Empty when nothing was given. */
    fun reasonFor(dateKey: String, marketId: String? = null): String =
        byDate[dateKey]?.firstOrNull { it.appliesTo(marketId) }?.reason.orEmpty()

    companion object {
        val NONE = MarketClosures()
    }
}
