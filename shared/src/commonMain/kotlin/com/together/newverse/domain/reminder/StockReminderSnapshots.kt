package com.together.newverse.domain.reminder

import com.together.newverse.domain.model.Order
import com.together.newverse.domain.model.RefillNeed
import com.together.newverse.domain.model.RefillState
import com.together.newverse.domain.model.StockReminderLine
import com.together.newverse.domain.model.StockReminderSnapshot
import com.together.newverse.util.OrderDateUtils
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

/**
 * Builds the buy list the background worker will post.
 *
 * Pure, and separate from the view model, because this is the whole content of the
 * notification: what it leaves out is as much a decision as what it keeps, and both
 * deserve tests that do not need a device.
 */
object StockReminderSnapshots {

    /**
     * @param needs the refill list as the stock screen shows it, most urgent first.
     * @param orders every order the seller is holding, for finding the market day
     *   this list is for.
     */
    fun of(
        needs: List<RefillNeed>,
        orders: List<Order>,
        nowMillis: Long,
        timeZone: TimeZone = TimeZone.currentSystemDefault()
    ): StockReminderSnapshot {
        val now = Instant.fromEpochMilliseconds(nowMillis)

        // The next pickup the seller actually has an order for, or — with none yet —
        // the next one the schedule produces, so a seller with a quiet week still
        // gets their reorder levels covered.
        val nextPickUp = orders
            .filter { it.origin.isReal && it.pickUpDate > nowMillis }
            .minOfOrNull { it.pickUpDate }
            ?: OrderDateUtils.calculateNextPickupDate(now, timeZone).toEpochMilliseconds()

        return StockReminderSnapshot(
            cycle = OrderDateUtils.formatDateKey(Instant.fromEpochMilliseconds(nextPickUp), timeZone),
            pickUpDate = nextPickUp,
            lines = needs.mapNotNull { it.line() }
        )
    }

    /**
     * One line of the list, or null for a need that does not belong on one.
     *
     * Dropped: articles never counted, because on a fresh ledger that is the entire
     * catalogue and a notification cannot ask a question; and anything with no
     * shortfall, such as a unit mismatch, which needs the stock screen rather than a
     * line saying "0 kg".
     */
    private fun RefillNeed.line(): StockReminderLine? {
        if (state == RefillState.UNCOUNTED) return null
        if (shortfall <= 0.0) return null
        return StockReminderLine(productName = productName, shortfall = shortfall, unit = unit)
    }
}
