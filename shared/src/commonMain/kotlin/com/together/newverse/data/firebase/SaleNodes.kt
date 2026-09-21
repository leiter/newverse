package com.together.newverse.data.firebase

import com.together.newverse.domain.model.Sale
import com.together.newverse.domain.model.SaleLine
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * How sales are laid out in the Realtime Database. Both nodes are seller-only.
 *
 * ```
 * /sales/{sellerId}/{yyyyMM}/{saleId}           the sale, grouped by booking month
 * /sale_index/{sellerId}/{orderId}/{saleId}     = yyyyMM, which sales belong to an order
 * ```
 *
 * A sale is written once, together with its index entry, in one multi-path update;
 * the database rules refuse to change or delete either afterwards. The month is the
 * seller's local month of [Sale.confirmedAt], so a monthly export reads one node.
 *
 * Pure functions only — no Firebase types — so all of it is unit-testable.
 */
internal object SaleNodes {

    const val SALES_ROOT = "sales"
    const val INDEX_ROOT = "sale_index"

    fun monthPath(sellerId: String, month: String) = "$SALES_ROOT/$sellerId/$month"
    fun salePath(sellerId: String, month: String, saleId: String) = "${monthPath(sellerId, month)}/$saleId"
    fun orderIndexPath(sellerId: String, orderId: String) = "$INDEX_ROOT/$sellerId/$orderId"

    /** The booking month of an instant, "yyyyMM", in the seller's time zone. */
    fun monthKey(epochMillis: Long, timeZone: TimeZone): String {
        val date = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(timeZone).date
        return "${date.year}${date.month.number.toString().padStart(2, '0')}"
    }

    /**
     * Every month touched by the period [fromMillis, untilMillis) — the month nodes
     * to read for a week or month export. A week can span two months.
     */
    fun monthKeys(fromMillis: Long, untilMillis: Long, timeZone: TimeZone): List<String> {
        require(untilMillis > fromMillis) { "Empty period" }
        val first = monthKey(fromMillis, timeZone)
        val last = monthKey(untilMillis - 1, timeZone)
        val keys = mutableListOf<String>()
        var year = first.take(4).toInt()
        var month = first.takeLast(2).toInt()
        while (true) {
            val key = "$year${month.toString().padStart(2, '0')}"
            keys += key
            if (key == last) return keys
            month++
            if (month > 12) { month = 1; year++ }
        }
    }

    /** The update map that records [sale] under [saleId]: the sale and its index entry. */
    fun recordUpdate(sellerId: String, saleId: String, sale: Sale, timeZone: TimeZone): Map<String, Any?> {
        require(saleId.isNotEmpty()) { "saleId must be assigned before building the update" }
        require(sale.orderId.isNotEmpty()) { "A sale needs the order it belongs to" }
        require(sale.lines.isNotEmpty()) { "A sale without lines books nothing" }
        val month = monthKey(sale.confirmedAt, timeZone)
        return mapOf(
            salePath(sellerId, month, saleId) to saleToMap(sale),
            "${orderIndexPath(sellerId, sale.orderId)}/$saleId" to month
        )
    }

    fun saleToMap(sale: Sale): Map<String, Any?> = buildMap {
        put("orderId", sale.orderId)
        put("confirmedAt", sale.confirmedAt)
        put("pickUpDate", sale.pickUpDate)
        // The database hands integer-keyed children back as a list; the reader
        // accepts both shapes.
        put("lines", sale.lines.withIndex().associate { (i, line) -> i.toString() to lineToMap(line) })
        sale.reverses?.let { put("reverses", it) }
    }

    private fun lineToMap(line: SaleLine): Map<String, Any?> = buildMap {
        put("articleId", line.articleId)
        put("productId", line.productId)
        put("productName", line.productName)
        put("unit", line.unit)
        put("quantity", line.quantity)
        put("unitPriceCents", line.unitPriceCents)
        put("taxRate", line.taxRate)
        line.acquirePriceCents?.let { put("acquirePriceCents", it) }
    }

    /**
     * Reads a stored sale, or null if it is malformed. A sale with an unreadable line
     * is rejected whole: dropping the line would silently understate the books.
     */
    fun saleFromMap(saleId: String, value: Map<*, *>): Sale? {
        val orderId = value["orderId"] as? String ?: return null
        val rawLines = when (val raw = value["lines"]) {
            is Map<*, *> -> raw.entries
                .sortedBy { (it.key as? String)?.toIntOrNull() ?: Int.MAX_VALUE }
                .map { it.value }
            is List<*> -> raw.filterNotNull()
            else -> return null
        }
        val lines = rawLines.map { raw -> (raw as? Map<*, *>)?.let(::lineFromMap) ?: return null }
        if (lines.isEmpty()) return null

        return Sale(
            id = saleId,
            orderId = orderId,
            confirmedAt = value.long("confirmedAt") ?: return null,
            pickUpDate = value.long("pickUpDate") ?: 0L,
            lines = lines,
            reverses = value["reverses"] as? String
        )
    }

    private fun lineFromMap(value: Map<*, *>): SaleLine? = SaleLine(
        articleId = value["articleId"] as? String ?: "",
        productId = value["productId"] as? String ?: "",
        productName = value["productName"] as? String ?: "",
        unit = value["unit"] as? String ?: "",
        quantity = (value["quantity"] as? Number)?.toDouble() ?: return null,
        unitPriceCents = value.long("unitPriceCents") ?: return null,
        taxRate = (value["taxRate"] as? Number)?.toDouble() ?: return null,
        acquirePriceCents = value.long("acquirePriceCents")
    )

    // The database returns whole numbers as Long, but a Double can come back for
    // values it stored as floating point.
    private fun Map<*, *>.long(key: String): Long? = (this[key] as? Number)?.toLong()
}
