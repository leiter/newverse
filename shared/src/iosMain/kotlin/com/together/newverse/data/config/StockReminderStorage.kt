package com.together.newverse.data.config

import com.together.newverse.domain.model.StockReminderLine
import com.together.newverse.domain.model.StockReminderSettings
import com.together.newverse.domain.model.StockReminderSnapshot
import platform.Foundation.NSUserDefaults

/**
 * iOS implementation of [StockReminderStorage] on the standard NSUserDefaults.
 *
 * The setting and the snapshot persist, but **nothing schedules a reminder on iOS
 * yet** — see [com.together.newverse.domain.reminder.IosStockReminderScheduler].
 * Storing them is harmless and means they are already there when scheduling lands.
 */
actual class StockReminderStorage {

    private val defaults = NSUserDefaults.standardUserDefaults

    actual fun load(): StockReminderSettings {
        val fallback = StockReminderSettings()
        // objectForKey distinguishes "never written" from a stored false.
        return StockReminderSettings(
            enabled = defaults.objectForKey(KEY_ENABLED)?.let { defaults.boolForKey(KEY_ENABLED) }
                ?: fallback.enabled,
            lastFiredCycle = defaults.stringForKey(KEY_LAST_FIRED_CYCLE) ?: fallback.lastFiredCycle
        )
    }

    actual fun save(settings: StockReminderSettings) {
        defaults.setBool(settings.enabled, forKey = KEY_ENABLED)
        defaults.setObject(settings.lastFiredCycle, forKey = KEY_LAST_FIRED_CYCLE)
    }

    actual fun loadSnapshot(): StockReminderSnapshot {
        val cycle = defaults.stringForKey(KEY_CYCLE) ?: return StockReminderSnapshot()
        val stored = defaults.stringForKey(KEY_LINES).orEmpty()
        return StockReminderSnapshot(
            cycle = cycle,
            pickUpDate = defaults.objectForKey(KEY_PICKUP)?.let { defaults.integerForKey(KEY_PICKUP) } ?: 0L,
            lines = stored.split(LINE_SEPARATOR).mapNotNull(::decodeLine)
        )
    }

    actual fun saveSnapshot(snapshot: StockReminderSnapshot) {
        defaults.setObject(snapshot.cycle, forKey = KEY_CYCLE)
        defaults.setInteger(snapshot.pickUpDate, forKey = KEY_PICKUP)
        defaults.setObject(
            snapshot.lines.joinToString(LINE_SEPARATOR) { line ->
                listOf(line.productName, line.shortfall.toString(), line.unit).joinToString(FIELD_SEPARATOR)
            },
            forKey = KEY_LINES
        )
    }

    actual fun ownerUserId(): String? = defaults.stringForKey(KEY_OWNER)

    actual fun setOwnerUserId(userId: String) {
        if (defaults.stringForKey(KEY_OWNER) == userId) return
        clear()
        defaults.setObject(userId, forKey = KEY_OWNER)
    }

    actual fun clear() {
        listOf(KEY_ENABLED, KEY_LAST_FIRED_CYCLE, KEY_CYCLE, KEY_PICKUP, KEY_LINES, KEY_OWNER)
            .forEach { defaults.removeObjectForKey(it) }
    }

    private fun decodeLine(stored: String): StockReminderLine? {
        val parts = stored.split(FIELD_SEPARATOR)
        if (parts.size != 3) return null
        val shortfall = parts[1].toDoubleOrNull() ?: return null
        return StockReminderLine(productName = parts[0], shortfall = shortfall, unit = parts[2])
    }

    private companion object {
        const val KEY_ENABLED = "stock_reminder_enabled"
        const val KEY_LAST_FIRED_CYCLE = "stock_reminder_last_fired_cycle"
        const val KEY_CYCLE = "stock_reminder_cycle"
        const val KEY_PICKUP = "stock_reminder_pickup"
        const val KEY_LINES = "stock_reminder_lines"
        const val KEY_OWNER = "stock_reminder_owner"
        const val LINE_SEPARATOR = "\n"
        const val FIELD_SEPARATOR = "\t"
    }
}
