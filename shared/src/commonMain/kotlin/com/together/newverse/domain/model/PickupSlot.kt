package com.together.newverse.domain.model

import com.together.newverse.domain.config.OrderScheduleConfig
import com.together.newverse.util.OrderDateUtils
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * A market and one of its pickup dates. Never chosen separately.
 *
 * Once each market runs on its own day, the market decides which dates exist, so
 * offering the two as independent choices would let a buyer pick Neukölln and then
 * a Thursday Neukölln does not have. See `doc/guides/market-pricing-and-access-design.md`
 * §4.
 */
data class PickupSlot(
    val marketId: String,
    /** Epoch millis of the pickup day, as `Order.pickUpDate` stores it. */
    val date: Long,
)

object PickupSlots {

    /**
     * The next [count] slots across [markets], in date order.
     *
     * Each market contributes the next occurrences of its own day, minus the days
     * the seller has closed and minus anything already past its edit deadline. The
     * lists are then merged chronologically, so the buyer reads one list of "where
     * and when" rather than one list per stall.
     *
     * A market with no day set contributes nothing: there is no schedule to derive
     * occurrences from, and guessing one would offer a date the stall does not keep.
     */
    fun next(
        markets: List<Market>,
        closures: MarketClosures = MarketClosures.NONE,
        count: Int = 5,
        now: Instant = Clock.System.now(),
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
        defaultConfig: OrderScheduleConfig? = null,
        perMarket: Boolean = false,
    ): List<PickupSlot> {
        val open = markets.filter { it.active && it.pickupDay != null }
        if (open.isEmpty()) return emptyList()

        return open
            .flatMap { market ->
                OrderDateUtils.getAvailablePickupDates(
                    count = count,
                    now = now,
                    timeZone = timeZone,
                    config = defaultConfig?.let { market.scheduleConfig(it) }
                        ?: OrderDateUtils.marketConfig(market),
                    closures = closures,
                    marketId = market.id
                ).map { PickupSlot(market.id, it.toEpochMilliseconds()) }
            }
            // Ties are possible only when two markets share a day; ordering them by
            // id after the date keeps the list stable between reads.
            .sortedWith(compareBy({ it.date }, { it.marketId }))
            .let { if (perMarket) it else it.take(count) }
    }

    /**
     * The next open slot of **each** market, in date order — the slots a buyer may
     * actually order for.
     *
     * One per market, never a run of future weeks: the seller cannot promise a
     * catalogue or a price weeks ahead, and an order snapshots both at the moment it
     * is placed. A buyer who misses this week orders next week. See
     * `doc/guides/ordering-business-rules.md` §2.
     *
     * A market whose next day is closed contributes its following open day rather
     * than dropping out, so closing one Thursday moves that stall's slot instead of
     * hiding the stall.
     */
    fun nextPerMarket(
        markets: List<Market>,
        closures: MarketClosures = MarketClosures.NONE,
        now: Instant = Clock.System.now(),
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
        defaultConfig: OrderScheduleConfig? = null,
    ): List<PickupSlot> = next(
        markets = markets,
        closures = closures,
        count = 1,
        now = now,
        timeZone = timeZone,
        defaultConfig = defaultConfig,
        // next() truncates the merged list to [count] so that a request for five
        // slots means five; here [count] is per market and every market keeps its
        // one, however many stalls there are.
        perMarket = true,
    )

    /**
     * Whether [slot] can still be ordered for: its market still runs, the day is not
     * closed, and its own deadline has not passed.
     *
     * Checked as well as generated, because a draft basket can hold a slot chosen
     * days ago — before the seller closed that day or retired that stall.
     */
    fun isValid(
        slot: PickupSlot,
        markets: List<Market>,
        closures: MarketClosures = MarketClosures.NONE,
        now: Instant = Clock.System.now(),
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): Boolean {
        val market = markets.firstOrNull { it.id == slot.marketId && it.active } ?: return false
        return OrderDateUtils.isPickupDateValid(
            pickupDate = Instant.fromEpochMilliseconds(slot.date),
            now = now,
            timeZone = timeZone,
            config = OrderDateUtils.marketConfig(market),
            closures = closures,
            marketId = market.id
        )
    }
}
