package com.together.newverse.domain.model

import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.time.Instant

/**
 * The booked sales of a period as a CSV file for the tax advisor.
 *
 * German spreadsheet conventions, so it opens correctly in Excel and LibreOffice
 * with a double click: semicolon separated, decimal comma, dd.MM.yyyy dates, UTF-8
 * with byte order mark, CRLF line ends. One row per sold article; a cancellation
 * is a row of its own with negative quantity and amounts, pointing at the sale it
 * cancels. Amounts are exactly the cents of the sale lines, so the column sums
 * match the Abrechnung.
 *
 * Receipt and order numbers are the database ids with a letter in front — "V" for
 * Verkauf, "B" for Bestellung. The ids start with "-", which Excel takes for the
 * start of a formula and shows as #NAME?.
 */
object SalesCsv {

    private const val BOM = "\uFEFF"   // UTF-8 byte order mark: Excel then reads umlauts right
    private const val SEPARATOR = ";"
    private const val LINE_END = "\r\n"

    val HEADER = listOf(
        "Datum", "Beleg", "Storno zu Beleg", "Bestellung", "Artikelnr", "Artikel",
        "Menge", "Einheit", "Einzelpreis brutto", "MwSt-Satz %", "Netto", "MwSt", "Brutto"
    )

    fun write(sales: List<Sale>, timeZone: TimeZone): String = buildString {
        append(BOM)
        appendRow(HEADER)
        sales.sortedWith(compareBy({ it.confirmedAt }, { it.id })).forEach { sale ->
            val date = formatDate(sale.confirmedAt, timeZone)
            sale.lines.forEach { line ->
                appendRow(
                    listOf(
                        date,
                        receiptNumber(sale.id),
                        sale.reverses?.let(::receiptNumber).orEmpty(),
                        "B${sale.orderId}",
                        text(line.productId),
                        text(line.productName),
                        formatQuantity(line.quantity),
                        text(line.unit),
                        Money.formatCents(line.unitPriceCents),
                        formatRate(line.taxRate),
                        Money.formatCents(line.netCents),
                        Money.formatCents(line.vatCents),
                        Money.formatCents(line.grossCents)
                    )
                )
            }
        }
    }

    /** "Verkaeufe_2026-KW39.csv", "Verkaeufe_2026-09.csv" — ASCII only, safe for mail and file systems. */
    fun fileName(period: BookingPeriod): String = when (period) {
        is BookingPeriod.Week -> "Verkaeufe_${period.year}-KW${period.week.toString().padStart(2, '0')}.csv"
        is BookingPeriod.Month -> "Verkaeufe_${period.year}-${period.month.toString().padStart(2, '0')}.csv"
    }

    /** The receipt number a sale is listed under. */
    fun receiptNumber(saleId: String): String = "V$saleId"

    /**
     * Free text that a spreadsheet must not run as a formula (OWASP CSV injection):
     * a leading =, +, -, @ or tab gets an apostrophe in front.
     */
    internal fun text(value: String): String =
        if (value.isNotEmpty() && value[0] in "=+-@\t") "'$value" else value

    private fun StringBuilder.appendRow(fields: List<String>) {
        append(fields.joinToString(SEPARATOR) { escape(it) })
        append(LINE_END)
    }

    /** Quotes a field that contains the separator, a quote or a line break. */
    internal fun escape(field: String): String =
        if (field.any { it == ';' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + field.replace("\"", "\"\"") + "\""
        } else field

    private fun formatDate(epochMillis: Long, timeZone: TimeZone): String {
        val date = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(timeZone).date
        return "${date.day.toString().padStart(2, '0')}.${date.month.number.toString().padStart(2, '0')}.${date.year}"
    }

    /** Up to three decimals (grams), no trailing zeros: "1,62", "2", "-0,5". */
    internal fun formatQuantity(quantity: Double): String {
        val thousandths = Money.roundHalfAwayFromZero(quantity * 1000)
        val sign = if (thousandths < 0) "-" else ""
        val whole = abs(thousandths) / 1000
        val fraction = (abs(thousandths) % 1000).toString().padStart(3, '0').trimEnd('0')
        return if (fraction.isEmpty()) "$sign$whole" else "$sign$whole,$fraction"
    }

    /** 0.07 → "7", 0.19 → "19", 0.055 → "5,5". */
    internal fun formatRate(taxRate: Double): String {
        val tenths = Money.roundHalfAwayFromZero(taxRate * 1000)
        return if (tenths % 10 == 0L) "${tenths / 10}" else "${tenths / 10},${abs(tenths % 10)}"
    }
}
