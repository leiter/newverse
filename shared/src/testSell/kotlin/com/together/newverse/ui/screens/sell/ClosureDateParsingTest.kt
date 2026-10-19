package com.together.newverse.ui.screens.sell

import com.together.newverse.domain.model.Market
import com.together.newverse.domain.model.dayOfWeekFor
import kotlinx.datetime.DayOfWeek
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClosureDateParsingTest {

    @Test
    fun `a German date becomes the stored key`() {
        assertEquals("20241023", parseClosureDate("23.10.2024"))
    }

    @Test
    fun `single digits are padded`() {
        assertEquals("20240105", parseClosureDate("5.1.2024"))
    }

    @Test
    fun `slashes and dashes are accepted as separators`() {
        assertEquals("20241023", parseClosureDate("23/10/2024"))
        assertEquals("20241023", parseClosureDate("23-10-2024"))
    }

    @Test
    fun `surrounding spaces do not matter`() {
        assertEquals("20241023", parseClosureDate("  23.10.2024 "))
    }

    // A half-typed date stored as a closure would take a market day away with no
    // way for the seller to see why, so parsing refuses anything doubtful.
    @Test
    fun `nonsense is rejected rather than guessed at`() {
        assertNull(parseClosureDate(""))
        assertNull(parseClosureDate("23.10"))
        assertNull(parseClosureDate("23.13.2024"))
        assertNull(parseClosureDate("32.10.2024"))
        assertNull(parseClosureDate("abc"))
        assertNull(parseClosureDate("23.10.24"))
    }

    @Test
    fun `month lengths are respected`() {
        assertNull(parseClosureDate("31.04.2024"))
        assertEquals("20240430", parseClosureDate("30.04.2024"))
    }

    @Test
    fun `february knows about leap years`() {
        assertEquals("20240229", parseClosureDate("29.02.2024"))
        assertNull(parseClosureDate("29.02.2023"))
        // 2000 is a leap year, 1900 and 2100 are not.
        assertEquals("20000229", parseClosureDate("29.02.2000"))
        assertNull(parseClosureDate("29.02.2100"))
    }

    @Test
    fun `the stored key reads back as it was typed`() {
        assertEquals("23.10.2024", formatClosureDate("20241023"))
        // Anything unexpected is shown as-is rather than sliced into nonsense.
        assertEquals("oops", formatClosureDate("oops"))
    }
}

class ClosureRangeTest {

    private val thursdayMarket = Market(id = "m1", name = "Kreuzberg", dayIndex = 3)   // Thursday
    private val saturdayMarket = Market(id = "m2", name = "Neukölln", dayIndex = 5)    // Saturday
    private val markets = listOf(thursdayMarket, saturdayMarket)

    @Test
    fun `day index maps onto the ISO week`() {
        assertEquals(DayOfWeek.MONDAY, dayOfWeekFor(0))
        assertEquals(DayOfWeek.THURSDAY, dayOfWeekFor(3))
        assertEquals(DayOfWeek.SUNDAY, dayOfWeekFor(6))
        assertNull(dayOfWeekFor(-1))
        assertNull(dayOfWeekFor(7))
    }

    // A fortnight of vacation is a handful of market days, not fourteen records.
    @Test
    fun `a seller-wide range covers every market's own day`() {
        // 2024-10-21 Monday .. 2024-11-03 Sunday: two Thursdays, two Saturdays.
        val keys = expandClosureRange("20241021", "20241103", markets, "", DayOfWeek.THURSDAY)
        assertEquals(listOf("20241024", "20241026", "20241031", "20241102"), keys)
    }

    @Test
    fun `a range scoped to one market covers only that market's day`() {
        val keys = expandClosureRange("20241021", "20241103", markets, "m1", DayOfWeek.THURSDAY)
        assertEquals(listOf("20241024", "20241031"), keys)
    }

    @Test
    fun `with no markets configured the range falls back to the pickup day`() {
        val keys = expandClosureRange("20241021", "20241103", emptyList(), "", DayOfWeek.THURSDAY)
        assertEquals(listOf("20241024", "20241031"), keys)
    }

    @Test
    fun `a range containing no market day yields nothing`() {
        // Monday to Wednesday: no Thursday, no Saturday.
        assertEquals(emptyList(), expandClosureRange("20241021", "20241023", markets, "", DayOfWeek.THURSDAY))
    }

