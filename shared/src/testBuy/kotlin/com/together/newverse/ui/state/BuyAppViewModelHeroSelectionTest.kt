package com.together.newverse.ui.state

import com.together.newverse.domain.model.Article
import com.together.newverse.domain.model.BuyerProfile
import com.together.newverse.domain.model.SellerProfile
import com.together.newverse.test.FakeArticleRepository
import com.together.newverse.test.FakeAuthRepository
import com.together.newverse.test.FakeBasketRepository
import com.together.newverse.test.FakeMarketRepository
import com.together.newverse.test.FakeOrderRepository
import com.together.newverse.test.FakeProfileRepository
import com.together.newverse.test.FakeSellerConfig
import com.together.newverse.test.MainDispatcherRule
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What the hero card is allowed to show.
 *
 * `MainScreenState.articles` is the whole catalogue, withdrawn rows included — that is
 * what lets a withdrawal arrive as a `MODE_CHANGED` — while the grid renders
 * `filteredArticles`, which filters on `available`. The selection has to obey the grid's
 * rule, or a withdrawn article sits on the hero card above a grid that does not list it
 * and offers an order checkout would refuse.
 *
 * Found in production: after 62 unstocked articles were withdrawn for a trial, the
 * oldest article in the catalogue was withdrawn and became the hero.
 */
class BuyAppViewModelHeroSelectionTest {

    private val dispatcherRule = MainDispatcherRule()
    private lateinit var articleRepository: FakeArticleRepository
    private lateinit var orderRepository: FakeOrderRepository
    private lateinit var profileRepository: FakeProfileRepository
    private lateinit var authRepository: FakeAuthRepository
    private lateinit var basketRepository: FakeBasketRepository
    private lateinit var marketRepository: FakeMarketRepository

    /** First in the catalogue, as the oldest push id is. */
    private val milan = Article(
        id = "a0", productName = "Milan", price = 3.80,
        unit = "kg", available = true, mode = Article.MODE_ADDED
    )
    private val moehren = Article(
        id = "a1", productName = "Möhren", price = 3.80,
        unit = "kg", available = true, mode = Article.MODE_ADDED
    )

    @BeforeTest
    fun setUp() {
        dispatcherRule.setup()
        articleRepository = FakeArticleRepository()
        orderRepository = FakeOrderRepository()
        profileRepository = FakeProfileRepository()
        authRepository = FakeAuthRepository()
        basketRepository = FakeBasketRepository()
        marketRepository = FakeMarketRepository()
    }

    @AfterTest
    fun tearDown() {
        articleRepository.reset(); orderRepository.reset(); profileRepository.reset()
        authRepository.reset(); basketRepository.reset(); dispatcherRule.tearDown()
    }

    private suspend fun TestScope.started(catalog: List<Article>): BuyAppViewModel {
        profileRepository.setSellerProfile(SellerProfile(sellerId = "demo_seller_id"))
        profileRepository.setBuyerProfile(
            BuyerProfile(id = "buyer_1", displayName = "Test", isSelfPickup = true)
        )
        authRepository.setCurrentUserId("buyer_1")
        val viewModel = BuyAppViewModel(
            articleRepository = articleRepository,
            orderRepository = orderRepository,
            profileRepository = profileRepository,
            authRepository = authRepository,
            basketRepository = basketRepository,
            sellerConfig = FakeSellerConfig(),
            marketRepository = marketRepository
        )
        advanceUntilIdle()
        catalog.forEach { articleRepository.emitArticle(it) }
        advanceUntilIdle()
        return viewModel
    }

    @Test
    fun `a withdrawn first article is not selected`() = runTest {
        val vm = started(listOf(milan.copy(available = false), moehren))

        assertEquals("Möhren", vm.state.value.mainScreen.selectedArticle?.productName)
    }

    @Test
    fun `the first article is selected when it is on sale`() = runTest {
        val vm = started(listOf(milan, moehren))

        assertEquals("Milan", vm.state.value.mainScreen.selectedArticle?.productName)
    }

    @Test
    fun `withdrawing the selected article moves the selection on`() = runTest {
        // An article sells out while the buyer has it on screen — which is what a
        // stock-gated trial does several times a day.
        val vm = started(listOf(milan, moehren))
        assertEquals("Milan", vm.state.value.mainScreen.selectedArticle?.productName)

        articleRepository.emitArticle(milan.copy(available = false, mode = Article.MODE_CHANGED))
        advanceUntilIdle()

        assertEquals("Möhren", vm.state.value.mainScreen.selectedArticle?.productName)
    }

    @Test
    fun `nothing is selected when nothing is on sale`() = runTest {
        // Better than the last withdrawn article: the screen has an empty state, and
        // the card would invite an order nobody can fill.
        val vm = started(listOf(milan.copy(available = false), moehren.copy(available = false)))

        assertNull(vm.state.value.mainScreen.selectedArticle)
    }

    @Test
    fun `the grid and the hero never disagree`() = runTest {
        val vm = started(listOf(milan.copy(available = false), moehren))
        val state = vm.state.value.mainScreen

        val selected = state.selectedArticle
        assertTrue(
            selected == null || state.filteredArticles.any { it.id == selected.id },
            "the hero showed ${selected?.productName}, which the grid does not list"
        )
    }
}
