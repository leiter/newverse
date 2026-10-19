package com.together.newverse.domain.model

import com.together.newverse.domain.config.OrderScheduleConfig
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.isoDayNumber

/**
 * Represents a market location with schedule
 */
data class Market(
    val id: String = "",
    val name: String = "",
    val street: String = "",
    val houseNumber: String = "",
    val city: String = "",
    val zipCode: String = "",
    val dayOfWeek: String = "",
    val begin: String = "",
    val end: String = "",
    val dayIndex: Int = -1,
    /**
     * How to reach *this stall*, set in the market dialog next to its address and
     * hours. Additive to [SellerProfile.contacts], never a replacement for them —
     * see `doc/seller-contact-design.md`.
     */
    val contacts: List<SellerContact> = emptyList(),
    /**
     * Whether the market still runs.
     *
     * Markets are deactivated, never deleted: orders reference a market by id and
     * historical orders have to stay readable. A deactivated market keeps its row
     * so `Order.marketId` resolves, and simply stops being offered.
     */
    val active: Boolean = true,
    /**
     * Why this market's prices differ, if they do. Empty means they do not.
     *
     * Stored on the market rather than in a node of its own: the market is already
     * the thing a condition belongs to, and is already readable by any signed-in
     * user, so a buyer can price the catalogue before being approved.
     */
    val priceConditions: List<PriceCondition> = emptyList(),
    /** Bumped whenever [priceConditions] change; snapshotted into each order. */
    val pricingRevision: Long = 0L,
) {
    /** This market's pricing, in the shape [resolvePrice] takes. */
    val pricing: MarketPricing
        get() = MarketPricing(marketId = id, revision = pricingRevision, conditions = priceConditions)

    /**
     * The day this market runs, or null when the seller never set one.
     *
     * [dayIndex] counts Monday as 0, which is the order the market dialog lists the
     * days in. Both live databases stored Sunday=1..Saturday=7 until the migration
     * repaired them — see `doc/market-pricing-and-access-design.md` §1.
     */
    val pickupDay: DayOfWeek? get() = dayOfWeekFor(dayIndex)

    /**
     * This market's ordering schedule: its own pickup day, with the deadline the
     * same distance before it as [default] puts it before its own.
     *
     * The distance is what the rule means — "you have until two days before" — not
     * the weekday it happens to land on. Keeping `deadlineDay` fixed instead would
     * give a Monday market six days' notice and a Saturday market four, from the
     * same configuration.
     */
    fun scheduleConfig(default: OrderScheduleConfig): OrderScheduleConfig {
        val day = pickupDay ?: return default
        val leadDays = (default.pickupDay.isoDayNumber - default.deadlineDay.isoDayNumber + 7) % 7
        val deadline = DayOfWeek(((day.isoDayNumber - leadDays - 1 + 7) % 7) + 1)
        return MarketScheduleConfig(day, deadline, default.deadlineHour, default.deadlineMinute)
    }
}

/** 0..6 for Monday..Sunday, or null outside the week. The dialog's own order. */
fun dayOfWeekFor(dayIndex: Int): DayOfWeek? =
    if (dayIndex in 0..6) DayOfWeek(dayIndex + 1) else null

private data class MarketScheduleConfig(
    override val pickupDay: DayOfWeek,
    override val deadlineDay: DayOfWeek,
    override val deadlineHour: Int,
    override val deadlineMinute: Int,
) : OrderScheduleConfig
