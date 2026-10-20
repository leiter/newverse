package com.together.newverse.domain.model

/**
 * The seller's settings for the refill reminder.
 *
 * Far simpler than [OrderReminderSettings], and deliberately so: the buyer picks days
 * and a time because their week is theirs to arrange, while the seller's reminder has
 * exactly one useful moment — once the edit deadline has passed, demand for the
 * coming market is final and the buy list cannot change again. There is nothing to
 * configure but whether it arrives.
 *
 * @param lastFiredCycle the pickup cycle (`yyyyMMdd`) the reminder last fired for.
 *   Per *cycle*, not per date: that is what makes "one reminder per market day" true
 *   by construction, however often the worker runs, with no per-article bookkeeping.
 */
data class StockReminderSettings(
    val enabled: Boolean = true,
    val lastFiredCycle: String = ""
)

/** One line of the buy list, as the reminder reports it. */
data class StockReminderLine(
    val productName: String,
    val shortfall: Double,
    val unit: String
)

/**
 * What the app last worked out about the coming market, left where the background
 * worker can find it.
 *
 * The worker wakes with no Firebase and no session (see `StockReminderStorage`), so
 * it cannot derive any of this itself — the seller app writes it whenever it
 * recalculates the refill list, and the worker only decides whether to post it.
 *
 * @param cycle the pickup cycle (`yyyyMMdd`) these lines are about.
 * @param pickUpDate epoch millis of that pickup, so the worker can work out the
 *   deadline without resolving markets.
 * @param lines what to bring, most urgent first; empty means nothing to say.
 */
data class StockReminderSnapshot(
    val cycle: String = "",
    val pickUpDate: Long = 0L,
    val lines: List<StockReminderLine> = emptyList()
) {
    /** Whether there is anything worth waking the seller for. */
    val hasAnything: Boolean get() = cycle.isNotEmpty() && pickUpDate > 0L && lines.isNotEmpty()
}
