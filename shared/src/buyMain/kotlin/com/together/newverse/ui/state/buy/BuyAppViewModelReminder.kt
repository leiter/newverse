package com.together.newverse.ui.state.buy

import com.together.newverse.domain.model.OrderReminderSettings
import com.together.newverse.domain.reminder.OrderReminderSchedule
import com.together.newverse.ui.state.BuyAppViewModel
import com.together.newverse.ui.state.BuyReminderAction
import com.together.newverse.util.Log
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek

private const val TAG = "BuyReminder"

/**
 * Order reminder settings: the buyer's choice of days and time for the nudge to place an
 * order before their market's deadline.
 *
 * Settings are device-local ([com.together.newverse.data.config.OrderReminderStorage]),
 * because what schedules the reminder is device-local too. Every change is written
 * through immediately and the next reminder re-armed — there is no save button, and a
 * setting that survived in memory but not on disk would silently stop reminding.
 */
internal fun BuyAppViewModel.handleReminderAction(action: BuyReminderAction) {
    when (action) {
        is BuyReminderAction.LoadReminderSettings -> loadReminderSettings()
        is BuyReminderAction.SetReminderEnabled ->
            updateReminder { it.copy(enabled = action.enabled) }
        is BuyReminderAction.ToggleReminderDay ->
            updateReminder { it.copy(days = it.days.toggle(action.day)) }
        is BuyReminderAction.SetReminderTime ->
            updateReminder { it.copy(hour = action.hour, minute = action.minute) }
        is BuyReminderAction.SetReminderOnlyWhenNotOrdered ->
            updateReminder { it.copy(onlyWhenNotOrdered = action.onlyWhenNotOrdered) }
    }
}

private fun Set<DayOfWeek>.toggle(day: DayOfWeek): Set<DayOfWeek> =
    if (day in this) this - day else this + day

internal fun BuyAppViewModel.loadReminderSettings() {
    val storage = orderReminderStorage ?: return
    val settings = storage.load()
    _state.update {
        it.copy(
            orderReminder = settings,
            notificationsBlocked = orderReminderScheduler?.canDeliver() == false
        )
    }
    // Arm on every start: background work can be dropped by the system, and a settings
    // change made on a previous run may never have been armed.
    orderReminderScheduler?.reschedule()
    Log.d(TAG) { "loadReminderSettings: enabled=${settings.enabled} days=${settings.days.size}" }
}

/**
 * Apply a change, persist it, and re-arm.
 *
 * [OrderReminderSettings.lastFiredDate] is cleared whenever the schedule itself changes,
 * so a buyer who moves the time to later today still gets that reminder instead of being
 * told it already fired.
 */
private fun BuyAppViewModel.updateReminder(transform: (OrderReminderSettings) -> OrderReminderSettings) {
    val storage = orderReminderStorage ?: return
    val current = _state.value.orderReminder
    val updated = transform(current).let { next ->
        val scheduleChanged = next.days != current.days ||
            next.hour != current.hour ||
            next.minute != current.minute
        if (scheduleChanged) next.copy(lastFiredDate = "") else next
    }

    storage.save(updated)
    _state.update {
        it.copy(
            orderReminder = updated,
            notificationsBlocked = orderReminderScheduler?.canDeliver() == false
        )
    }
    orderReminderScheduler?.reschedule()
    Log.d(TAG) { "updateReminder: saved enabled=${updated.enabled} at ${updated.hour}:${updated.minute}" }
}

/**
 * Keep the worker's local snapshot of which pickup cycles are already ordered for in step
 * with the profile, and bind the settings to the signed-in buyer.
 *
 * Called from the profile observer rather than on a timer: the worker cannot read Firebase
 * when it wakes, so whatever the app last saw is what the reminder is evaluated against.
 */
