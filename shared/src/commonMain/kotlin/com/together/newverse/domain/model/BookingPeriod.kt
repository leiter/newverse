package com.together.newverse.domain.model

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus

/**
 * A period of the books: a calendar week or a calendar month, in the seller's local
 * dates. A sale belongs to the period its confirmation falls into.
 */
sealed interface BookingPeriod {

    /** First day of the period. */
    val start: LocalDate

    /** First day after the period. */
    val endExclusive: LocalDate

    fun previous(): BookingPeriod
    fun next(): BookingPeriod

    /** The period as [from, until) epoch milliseconds, local midnight to local midnight. */
    fun startMillis(timeZone: TimeZone): Long = start.atStartOfDayIn(timeZone).toEpochMilliseconds()
    fun endMillis(timeZone: TimeZone): Long = endExclusive.atStartOfDayIn(timeZone).toEpochMilliseconds()

    operator fun contains(date: LocalDate): Boolean = date >= start && date < endExclusive

    /**
     * An ISO 8601 week — the German "KW": weeks start on Monday, and week 1 is the
     * week containing the year's first Thursday. So 1 Jan 2027, a Friday, still
     * belongs to KW 53 of 2026.
     */
    data class Week(val year: Int, val week: Int) : BookingPeriod {
        init {
            require(week in 1..53) { "ISO week $week out of range" }
        }

        override val start: LocalDate = mondayOfWeek1(year).plus((week - 1) * 7, DateTimeUnit.DAY)
        override val endExclusive: LocalDate get() = start.plus(7, DateTimeUnit.DAY)

        override fun previous(): Week = weekOf(start.minus(7, DateTimeUnit.DAY))
        override fun next(): Week = weekOf(start.plus(7, DateTimeUnit.DAY))
    }

    data class Month(val year: Int, val month: Int) : BookingPeriod {
        init {
            require(month in 1..12) { "Month $month out of range" }
        }

        override val start: LocalDate get() = LocalDate(year, month, 1)
        override val endExclusive: LocalDate get() = next().start

        override fun previous(): Month = if (month == 1) Month(year - 1, 12) else Month(year, month - 1)
        override fun next(): Month = if (month == 12) Month(year + 1, 1) else Month(year, month + 1)
    }

    companion object {
        fun weekOf(date: LocalDate): Week {
            val monday = date.minus(date.dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY)
            // The week belongs to the year its Thursday falls in.
            val thursday = monday.plus(3, DateTimeUnit.DAY)
            return Week(thursday.year, (thursday.dayOfYear - 1) / 7 + 1)
        }

        fun monthOf(date: LocalDate): Month = Month(date.year, date.month.number)

        /** Week 1 is the week containing 4 January. */
        private fun mondayOfWeek1(year: Int): LocalDate {
            val jan4 = LocalDate(year, 1, 4)
            return jan4.minus(jan4.dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY)
        }
    }
}
