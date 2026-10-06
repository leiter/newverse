package com.together.newverse.data.config

import com.together.newverse.domain.model.OrderReminderSettings

/**
 * Platform storage for the order reminder: the buyer's settings, plus the snapshot the
 * background worker evaluates them against.
 *
 * Unlike [PendingInviteTokenStorage] this is **not** scoped per user. The worker wakes
 * with no session — no signed-in user, possibly no network — so the settings have to be
 * readable without one. One buyer per device is the normal case; [ownerUserId] records
 * whose settings these are so a different buyer signing in on the same device can reset
 * them rather than inherit a stranger's reminders.
 *
 * [cachedPlacedCycles] is the local snapshot of `BuyerProfile.placedOrderIds.keys`,
 * refreshed whenever the app observes the profile. The worker needs it to answer "has
 * this buyer already ordered?" offline. It can be stale — a buyer who orders on another
 * device may still get one reminder — which is why the notification only ever says
 * "don't forget", never anything that depends on being right about their order.
 */
expect class OrderReminderStorage {
    fun load(): OrderReminderSettings
    fun save(settings: OrderReminderSettings)

    fun cachedPlacedCycles(): Set<String>
    fun cachePlacedCycles(cycles: Set<String>)

    /** The uid these settings belong to, or null when nothing has been stored yet. */
    fun ownerUserId(): String?

    /**
     * Note the signed-in buyer. When it differs from [ownerUserId], everything stored
     * is dropped first so reminders never carry over between accounts.
     */
    fun setOwnerUserId(userId: String)

    fun clear()
}
