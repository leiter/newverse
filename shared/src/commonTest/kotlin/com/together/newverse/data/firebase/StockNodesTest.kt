package com.together.newverse.data.firebase

import com.together.newverse.domain.model.MeasuredQuantity
import com.together.newverse.domain.model.QuantitySource
import com.together.newverse.domain.model.StockMovement
import com.together.newverse.domain.model.StockMovementKind
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StockNodesTest {

    private val berlin = TimeZone.of("Europe/Berlin")
    private val seller = "seller1"

    private fun movement(
        articleId: String = "apple",
        quantity: Double = 15.0,
        kind: StockMovementKind = StockMovementKind.INTAKE,
        at: Long = 1_791_500_000_000L,
        countedTo: Double? = null,
        source: QuantitySource = QuantitySource.SCALE,
        note: String = "",
        productId: String = "",
        saleId: String? = null
    ) = StockMovement(
        articleId = articleId,
        productId = productId,
        quantity = quantity,
        unit = "kg",
        kind = kind,
        source = source,
        recordedAt = at,
        countedTo = countedTo,
        note = note,
        saleId = saleId
    )

    // --- paths ----------------------------------------------------------------

    @Test
    fun `paths follow the documented layout`() {
        assertEquals("stock_movements/seller1/202610", StockNodes.monthPath(seller, "202610"))
        assertEquals("stock_movements/seller1/202610/m1", StockNodes.movementPath(seller, "202610", "m1"))
        assertEquals("stock/seller1", StockNodes.levelsPath(seller))
        assertEquals("stock/seller1/apple", StockNodes.levelPath(seller, "apple"))
        assertEquals("stock/seller1/apple/onHand", StockNodes.levelOnHandPath(seller, "apple"))
    }

    @Test
    fun `the month is the seller's local month`() {
        // 30 Sept 23:30 Berlin is already October in UTC, but belongs to September here.
        val lateSeptember = 1_790_890_200_000L
        assertEquals(
            StockNodes.monthKey(lateSeptember, berlin),
            SaleNodes.monthKey(lateSeptember, berlin)
        )
    }

    // --- writing --------------------------------------------------------------

    @Test
    fun `appending writes the movement under its month`() {
        val update = StockNodes.appendUpdate(seller, "m1", movement(), berlin)
        val month = StockNodes.monthKey(movement().recordedAt, berlin)
        assertEquals(setOf("stock_movements/seller1/$month/m1"), update.keys)
    }

    @Test
    fun `appending refuses a movement with no id or no article`() {
        val thrown = runCatching { StockNodes.appendUpdate(seller, "", movement(), berlin) }
        assertTrue(thrown.isFailure)
        val noArticle = runCatching {
            StockNodes.appendUpdate(seller, "m1", movement(articleId = ""), berlin)
        }
        assertTrue(noArticle.isFailure)
    }

    @Test
    fun `optional fields are left out rather than written empty`() {
        val map = StockNodes.movementToMap(movement())
        assertEquals(
            setOf("articleId", "quantity", "unit", "kind", "source", "recordedAt"),
            map.keys
        )
    }

    @Test
    fun `optional fields are written when they are known`() {
        val map = StockNodes.movementToMap(
            movement(productId = "112108", note = "Lieferung Terra", saleId = "sale_1")
        )
        assertEquals("112108", map["productId"])
        assertEquals("Lieferung Terra", map["note"])
        assertEquals("sale_1", map["saleId"])
    }

    @Test
    fun `enums are stored by name`() {
        val map = StockNodes.movementToMap(movement(kind = StockMovementKind.LOSS, source = QuantitySource.MANUAL))
        assertEquals("LOSS", map["kind"])
        assertEquals("MANUAL", map["source"])
    }

    // --- level changes --------------------------------------------------------

    @Test
    fun `an ordinary movement adjusts the level`() {
        val changes = StockNodes.levelChanges(listOf(movement(quantity = 15.0)))
        assertEquals(StockNodes.LevelChange.Adjust(15.0), changes["apple"])
    }

    @Test
    fun `a stocktake sets the level to what was counted`() {
        val changes = StockNodes.levelChanges(
            listOf(movement(kind = StockMovementKind.STOCKTAKE, quantity = -2.6, countedTo = 12.4))
        )
        assertEquals(StockNodes.LevelChange.SetTo(12.4), changes["apple"])
    }

    @Test
    fun `several movements of one article become one change`() {
        // The bug this guards: one path can be written once, so two movements of the
        // same article must arrive aggregated or one is silently dropped.
        val changes = StockNodes.levelChanges(
            listOf(
                movement(quantity = 15.0, at = 1L),
                movement(quantity = -2.0, at = 2L),
                movement(quantity = -0.5, at = 3L)
            )
        )
        assertEquals(1, changes.size)
        assertEquals(StockNodes.LevelChange.Adjust(12.5), changes["apple"])
    }

    @Test
    fun `movements after a count are added on top of it`() {
        val changes = StockNodes.levelChanges(
            listOf(
                movement(quantity = 15.0, at = 1L),
                movement(kind = StockMovementKind.STOCKTAKE, quantity = -2.6, countedTo = 12.4, at = 2L),
                movement(quantity = -0.4, at = 3L)
            )
        )
        assertEquals(StockNodes.LevelChange.SetTo(12.0), changes["apple"])
    }

    @Test
    fun `the newest count wins, whatever order they are passed in`() {
        val forward = listOf(
            movement(kind = StockMovementKind.STOCKTAKE, quantity = 0.0, countedTo = 5.0, at = 1L),
            movement(kind = StockMovementKind.STOCKTAKE, quantity = 0.0, countedTo = 9.0, at = 2L)
        )
        assertEquals(StockNodes.LevelChange.SetTo(9.0), StockNodes.levelChanges(forward)["apple"])
        assertEquals(StockNodes.LevelChange.SetTo(9.0), StockNodes.levelChanges(forward.reversed())["apple"])
    }

    @Test
    fun `each article gets its own change`() {
        val changes = StockNodes.levelChanges(
            listOf(
                movement(articleId = "apple", quantity = 15.0),
                movement(articleId = "pear", quantity = 4.0)
            )
        )
        assertEquals(StockNodes.LevelChange.Adjust(15.0), changes["apple"])
        assertEquals(StockNodes.LevelChange.Adjust(4.0), changes["pear"])
    }

    @Test
    fun `level fields come from the newest movement and never include onHand`() {
        val fields = StockNodes.levelFields(
            seller,
            listOf(
                movement(quantity = 15.0, at = 1L, source = QuantitySource.SCALE),
                movement(quantity = -2.0, at = 9L, source = QuantitySource.MANUAL)
            )
        )
        assertEquals("kg", fields["stock/seller1/apple/unit"])
        assertEquals(9L, fields["stock/seller1/apple/lastMovementAt"])
        assertEquals("MANUAL", fields["stock/seller1/apple/lastSource"])
        assertTrue(fields.keys.none { it.endsWith("/onHand") })
    }

    @Test
    fun `only a count sets lastCountedAt`() {
        val withoutCount = StockNodes.levelFields(seller, listOf(movement()))
        assertTrue("stock/seller1/apple/lastCountedAt" !in withoutCount)

        val withCount = StockNodes.levelFields(
            seller,
            listOf(movement(kind = StockMovementKind.STOCKTAKE, countedTo = 12.4, at = 7L))
        )
        assertEquals(7L, withCount["stock/seller1/apple/lastCountedAt"])
    }

    // --- reading --------------------------------------------------------------

    @Test
    fun `a movement survives a round trip`() {
        val original = movement(
            quantity = -0.5,
            kind = StockMovementKind.LOSS,
            source = QuantitySource.MANUAL,
            note = "verdorben",
            productId = "112108"
        )
        val read = StockNodes.movementFromMap("m1", StockNodes.movementToMap(original))
        assertEquals(original.copy(id = "m1"), read)
    }

    @Test
    fun `a stocktake survives a round trip`() {
        val original = movement(kind = StockMovementKind.STOCKTAKE, quantity = -2.6, countedTo = 12.4)
        val read = StockNodes.movementFromMap("m1", StockNodes.movementToMap(original))
        assertEquals(original.copy(id = "m1"), read)
    }

    @Test
    fun `a malformed movement reads as null rather than throwing`() {
        val good = StockNodes.movementToMap(movement())
        for (missing in listOf("articleId", "quantity", "unit", "kind", "source", "recordedAt")) {
            assertNull(StockNodes.movementFromMap("m1", good - missing), "missing $missing")
        }
        assertNull(StockNodes.movementFromMap("m1", good + ("articleId" to "")))
        assertNull(StockNodes.movementFromMap("m1", good + ("unit" to "")))
        assertNull(StockNodes.movementFromMap("m1", good + ("kind" to "SHRINKAGE")))
        assertNull(StockNodes.movementFromMap("m1", good + ("source" to "GUESS")))
    }

    @Test
    fun `a stocktake without a counted value reads as null`() {
        // StockMovement's init block cannot guard what is already in the database.
        val map = StockNodes.movementToMap(movement()) + ("kind" to "STOCKTAKE")
        assertNull(StockNodes.movementFromMap("m1", map))
    }

    @Test
    fun `whole numbers coming back as Long still read`() {
        val map = mapOf(
            "articleId" to "apple", "quantity" to 15L, "unit" to "kg",
            "kind" to "INTAKE", "source" to "SCALE", "recordedAt" to 1_791_500_000_000L
        )
        assertEquals(15.0, StockNodes.movementFromMap("m1", map)?.quantity)
    }

    @Test
    fun `a level reads back with its provenance`() {
        val level = StockNodes.levelFromMap(
            "apple",
            mapOf(
                "onHand" to 12.4, "unit" to "kg",
                "lastMovementAt" to 9L, "lastCountedAt" to 7L, "lastSource" to "SCALE"
            )
        )!!
        assertEquals(12.4, level.onHand)
        assertEquals("kg", level.unit)
        assertEquals(9L, level.lastMovementAt)
        assertEquals(7L, level.lastCountedAt)
        assertEquals(QuantitySource.SCALE, level.lastSource)
        assertTrue(level.wasEverCounted)
    }

    @Test
    fun `a level without an amount or unit reads as null`() {
        assertNull(StockNodes.levelFromMap("apple", mapOf("unit" to "kg")))
        assertNull(StockNodes.levelFromMap("apple", mapOf("onHand" to 1.0)))
    }

    @Test
    fun `a movement built from a weighing keeps the scale as its source`() {
        val weighed = MeasuredQuantity(2.5, "kg", QuantitySource.SCALE, measuredAt = 500L)
        val intake = StockMovement.intake("apple", weighed, recordedAt = 9_999L)
        val map = StockNodes.movementToMap(intake)
        assertEquals("SCALE", map["source"])
        // The weighing's own time wins over the time it was booked.
        assertEquals(500L, map["recordedAt"])
    }
}