internal fun BuyAppViewModel.syncReminderOrderSnapshot(userId: String?, placedCycles: Set<String>) {
    val storage = orderReminderStorage ?: return
    if (!userId.isNullOrBlank()) {
        // Drops stored settings when a different buyer signs in on this device.
        storage.setOwnerUserId(userId)
        // A new owner wipes the storage, schedule included; put it straight back so
        // the worker does not fall back to the default week until the market changes.
        if (storage.cachedSchedule() == null && _state.value.contextualMarketId != null) {
            storage.cacheSchedule(_state.value.reminderSchedule)
        }
    }
    storage.cachePlacedCycles(placedCycles)
    Log.d(TAG) { "syncReminderOrderSnapshot: ${placedCycles.size} placed cycles cached" }
}

/**
 * Keep the worker's local snapshot of the seller's closed market days in step.
 *
 * Same reasoning as [syncReminderOrderSnapshot]: the worker has no Firebase when it
 * wakes, so a day the seller closed is only known to it if the app cached the key
 * while it was open. Scoped to the buyer's contextual market, so a closure that
 * shuts a stall they do not use does not suppress their reminder.
 */
internal fun BuyAppViewModel.syncReminderClosureSnapshot() {
    val storage = orderReminderStorage ?: return
    val marketId = _state.value.contextualMarketId
    val keys = _state.value.sellerClosures.closedCycles(marketId)
    storage.cacheClosedCycles(keys)
    Log.d(TAG) { "syncReminderClosureSnapshot: ${keys.size} closed cycles cached" }
}

/**
 * Follow the buyer's market for as long as the app runs.
 *
 * The market the reminder is about can change from several places — the seller's
 * markets loading, the buyer choosing a default, a basket slot at another stall — so
 * rather than syncing at each of them, this watches the one derived value they all
 * feed, [com.together.newverse.ui.state.BuyAppState.reminderSchedule].
 */
internal fun BuyAppViewModel.observeReminderSchedule() {
    if (orderReminderStorage == null) return
    scope.launch {
        _state
            // Only once a market is known. Before the seller's markets load — and while
            // a buyer at several stalls has not said which — the schedule is just the
            // app default, and caching that would overwrite the real one from the last
            // run and walk the buyer's days back and forth on every start.
            .map { state ->
                state.contextualMarketId?.let { OrderReminderSchedule.encode(state.reminderSchedule) }
            }
            .filterNotNull()
            .distinctUntilChanged()
            .collect { syncReminderSchedule() }
    }
}

/**
 * Cache the buyer's market schedule for the worker, and move their reminder days with
 * it when the deadline moved.
 *
 * Days keep their distance to the deadline rather than their weekday
 * ([OrderReminderSchedule.shiftDays]): Monday and Tuesday before a Tuesday deadline
 * become Tuesday and Wednesday before a Wednesday one. Done here, once, rather than
 * at read time, so the card, the stored settings and the worker always agree.
 */
internal fun BuyAppViewModel.syncReminderSchedule() {
    val storage = orderReminderStorage ?: return
    val schedule = _state.value.reminderSchedule
    val previous = storage.cachedSchedule() ?: OrderReminderSchedule.DEFAULT
    storage.cacheSchedule(schedule)
    Log.d(TAG) { "syncReminderSchedule: ${OrderReminderSchedule.encode(schedule)}" }

    if (OrderReminderSchedule.sameSchedule(previous, schedule)) return
    val current = _state.value.orderReminder
    val shifted = OrderReminderSchedule.shiftDays(current.days, previous, schedule)
    if (shifted == current.days) return

    // Not through updateReminder: that clears lastFiredDate, which is right when the
    // buyer moves their own time but would let a market switch fire a second
    // reminder on a day that already had one.
    val updated = current.copy(days = shifted)
    storage.save(updated)
    _state.update { it.copy(orderReminder = updated) }
    orderReminderScheduler?.reschedule()
    Log.d(TAG) { "syncReminderSchedule: days moved with the deadline to $shifted" }
}

/**
 * Rebuild the offered pickup dates now that the seller's closed days are known.
 *
 * The basket screen builds its first list in `init`, before Firebase has answered,
 * so without this a day closed by the seller stays on offer until the list happens
 * to be rebuilt for another reason.
 */
internal fun BuyAppViewModel.refreshDatesForClosures() {
    basketScreenLoadAvailableDates()
}
