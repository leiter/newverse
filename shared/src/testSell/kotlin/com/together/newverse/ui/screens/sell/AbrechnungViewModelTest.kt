package com.together.newverse.ui.screens.sell

import com.together.newverse.domain.model.Article
import com.together.newverse.domain.model.BookingPeriod
import com.together.newverse.domain.model.Order
import com.together.newverse.domain.model.OrderStatus
import com.together.newverse.domain.model.OrderedProduct
import com.together.newverse.domain.model.Sale
import com.together.newverse.domain.model.SaleLine
import com.together.newverse.domain.model.SellerArticle
import com.together.newverse.domain.model.SellerArticleData
import com.together.newverse.domain.model.TaxRate
import com.together.newverse.test.FakeAuthRepository
import com.together.newverse.test.FakeOrderRepository
import com.together.newverse.test.FakeSaleRepository
import com.together.newverse.test.FakeSellerArticleRepository
import com.together.newverse.test.MainDispatcherRule
import com.together.newverse.ui.state.core.AsyncState
import com.together.newverse.util.OrderDateUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * "Zeitraum" is the books: booked sales of a calendar week or month, with the VAT
 * rate and purchase price each sale carries. "Nächste Abholung" is a forecast of open
 * orders, priced from the seller's catalog.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AbrechnungViewModelTest {

    private val dispatcherRule = MainDispatcherRule()
    private lateinit var articleRepository: FakeSellerArticleRepository
    private lateinit var orderRepository: FakeOrderRepository
    private lateinit var saleRepository: FakeSaleRepository
    private lateinit var authRepository: FakeAuthRepository

    private val berlin = TimeZone.of("Europe/Berlin")
    private val today = LocalDate(2026, 9, 24)

    @BeforeTest
    fun setup() {
        dispatcherRule.setup()
        articleRepository = FakeSellerArticleRepository()
        orderRepository = FakeOrderRepository()
        saleRepository = FakeSaleRepository()
        authRepository = FakeAuthRepository()
        authRepository.setCurrentUserId("seller_123")
    }

    @AfterTest
    fun tearDown() {
        articleRepository.reset()
        orderRepository.reset()
        saleRepository.reset()
        authRepository.reset()
        dispatcherRule.tearDown()
    }

    private fun createViewModel() = AbrechnungViewModel(
        sellerArticleRepository = articleRepository,
        orderRepository = orderRepository,
        authRepository = authRepository,
        saleRepository = saleRepository,
        timeZone = berlin,
        today = { today }
    )

    private fun millis(year: Int, month: Int, day: Int, hour: Int = 17) =
        LocalDateTime(year, month, day, hour, 0).toInstant(berlin).toEpochMilliseconds()

    private fun saleLine(
        quantity: Double = 1.0,
        unitPriceCents: Long,
        taxRate: Double = TaxRate.REDUCED.rate,
        acquirePriceCents: Long? = null
    ) = SaleLine("a", "112108", "Apfel", "kg", quantity, unitPriceCents, taxRate, acquirePriceCents)

    private fun sale(id: String, confirmedAt: Long, vararg lines: SaleLine) =
        Sale(id = id, orderId = "order_$id", confirmedAt = confirmedAt, pickUpDate = confirmedAt, lines = lines.toList())

    private fun periodSummary(viewModel: AbrechnungViewModel): PeriodSummary {
        val state = viewModel.periodSummary.value
        assertIs<AsyncState.Success<PeriodSummary>>(state)
        return state.data
    }

    // ===== Zeitraum: the books =====

    @Test
    fun `the period starts at the current month`() = runTest {
        val viewModel = createViewModel()

        assertEquals(BookingPeriod.Month(2026, 9), viewModel.period.value)
    }

    @Test
    fun `the period sums the booked sales`() = runTest {
        saleRepository.setSales(listOf(
            sale("s1", millis(2026, 9, 3), saleLine(quantity = 1.62, unitPriceCents = 319, acquirePriceCents = 196)),
            sale("s2", millis(2026, 9, 10), saleLine(unitPriceCents = 462, taxRate = TaxRate.STANDARD.rate,
                acquirePriceCents = 268))
        ))

        val viewModel = createViewModel()
        advanceUntilIdle()

        val summary = periodSummary(viewModel)
        assertEquals(2, summary.saleCount)
        with(summary.financials) {
            assertEquals(9.79, grossTotal, 0.001)     // 5.17 + 4.62
            assertEquals(0.34, vatAmount7, 0.001)
            assertEquals(0.74, vatAmount19, 0.001)
            assertEquals(8.71, netTotal, 0.001)       // 4.83 + 3.88
            assertEquals(5.86, acquireCost, 0.001)    // 3.18 + 2.68
            assertEquals(2.85, grossProfit, 0.001)
            assertFalse(isGrossProfitIncomplete)
        }
    }

    @Test
    fun `sales outside the period are not counted`() = runTest {
        saleRepository.setSales(listOf(
            sale("aug", millis(2026, 8, 31, 23), saleLine(unitPriceCents = 100)),
            sale("sep", millis(2026, 9, 15), saleLine(unitPriceCents = 200)),
            sale("oct", millis(2026, 10, 1, 0), saleLine(unitPriceCents = 400))
        ))

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(2.0, periodSummary(viewModel).financials.grossTotal, 0.001)
    }

    @Test
    fun `a cancellation takes its sale back out of the totals`() = runTest {
        val original = sale("s1", millis(2026, 9, 3), saleLine(unitPriceCents = 319, acquirePriceCents = null))
        saleRepository.setSales(listOf(
            original,
            original.reversal(confirmedAt = millis(2026, 9, 4)).copy(id = "s2"),
            sale("s3", millis(2026, 9, 5), saleLine(unitPriceCents = 200, acquirePriceCents = 100))
        ))

        val viewModel = createViewModel()
        advanceUntilIdle()

        val summary = periodSummary(viewModel)
        assertEquals(2, summary.saleCount)
        assertEquals(1, summary.cancellationCount)
        assertEquals(2.0, summary.financials.grossTotal, 0.001)
        // The cancelled line had no purchase price; its cancellation cancels the count.
        assertEquals(0, summary.financials.itemsWithoutAcquirePrice)
    }

    @Test
    fun `an unknown purchase price is counted as unknown, not as free`() = runTest {
        saleRepository.setSales(listOf(sale("s1", millis(2026, 9, 3),
            saleLine(unitPriceCents = 300, acquirePriceCents = 150),
            saleLine(unitPriceCents = 500, acquirePriceCents = null)
        )))

        val viewModel = createViewModel()
        advanceUntilIdle()

        with(periodSummary(viewModel).financials) {
            assertEquals(1.5, acquireCost, 0.001)
            assertEquals(1, itemsWithoutAcquirePrice)
            assertTrue(isGrossProfitIncomplete)
        }
    }

    @Test
    fun `an empty period says so`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertTrue(periodSummary(viewModel).isEmpty)
    }

    @Test
    fun `stepping back shows the previous month`() = runTest {
        saleRepository.setSales(listOf(sale("aug", millis(2026, 8, 20), saleLine(unitPriceCents = 100))))
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.previousPeriod()
        advanceUntilIdle()

        assertEquals(BookingPeriod.Month(2026, 8), viewModel.period.value)
        assertEquals(1.0, periodSummary(viewModel).financials.grossTotal, 0.001)
    }

    @Test
    fun `there is no stepping into the future`() = runTest {
        val viewModel = createViewModel()

        assertFalse(viewModel.canGoNext(viewModel.period.value))
        viewModel.nextPeriod()
        assertEquals(BookingPeriod.Month(2026, 9), viewModel.period.value)

        viewModel.previousPeriod()
        assertTrue(viewModel.canGoNext(viewModel.period.value))
    }

    @Test
    fun `switching to weeks shows the current week`() = runTest {
        saleRepository.setSales(listOf(
            sale("before", millis(2026, 9, 18), saleLine(unitPriceCents = 100)),
            sale("inWeek", millis(2026, 9, 22), saleLine(unitPriceCents = 200))
        ))
        val viewModel = createViewModel()

        viewModel.setPeriodType(PeriodType.WEEK)
        advanceUntilIdle()

        assertEquals(BookingPeriod.Week(2026, 39), viewModel.period.value)
        assertEquals(2.0, periodSummary(viewModel).financials.grossTotal, 0.001)
    }

    @Test
    fun `switching type keeps a past period in view`() = runTest {
        val viewModel = createViewModel()
        viewModel.previousPeriod()                  // August 2026

        viewModel.setPeriodType(PeriodType.WEEK)

        assertEquals(BookingPeriod.weekOf(LocalDate(2026, 8, 1)), viewModel.period.value)
    }

    @Test
    fun `a sale booked while the period is shown appears`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertTrue(periodSummary(viewModel).isEmpty)

        saleRepository.recordSale("seller_123", sale("", millis(2026, 9, 24), saleLine(unitPriceCents = 250)))
        advanceUntilIdle()

        assertEquals(1, periodSummary(viewModel).saleCount)
    }

    // ===== Nächste Abholung: forecast from the catalog =====

    private fun nextPickupOrder(vararg items: OrderedProduct) = Order(
        id = "order_1",
        sellerId = "seller_123",
        status = OrderStatus.PLACED,
        pickUpDate = OrderDateUtils.calculateNextPickupDate().toEpochMilliseconds(),
        articles = items.toList()
    )

    private fun pickupFinancials(viewModel: AbrechnungViewModel): OrderFinancials {
        val state = viewModel.pickupSummary.value
        assertIs<AsyncState.Success<PickupSummary>>(state)
        return state.data.financials
    }

    private fun catalogArticle(id: String, productId: String = "bnn_$id", taxRate: TaxRate = TaxRate.REDUCED,
                               acquirePrice: Double? = null) = SellerArticle(
        article = Article(id = id, productId = productId, taxRate = taxRate.rate),
        sellerData = acquirePrice?.let { SellerArticleData(acquirePrice = it) }
    )

    @Test
    fun `pickup forecast takes purchase cost and VAT from the catalog`() = runTest {
        articleRepository.setArticles(listOf(
            catalogArticle("a", acquirePrice = 1.5),
            catalogArticle("b", taxRate = TaxRate.STANDARD)
        ))
        orderRepository.setOrders(listOf(nextPickupOrder(
            OrderedProduct(id = "a", productId = "bnn_a", price = 3.21, amountCount = 2.0),
            OrderedProduct(id = "b", productId = "bnn_b", price = 11.90, amountCount = 1.0)
        )))

        val viewModel = createViewModel()
        advanceUntilIdle()

        with(pickupFinancials(viewModel)) {
            assertEquals(3.0, acquireCost, 0.001)
            assertEquals(1.90, vatAmount19, 0.001)
            assertEquals(1, itemsWithoutAcquirePrice)
        }
    }

    @Test
    fun `pickup forecast matches articles by id before BNN number`() = runTest {
        articleRepository.setArticles(listOf(
            catalogArticle("a", productId = "bnn_a", acquirePrice = 1.0),
            catalogArticle("b", productId = "bnn_a", acquirePrice = 2.0)
        ))
        orderRepository.setOrders(listOf(nextPickupOrder(
            OrderedProduct(id = "b", productId = "bnn_a", price = 5.0, amountCount = 1.0)
        )))

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(2.0, pickupFinancials(viewModel).acquireCost, 0.001)
    }

    @Test
    fun `pickup forecast does not match articles by a blank BNN number`() = runTest {
        articleRepository.setArticles(listOf(catalogArticle("manual", productId = "", acquirePrice = 9.0)))
        orderRepository.setOrders(listOf(nextPickupOrder(
            OrderedProduct(id = "deleted", productId = "", price = 5.0, amountCount = 1.0)
        )))

        val viewModel = createViewModel()
        advanceUntilIdle()

        with(pickupFinancials(viewModel)) {
            assertEquals(0.0, acquireCost, 0.001)
            assertEquals(1, itemsWithoutAcquirePrice)
        }
    }
}