    @Test
    fun `a backwards range yields nothing`() {
        assertEquals(emptyList(), expandClosureRange("20241103", "20241021", markets, "", DayOfWeek.THURSDAY))
    }

    @Test
    fun `a single-day range is that day when it is a market day`() {
        assertEquals(listOf("20241024"), expandClosureRange("20241024", "20241024", markets, "m1", DayOfWeek.THURSDAY))
    }

    @Test
    fun `a mistyped year cannot produce an unbounded list`() {
        // 20241021 to 29991231 would be ~360k days without the guard.
        val keys = expandClosureRange("20241021", "29991231", markets, "m1", DayOfWeek.THURSDAY)
        assertTrue(keys.size <= MAX_RANGE_DAYS / 7 + 1, "unbounded expansion: ${keys.size}")
    }

    @Test
    fun `an unparseable bound yields nothing`() {
        assertEquals(emptyList(), expandClosureRange("nope", "20241103", markets, "", DayOfWeek.THURSDAY))
        assertEquals(emptyList(), expandClosureRange("20241021", "20241332", markets, "", DayOfWeek.THURSDAY))
    }
}

/**
 * Closing a day that already has orders is the case that matters: those buyers
 * would arrive at a market that is not there, and after the edit deadline only the
 * seller can move their orders.
 */
class ClosureAdditionTest {

    private val thursdayMarket = Market(id = "m1", name = "Kreuzberg", dayIndex = 3)
    private val markets = listOf(thursdayMarket)
    private val thursday = "20241024"
    private val nextThursday = "20241031"

    private fun resolve(
        from: String,
        until: String = "",
        marketId: String = "",
        orders: Map<String, Int> = emptyMap(),
    ) = resolveClosureAddition(from, until, markets, marketId, DayOfWeek.THURSDAY, orders)

    @Test
    fun `a clear day is accepted`() {
        assertEquals(ClosureAddition.Days(listOf(thursday)), resolve("24.10.2024"))
    }

    @Test
    fun `a day with orders is refused and the count is reported`() {
        val outcome = resolve("24.10.2024", orders = mapOf(thursday to 14))
        assertEquals(ClosureAddition.HasOrders(thursday, 14), outcome)
    }

    @Test
    fun `a range is refused whole when any day in it has orders`() {
        // 24.10 is clear, 31.10 is not: neither is closed, because closing half a
        // vacation silently would be worse than refusing it.
        val outcome = resolve("21.10.2024", "03.11.2024", orders = mapOf(nextThursday to 2))
        assertEquals(ClosureAddition.HasOrders(nextThursday, 2), outcome)
    }

    @Test
    fun `a cancelled-down-to-zero day does not block`() {
        // The count map only carries live orders; a zero is treated as clear.
        assertEquals(ClosureAddition.Days(listOf(thursday)), resolve("24.10.2024", orders = mapOf(thursday to 0)))
    }

    @Test
    fun `orders on an unrelated day do not block`() {
        assertEquals(
            ClosureAddition.Days(listOf(thursday)),
            resolve("24.10.2024", orders = mapOf("20241017" to 9))
        )
    }

    @Test
    fun `a backwards range is named as such rather than silently empty`() {
        assertEquals(ClosureAddition.Backwards, resolve("03.11.2024", "21.10.2024"))
    }

    @Test
    fun `a range with no market day says so`() {
        assertEquals(ClosureAddition.NoMarketDay, resolve("21.10.2024", "23.10.2024"))
    }

    @Test
    fun `an unparseable date is rejected before anything else`() {
        assertEquals(ClosureAddition.InvalidDate, resolve("nope"))
        assertEquals(ClosureAddition.InvalidDate, resolve("24.10.2024", "nope"))
    }

    // A seller may know their stall is cancelled on a day the schedule does not
    // consider a market day at all, so a single date is taken at face value.
    @Test
    fun `a single non-market day is still accepted`() {
        assertEquals(ClosureAddition.Days(listOf("20241022")), resolve("22.10.2024"))
    }

    @Test
    fun `a range scoped to one market only closes that market's days`() {
        val outcome = resolve("21.10.2024", "03.11.2024", marketId = "m1")
        assertEquals(ClosureAddition.Days(listOf(thursday, nextThursday)), outcome)
    }
}
