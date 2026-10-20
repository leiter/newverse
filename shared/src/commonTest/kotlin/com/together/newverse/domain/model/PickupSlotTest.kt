package com.together.newverse.domain.model

import com.together.newverse.data.config.DefaultOrderScheduleConfig
import com.together.newverse.util.OrderDateUtils
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * A slot is a market and one of its dates together. The reference week is January
 * 2024: Mon 8th, Thu 11th, Sat 13th.
 */
class PickupSlotTest {

    private val utc = TimeZone.UTC
    private val monday = Instant.parse("2024-01-08T09:00:00Z")
    private val default = DefaultOrderScheduleConfig()   // Thursday pickup, Tuesday 23:59

    private val kreuzberg = Market(id = "m1", name = "Kreuzberg", dayIndex = 3)    // Thursday
    private val neukoelln = Market(id = "m2", name = "Neukölln", dayIndex = 5)     // Saturday

    private fun dayOf(slot: PickupSlot) =
        Instant.fromEpochMilliseconds(slot.date).toLocalDateTime(utc).date

    // ===== a market carries its own schedule =====

    @Test
    fun `a market's pickup day comes from its index`() {
        assertEquals(DayOfWeek.THURSDAY, kreuzberg.pickupDay)
        assertEquals(DayOfWeek.SATURDAY, neukoelln.pickupDay)
        assertEquals(null, Market(id = "m3", name = "Unset").pickupDay)
    }

    // The rule is "two days before", not "on a Tuesday": holding the weekday fixed
    // would give a Monday market six days' notice from the same configuration.
    @Test
    fun `the deadline keeps its distance from pickup, not its weekday`() {
        assertEquals(DayOfWeek.TUESDAY, kreuzberg.scheduleConfig(default).deadlineDay)
        assertEquals(DayOfWeek.THURSDAY, neukoelln.scheduleConfig(default).deadlineDay)

        val monMarket = Market(id = "m4", name = "Monday", dayIndex = 0)
        assertEquals(DayOfWeek.SATURDAY, monMarket.scheduleConfig(default).deadlineDay)
    }

    @Test
    fun `the deadline time is carried over unchanged`() {
        val config = neukoelln.scheduleConfig(default)
        assertEquals(default.deadlineHour, config.deadlineHour)
        assertEquals(default.deadlineMinute, config.deadlineMinute)
    }

    @Test
    fun `a market with no day keeps the app default`() {
        val config = Market(id = "m3", name = "Unset").scheduleConfig(default)
        assertEquals(default.pickupDay, config.pickupDay)
        assertEquals(default.deadlineDay, config.deadlineDay)
    }

    // ===== slots merge chronologically =====

    @Test
    fun `two markets interleave by date`() {
        val slots = PickupSlots.next(listOf(kreuzberg, neukoelln), count = 4, now = monday, timeZone = utc)

        assertEquals(listOf("m1", "m2", "m1", "m2"), slots.map { it.marketId })
        assertEquals(
            listOf(DayOfWeek.THURSDAY, DayOfWeek.SATURDAY, DayOfWeek.THURSDAY, DayOfWeek.SATURDAY),
            slots.map { dayOf(it).dayOfWeek }
        )
        assertEquals(slots.map { it.date }.sorted(), slots.map { it.date })
    }

    @Test
    fun `one market gives the same dates the date picker always gave`() {
        val slots = PickupSlots.next(listOf(kreuzberg), count = 3, now = monday, timeZone = utc)
        val dates = OrderDateUtils.getAvailablePickupDates(count = 3, now = monday, timeZone = utc)

        assertEquals(dates.map { it.toEpochMilliseconds() }, slots.map { it.date })
        assertTrue(slots.all { it.marketId == "m1" })
    }

    @Test
    fun `a market with no day set contributes nothing`() {
        val unset = Market(id = "m3", name = "Unset")
        val slots = PickupSlots.next(listOf(kreuzberg, unset), count = 4, now = monday, timeZone = utc)
        assertTrue(slots.none { it.marketId == "m3" })
    }

    @Test
    fun `a deactivated market contributes nothing`() {
        val slots = PickupSlots.next(
            listOf(kreuzberg, neukoelln.copy(active = false)), count = 4, now = monday, timeZone = utc
        )
        assertTrue(slots.none { it.marketId == "m2" })
    }

