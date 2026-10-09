package com.together.newverse.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StockMovementTest {

    private val apples = "art_apples"

    private fun weighed(value: Double, at: Long = 1_000L, unit: String = "kg") =
        MeasuredQuantity(value, unit, QuantitySource.SCALE, measuredAt = at)

    @Test
    fun `intake adds, loss subtracts`() {
        val movements = listOf(
            StockMovement.intake(apples, weighed(15.0), recordedAt = 1_000L),
            StockMovement.loss(apples, weighed(0.5), recordedAt = 2_000L)
        )

        val level = movements.levelOf(apples)!!
        assertEquals(14.5, level.onHand)
        assertEquals("kg", level.unit)
    }

    @Test
    fun `stocktake stores the difference and sums to what was found`() {
        val intake = StockMovement.intake(apples, weighed(15.0), recordedAt = 1_000L)
        val onHand = listOf(intake).levelOf(apples)!!.onHand

        val count = StockMovement.stocktake(
            articleId = apples,
            found = weighed(12.4, at = 3_000L),
            previousOnHand = onHand,
            recordedAt = 3_000L
        )

        // The movement holds only the correction...
        assertEquals(-2.6, count.quantity)
        assertEquals(12.4, count.countedTo)
        // ...so the ledger still sums to the counted level.
        assertEquals(12.4, listOf(intake, count).levelOf(apples)!!.onHand)
    }

    @Test
    fun `a stocktake must say what was found`() {
        assertFailsWith<IllegalArgumentException> {
            StockMovement(
                articleId = apples,
                quantity = -1.0,
                unit = "kg",
                kind = StockMovementKind.STOCKTAKE,
                source = QuantitySource.MANUAL,
                recordedAt = 1_000L
            )
        }
    }

    @Test
    fun `a movement without a unit is refused`() {
        assertFailsWith<IllegalArgumentException> {
            StockMovement(
                articleId = apples,
                quantity = 1.0,
                unit = "",
                kind = StockMovementKind.INTAKE,
                source = QuantitySource.MANUAL,
                recordedAt = 1_000L
            )
        }
    }

    @Test
    fun `a sale takes its lines out of storage and its cancellation puts them back`() {
        val sale = Sale(
            id = "sale_1",
            orderId = "order_1",
            confirmedAt = 5_000L,
            pickUpDate = 5_000L,
            lines = listOf(
                SaleLine(apples, "301", "Äpfel", "kg", quantity = 2.0, unitPriceCents = 350, taxRate = 0.07)
            )
        )

        val out = StockMovement.forSale(sale)
        assertEquals(1, out.size)
        assertEquals(-2.0, out.single().quantity)
        assertEquals("sale_1", out.single().saleId)
        assertEquals(StockMovementKind.SALE, out.single().kind)

        val back = StockMovement.forSale(sale.reversal(confirmedAt = 6_000L))
        assertEquals(2.0, back.single().quantity)

        // Stock after a sale and its cancellation is what it was before.
        val stocked = StockMovement.intake(apples, weighed(10.0), recordedAt = 1_000L)
        assertEquals(10.0, (listOf(stocked) + out + back).levelOf(apples)!!.onHand)
    }

    @Test
    fun `a level reports its newest movement and its newest count`() {
        val movements = listOf(
            StockMovement.intake(apples, weighed(15.0), recordedAt = 1_000L),
            StockMovement.stocktake(apples, weighed(12.4, at = 3_000L), previousOnHand = 15.0, recordedAt = 3_000L),
            StockMovement.loss(
                apples,
                MeasuredQuantity(0.4, "kg", QuantitySource.MANUAL, measuredAt = 4_000L),
                recordedAt = 4_000L
            )
        )

        val level = movements.levelOf(apples)!!
        assertEquals(12.0, level.onHand)
        assertEquals(4_000L, level.lastMovementAt)
        assertEquals(3_000L, level.lastCountedAt)
        assertEquals(QuantitySource.MANUAL, level.lastSource)
        assertTrue(level.wasEverCounted)
    }

    @Test
    fun `an article never counted says so`() {
        val level = listOf(StockMovement.intake(apples, weighed(3.0), recordedAt = 1L)).levelOf(apples)!!
        assertNull(level.lastCountedAt)
        assertTrue(!level.wasEverCounted)
    }

    @Test
    fun `levels are kept per article`() {
        val movements = listOf(
            StockMovement.intake(apples, weighed(15.0), recordedAt = 1_000L),
            StockMovement.intake("art_pears", weighed(4.0), recordedAt = 1_000L)
        )

        val levels = movements.toLevels()
        assertEquals(2, levels.size)
        assertEquals(15.0, levels[apples]!!.onHand)
        assertEquals(4.0, levels["art_pears"]!!.onHand)
    }

    @Test
    fun `an unknown article has no level`() {
        assertNull(emptyList<StockMovement>().levelOf(apples))
    }

    @Test
    fun `summing stays at gram precision`() {
        val movements = (1..3).map {
            StockMovement.intake(apples, weighed(0.1), recordedAt = it.toLong())
        }
        assertEquals(0.3, movements.levelOf(apples)!!.onHand)
    }

    @Test
    fun `a weighed amount is only usable once it settles`() {
        val settling = MeasuredQuantity(1.2, "kg", QuantitySource.SCALE, measuredAt = 1L, isSettled = false)
        assertTrue(!settling.isUsable)
        assertTrue(settling.copy(isSettled = true).isUsable)
        assertTrue(!MeasuredQuantity.typed(0.0, "kg").isUsable)
    }

    @Test
    fun `a reading fills a quantity field the way the seller would type it`() {
        assertEquals("1.5", weighed(1.5).toInputString())
        assertEquals("2", weighed(2.0).toInputString())
        assertEquals("0.25", weighed(0.25).toInputString())
    }
}
