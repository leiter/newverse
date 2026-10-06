package com.together.newverse.ui.state.buy

import com.together.newverse.domain.model.OrderReminderSettings
import com.together.newverse.ui.state.BuyAppViewModel
import com.together.newverse.ui.state.BuyReminderAction
import com.together.newverse.util.Log
import kotlinx.coroutines.flow.update
import kotlinx.datetime.DayOfWeek

private const val TAG = "BuyReminder"

/**
 * Order reminder settings: the buyer's choice of days and time for the nudge to place an
 * order before the Tuesday deadline.
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
    }
    storage.cachePlacedCycles(placedCycles)
    Log.d(TAG) { "syncReminderOrderSnapshot: ${placedCycles.size} placed cycles cached" }
}
