package com.together.newverse.data.config

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.together.newverse.domain.model.OrderReminderSettings
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.isoDayNumber

/**
 * Android implementation of [OrderReminderStorage], on a single SharedPreferences file
 * so the background worker can read it without a signed-in session.
 */
actual class OrderReminderStorage(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    actual fun load(): OrderReminderSettings {
        val defaults = OrderReminderSettings()
        return OrderReminderSettings(
            enabled = prefs.getBoolean(KEY_ENABLED, defaults.enabled),
            days = prefs.getString(KEY_DAYS, null)?.let(::decodeDays) ?: defaults.days,
            hour = prefs.getInt(KEY_HOUR, defaults.hour),
            minute = prefs.getInt(KEY_MINUTE, defaults.minute),
            onlyWhenNotOrdered = prefs.getBoolean(KEY_ONLY_NOT_ORDERED, defaults.onlyWhenNotOrdered),
            lastFiredDate = prefs.getString(KEY_LAST_FIRED, null) ?: defaults.lastFiredDate
        )
    }

    actual fun save(settings: OrderReminderSettings) {
        prefs.edit {
            putBoolean(KEY_ENABLED, settings.enabled)
            putString(KEY_DAYS, encodeDays(settings.days))
            putInt(KEY_HOUR, settings.hour)
            putInt(KEY_MINUTE, settings.minute)
            putBoolean(KEY_ONLY_NOT_ORDERED, settings.onlyWhenNotOrdered)
            putString(KEY_LAST_FIRED, settings.lastFiredDate)
        }
    }

    actual fun cachedPlacedCycles(): Set<String> =
        prefs.getStringSet(KEY_PLACED_CYCLES, emptySet())?.toSet() ?: emptySet()

    actual fun cachePlacedCycles(cycles: Set<String>) {
        // Copy: SharedPreferences keeps the instance handed to it, and a caller's set
        // can change underneath.
        prefs.edit { putStringSet(KEY_PLACED_CYCLES, cycles.toSet()) }
    }

    actual fun ownerUserId(): String? = prefs.getString(KEY_OWNER, null)

    actual fun setOwnerUserId(userId: String) {
        if (prefs.getString(KEY_OWNER, null) == userId) return
        prefs.edit {
            clear()
            putString(KEY_OWNER, userId)
        }
    }

    actual fun clear() {
        prefs.edit { clear() }
    }

    private companion object {
        const val PREFS_FILE = "newverse_order_reminder"
        const val KEY_ENABLED = "enabled"
        const val KEY_DAYS = "days"
        const val KEY_HOUR = "hour"
        const val KEY_MINUTE = "minute"
        const val KEY_ONLY_NOT_ORDERED = "only_when_not_ordered"
        const val KEY_LAST_FIRED = "last_fired_date"
        const val KEY_PLACED_CYCLES = "placed_cycles"
        const val KEY_OWNER = "owner_user_id"
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
