package com.together.newverse.ui.screens.sell

import com.together.newverse.domain.model.ActionAudience
import com.together.newverse.domain.model.SellerProfile
import com.together.newverse.domain.model.SellerUiAction
import com.together.newverse.test.FakeArticleRepository
import com.together.newverse.test.FakeAuthRepository
import com.together.newverse.test.FakeMarketRepository
import com.together.newverse.test.FakeOrderRepository
import com.together.newverse.test.FakeProfileRepository
import com.together.newverse.test.FakeSellerUiActionRepository
import com.together.newverse.test.MainDispatcherRule
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Publishing a button says whether it worked.
 *
 * Written after a refused write cost an afternoon: the repository logged a warning,
 * the editor looked exactly as it does on success, and the only way to find out that
 * the database had rejected the row was to read the database. The rejection was a
 * ruleset that did not yet know a field this app writes, which is the ordinary shape
 * of a half-deployed change — so it will happen again, and it has to be visible.
 */
class SellerProfileViewModelUiActionTest {

    private val dispatcherRule = MainDispatcherRule()

    private lateinit var profileRepository: FakeProfileRepository
    private lateinit var authRepository: FakeAuthRepository
    private lateinit var articleRepository: FakeArticleRepository
    private lateinit var orderRepository: FakeOrderRepository
    private lateinit var marketRepository: FakeMarketRepository
    private lateinit var uiActionRepository: FakeSellerUiActionRepository

    @BeforeTest
    fun setUp() {
        dispatcherRule.setup()
        profileRepository = FakeProfileRepository()
        authRepository = FakeAuthRepository()
        articleRepository = FakeArticleRepository()
        orderRepository = FakeOrderRepository()
        marketRepository = FakeMarketRepository()
        uiActionRepository = FakeSellerUiActionRepository()

        authRepository.setCurrentUserId("seller_123")
        profileRepository.setSellerProfile(SellerProfile(sellerId = "seller_123", id = "seller_123"))
    }

    @AfterTest
    fun tearDown() {
        profileRepository.reset()
        authRepository.reset()
        articleRepository.reset()
        orderRepository.reset()
        uiActionRepository.reset()
        dispatcherRule.tearDown()
    }

    private fun createViewModel() = SellerProfileViewModel(
        profileRepository = profileRepository,
        authRepository = authRepository,
        articleRepository = articleRepository,
        orderRepository = orderRepository,
        marketRepository = marketRepository,
        sellerUiActionRepository = uiActionRepository
    )

    private val action = SellerUiAction(label = "Wünsche", value = "example.org")

    @Test
    fun `a refused publish is reported, not swallowed`() = runTest {
        uiActionRepository.shouldFailSave = true
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.saveUiAction(action)
        advanceUntilIdle()

        assertNotNull(
            viewModel.uiActionError.value,
            "a refused write left the seller with no sign anything went wrong"
        )
    }

    @Test
    fun `a successful publish clears an earlier refusal`() = runTest {
        uiActionRepository.shouldFailSave = true
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.saveUiAction(action)
        advanceUntilIdle()
        assertNotNull(viewModel.uiActionError.value)

        uiActionRepository.shouldFailSave = false
        viewModel.saveUiAction(action)
        advanceUntilIdle()

        assertNull(viewModel.uiActionError.value, "the old error outlived the problem")
    }

    @Test
    fun `publishing with nothing wrong reports no error`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.saveUiAction(action)
        advanceUntilIdle()

        assertNull(viewModel.uiActionError.value)
        assertEquals(listOf("Wünsche"), viewModel.uiActions.value.map { it.label })
    }

    @Test
    fun `changing a button edits it in place rather than adding another`() = runTest {
        // The row is replaced by id, so there is no delete-and-recreate step: a seller
        // who changes the audience of an existing button keeps one button.
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.saveUiAction(action)
        advanceUntilIdle()
        val published = viewModel.uiActions.value.single()

        viewModel.saveUiAction(published.copy(audience = ActionAudience.APPROVED_ONLY))
        advanceUntilIdle()

        val after = viewModel.uiActions.value.single()
        assertEquals(published.id, after.id)
        assertEquals(ActionAudience.APPROVED_ONLY, after.audience)
    }
}
