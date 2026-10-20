package com.together.newverse.domain.reminder

import com.together.newverse.data.config.DefaultOrderScheduleConfig
import com.together.newverse.domain.config.OrderScheduleConfig
import com.together.newverse.domain.model.StockReminderSettings
import com.together.newverse.domain.model.StockReminderSnapshot
import com.together.newverse.util.OrderDateUtils
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * Decides when the seller should be told what to bring to the coming market.
 *
 * Pure: every input is a parameter, so the rules are testable without a clock, a
 * device or a network — the same reason [OrderReminderEvaluator] is built this way,
 * and both platforms' schedulers delegate here rather than reimplementing them.
 *
 * The one moment worth notifying is after the **edit deadline**: until then buyers
 * can still add to their orders and the buy list can still grow, and after it the
 * numbers are frozen until the market happens. That also makes the reminder
 * once-per-cycle by construction, so no per-article "already told you" bookkeeping is
 * needed. See `doc/design/stock-driven-availability-design.md` §3.
 */
object StockReminderEvaluator {

    private val defaultConfig: OrderScheduleConfig = DefaultOrderScheduleConfig()

    /**
     * The hour a reminder may first arrive on the deadline's own terms.
     *
     * The deadline is 23:59, and a shopping list that buzzes at 23:59 is a shopping
     * list the seller switches off. It is posted the next morning instead — still
     * before the wholesaler, still after demand went final.
     */
    const val POST_HOUR = 7

    /**
     * How long after [postAt] a reminder is still worth posting.
     *
     * Wide, because unlike the buyer's reminder this one does not go stale at an odd
     * hour — a buy list is useful all day. It does stop mattering once the market
     * itself has started, which [isDue] checks separately.
     */
    private const val GRACE_HOURS = 14

    /**
     * When the reminder for a pickup on [pickUpDate] should be posted.
     *
     * The deadline, moved to [POST_HOUR] on the following morning when it falls at
     * night. A deadline already at or before [POST_HOUR] — a seller with an unusual
     * schedule — posts on its own day instead of waiting a night.
     */
    fun postAt(
        pickUpDate: Instant,
        timeZone: TimeZone,
        config: OrderScheduleConfig = defaultConfig,
        postHour: Int = POST_HOUR
    ): Instant {
        val deadline = OrderDateUtils.calculateEditDeadline(pickUpDate, timeZone, config)
        val local = deadline.toLocalDateTime(timeZone)
        val sameDay = local.date.atTime(LocalTime(postHour, 0)).toInstant(timeZone)
        if (sameDay >= deadline) return sameDay
        return local.date.plus(1, DateTimeUnit.DAY).atTime(LocalTime(postHour, 0)).toInstant(timeZone)
    }

    /**
     * Whether the refill reminder should be posted at [now].
     *
     * Everything it needs is in [settings] and [snapshot], both of which the app
     * stored while it was running: the worker reads no database.
     */
    fun isDue(
        settings: StockReminderSettings,
        snapshot: StockReminderSnapshot,
        now: Instant,
        timeZone: TimeZone,
        config: OrderScheduleConfig = defaultConfig,
        postHour: Int = POST_HOUR
    ): Boolean {
        if (!settings.enabled) return false
        // Nothing short, or nothing known: silence. An empty list is the good case
        // and does not deserve a notification saying so.
        if (!snapshot.hasAnything) return false
        // One per market day, whatever the worker does.
        if (settings.lastFiredCycle == snapshot.cycle) return false

        val pickUp = Instant.fromEpochMilliseconds(snapshot.pickUpDate)
        // The market has been and gone; a buy list for it is history. This is also
        // what stops a stale snapshot being posted weeks later.
        if (now >= pickUp) return false

        val post = postAt(pickUp, timeZone, config, postHour)
        if (now < post) return false
        return now < post + GRACE_HOURS.hours
    }

    /**
     * The next instant a reminder could fire for [snapshot], or null when none can.
     *
     * Used to arm the background work. A cycle already fired for, or already past,
     * yields null rather than a time in the past — the next snapshot the app writes
     * arms the next one, which is the cycle after this.
     */
    fun nextOccurrence(
        settings: StockReminderSettings,
        snapshot: StockReminderSnapshot,
        now: Instant,
        timeZone: TimeZone,
        config: OrderScheduleConfig = defaultConfig,
        postHour: Int = POST_HOUR
    ): Instant? {
        if (!settings.enabled) return null
        if (!snapshot.hasAnything) return null
        if (settings.lastFiredCycle == snapshot.cycle) return null

        val pickUp = Instant.fromEpochMilliseconds(snapshot.pickUpDate)
        if (now >= pickUp) return null

        val post = postAt(pickUp, timeZone, config, postHour)
        // Still ahead: wait for it. Already passed but inside the grace window: run
        // now, because the device was probably asleep when the moment came.
        return when {
            post > now -> post
            now < post + GRACE_HOURS.hours -> now
            else -> null
        }
    }
}
