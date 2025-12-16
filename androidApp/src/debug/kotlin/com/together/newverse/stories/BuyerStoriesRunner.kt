package com.together.newverse.stories

import com.together.newverse.domain.repository.ArticleRepository
import com.together.newverse.domain.repository.BasketRepository
import com.together.newverse.domain.repository.OrderRepository
import com.together.newverse.domain.repository.ProfileRepository
import com.together.newverse.ui.state.BuyAppViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Buyer Stories Runner
 *
 * Orchestrates execution of buyer interaction story scenarios.
 * Use this to run individual stories or all stories in sequence.
 *
 * USAGE:
 *
 * 1. Individual Story:
 *    ```kotlin
 *    val runner = BuyerStoriesRunner(
 *        buyAppViewModel, basketViewModel,
 *        articleRepository, basketRepository, orderRepository, profileRepository,
 *        sellerId = "your-seller-id"
 *    )
 *    runner.runStory(BuyerStory.STORY_1_BROWSE_AND_ADD)
 *    ```
 *
 * 2. All Stories in Sequence:
 *    ```kotlin
 *    runner.runAllStories()
 *    ```
 *
 * 3. Custom Sequence:
 *    ```kotlin
 *    runner.runStories(listOf(
 *        BuyerStory.STORY_1_BROWSE_AND_ADD,
 *        BuyerStory.STORY_2_MODIFY_QUANTITIES,
 *        BuyerStory.STORY_3_CHECKOUT
 *    ))
 *    ```
 *
 * INTEGRATION EXAMPLE (in debug build):
 *
 * ```kotlin
 * // In your debug Activity or Composable:
 *
 * @Composable
 * fun DebugPanel(viewModel: BuyAppViewModel, basketViewModel: BasketViewModel) {
 *     val scope = rememberCoroutineScope()
 *     val runner = remember {
 *         BuyerStoriesRunner(
 *             buyAppViewModel = viewModel,
 *             basketViewModel = basketViewModel,
 *             articleRepository = koinInject(),
 *             basketRepository = koinInject(),
 *             orderRepository = koinInject(),
 *             profileRepository = koinInject(),
 *             sellerId = "your-seller-id"
 *         )
 *     }
 *
 *     Column {
 *         Button(onClick = { scope.launch { runner.runStory(BuyerStory.STORY_5_COMPLETE_JOURNEY) } }) {
 *             Text("Run Complete Journey")
 *         }
 *         Button(onClick = { scope.launch { runner.runAllStories() } }) {
 *             Text("Run All Stories")
 *         }
 *     }
 * }
 * ```
 */
