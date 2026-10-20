package com.together.newverse.domain.reminder

import com.together.newverse.domain.model.Order
import com.together.newverse.domain.model.OrderOrigin
import com.together.newverse.domain.model.OrderStatus
import com.together.newverse.domain.model.RefillNeed
import com.together.newverse.domain.model.RefillState
import com.together.newverse.util.OrderDateUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant

class StockReminderSnapshotsTest {

    private val zone = TimeZone.of("Europe/Berlin")

    /** Monday 19 October 2026, 09:00 — well before that week's Thursday market. */
    private val now = LocalDate(2026, 10, 19).atTime(LocalTime(9, 0)).toInstant(zone)

    private fun need(
        name: String,
        state: RefillState = RefillState.SHORT,
        onHand: Double = 1.0,
        committed: Double = 5.0,
        reorderLevel: Double = 0.0
    ) = RefillNeed(
        articleId = name,
        productName = name,
        unit = "kg",
        onHand = onHand,
        reorderLevel = reorderLevel,
        state = state,
        hasLevel = true,
        committed = committed
    )

    private fun order(pickUpDate: Long, origin: OrderOrigin = OrderOrigin.PRODUCTION) = Order(
        id = "o$pickUpDate",
        status = OrderStatus.PLACED,
        origin = origin,
        pickUpDate = pickUpDate
    )

    private fun pickUp(day: Int) =
        LocalDate(2026, 10, day).atTime(LocalTime(10, 0)).toInstant(zone).toEpochMilliseconds()

    /**
     * The same day as the schedule produces it: [OrderDateUtils.calculateNextPickupDate]
     * returns the market *date*, so midnight, while an order carries whatever time it
     * was given.
     */
    private fun scheduledPickUp(day: Int) =
        LocalDate(2026, 10, day).atTime(LocalTime(0, 0)).toInstant(zone).toEpochMilliseconds()

    @Test
    fun `the list carries what to bring, in the order the screen shows it`() {
        val snapshot = StockReminderSnapshots.of(
            needs = listOf(need("Äpfel", committed = 13.0), need("Möhren", committed = 4.0)),
            orders = listOf(order(pickUp(22))),
            nowMillis = now.toEpochMilliseconds(),
            timeZone = zone
        )

        assertEquals(listOf("Äpfel", "Möhren"), snapshot.lines.map { it.productName })
        assertEquals(12.0, snapshot.lines.first().shortfall)
        assertEquals("kg", snapshot.lines.first().unit)
        assertTrue(snapshot.hasAnything)
    }

    @Test
    fun `the day is the next pickup the seller has an order for`() {
        val snapshot = StockReminderSnapshots.of(
            needs = listOf(need("Äpfel")),
            // Out of order, and one already past, to make sure the nearest future
            // one wins rather than the first in the list.
            orders = listOf(order(pickUp(29)), order(pickUp(22)), order(pickUp(15))),
            nowMillis = now.toEpochMilliseconds(),
            timeZone = zone
        )

        assertEquals(pickUp(22), snapshot.pickUpDate)
        assertEquals(
            OrderDateUtils.formatDateKey(Instant.fromEpochMilliseconds(pickUp(22)), zone),
            snapshot.cycle
        )
    }

    @Test
    fun `with no orders the schedule supplies the day`() {
        // A quiet week still has reorder levels to cover.
        val snapshot = StockReminderSnapshots.of(
            needs = listOf(need("Äpfel", state = RefillState.LOW, onHand = 2.0, committed = 0.0, reorderLevel = 5.0)),
            orders = emptyList(),
            nowMillis = now.toEpochMilliseconds(),
            timeZone = zone
        )

        assertEquals(scheduledPickUp(22), snapshot.pickUpDate)
        assertEquals(3.0, snapshot.lines.single().shortfall)
    }

    @Test
    fun `demo orders do not decide the market day`() {
        val snapshot = StockReminderSnapshots.of(
            needs = listOf(need("Äpfel")),
            orders = listOf(order(pickUp(20), origin = OrderOrigin.DEMO)),
            nowMillis = now.toEpochMilliseconds(),
            timeZone = zone
        )

        assertEquals(scheduledPickUp(22), snapshot.pickUpDate, "a demo order set the market day")
    }

    @Test
    fun `articles never counted are left off the list`() {
        // On a fresh ledger that is the whole catalogue, and a notification cannot
        // ask a question.
        val snapshot = StockReminderSnapshots.of(
            needs = listOf(
                need("Äpfel"),
                RefillNeed(
                    articleId = "nuts", productName = "Nüsse", unit = "kg",
                    onHand = 0.0, reorderLevel = 5.0, state = RefillState.UNCOUNTED, hasLevel = false
                )
            ),
            orders = listOf(order(pickUp(22))),
            nowMillis = now.toEpochMilliseconds(),
            timeZone = zone
        )

        assertEquals(listOf("Äpfel"), snapshot.lines.map { it.productName })
    }

    @Test
    fun `a need with no shortfall is left off too`() {
        // A unit mismatch lands here: it needs the stock screen, not a line saying 0.
        val mismatch = RefillNeed(
            articleId = "eggs", productName = "Eier", unit = "Stück",
            onHand = 20.0, reorderLevel = 6.0, state = RefillState.OK,
            hasLevel = true, unitMismatch = true
        )

        val snapshot = StockReminderSnapshots.of(
            needs = listOf(mismatch),
            orders = listOf(order(pickUp(22))),
            nowMillis = now.toEpochMilliseconds(),
            timeZone = zone
        )

        assertTrue(snapshot.lines.isEmpty())
        assertTrue(!snapshot.hasAnything, "a snapshot with no lines must not be postable")
    }

    @Test
    fun `nothing to bring is a snapshot with nothing to say`() {
        val snapshot = StockReminderSnapshots.of(
            needs = emptyList(),
            orders = listOf(order(pickUp(22))),
            nowMillis = now.toEpochMilliseconds(),
            timeZone = zone
        )

        // The cycle is still recorded — it is what cancels a stale armed reminder —
        // but there is nothing to post.
        assertEquals(pickUp(22), snapshot.pickUpDate)
        assertTrue(!snapshot.hasAnything)
    }
}
