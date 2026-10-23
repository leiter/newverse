package com.together.newverse.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant

class OrderShortfallsTest {

    private val zone = TimeZone.of("Europe/Berlin")

    // Pinned, not the real clock: these orders are collected on fixed October days,
    // and demand only counts an order whose pickup day is still ahead. Reading the
    // system clock here would make the suite start failing on 22 October 2026.
    private val now = LocalDate(2026, 10, 20).atTime(LocalTime(8, 0)).toInstant(zone)

    private fun pickUp(day: Int) =
        LocalDate(2026, 10, day).atTime(LocalTime(10, 0)).toInstant(zone).toEpochMilliseconds()

    private fun need(
        articleId: String,
        name: String = articleId,
        state: RefillState = RefillState.SHORT,
        onHand: Double = 1.0,
        committed: Double = 6.0,
        unitMismatch: Boolean = false
    ) = RefillNeed(
        articleId = articleId,
        productName = name,
        unit = "kg",
        onHand = onHand,
        reorderLevel = 0.0,
        state = state,
        hasLevel = true,
        committed = committed,
        unitMismatch = unitMismatch
    )

    private fun order(
        id: String,
        buyer: String = "Anna",
        buyerId: String = "buyer_$buyer",
        day: Int = 22,
        items: List<Pair<String, Double>> = listOf("apple" to 3.0),
        status: OrderStatus = OrderStatus.PLACED,
        origin: OrderOrigin = OrderOrigin.PRODUCTION
    ) = Order(
        id = id,
        buyerProfile = BuyerProfile(id = buyerId, displayName = buyer),
        status = status,
        origin = origin,
        pickUpDate = pickUp(day),
        articles = items.map { (articleId, amount) ->
            OrderedProduct(productId = articleId, productName = articleId, unit = "kg", amountCount = amount)
        }
    )

    @Test
    fun `an order holding a short article is listed with what is missing`() {
        val shortfalls = listOf(order("o1", items = listOf("apple" to 3.0)))
            .shortfallsAgainst(listOf(need("apple", "Äpfel", onHand = 1.0, committed = 6.0)), zone, now)

        val shortfall = shortfalls.single()
        assertEquals("o1", shortfall.orderId)
        assertEquals("Anna", shortfall.buyerName)
        assertEquals("buyer_Anna", shortfall.buyerId)
        assertEquals("20261022", shortfall.cycle)

        val line = shortfall.lines.single()
        assertEquals("Äpfel", line.productName)
        assertEquals(3.0, line.ordered)
        // The total gap, not this order's share of it.
        assertEquals(5.0, line.missingInTotal)
    }

    @Test
    fun `every order wanting the article is listed, with no share worked out`() {
        // 1 kg on the shelf, three buyers wanting 5 kg each. Who goes without is a
        // decision about customers, so all three appear and none is allocated.
        val orders = listOf(
            order("o1", buyer = "Anna", items = listOf("apple" to 5.0)),
            order("o2", buyer = "Bert", items = listOf("apple" to 5.0)),
            order("o3", buyer = "Cem", items = listOf("apple" to 5.0))
        )

        val shortfalls = orders.shortfallsAgainst(
            listOf(need("apple", "Äpfel", onHand = 1.0, committed = 15.0)), zone, now
        )

        assertEquals(listOf("Anna", "Bert", "Cem"), shortfalls.map { it.buyerName })
        assertTrue(shortfalls.all { it.lines.single().missingInTotal == 14.0 })
    }

    @Test
    fun `only the short lines of an order are reported`() {
        val shortfalls = listOf(order("o1", items = listOf("apple" to 3.0, "nuts" to 2.0)))
            .shortfallsAgainst(
                listOf(
                    need("apple", "Äpfel"),
                    need("nuts", "Nüsse", state = RefillState.OK, onHand = 50.0, committed = 2.0)
                ),
                zone,
                now
            )

        assertEquals(listOf("Äpfel"), shortfalls.single().lines.map { it.productName })
    }

    @Test
    fun `nothing short means nobody to talk to`() {
        val shortfalls = listOf(order("o1"))
            .shortfallsAgainst(listOf(need("apple", state = RefillState.LOW, onHand = 4.0, committed = 1.0)), zone, now)

        assertTrue(shortfalls.isEmpty())
    }

    @Test
    fun `a thin buffer is the seller's business, not the customer's`() {
        // LOW means the walk-in buffer is thin; every order can still be filled.
        val shortfalls = listOf(order("o1"))
            .shortfallsAgainst(listOf(need("apple", state = RefillState.LOW)), zone, now)

        assertTrue(shortfalls.isEmpty())
    }

    @Test
    fun `an uncounted article raises nobody`() {
        val shortfalls = listOf(order("o1"))
            .shortfallsAgainst(listOf(need("apple", state = RefillState.UNCOUNTED)), zone, now)

        assertTrue(shortfalls.isEmpty())
    }

    @Test
    fun `a unit mismatch is not reported to a customer`() {
        // The numbers are in doubt; telling a buyer their order is short on that
        // basis would be an apology for arithmetic nobody trusts.
        val shortfalls = listOf(order("o1"))
            .shortfallsAgainst(listOf(need("apple", unitMismatch = true)), zone, now)

        assertTrue(shortfalls.isEmpty())
    }

    @Test
    fun `orders the seller no longer owes are left out`() {
        val orders = listOf(
            order("done", status = OrderStatus.COMPLETED),
            order("gone", status = OrderStatus.CANCELLED),
            order("demo", origin = OrderOrigin.DEMO)
        )

        assertTrue(orders.shortfallsAgainst(listOf(need("apple")), zone, now).isEmpty())
    }

    @Test
    fun `the soonest pickup comes first`() {
        val orders = listOf(
            order("later", buyer = "Bert", day = 29),
            order("sooner", buyer = "Anna", day = 22)
        )

        val shortfalls = orders.shortfallsAgainst(listOf(need("apple", committed = 8.0)), zone, now)

        assertEquals(listOf("sooner", "later"), shortfalls.map { it.orderId })
    }

    // --- the draft ------------------------------------------------------------

    @Test
    fun `the draft names the buyer, the day and what is missing`() {
        val shortfall = listOf(order("o1", buyer = "Anna", items = listOf("apple" to 3.0)))
            .shortfallsAgainst(listOf(need("apple", "Äpfel")), zone, now)
            .single()

        val draft = draftShortfallMessage(shortfall, zone)

        assertTrue(draft.startsWith("Hallo Anna,"), draft)
        assertTrue(draft.contains("22.10."), draft)
        assertTrue(draft.contains("Äpfel"), draft)
        // What they ordered, not the shop's total gap: the total is the seller's
        // business and would read as a complaint.
        assertTrue(draft.contains("3"), draft)
        assertTrue(!draft.contains("5 kg"), draft)
    }

    @Test
    fun `a draft without a name still reads like a greeting`() {
        val shortfall = listOf(order("o1", buyer = "", buyerId = "b1"))
            .shortfallsAgainst(listOf(need("apple", "Äpfel")), zone, now)
            .single()

        assertTrue(draftShortfallMessage(shortfall, zone).startsWith("Hallo,"))
    }

    @Test
    fun `the draft promises only that the seller will be in touch`() {
        // It must not commit to a refund, a substitute or a delivery: what happens
        // next is the seller's decision, taken after they have been to the market.
        val shortfall = listOf(order("o1"))
            .shortfallsAgainst(listOf(need("apple", "Äpfel")), zone, now)
            .single()

        val draft = draftShortfallMessage(shortfall, zone)

        assertTrue(draft.contains("Ich melde mich"), draft)
    }
}
