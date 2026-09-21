package com.together.newverse.data.firebase

import com.together.newverse.domain.model.Sale
import com.together.newverse.domain.model.SaleLine
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SaleNodesTest {

    private val berlin = TimeZone.of("Europe/Berlin")

    private fun millis(year: Int, month: Int, day: Int, hour: Int = 12, minute: Int = 0) =
        LocalDateTime(year, month, day, hour, minute).toInstant(berlin).toEpochMilliseconds()

    private val line = SaleLine(
        articleId = "apple", productId = "112108", productName = "Apfel Topaz", unit = "kg",
        quantity = 1.62, unitPriceCents = 319, taxRate = 0.07, acquirePriceCents = 196
    )

    private val sale = Sale(
        orderId = "order_1",
        confirmedAt = millis(2026, 9, 24, 17, 30),
        pickUpDate = millis(2026, 9, 24, 16),
        lines = listOf(line, line.copy(articleId = "sweet", quantity = 1.0, unitPriceCents = 462,
            taxRate = 0.19, acquirePriceCents = null))
    )

    // --- months ---

    @Test
    fun `booking month is the seller's local month`() {
        // 30 Sep 23:30 in Berlin is 21:30 UTC — still September locally.
        assertEquals("202609", SaleNodes.monthKey(millis(2026, 9, 30, 23, 30), berlin))
        assertEquals("202610", SaleNodes.monthKey(millis(2026, 10, 1, 0, 30), berlin))
    }

    @Test
    fun `a week spanning two months reads both`() {
        // KW 40/2026: Mon 28 Sep – Mon 5 Oct
        assertEquals(
            listOf("202609", "202610"),
            SaleNodes.monthKeys(millis(2026, 9, 28, 0), millis(2026, 10, 5, 0), berlin)
        )
    }

    @Test
    fun `a calendar month reads one node`() {
        assertEquals(
            listOf("202609"),
            SaleNodes.monthKeys(millis(2026, 9, 1, 0), millis(2026, 10, 1, 0), berlin)
        )
    }

    @Test
    fun `a period across the year end`() {
        assertEquals(
            listOf("202612", "202701"),
            SaleNodes.monthKeys(millis(2026, 12, 28, 0), millis(2027, 1, 4, 0), berlin)
        )
    }

    // --- writing ---

    @Test
    fun `a sale is recorded with its index entry`() {
        val update = SaleNodes.recordUpdate("seller1", "s1", sale, berlin)

        assertEquals(setOf("sales/seller1/202609/s1", "sale_index/seller1/order_1/s1"), update.keys)
        assertEquals("202609", update["sale_index/seller1/order_1/s1"])
    }

    @Test
    fun `a sale without lines or order is refused`() {
        assertFailsWith<IllegalArgumentException> {
            SaleNodes.recordUpdate("seller1", "s1", sale.copy(lines = emptyList()), berlin)
        }
        assertFailsWith<IllegalArgumentException> {
            SaleNodes.recordUpdate("seller1", "s1", sale.copy(orderId = ""), berlin)
        }
        assertFailsWith<IllegalArgumentException> {
            SaleNodes.recordUpdate("seller1", "", sale, berlin)
        }
    }

    // --- reading ---

    @Test
    fun `a sale round-trips`() {
        val stored = SaleNodes.saleToMap(sale)

        assertEquals(sale.copy(id = "s1"), SaleNodes.saleFromMap("s1", stored))
    }

    @Test
    fun `a cancellation round-trips`() {
        val storno = sale.copy(id = "s1").reversal(confirmedAt = millis(2026, 9, 25))

        assertEquals(storno.copy(id = "s2"), SaleNodes.saleFromMap("s2", SaleNodes.saleToMap(storno)))
    }

    @Test
    fun `lines read back as a list keep their order`() {
        // The database returns integer-keyed children as a list.
        val stored = SaleNodes.saleToMap(sale).toMutableMap()
        stored["lines"] = (stored["lines"] as Map<*, *>).entries.sortedBy { it.key as String }.map { it.value }

        assertEquals(listOf("apple", "sweet"), SaleNodes.saleFromMap("s1", stored)?.lines?.map { it.articleId })
    }

    @Test
    fun `whole numbers stored as Double are read`() {
        val stored = SaleNodes.saleToMap(sale).toMutableMap()
        stored["confirmedAt"] = (stored["confirmedAt"] as Long).toDouble()

        assertEquals(sale.confirmedAt, SaleNodes.saleFromMap("s1", stored)?.confirmedAt)
    }

    @Test
    fun `a sale with an unreadable line is rejected whole`() {
        val stored = SaleNodes.saleToMap(sale).toMutableMap()
        stored["lines"] = mapOf("0" to mapOf("articleId" to "apple"), "1" to (stored["lines"] as Map<*, *>)["1"])

        assertNull(SaleNodes.saleFromMap("s1", stored), "a half-read sale would understate the books")
    }

    @Test
    fun `a sale without lines or order is unreadable`() {
        assertNull(SaleNodes.saleFromMap("s1", SaleNodes.saleToMap(sale) - "lines"))
        assertNull(SaleNodes.saleFromMap("s1", SaleNodes.saleToMap(sale) - "orderId"))
    }

    @Test
    fun `unknown purchase price stays unknown`() {
        val read = SaleNodes.saleFromMap("s1", SaleNodes.saleToMap(sale))

        assertNull(read?.lines?.get(1)?.acquirePriceCents)
        assertTrue(SaleNodes.saleToMap(sale).let { (it["lines"] as Map<*, *>)["1"] as Map<*, *> }
            .containsKey("acquirePriceCents").not())
    }
}