class BuyerStoriesRunner(
    private val buyAppViewModel: BuyAppViewModel,
    private val articleRepository: ArticleRepository,
    private val basketRepository: BasketRepository,
    private val orderRepository: OrderRepository,
    private val profileRepository: ProfileRepository
) {

    /**
     * Run a single story
     */
    suspend fun runStory(story: BuyerStory) {
        printStoryHeader(story)

        when (story) {
            BuyerStory.STORY_1_BROWSE_AND_ADD -> {
                runBuyerStory1_BrowseAndAddToCart(
                    buyAppViewModel,
                    articleRepository,
                    basketRepository
                )
            }

            BuyerStory.STORY_2_MODIFY_QUANTITIES -> {
                runBuyerStory2_ModifyQuantitiesAndFavorites(
                    buyAppViewModel,
                    basketRepository,
                    profileRepository
                )
            }

            BuyerStory.STORY_3_CHECKOUT -> {
                runBuyerStory3_CheckoutNewOrder(
                    buyAppViewModel,
                    basketRepository,
                    orderRepository
                )
            }

            BuyerStory.STORY_4_EDIT_ORDER -> {
                runBuyerStory4_EditExistingOrder(
                    buyAppViewModel,
                    basketRepository,
                    orderRepository
                )
            }

            BuyerStory.STORY_5_COMPLETE_JOURNEY -> {
                runBuyerStory5_CompleteBuyerJourney(
                    buyAppViewModel,
                    articleRepository,
                    basketRepository,
                    orderRepository,
                    profileRepository
                )
            }
        }

        printStoryFooter(story)
    }

    /**
     * Run multiple stories in sequence
     */
    suspend fun runStories(stories: List<BuyerStory>) {
        println("\n" + "=".repeat(80))
        println("RUNNING ${stories.size} BUYER STORIES IN SEQUENCE")
        println("=".repeat(80))

        stories.forEachIndexed { index, story ->
            println("\n[Story ${index + 1}/${stories.size}]")
            runStory(story)

            if (index < stories.size - 1) {
                println("\n[Pause between stories - 2s]")
                delay(2000)
            }
        }

        println("\n" + "=".repeat(80))
        println("ALL STORIES COMPLETED")
        println("=".repeat(80))
    }

    /**
     * Run all stories in recommended order
     */
    suspend fun runAllStories() {
        runStories(
            listOf(
                BuyerStory.STORY_1_BROWSE_AND_ADD,
                BuyerStory.STORY_2_MODIFY_QUANTITIES,
                BuyerStory.STORY_3_CHECKOUT,
                BuyerStory.STORY_4_EDIT_ORDER,
                BuyerStory.STORY_5_COMPLETE_JOURNEY
            )
        )
    }

    /**
     * Run stories in parallel (for performance testing)
     * WARNING: This may cause conflicts in shared state!
     */
    fun runStoriesInParallel(scope: CoroutineScope, stories: List<BuyerStory>) {
        println("\n" + "=".repeat(80))
        println("RUNNING ${stories.size} BUYER STORIES IN PARALLEL")
        println("WARNING: May cause state conflicts!")
        println("=".repeat(80))

        stories.forEach { story ->
            scope.launch {
                runStory(story)
            }
        }
    }

    private fun printStoryHeader(story: BuyerStory) {
        println("\n\n")
        println("╔" + "═".repeat(78) + "╗")
        println("║ ${story.displayName.padEnd(76)} ║")
        println("║ ${story.description.take(76).padEnd(76)} ║")
        println("╚" + "═".repeat(78) + "╝")
    }

    private fun printStoryFooter(story: BuyerStory) {
        println("\n╔" + "═".repeat(78) + "╗")
        println("║ ✓ ${story.displayName} COMPLETED".padEnd(77) + "║")
        println("╚" + "═".repeat(78) + "╝")
    }
}

/**
 * Available buyer stories
 */
enum class BuyerStory(val displayName: String, val description: String) {
    STORY_1_BROWSE_AND_ADD(
        "Story 1: Browse & Add to Cart",
        "User browses articles and adds items to cart"
    ),

    STORY_2_MODIFY_QUANTITIES(
        "Story 2: Modify Quantities & Favorites",
        "User changes quantities, manages favorites, removes items"
    ),

    STORY_3_CHECKOUT(
        "Story 3: Checkout New Order",
        "User completes checkout flow and places order"
    ),

    STORY_4_EDIT_ORDER(
        "Story 4: Edit Existing Order",
        "User loads and edits an existing order"
    ),

    STORY_5_COMPLETE_JOURNEY(
        "Story 5: Complete Buyer Journey",
        "End-to-end realistic buyer journey through entire app"
    )
}

/**
 * Helper extension for easy story execution from ViewModels
 */
suspend fun BuyAppViewModel.runBuyerStory(
    story: BuyerStory,
    articleRepository: ArticleRepository,
    basketRepository: BasketRepository,
    orderRepository: OrderRepository,
    profileRepository: ProfileRepository
) {
    val runner = BuyerStoriesRunner(
        buyAppViewModel = this,
        articleRepository = articleRepository,
        basketRepository = basketRepository,
        orderRepository = orderRepository,
        profileRepository = profileRepository
    )
    runner.runStory(story)
}