    @Test
    fun `no markets means no slots`() {
        assertTrue(PickupSlots.next(emptyList(), count = 4, now = monday, timeZone = utc).isEmpty())
    }

    // ===== closures apply per market =====

    @Test
    fun `closing one market's day leaves the other's alone`() {
        val all = PickupSlots.next(listOf(kreuzberg, neukoelln), count = 4, now = monday, timeZone = utc)
        val firstThursday = all.first { it.marketId == "m1" }
        val closures = MarketClosures(
            listOf(MarketClosure(MarketClosure.dateKey(
                Instant.fromEpochMilliseconds(firstThursday.date), utc
            ), "m1"))
        )

        val filtered = PickupSlots.next(
            listOf(kreuzberg, neukoelln), closures = closures, count = 4, now = monday, timeZone = utc
        )

        assertFalse(filtered.contains(firstThursday))
        // Neukölln's Saturday is untouched and still leads the remaining list.
        assertTrue(filtered.any { it.marketId == "m2" })
    }

    @Test
    fun `a seller-wide closure removes that day from every market`() {
        val all = PickupSlots.next(listOf(kreuzberg, neukoelln), count = 6, now = monday, timeZone = utc)
        val keys = all.take(2).map { MarketClosure.dateKey(Instant.fromEpochMilliseconds(it.date), utc) }
        val closures = MarketClosures(keys.map { MarketClosure(it) })

        val filtered = PickupSlots.next(
            listOf(kreuzberg, neukoelln), closures = closures, count = 6, now = monday, timeZone = utc
        )

        assertTrue(filtered.none { slot ->
            MarketClosure.dateKey(Instant.fromEpochMilliseconds(slot.date), utc) in keys
        })
        assertEquals(6, filtered.size)
    }

    // ===== a slot chosen earlier can go stale =====

    @Test
    fun `a slot stays valid while its market runs and its day is open`() {
        val slot = PickupSlots.next(listOf(kreuzberg), count = 1, now = monday, timeZone = utc).single()
        assertTrue(PickupSlots.isValid(slot, listOf(kreuzberg), now = monday, timeZone = utc))
    }

    @Test
    fun `a slot whose market was retired is no longer valid`() {
        val slot = PickupSlots.next(listOf(kreuzberg), count = 1, now = monday, timeZone = utc).single()
        assertFalse(
            PickupSlots.isValid(slot, listOf(kreuzberg.copy(active = false)), now = monday, timeZone = utc)
        )
    }

    @Test
    fun `a slot whose market no longer exists is no longer valid`() {
        val slot = PickupSlots.next(listOf(kreuzberg), count = 1, now = monday, timeZone = utc).single()
        assertFalse(PickupSlots.isValid(slot, listOf(neukoelln), now = monday, timeZone = utc))
    }

    @Test
    fun `a slot whose day has since been closed is no longer valid`() {
        val slot = PickupSlots.next(listOf(kreuzberg), count = 1, now = monday, timeZone = utc).single()
        val closures = MarketClosures(
            listOf(MarketClosure(MarketClosure.dateKey(Instant.fromEpochMilliseconds(slot.date), utc)))
        )
        assertFalse(
            PickupSlots.isValid(slot, listOf(kreuzberg), closures = closures, now = monday, timeZone = utc)
        )
    }

    // A Saturday market's deadline is Thursday, so on Friday its Saturday has gone
    // while a Thursday market is already looking at next week.
    @Test
    fun `each market's deadline is its own`() {
        val friday = Instant.parse("2024-01-12T09:00:00Z")
        val slots = PickupSlots.next(listOf(neukoelln), count = 1, now = friday, timeZone = utc)

        // Not the 13th: that Saturday's deadline passed on Thursday the 11th.
        assertEquals(20, dayOf(slots.single()).dayOfMonth)
    }

    // ===== one slot per market is all a buyer is offered =====

    @Test
    fun `each market offers its next day and nothing beyond it`() {
        val slots = PickupSlots.nextPerMarket(
            listOf(kreuzberg, neukoelln), now = monday, timeZone = utc
        )

        assertEquals(2, slots.size)
        assertEquals(listOf("m1", "m2"), slots.map { it.marketId })
        assertEquals(11, dayOf(slots[0]).dayOfMonth)   // Thu 11th
        assertEquals(13, dayOf(slots[1]).dayOfMonth)   // Sat 13th
    }

