package com.together.newverse.ui.screens.sell

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.together.newverse.domain.model.Market
import com.together.newverse.domain.model.MarketClosure
import com.together.newverse.domain.model.dayOfWeekFor
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import com.together.newverse.util.formatString
import newverse.shared.generated.resources.closures_orders_blocked
import newverse.shared.generated.resources.closures_range_backwards
import newverse.shared.generated.resources.closures_range_empty
import newverse.shared.generated.resources.closures_until_hint
import org.jetbrains.compose.resources.stringResource
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.button_save
import newverse.shared.generated.resources.closures_add
import newverse.shared.generated.resources.closures_all_markets
import newverse.shared.generated.resources.closures_date_hint
import newverse.shared.generated.resources.closures_empty
import newverse.shared.generated.resources.closures_invalid_date
import newverse.shared.generated.resources.closures_market
import newverse.shared.generated.resources.closures_reason_hint
import newverse.shared.generated.resources.closures_remove
import newverse.shared.generated.resources.closures_title

/**
 * The days on which a market does not happen.
 *
 * Entered as `TT.MM.JJJJ` and stored as `yyyyMMdd`, so the stored form carries no
 * timezone and sorts. A closure with no market selected shuts every market — the
 * seller being away — which is the common case and therefore the default.
 */
@Composable
fun MarketClosuresCard(
    closures: List<MarketClosure>,
    markets: List<Market>,
    isSaving: Boolean,
    /** Live orders per pickup day, `yyyyMMdd` to count. A day with orders cannot be closed. */
    ordersByCycle: Map<String, Int> = emptyMap(),
    pickupDay: DayOfWeek = DayOfWeek.THURSDAY,
    onSave: (List<MarketClosure>) -> Unit,
) {
    var draft by remember(closures) { mutableStateOf(closures) }
    var dateInput by remember { mutableStateOf("") }
    var untilInput by remember { mutableStateOf("") }
    var reasonInput by remember { mutableStateOf("") }
    var marketInput by remember { mutableStateOf("") }
    var marketMenuOpen by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val invalidDate = stringResource(Res.string.closures_invalid_date)
    val allMarkets = stringResource(Res.string.closures_all_markets)
    val rangeBackwards = stringResource(Res.string.closures_range_backwards)
    val rangeEmpty = stringResource(Res.string.closures_range_empty)
    val ordersBlocked = stringResource(Res.string.closures_orders_blocked)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(Res.string.closures_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.semantics { heading() }
            )

            if (draft.isEmpty()) {
                Text(
                    text = stringResource(Res.string.closures_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            draft.sortedBy { it.date }.forEach { closure ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = formatClosureDate(closure.date),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        val scope = if (closure.isSellerWide) allMarkets
                        else markets.firstOrNull { it.id == closure.marketId }?.name.orEmpty()
                        val detail = listOf(scope, closure.reason).filter { it.isNotBlank() }
                        if (detail.isNotEmpty()) {
                            Text(
                                text = detail.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = { draft = draft - closure }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(Res.string.closures_remove),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            OutlinedTextField(
                value = dateInput,
                onValueChange = { dateInput = it; error = null },
                label = { Text(stringResource(Res.string.closures_date_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = error != null
            )

            OutlinedTextField(
                value = untilInput,
                onValueChange = { untilInput = it; error = null },
                label = { Text(stringResource(Res.string.closures_until_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = reasonInput,
                onValueChange = { reasonInput = it },
                label = { Text(stringResource(Res.string.closures_reason_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            if (markets.isNotEmpty()) {
                Column {
                    OutlinedTextField(
                        value = markets.firstOrNull { it.id == marketInput }?.name ?: allMarkets,
                        onValueChange = {},
                        readOnly = true,
                        enabled = false,
                        label = { Text(stringResource(Res.string.closures_market)) },
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { marketMenuOpen = true }
                    )
                    DropdownMenu(
                        expanded = marketMenuOpen,
                        onDismissRequest = { marketMenuOpen = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(allMarkets) },
                            onClick = { marketInput = ""; marketMenuOpen = false }
                        )
                        markets.forEach { market ->
                            DropdownMenuItem(
                                text = { Text(market.name) },
                                onClick = { marketInput = market.id; marketMenuOpen = false }
                            )
                        }
                    }
                }
            }

            error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            TextButton(onClick = {
                when (
                    val outcome = resolveClosureAddition(
                        fromInput = dateInput,
                        untilInput = untilInput,
                        markets = markets,
                        marketId = marketInput,
                        pickupDay = pickupDay,
                        ordersByCycle = ordersByCycle
                    )
                ) {
                    ClosureAddition.InvalidDate -> error = invalidDate
                    ClosureAddition.Backwards -> error = rangeBackwards
                    ClosureAddition.NoMarketDay -> error = rangeEmpty
                    is ClosureAddition.HasOrders ->
                        error = formatString(ordersBlocked, outcome.orderCount)
                    is ClosureAddition.Days -> {
                        // Replacing rather than appending keeps one entry per day and
                        // scope, so entering the same day twice is a correction.
                        draft = draft.filterNot { existing ->
                            existing.marketId == marketInput && existing.date in outcome.keys
                        } + outcome.keys.map {
                            MarketClosure(date = it, marketId = marketInput, reason = reasonInput.trim())
                        }
                        dateInput = ""
                        untilInput = ""
                        reasonInput = ""
                        error = null
                    }
                }
            }) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(Res.string.closures_add))
            }

            Button(
                onClick = { onSave(draft) },
                enabled = !isSaving && draft.toSet() != closures.toSet(),
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(stringResource(Res.string.button_save))
            }
        }
    }
}

/**
 * `TT.MM.JJJJ` to the stored `yyyyMMdd`, or null when it is not a real date.
 *
 * Deliberately strict: a half-typed date silently stored as a closure would take a
 * market day away with no way for the seller to see why.
 */
internal fun parseClosureDate(input: String): String? {
    val parts = input.trim().split(".", "/", "-")
    if (parts.size != 3) return null
    val day = parts[0].toIntOrNull() ?: return null
    val month = parts[1].toIntOrNull() ?: return null
    val year = parts[2].toIntOrNull() ?: return null
    if (month !in 1..12 || day !in 1..31 || year < 2000 || year > 2999) return null

    val daysInMonth = when (month) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11 -> 30
        else -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
    }
    if (day > daysInMonth) return null

    return "$year" + month.toString().padStart(2, '0') + day.toString().padStart(2, '0')
}

/** `yyyyMMdd` back to `TT.MM.JJJJ` for display. */
internal fun formatClosureDate(key: String): String =
    if (key.length != 8) key
    else "${key.substring(6, 8)}.${key.substring(4, 6)}.${key.substring(0, 4)}"

/**
 * The market days inside an inclusive date range.
 *
 * A fortnight of vacation is two to four actual market days, so the seller enters a
 * range and it is stored as those individual days. Expanding to *every* date would
 * fill the list with days on which nothing was going to happen anyway.
 *
 * Which weekdays count depends on the scope: one market contributes its own day,
 * and a seller-wide closure covers every market's day — falling back to the single
 * configured pickup day when no markets are set up yet.
 */
internal fun expandClosureRange(
    fromKey: String,
    toKey: String,
    markets: List<Market>,
    marketId: String,
    fallbackDay: DayOfWeek,
): List<String> {
    val from = keyToLocalDate(fromKey) ?: return emptyList()
    val to = keyToLocalDate(toKey) ?: return emptyList()
    if (to < from) return emptyList()

    val scoped = if (marketId.isEmpty()) markets else markets.filter { it.id == marketId }
    val days: Set<DayOfWeek> = scoped
        .mapNotNull { dayOfWeekFor(it.dayIndex) }
        .toSet()
        .ifEmpty { setOf(fallbackDay) }

    val keys = mutableListOf<String>()
    var date = from
    // Bounded by the range itself, but a wide range is still worth capping so a
    // mistyped year cannot produce tens of thousands of records.
    var guard = 0
    while (date <= to && guard < MAX_RANGE_DAYS) {
        guard++
        if (date.dayOfWeek in days) keys += MarketClosure.dateKey(date)
        date = date.plus(1, DateTimeUnit.DAY)
    }
    return keys
}

/** Two years: long enough for any real closure, short enough to catch a typo. */
internal const val MAX_RANGE_DAYS = 730

internal fun keyToLocalDate(key: String): LocalDate? {
    if (key.length != 8) return null
    val year = key.substring(0, 4).toIntOrNull() ?: return null
    val month = key.substring(4, 6).toIntOrNull() ?: return null
    val day = key.substring(6, 8).toIntOrNull() ?: return null
    return runCatching { LocalDate(year, month, day) }.getOrNull()
}

/** What entering a closure date, or a range of them, comes to. */
sealed interface ClosureAddition {
    /** The days to close. */
    data class Days(val keys: List<String>) : ClosureAddition

    data object InvalidDate : ClosureAddition
    data object Backwards : ClosureAddition
    data object NoMarketDay : ClosureAddition

    /**
     * At least one day in the range already has orders on it.
     *
     * The closure is refused rather than written with a warning: those buyers would
     * arrive at a market that is not there, and once the edit deadline has passed
     * they cannot move their own orders — only the seller can. [date] is the first
     * such day, so the message can name what is in the way.
     */
    data class HasOrders(val date: String, val orderCount: Int) : ClosureAddition
}

/**
 * Decide what the seller's input means, without touching any state.
 *
 * Pulled out of the click handler so the refusal rules — a backwards range, a range
 * with no market day in it, a day that already has orders — can be tested without a
 * composition.
 */
internal fun resolveClosureAddition(
    fromInput: String,
    untilInput: String,
    markets: List<Market>,
    marketId: String,
    pickupDay: DayOfWeek,
    ordersByCycle: Map<String, Int>,
): ClosureAddition {
    val fromKey = parseClosureDate(fromInput) ?: return ClosureAddition.InvalidDate
    val toKey = if (untilInput.isBlank()) fromKey
    else parseClosureDate(untilInput) ?: return ClosureAddition.InvalidDate

    if (toKey < fromKey) return ClosureAddition.Backwards

    // A single day stays that day even when it is not a market day: the seller may
    // know something the schedule does not. A range expands to market days only.
    val keys = if (toKey == fromKey) listOf(fromKey)
    else expandClosureRange(fromKey, toKey, markets, marketId, pickupDay)

    if (keys.isEmpty()) return ClosureAddition.NoMarketDay

    val blocked = keys.firstOrNull { (ordersByCycle[it] ?: 0) > 0 }
    if (blocked != null) return ClosureAddition.HasOrders(blocked, ordersByCycle.getValue(blocked))

    return ClosureAddition.Days(keys)
}
