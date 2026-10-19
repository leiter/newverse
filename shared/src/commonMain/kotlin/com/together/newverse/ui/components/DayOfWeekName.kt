package com.together.newverse.ui.components

import androidx.compose.runtime.Composable
import kotlinx.datetime.DayOfWeek
import org.jetbrains.compose.resources.stringResource
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.day_friday
import newverse.shared.generated.resources.day_monday
import newverse.shared.generated.resources.day_saturday
import newverse.shared.generated.resources.day_sunday
import newverse.shared.generated.resources.day_thursday
import newverse.shared.generated.resources.day_tuesday
import newverse.shared.generated.resources.day_wednesday

/**
 * The localized name of a weekday.
 *
 * The basket's date rows used to print `day_thursday` as a literal, which was true
 * only while every pickup was a Thursday. Once each market keeps its own day, a
 * Saturday slot labelled "Donnerstag" is worse than no label.
 */
@Composable
fun DayOfWeek.displayName(): String = stringResource(
    when (this) {
        DayOfWeek.MONDAY -> Res.string.day_monday
        DayOfWeek.TUESDAY -> Res.string.day_tuesday
        DayOfWeek.WEDNESDAY -> Res.string.day_wednesday
        DayOfWeek.THURSDAY -> Res.string.day_thursday
        DayOfWeek.FRIDAY -> Res.string.day_friday
        DayOfWeek.SATURDAY -> Res.string.day_saturday
        DayOfWeek.SUNDAY -> Res.string.day_sunday
        else -> Res.string.day_monday
    }
)