    @Test
    fun `a single market offers a single slot`() {
        val slots = PickupSlots.nextPerMarket(listOf(kreuzberg), now = monday, timeZone = utc)

        assertEquals(1, slots.size)
        assertEquals(11, dayOf(slots.single()).dayOfMonth)
    }

    // Every market keeps its slot however many there are: the merged list is not
    // truncated the way an explicit `count` is.
    @Test
    fun `three markets give three slots`() {
        val mitte = Market(id = "m5", name = "Mitte", dayIndex = 2)   // Wednesday
        val slots = PickupSlots.nextPerMarket(
            listOf(kreuzberg, neukoelln, mitte), now = monday, timeZone = utc
        )

        assertEquals(3, slots.size)
        assertEquals(setOf("m1", "m2", "m5"), slots.map { it.marketId }.toSet())
    }

    // Closing a day moves that stall's slot; it does not drop the stall.
    @Test
    fun `a closed next day hands the market its following open day`() {
        val closures = MarketClosures(
            listOf(MarketClosure("20240111", "m1", "Feiertag"))
        )
        val slots = PickupSlots.nextPerMarket(
            listOf(kreuzberg), closures = closures, now = monday, timeZone = utc
        )

        assertEquals(1, slots.size)
        assertEquals(18, dayOf(slots.single()).dayOfMonth)   // Thu 18th, a week on
    }

    @Test
    fun `a market with no day contributes no slot`() {
        val unset = Market(id = "m3", name = "Unset")
        val slots = PickupSlots.nextPerMarket(
            listOf(kreuzberg, unset), now = monday, timeZone = utc
        )

        assertEquals(listOf("m1"), slots.map { it.marketId })
    }
}

/**
 * An order's deadline is already market-correct without being told the market.
 *
 * `calculateEditDeadline` subtracts the config's deadline-to-pickup distance from
 * the order's own pickup date, and `Market.scheduleConfig` preserves that distance.
 * So a Saturday order deadlines on Thursday whether it is asked with the app
 * default or with its market's schedule, and `Order.canEdit` needs no market.
 *
 * This holds only while every market shares one lead time. Give markets their own
 * and these assertions are what will tell you that `Order` must start resolving its
 * market.
 */
class OrderDeadlineIsMarketRelativeTest {

    private val utc = TimeZone.UTC
    private val default = DefaultOrderScheduleConfig()

    private fun bothAgree(pickup: Instant, dayIndex: Int) {
        val market = Market(id = "m", name = "M", dayIndex = dayIndex)
        assertEquals(
            OrderDateUtils.calculateEditDeadline(pickup, utc, default),
            OrderDateUtils.calculateEditDeadline(pickup, utc, market.scheduleConfig(default)),
            "deadline differs for dayIndex $dayIndex"
        )
    }

    @Test
    fun `every weekday's deadline is the same under either config`() {
        // 2024-01-08 is a Monday, so these run Monday through Sunday.
        repeat(7) { offset ->
            val pickup = Instant.parse("2024-01-08T00:00:00Z") + (offset * 24).hours
            bothAgree(pickup, offset)
        }
    }

    @Test
    fun `a Saturday order deadlines on the Thursday before it`() {
        val saturday = Instant.parse("2024-01-13T00:00:00Z")
        val deadline = OrderDateUtils.calculateEditDeadline(saturday, utc, default)
        val local = deadline.toLocalDateTime(utc)

        assertEquals(DayOfWeek.THURSDAY, local.dayOfWeek)
        assertEquals(11, local.dayOfMonth)
    }

    @Test
    fun `canEdit follows the order's own day without being given a market`() {
        val saturday = Instant.parse("2024-01-13T00:00:00Z")
        // Thursday 18:00, before that Saturday's Thursday 23:59 deadline.
        assertTrue(
            OrderDateUtils.canEditOrder(saturday, Instant.parse("2024-01-11T18:00:00Z"), utc)
        )
        // Friday: gone.
        assertFalse(
            OrderDateUtils.canEditOrder(saturday, Instant.parse("2024-01-12T09:00:00Z"), utc)
        )
    }
}
