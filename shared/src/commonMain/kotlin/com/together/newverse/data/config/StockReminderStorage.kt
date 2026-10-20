package com.together.newverse.data.config

import com.together.newverse.domain.model.StockReminderSettings
import com.together.newverse.domain.model.StockReminderSnapshot

/**
 * Platform storage for the seller's refill reminder: the setting, plus the snapshot
 * of the coming market's buy list that the background worker posts.
 *
 * Not scoped per user, for the same reason as [OrderReminderStorage]: the worker
 * wakes with no session and possibly no network, so everything it needs has to be
 * readable without one. [ownerUserId] records whose numbers these are, so a
 * different seller signing in on the same device starts clean rather than inheriting
 * a stranger's shortfalls — which here would mean a notification naming another
 * business's articles.
 *
 * The snapshot can be stale. That is tolerable in one direction only: it may name an
 * article the seller has since restocked, which costs them a glance at the stock
 * screen, and the worker drops it entirely once the market it was about has
 * happened.
 */
expect class StockReminderStorage {
    fun load(): StockReminderSettings
    fun save(settings: StockReminderSettings)

    fun loadSnapshot(): StockReminderSnapshot
    fun saveSnapshot(snapshot: StockReminderSnapshot)

    /** The uid these numbers belong to, or null when nothing has been stored yet. */
    fun ownerUserId(): String?

    /**
     * Note the signed-in seller. When it differs from [ownerUserId], everything
     * stored is dropped first.
     */
    fun setOwnerUserId(userId: String)

    fun clear()
}
