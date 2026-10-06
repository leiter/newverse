package com.together.newverse.data.config

import com.together.newverse.domain.model.OrderReminderSettings
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.isoDayNumber
import platform.Foundation.NSUserDefaults

/**
 * iOS implementation of [OrderReminderStorage] on the standard NSUserDefaults.
 *
 * Settings persist and the profile UI works, but **nothing schedules a reminder on iOS
 * yet** — `BGTaskScheduler` + `UNUserNotificationCenter` are still to come (see
 * `doc/order-ready-notification-design.md` C5). Writing settings here is harmless and
 * means they survive to be picked up when scheduling lands.
 */
actual class OrderReminderStorage {

    private val defaults = NSUserDefaults.standardUserDefaults

    actual fun load(): OrderReminderSettings {
        val fallback = OrderReminderSettings()
        // objectForKey distinguishes "never written" from a stored false/0.
        return OrderReminderSettings(
            enabled = defaults.objectForKey(KEY_ENABLED)?.let { defaults.boolForKey(KEY_ENABLED) }
                ?: fallback.enabled,
            days = defaults.stringForKey(KEY_DAYS)?.let(::decodeDays) ?: fallback.days,
            hour = defaults.objectForKey(KEY_HOUR)?.let { defaults.integerForKey(KEY_HOUR).toInt() }
                ?: fallback.hour,
            minute = defaults.objectForKey(KEY_MINUTE)?.let { defaults.integerForKey(KEY_MINUTE).toInt() }
                ?: fallback.minute,
            onlyWhenNotOrdered = defaults.objectForKey(KEY_ONLY_NOT_ORDERED)
                ?.let { defaults.boolForKey(KEY_ONLY_NOT_ORDERED) } ?: fallback.onlyWhenNotOrdered,
            lastFiredDate = defaults.stringForKey(KEY_LAST_FIRED) ?: fallback.lastFiredDate
        )
    }

    actual fun save(settings: OrderReminderSettings) {
        defaults.setBool(settings.enabled, forKey = KEY_ENABLED)
        defaults.setObject(encodeDays(settings.days), forKey = KEY_DAYS)
        defaults.setInteger(settings.hour.toLong(), forKey = KEY_HOUR)
        defaults.setInteger(settings.minute.toLong(), forKey = KEY_MINUTE)
        defaults.setBool(settings.onlyWhenNotOrdered, forKey = KEY_ONLY_NOT_ORDERED)
        defaults.setObject(settings.lastFiredDate, forKey = KEY_LAST_FIRED)
    }

    @Suppress("UNCHECKED_CAST")
    actual fun cachedPlacedCycles(): Set<String> =
        (defaults.arrayForKey(KEY_PLACED_CYCLES) as? List<String>)?.toSet() ?: emptySet()

    actual fun cachePlacedCycles(cycles: Set<String>) {
        defaults.setObject(cycles.toList(), forKey = KEY_PLACED_CYCLES)
    }

    actual fun ownerUserId(): String? = defaults.stringForKey(KEY_OWNER)

    actual fun setOwnerUserId(userId: String) {
        if (defaults.stringForKey(KEY_OWNER) == userId) return
        clear()
        defaults.setObject(userId, forKey = KEY_OWNER)
    }

    actual fun clear() {
        listOf(
            KEY_ENABLED, KEY_DAYS, KEY_HOUR, KEY_MINUTE,
            KEY_ONLY_NOT_ORDERED, KEY_LAST_FIRED, KEY_PLACED_CYCLES, KEY_OWNER
        ).forEach { defaults.removeObjectForKey(it) }
    }

    private companion object {
        const val KEY_ENABLED = "newverse_reminder_enabled"
        const val KEY_DAYS = "newverse_reminder_days"
        const val KEY_HOUR = "newverse_reminder_hour"
        const val KEY_MINUTE = "newverse_reminder_minute"
        const val KEY_ONLY_NOT_ORDERED = "newverse_reminder_only_not_ordered"
        const val KEY_LAST_FIRED = "newverse_reminder_last_fired"
        const val KEY_PLACED_CYCLES = "newverse_reminder_placed_cycles"
        const val KEY_OWNER = "newverse_reminder_owner"
    }
}

/** ISO day numbers, comma separated: stable across locales and enum reordering. */
internal fun encodeDays(days: Set<DayOfWeek>): String =
    days.map { it.isoDayNumber }.sorted().joinToString(",")

internal fun decodeDays(encoded: String): Set<DayOfWeek> =
    encoded.split(",")
        .mapNotNull { part -> part.trim().toIntOrNull() }
        .mapNotNull { iso -> DayOfWeek.entries.firstOrNull { it.isoDayNumber == iso } }
        .toSet()
