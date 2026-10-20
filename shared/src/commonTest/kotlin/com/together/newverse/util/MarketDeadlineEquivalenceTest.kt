package com.together.newverse.util

import com.together.newverse.domain.model.Market
import com.together.newverse.domain.model.Order
import com.together.newverse.domain.model.OrderStatus
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * A market's own schedule and the app-wide one compute the same edit deadline.
 *
 * This is why `Order.canEdit`, `getEditDeadline` and `getWindowStatus` are already
 * market-correct while passing no config at all, and it is load-bearing: fourteen call
 * sites rely on it, and a design doc once listed them as a bug on the strength of the
 * missing parameter alone.
 *
 * The reason is structural, not coincidental. [OrderDateUtils.calculateEditDeadline]
 * takes only the *lead distance* out of a config — `daysBetween(deadlineDay, pickupDay)`
 * — and subtracts it from the real pickup date. [Market.scheduleConfig] is built to
 * preserve that distance and the cut-off time. So the config decides nothing the pickup
 * date has not already decided.
 *
 * **If these fail, the equivalence is gone and the fourteen call sites have to start
 * passing the market's config.** The likely causes, in order: `scheduleConfig` was
 * changed to pin a weekday instead of a distance; a market gained its own lead time; a
 * market gained its own cut-off hour. See `doc/guides/market-pricing-and-access-design.md`
 * step 3.
 */
class MarketDeadlineEquivalenceTest {

    /** The seller's zone — the one §7.3 says the arithmetic should be pinned to. */
    private val berlin = TimeZone.of("Europe/Berlin")

    /**
     * A full week, Monday 2026-10-26 to Sunday 2026-11-01, indexed the way the market
     * dialog orders its days: `dayIndex` 0 is Monday.
     */
    private val weekByDayIndex: List<Pair<Int, LocalDate>> = listOf(
        0 to LocalDate(2026, 10, 26),
        1 to LocalDate(2026, 10, 27),
        2 to LocalDate(2026, 10, 28),
        3 to LocalDate(2026, 10, 29),
        4 to LocalDate(2026, 10, 30),
        5 to LocalDate(2026, 10, 31),
        6 to LocalDate(2026, 11, 1),
    )

    private fun marketOn(dayIndex: Int) =
        Market(id = "m$dayIndex", name = "Market $dayIndex", dayIndex = dayIndex)

    @Test
    fun `the fixture week really runs Monday to Sunday`() {
        // The whole file is indexed off this, so it is asserted rather than assumed.
        weekByDayIndex.forEach { (dayIndex, date) ->
            assertEquals(
                dayIndex + 1,
                date.dayOfWeek.isoDayNumber,
                "$date is not dayIndex $dayIndex"
            )
        }
    }

    @Test
    fun `every market day yields the same deadline from either config`() {
        weekByDayIndex.forEach { (dayIndex, date) ->
            val pickup = date.atStartOfDayIn(berlin)
            val market = marketOn(dayIndex)

            val fromMarket = OrderDateUtils.calculateEditDeadline(
                pickup, berlin, OrderDateUtils.marketConfig(market)
            )
            val fromDefault = OrderDateUtils.calculateEditDeadline(
                pickup, berlin, OrderDateUtils.scheduleConfigFor(null)
            )

            assertEquals(
                fromDefault,
                fromMarket,
                "deadline diverged for a ${date.dayOfWeek} market — the 14 call sites " +
                    "that pass no config are now wrong"
            )
        }
    }

    @Test
    fun `the deadline is always two days before pickup at 23 59 local`() {
        // The lead distance the default config encodes, Tuesday 23:59 before a Thursday
        // pickup, stated as what it actually is: two days, at that time of day.
        weekByDayIndex.forEach { (dayIndex, date) ->
            val pickup = date.atStartOfDayIn(berlin)
            val deadline = OrderDateUtils
                .calculateEditDeadline(pickup, berlin, OrderDateUtils.marketConfig(marketOn(dayIndex)))
                .toLocalDateTime(berlin)

            assertEquals(date.minus(2, DateTimeUnit.DAY), deadline.date)
            assertEquals(23, deadline.hour)
            assertEquals(59, deadline.minute)
        }
    }

    @Test
    fun `scheduleConfig keeps the lead distance rather than pinning a weekday`() {
        // The mechanism the equivalence rests on, pinned directly: whatever day a market
        // runs, its deadline day sits the same number of days back as the default's does.
        val default = OrderDateUtils.scheduleConfigFor(null)
        val defaultLead =
            (default.pickupDay.isoDayNumber - default.deadlineDay.isoDayNumber + 7) % 7

        weekByDayIndex.forEach { (dayIndex, _) ->
            val config = OrderDateUtils.marketConfig(marketOn(dayIndex))
            val lead = (config.pickupDay.isoDayNumber - config.deadlineDay.isoDayNumber + 7) % 7

            assertEquals(defaultLead, lead, "market $dayIndex changed the lead distance")
            assertEquals(default.deadlineHour, config.deadlineHour)
            assertEquals(default.deadlineMinute, config.deadlineMinute)
            assertEquals(dayIndex + 1, config.pickupDay.isoDayNumber)
        }
    }

    @Test
    fun `a Saturday market is given Thursday, not Tuesday`() {
        // The specific case a design doc claimed was broken. It never was.
        val saturday = LocalDate(2026, 10, 31)
        val deadline = OrderDateUtils
            .calculateEditDeadline(saturday.atStartOfDayIn(berlin), berlin)
            .toLocalDateTime(berlin)

        assertEquals(DayOfWeek.THURSDAY, deadline.date.dayOfWeek)
        assertEquals(LocalDate(2026, 10, 29), deadline.date)
    }

    @Test
    fun `Order agrees with the market-aware computation without being told the market`() {
        weekByDayIndex.forEach { (dayIndex, date) ->
            val pickup = date.atStartOfDayIn(berlin)
            val order = Order(
                marketId = "m$dayIndex",
                pickUpDate = pickup.toEpochMilliseconds(),
                status = OrderStatus.PLACED
            )

            assertEquals(
                OrderDateUtils.calculateEditDeadline(
                    pickup, TimeZone.currentSystemDefault(), OrderDateUtils.marketConfig(marketOn(dayIndex))
                ),
                order.getEditDeadline(),
                "Order.getEditDeadline() diverged from the market's own schedule"
            )
        }
    }

    @Test
    fun `canEdit agrees either side of a Saturday market's deadline`() {
        val saturday = LocalDate(2026, 10, 31).atStartOfDayIn(berlin)
        val order = Order(
            marketId = "m5",
            pickUpDate = saturday.toEpochMilliseconds(),
            status = OrderStatus.PLACED
        )
        val deadline = order.getEditDeadline()

        assertTrue(
            order.canEdit(now = deadline.minus(1.minutes)),
            "editable a minute before its own deadline"
        )
        assertTrue(
            !order.canEdit(now = deadline.plus(1.minutes)),
            "still editable a minute after its own deadline"
        )
    }
}
