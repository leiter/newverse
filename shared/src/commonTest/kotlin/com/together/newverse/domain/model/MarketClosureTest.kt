package com.together.newverse.domain.model

import com.together.newverse.util.OrderDateUtils
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class MarketClosureTest {

    private val utc = TimeZone.UTC

    // 2024-01-08 is a Monday, so the next Thursday pickup is the 11th.
    private val monday = Instant.parse("2024-01-08T09:00:00Z")

    @Test
    fun `a date key is zero padded`() {
        assertEquals("20240105", MarketClosure.dateKey(LocalDate(2024, 1, 5)))
        assertEquals("20241231", MarketClosure.dateKey(LocalDate(2024, 12, 31)))
    }

    @Test
    fun `a closure without a market closes every market`() {
        val closures = MarketClosures(listOf(MarketClosure("20240111", reason = "Urlaub")))
        assertTrue(closures.isClosed("20240111"))
        assertTrue(closures.isClosed("20240111", "m1"))
        assertTrue(closures.isClosed("20240111", "m2"))
    }

    @Test
    fun `a closure naming a market leaves the others open`() {
        val closures = MarketClosures(listOf(MarketClosure("20240111", "m1", "Feiertag")))
        assertTrue(closures.isClosed("20240111", "m1"))
        assertFalse(closures.isClosed("20240111", "m2"))
        // Asking about no particular market asks about the business as a whole,
        // which is not shut just because one stall is.
        assertFalse(closures.isClosed("20240111"))
    }

    @Test
    fun `the reason is carried through for the buyer`() {
        val closures = MarketClosures(listOf(MarketClosure("20240111", "m1", "Feiertag")))
        assertEquals("Feiertag", closures.reasonFor("20240111", "m1"))
        assertEquals("", closures.reasonFor("20240118", "m1"))
    }

    @Test
    fun `a blank date is ignored rather than matching everything`() {
        assertTrue(MarketClosures(listOf(MarketClosure("", reason = "oops"))).isEmpty)
    }

    // ===== Effect on the dates a buyer is offered =====

    @Test
    fun `a closed day is left out of the offered dates`() {
        val open = OrderDateUtils.getAvailablePickupDates(count = 3, now = monday, timeZone = utc)
        assertEquals(3, open.size)

        val firstKey = MarketClosure.dateKey(open.first(), utc)
        val filtered = OrderDateUtils.getAvailablePickupDates(
            count = 3,
            now = monday,
            timeZone = utc,
            closures = MarketClosures(listOf(MarketClosure(firstKey)))
        )

        assertEquals(3, filtered.size)
        assertFalse(filtered.contains(open.first()))
        // The gap is filled from further out rather than shortening the list.
        assertEquals(open.drop(1), filtered.take(2))
    }

    @Test
    fun `a closure for another market does not shorten this market's list`() {
        val open = OrderDateUtils.getAvailablePickupDates(count = 3, now = monday, timeZone = utc)
        val closures = MarketClosures(listOf(MarketClosure(MarketClosure.dateKey(open.first(), utc), "m1")))

        assertEquals(open, OrderDateUtils.getAvailablePickupDates(
            count = 3, now = monday, timeZone = utc, closures = closures, marketId = "m2"
        ))
    }

    @Test
    fun `closing every candidate returns an empty list instead of hanging`() {
        // Every pickup day inside the search horizon is shut. Before the horizon
        // existed this spun forever.
        val everyThursday = (0 until OrderDateUtils.MAX_PICKUP_CANDIDATES + 5).map { week ->
            MarketClosure(
                MarketClosure.dateKey(
                    Instant.parse("2024-01-11T00:00:00Z").plus(
                        kotlin.time.Duration.parse("${week * 7}d")
                    ),
                    utc
                )
            )
        }

        val dates = OrderDateUtils.getAvailablePickupDates(
            count = 5, now = monday, timeZone = utc, closures = MarketClosures(everyThursday)
        )
        assertTrue(dates.isEmpty(), "expected no dates, got $dates")
    }

    @Test
    fun `a date chosen before the day was closed stops being valid`() {
        val chosen = OrderDateUtils.getAvailablePickupDates(count = 1, now = monday, timeZone = utc).first()
        assertTrue(OrderDateUtils.isPickupDateValid(chosen, monday, utc))

        val closures = MarketClosures(listOf(MarketClosure(MarketClosure.dateKey(chosen, utc))))
        assertFalse(OrderDateUtils.isPickupDateValid(chosen, monday, utc, closures = closures))
    }
}
