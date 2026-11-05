package com.together.newverse.ui.state

import com.together.newverse.test.FakeArticleRepository
import com.together.newverse.test.FakeAuthRepository
import com.together.newverse.test.FakeBasketRepository
import com.together.newverse.test.FakeOrderRepository
import com.together.newverse.test.FakeProfileRepository
import com.together.newverse.test.FakeSellerConfig
import com.together.newverse.test.MainDispatcherRule
import com.together.newverse.ui.state.buy.handleMainScreenAction
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Whether the hero card's amount field is open, which is the only thing that puts a
 * basket in the top bar.
 *
 * The flag is worth pinning because it is reported rather than observed: the field
 * tells the state when it gains and loses focus, and nothing recomputes it. A report
 * that never arrives -- the field removed rather than blurred -- would leave a basket
 * icon sitting in the bar with no keyboard under it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BuyAppViewModelQuantityFocusTest {

    private val dispatcherRule = MainDispatcherRule()
    private lateinit var authRepository: FakeAuthRepository

    @BeforeTest
    fun setup() {
        dispatcherRule.setup()
        authRepository = FakeAuthRepository()
        authRepository.setCurrentUserId("buyer_1")
    }

    @AfterTest
    fun tearDown() {
        authRepository.reset()
        dispatcherRule.tearDown()
    }

    private fun createViewModel() = BuyAppViewModel(
        articleRepository = FakeArticleRepository(),
        orderRepository = FakeOrderRepository(),
        profileRepository = FakeProfileRepository(),
        authRepository = authRepository,
        basketRepository = FakeBasketRepository(),
        sellerConfig = FakeSellerConfig()
    )

    @Test
    fun `starts closed, so nothing stands in for the bar before the buyer types`() = runTest {
        assertFalse(createViewModel().state.value.mainScreen.isQuantityInputFocused)
    }

    @Test
    fun `focus raises the flag and blur puts it down again`() = runTest {
        val viewModel = createViewModel()

        viewModel.handleMainScreenAction(BuyMainScreenAction.SetQuantityInputFocused(true))
        assertTrue(viewModel.state.value.mainScreen.isQuantityInputFocused)

        viewModel.handleMainScreenAction(BuyMainScreenAction.SetQuantityInputFocused(false))
        assertFalse(viewModel.state.value.mainScreen.isQuantityInputFocused)
    }

    @Test
    fun `a second report of the same state is not an event`() = runTest {
        // The field reports on focus change and again on disposal, so the same
        // value can arrive twice; the later one must not flip anything back.
        val viewModel = createViewModel()

        viewModel.handleMainScreenAction(BuyMainScreenAction.SetQuantityInputFocused(false))
        viewModel.handleMainScreenAction(BuyMainScreenAction.SetQuantityInputFocused(true))
        viewModel.handleMainScreenAction(BuyMainScreenAction.SetQuantityInputFocused(true))
        assertTrue(viewModel.state.value.mainScreen.isQuantityInputFocused)

        viewModel.handleMainScreenAction(BuyMainScreenAction.SetQuantityInputFocused(false))
        viewModel.handleMainScreenAction(BuyMainScreenAction.SetQuantityInputFocused(false))
        assertFalse(viewModel.state.value.mainScreen.isQuantityInputFocused)
    }
}
