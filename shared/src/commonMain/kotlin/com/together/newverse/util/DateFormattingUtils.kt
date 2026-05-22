package com.together.newverse.util

import kotlinx.datetime.*

enum class DateFormat {
    Short,
    Long,
    WithDayTime
}

object DateFormattingUtils {

    fun formatLocalizedDate(
        timeStamp: Long,
        locale: String = "de",
        timeZone: String = "Europe/Berlin",
        format: DateFormat = DateFormat.Short
    ): String {
        return try {
            val instant = Instant.fromEpochMilliseconds(timeStamp)
            val tz = TimeZone.of(timeZone)
            val localDateTime = instant.toLocalDateTime(tz)

            when (format) {
                DateFormat.Short -> formatShort(localDateTime, locale)
                DateFormat.Long -> formatLong(localDateTime, locale)
                DateFormat.WithDayTime -> formatWithDayTime(localDateTime, locale)
            }
        } catch (e: Exception) {
            ""
        }
    }

    private fun formatShort(dateTime: LocalDateTime, locale: String): String {
        val day = dateTime.dayOfMonth.toString().padStart(2, '0')
        val monthNum = dateTime.month.number.toString().padStart(2, '0')
        val year = dateTime.year.toString().takeLast(2)

        return when (locale) {
            "de" -> "$day.$monthNum.$year"
            "en" -> "$monthNum/$day/$year"
            else -> "$day.$monthNum.$year"
        }
    }

    private fun formatLong(dateTime: LocalDateTime, locale: String): String {
        val dayNum = dateTime.dayOfMonth
        val monthName = getMonthName(dateTime.month.number, locale)
        val year = dateTime.year

        return when (locale) {
            "de" -> "$dayNum. $monthName $year"
            "en" -> "$monthName $dayNum, $year"
            else -> "$dayNum. $monthName $year"
        }
    }

    private fun formatWithDayTime(dateTime: LocalDateTime, locale: String): String {
        val dayNum = dateTime.dayOfMonth
        val monthName = getMonthName(dateTime.month.number, locale)
        val year = dateTime.year
        val dayName = getDayOfWeekName(dateTime.dayOfWeek, locale)
        val hour = dateTime.hour.toString().padStart(2, '0')
        val minute = dateTime.minute.toString().padStart(2, '0')

        return when (locale) {
            "de" -> "$dayName, $dayNum. $monthName $year, $hour:$minute Uhr"
            "en" -> "$dayName, $monthName $dayNum, $year at $hour:$minute"
            else -> "$dayName, $dayNum. $monthName $year, $hour:$minute"
        }
    }

    private fun getMonthName(monthNumber: Int, locale: String): String {
        return when (locale) {
            "de" -> when (monthNumber) {
                1 -> "Januar"
                2 -> "Februar"
                3 -> "März"
                4 -> "April"
                5 -> "Mai"
                6 -> "Juni"
                7 -> "Juli"
                8 -> "August"
                9 -> "September"
                10 -> "Oktober"
                11 -> "November"
                12 -> "Dezember"
                else -> ""
            }
            "en" -> when (monthNumber) {
                1 -> "January"
                2 -> "February"
                3 -> "March"
                4 -> "April"
                5 -> "May"
                6 -> "June"
                7 -> "July"
                8 -> "August"
                9 -> "September"
                10 -> "October"
                11 -> "November"
                12 -> "December"
                else -> ""
            }
            else -> when (monthNumber) {
                1 -> "Januar"
                2 -> "Februar"
                3 -> "März"
                4 -> "April"
                5 -> "Mai"
                6 -> "Juni"
                7 -> "Juli"
                8 -> "August"
                9 -> "September"
                10 -> "Oktober"
                11 -> "November"
                12 -> "Dezember"
                else -> ""
            }
        }
    }

    private fun getDayOfWeekName(dayOfWeek: DayOfWeek, locale: String): String {
        return when (locale) {
            "de" -> when (dayOfWeek) {
                DayOfWeek.MONDAY -> "Montag"
                DayOfWeek.TUESDAY -> "Dienstag"
                DayOfWeek.WEDNESDAY -> "Mittwoch"
                DayOfWeek.THURSDAY -> "Donnerstag"
                DayOfWeek.FRIDAY -> "Freitag"
                DayOfWeek.SATURDAY -> "Samstag"
                DayOfWeek.SUNDAY -> "Sonntag"
            }
            "en" -> when (dayOfWeek) {
                DayOfWeek.MONDAY -> "Monday"
                DayOfWeek.TUESDAY -> "Tuesday"
                DayOfWeek.WEDNESDAY -> "Wednesday"
                DayOfWeek.THURSDAY -> "Thursday"
                DayOfWeek.FRIDAY -> "Friday"
                DayOfWeek.SATURDAY -> "Saturday"
                DayOfWeek.SUNDAY -> "Sunday"
            }
            else -> when (dayOfWeek) {
                DayOfWeek.MONDAY -> "Montag"
                DayOfWeek.TUESDAY -> "Dienstag"
                DayOfWeek.WEDNESDAY -> "Mittwoch"
                DayOfWeek.THURSDAY -> "Donnerstag"
                DayOfWeek.FRIDAY -> "Freitag"
                DayOfWeek.SATURDAY -> "Samstag"
                DayOfWeek.SUNDAY -> "Sonntag"
            }
        }
    }
}
