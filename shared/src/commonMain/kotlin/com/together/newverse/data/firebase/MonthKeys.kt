package com.together.newverse.data.firebase

import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * "yyyyMM" node keys, in the seller's time zone.
 *
 * Both seller-side ledgers group their records by month — sales under
 * `/sales/{sellerId}/{yyyyMM}` and stock movements under
 * `/stock_movements/{sellerId}/{yyyyMM}` — so a monthly export or a period read
 * touches as few nodes as possible. The month is local, not UTC: a sale booked at
 * 23:30 on the 30th belongs to that month for the seller, whatever UTC says.
 */
internal object MonthKeys {

    /** The month an instant falls in, "yyyyMM". */
    fun of(epochMillis: Long, timeZone: TimeZone): String {
        val date = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(timeZone).date
        return "${date.year}${date.month.number.toString().padStart(2, '0')}"
    }

    /**
     * Every month touched by the period [fromMillis, untilMillis) — the month nodes
     * to read for a week or month export. A week can span two months.
     */
    fun range(fromMillis: Long, untilMillis: Long, timeZone: TimeZone): List<String> {
        require(untilMillis > fromMillis) { "Empty period" }
        val first = of(fromMillis, timeZone)
        val last = of(untilMillis - 1, timeZone)
        val keys = mutableListOf<String>()
        var year = first.take(4).toInt()
        var month = first.takeLast(2).toInt()
        while (true) {
            val key = "$year${month.toString().padStart(2, '0')}"
            keys += key
            if (key == last) return keys
            month++
            if (month > 12) { month = 1; year++ }
        }
    }
}
