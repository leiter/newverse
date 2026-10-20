package com.together.newverse.data.config

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.together.newverse.domain.model.StockReminderLine
import com.together.newverse.domain.model.StockReminderSettings
import com.together.newverse.domain.model.StockReminderSnapshot

/**
 * Android implementation of [StockReminderStorage], on its own SharedPreferences file
 * so the background worker can read it without a signed-in session.
 *
 * The snapshot's lines are stored as one string per line (`name\tshortfall\tunit`) in
 * a string set, with an index so the urgency order survives. A set rather than a
 * list because that is what SharedPreferences offers; a tab because a product name
 * can contain anything else, including a comma or a semicolon.
 */
actual class StockReminderStorage(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    actual fun load(): StockReminderSettings {
        val defaults = StockReminderSettings()
        return StockReminderSettings(
            enabled = prefs.getBoolean(KEY_ENABLED, defaults.enabled),
            lastFiredCycle = prefs.getString(KEY_LAST_FIRED_CYCLE, null) ?: defaults.lastFiredCycle
        )
    }

    actual fun save(settings: StockReminderSettings) {
        prefs.edit {
            putBoolean(KEY_ENABLED, settings.enabled)
            putString(KEY_LAST_FIRED_CYCLE, settings.lastFiredCycle)
        }
    }

    actual fun loadSnapshot(): StockReminderSnapshot {
        val cycle = prefs.getString(KEY_CYCLE, null) ?: return StockReminderSnapshot()
        val stored = prefs.getStringSet(KEY_LINES, emptySet())?.toSet().orEmpty()
        return StockReminderSnapshot(
            cycle = cycle,
            pickUpDate = prefs.getLong(KEY_PICKUP, 0L),
            lines = stored.mapNotNull(::decodeLine).sortedBy { it.first }.map { it.second }
        )
    }

    actual fun saveSnapshot(snapshot: StockReminderSnapshot) {
        prefs.edit {
            putString(KEY_CYCLE, snapshot.cycle)
            putLong(KEY_PICKUP, snapshot.pickUpDate)
            putStringSet(
                KEY_LINES,
                snapshot.lines.mapIndexed { index, line -> encodeLine(index, line) }.toSet()
            )
        }
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

    private fun encodeLine(index: Int, line: StockReminderLine): String =
        listOf(index.toString(), line.productName, line.shortfall.toString(), line.unit)
            .joinToString(SEPARATOR)

    /** The stored order and the line, or null when the entry cannot be read back. */
    private fun decodeLine(stored: String): Pair<Int, StockReminderLine>? {
        val parts = stored.split(SEPARATOR)
        if (parts.size != 4) return null
        val index = parts[0].toIntOrNull() ?: return null
        val shortfall = parts[2].toDoubleOrNull() ?: return null
        return index to StockReminderLine(productName = parts[1], shortfall = shortfall, unit = parts[3])
    }

    private companion object {
        const val PREFS_FILE = "newverse_stock_reminder"
        const val SEPARATOR = "\t"
        const val KEY_ENABLED = "enabled"
        const val KEY_LAST_FIRED_CYCLE = "last_fired_cycle"
        const val KEY_CYCLE = "cycle"
        const val KEY_PICKUP = "pickup_date"
        const val KEY_LINES = "lines"
        const val KEY_OWNER = "owner"
    }
}
