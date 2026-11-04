package com.together.newverse.ui.components

import androidx.compose.runtime.Composable
import com.together.newverse.domain.config.OrderScheduleConfig
import com.together.newverse.util.OrderDateUtils
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.duration_days_hours
import newverse.shared.generated.resources.duration_deadline_passed
import newverse.shared.generated.resources.duration_hours_minutes
import newverse.shared.generated.resources.duration_minutes
import newverse.shared.generated.resources.duration_one_day_hours
import newverse.shared.generated.resources.duration_one_hour_minutes
import newverse.shared.generated.resources.duration_one_minute
import newverse.shared.generated.resources.duration_under_one_minute
import org.jetbrains.compose.resources.stringResource

/**
 * How long is left before an order's edit deadline, in the app's language.
 *
 * [OrderDateUtils.formatTimeUntilDeadline] builds the same sentence out of hardcoded
 * English ("10 hours, 38 minutes"), which reached German buyers in the pickup picker.
 * A plain util cannot read Compose resources, so the wording lives here and the util
 * keeps only the arithmetic.
 */
@Composable
fun timeUntilDeadlineText(
    pickupDate: Instant,
    now: Instant = Clock.System.now(),
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
    config: OrderScheduleConfig = OrderDateUtils.scheduleConfigFor(null),
): String {
    val period = OrderDateUtils.timeUntilDeadline(pickupDate, now, timeZone, config)
        ?: return stringResource(Res.string.duration_deadline_passed)

    val days = period.days
    val hours = period.hours
    val minutes = period.minutes

    return when {
        days > 1 -> stringResource(Res.string.duration_days_hours, days, hours)
        days == 1 -> stringResource(Res.string.duration_one_day_hours, hours)
        hours > 1 -> stringResource(Res.string.duration_hours_minutes, hours, minutes)
        hours == 1 -> stringResource(Res.string.duration_one_hour_minutes, minutes)
        minutes > 1 -> stringResource(Res.string.duration_minutes, minutes)
        minutes == 1 -> stringResource(Res.string.duration_one_minute)
        else -> stringResource(Res.string.duration_under_one_minute)
    }
}
