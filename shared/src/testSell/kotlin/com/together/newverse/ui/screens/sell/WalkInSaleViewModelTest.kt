package com.together.newverse.ui.screens.sell

import com.together.newverse.domain.model.Article
import com.together.newverse.domain.model.SellerArticle
import com.together.newverse.domain.model.SellerArticleData
import com.together.newverse.domain.model.TaxRate
import com.together.newverse.test.FakeAuthRepository
import com.together.newverse.test.FakeSaleRepository
import com.together.newverse.test.FakeSellerArticleRepository
import com.together.newverse.test.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class WalkInSaleViewModelTest {

    private val dispatcherRule = MainDispatcherRule()
    private lateinit var articleRepository: FakeSellerArticleRepository
    private lateinit var saleRepository: FakeSaleRepository
    private lateinit var authRepository: FakeAuthRepository

    private val apple = SellerArticle(
        Article(id = "apple", productId = "112108", productName = "Apfel Topaz", unit = "kg",
            price = 3.04, available = true, searchTerms = "apfel,obst", taxRate = TaxRate.REDUCED.rate),
        SellerArticleData(acquirePrice = 1.96)
    )
    private val sweet = SellerArticle(
        Article(id = "sweet", productId = "122654", productName = "Süßkartoffel", unit = "kg",
            price = 4.62, available = true, taxRate = TaxRate.STANDARD.rate)
    )
    private val sieve = SellerArticle(
        Article(id = "sieve", productName = "Teesieb", unit = "Stück", price = 2.59, available = false,
            taxRate = TaxRate.STANDARD.rate)
    )

    @BeforeTest
    fun setup() {
        dispatcherRule.setup()
        articleRepository = FakeSellerArticleRepository()
        saleRepository = FakeSaleRepository()
        authRepository = FakeAuthRepository()
        authRepository.setCurrentUserId("seller_123")
        articleRepository.setArticles(listOf(sieve, sweet, apple))
    }

    @AfterTest
    fun tearDown() {
        articleRepository.reset()
        saleRepository.reset()
        authRepository.reset()
        dispatcherRule.tearDown()
    }

    private fun createViewModel() = WalkInSaleViewModel(
        sellerArticleRepository = articleRepository,
        saleRepository = saleRepository,
        authRepository = authRepository,
        now = { 9_000L }
    )

    @Test
    fun `the catalog lists available articles first`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(listOf("apple", "sweet", "sieve"), viewModel.state.value.results.map { it.id })
    }

    @Test
    fun `search matches name, search terms and article number`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setQuery("obst")
        assertEquals(listOf("apple"), viewModel.state.value.results.map { it.id })
        viewModel.setQuery("1226")
        assertEquals(listOf("sweet"), viewModel.state.value.results.map { it.id })
        viewModel.setQuery("süß")
        assertEquals(listOf("sweet"), viewModel.state.value.results.map { it.id })
    }

    @Test
    fun `an added article starts at quantity 1 and its catalog price`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.add(apple)
        viewModel.add(apple)   // once only

        val line = viewModel.state.value.lines.single()
        assertEquals("1", line.quantityInput)
        assertEquals("3,04", line.priceInput)
        assertEquals(304L, viewModel.state.value.totalCents)
    }

    @Test
    fun `booking records a walk-in sale at the charged price`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.add(apple)
        viewModel.add(sweet)
        viewModel.setQuantity(0, "1,5")
        viewModel.setPrice(1, "4,00")   // market price instead of 4,62

        viewModel.book()
        advanceUntilIdle()

        val sale = saleRepository.sales.single()
        assertTrue(sale.isWalkIn)
        assertEquals(9_000L, sale.confirmedAt)
        assertEquals(listOf(1.5, 1.0), sale.lines.map { it.quantity })
        assertEquals(listOf(304L, 400L), sale.lines.map { it.unitPriceCents })
        assertEquals(listOf(0.07, 0.19), sale.lines.map { it.taxRate })
        assertEquals(listOf(196L, null), sale.lines.map { it.acquirePriceCents })
        assertNotNull(viewModel.state.value.booked)
        assertTrue(viewModel.state.value.lines.isEmpty(), "ready for the next customer")
    }

    @Test
    fun `invalid quantity or price books nothing`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.add(apple)
        viewModel.setPrice(0, "")

        viewModel.book()
        advanceUntilIdle()

        assertTrue(saleRepository.sales.isEmpty())
        assertEquals(WalkInMessage.INVALID_INPUT, viewModel.state.value.message)

        viewModel.setPrice(0, "3")
        assertNull(viewModel.state.value.message, "editing clears the message")
    }

    @Test
    fun `nothing selected books nothing`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.book()
        advanceUntilIdle()

        assertTrue(saleRepository.sales.isEmpty())
        assertEquals(WalkInMessage.NOTHING_SELECTED, viewModel.state.value.message)
    }

    @Test
    fun `a failed booking keeps the lines`() = runTest {
        saleRepository.shouldFailRecord = true
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.add(apple)

        viewModel.book()
        advanceUntilIdle()

        assertEquals(WalkInMessage.SAVE_FAILED, viewModel.state.value.message)
        assertEquals(1, viewModel.state.value.lines.size)
    }
}
