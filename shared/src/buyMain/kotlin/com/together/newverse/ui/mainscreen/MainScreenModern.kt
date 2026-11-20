package com.together.newverse.ui.mainscreen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.together.newverse.ui.adaptive.AdaptiveDefaults
import com.together.newverse.ui.adaptive.LocalWindowWidthClass
import com.together.newverse.ui.adaptive.WindowWidthClass
import com.together.newverse.ui.adaptive.constrainedContentWidth
import com.together.newverse.ui.state.BuyAction
import com.together.newverse.ui.state.BuyMainScreenAction
import com.together.newverse.ui.state.MainScreenState
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.main_order_locked_snackbar
import newverse.shared.generated.resources.main_section_fresh_subtitle
import newverse.shared.generated.resources.main_section_fresh_title
import newverse.shared.generated.resources.main_start_new_order
import newverse.shared.generated.resources.products_search_no_results
import org.jetbrains.compose.resources.stringResource

/**
 * Buy flavor MainScreenModern (default)
 *
 * This is the buyer/customer version of the main screen.
 * The sell flavor has its own version in sellMain.
 */
@Composable
fun MainScreenModern(
    state: MainScreenState,
    onAction: (BuyAction) -> Unit,
    onNavigateToProductDetail: (String) -> Unit = {}
) {
    MainScreenModernContent(
        state = state,
        onAction = onAction,
        onNavigateToProductDetail = onNavigateToProductDetail,
    )
}

@Composable
private fun MainScreenModernContent(
    state: MainScreenState,
    onAction: (BuyAction) -> Unit,
    onNavigateToProductDetail: (String) -> Unit,
) {
    val products = state.filteredArticles
    val selectedProduct = state.selectedArticle
    val quantity = state.selectedQuantity
    val basketItems = state.basketItems
    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current

    // While the search field holds focus the hero card folds away, so the field and
    // the chips rise to the top and the keyboard leaves as many product rows visible
    // as possible. Focus is the only trigger: a query still standing in the field
    // does not keep the card hidden once focus has gone.
    //
    // The flag stays local rather than going through BuyMainScreenAction, unlike
    // isQuantityInputFocused, which is in the state only because AppScaffold reads it.
    // Nothing outside this screen needs to know the search field has focus.
    var isSearchFocused by remember { mutableStateOf(false) }

    // Both platforms, and iOS is not the exception it looks like. ComposeUIViewController
    // runs with OnFocusBehavior.FocusableAboveKeyboard, which sounds as though it already
    // does this job by panning the scene -- it does not. Read directionalFocusOffset in
    // Compose 1.10.3: the pan is a rescue for a field the keyboard would cover, and it
    // returns 0 whenever the field's bottom is already above the keyboard's top. This
    // search field sits in the upper half of the screen, so iOS pans by nothing at all,
    // and gating the fold off there left the iPhone with neither the pan nor the fold.
    //
    // Nor can the two fight once both run: folding moves the field *up*, which only makes
    // the pan's hiddenToPart more negative, so it stays at zero.

    // Two product columns on phones, three on tablets (feed width is capped below)
    val productColumns = when (LocalWindowWidthClass.current) {
        WindowWidthClass.Compact -> 2
        else -> 3
    }

    // Show snackbar when order is not editable and user tries to modify
    val orderLockedMessage = stringResource(Res.string.main_order_locked_snackbar)
    val startNewOrderLabel = stringResource(Res.string.main_start_new_order)
    LaunchedEffect(state.showNewOrderSnackbar) {
        if (state.showNewOrderSnackbar) {
            val result = snackbarHostState.showSnackbar(
                message = orderLockedMessage,
                actionLabel = startNewOrderLabel
            )
            when (result) {
                SnackbarResult.ActionPerformed -> {
                    onAction(BuyMainScreenAction.StartNewOrder)
                }
                SnackbarResult.Dismissed -> {
                    onAction(BuyMainScreenAction.DismissNewOrderSnackbar)
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .constrainedContentWidth(max = AdaptiveDefaults.FeedMaxWidth)
            ) {
                // The hero, the search field and the chips sit above the grid rather
                // than inside it as a sticky header. Two things follow: the product
                // rows can no longer slide underneath the card, and a drag that starts
                // on the card no longer scrolls the list.
                //
                // The scrollable wrapper consumes nothing (`{ 0f }`). Its deltas travel
                // to the scaffold's nested-scroll parent instead, so a pull downwards on
                // the hero is spent entirely on bringing the top bar back into view.
                Column(
                    modifier = Modifier
                        .scrollable(
                            state = rememberScrollableState { 0f },
                            orientation = Orientation.Vertical,
                        )
                        .background(MaterialTheme.colorScheme.background)
                        .padding(start = 16.dp, end = 16.dp, top = 16.dp)
                ) {
                    AnimatedVisibility(
                        visible = !isSearchFocused,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        Column {
                            // Show skeleton or actual product
                            if (state.isLoading && selectedProduct == null) {
                                HeroProductCardSkeleton()
                            } else {
                                selectedProduct?.let { product ->
                                    // Match by id (Firebase key when loaded from order) or productId (Firebase key when freshly added)
                                    val basketItem = basketItems.find { it.id == product.id || it.productId == product.id }
                                    val isInBasket = basketItem != null
                                    val originalQuantity = basketItem?.amountCount ?: 0.0
                                    val isFavourite = state.favouriteArticles.contains(product.id)
                                    HeroProductCard(
                                        product = product,
                                        price = state.priceOf(product),
                                        quantity = quantity,
                                        originalQuantity = originalQuantity,
                                        isInBasket = isInBasket,
                                        isFavourite = isFavourite,
                                        onQuantityChange = { onAction(BuyMainScreenAction.UpdateQuantity(it)) },
                                        onQuantityFocusChange = {
                                            onAction(BuyMainScreenAction.SetQuantityInputFocused(it))
                                        },
                                        onAddToCart = { onAction(BuyMainScreenAction.AddToCart) },
                                        onRemoveFromBasket = { onAction(BuyMainScreenAction.RemoveFromBasket) },
                                        onToggleFavourite = { onAction(BuyMainScreenAction.ToggleFavourite(product.id)) },
                                        onNavigateToDetail = { onNavigateToProductDetail(product.id) }
                                    )
                                }
                            }

                            // Inside the collapsing block, so the gap goes with the card
                            // instead of leaving a 16.dp hole above the search field.
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }

                    // Search Bar
                    ProductSearchField(
                        query = state.searchQuery,
                        onQueryChange = { onAction(BuyMainScreenAction.UpdateSearchQuery(it)) },
                        onClear = { onAction(BuyMainScreenAction.ClearSearchQuery) },
                        onFocusChanged = { isSearchFocused = it },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Category Filter Chips
                    CategoryChips(
                        filterState = state.filterState,
                        onCycleFavourite = {
                            onAction(BuyMainScreenAction.CycleFavouriteFilter)
                        },
                        onToggleCategory = { category ->
                            onAction(BuyMainScreenAction.ToggleCategoryFilter(category))
                        },
                        onReset = { onAction(BuyMainScreenAction.ResetFilters) }
                    )
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 16.dp,
                        bottom = 16.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Section Header
                    item {
                        SectionHeader(
                            title = stringResource(Res.string.main_section_fresh_title),
                            subtitle = stringResource(Res.string.main_section_fresh_subtitle)
                        )
                    }

                    // Show loading skeleton
                    if (state.isLoading && products.isEmpty()) {
                        // Show skeleton product grid (3 rows)
                        items(3) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                repeat(productColumns) {
                                    ProductCardSkeleton(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    if (state.error != null) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    state.error.message,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    // No results state when search query returns empty
                    if (!state.isLoading && products.isEmpty() && state.searchQuery.isNotEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    stringResource(Res.string.products_search_no_results),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    // Announce the empty result to a screen reader as the query changes
                                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                                )
                            }
                        }
                    }

                    // Product Grid
                    items(products.chunked(productColumns)) { productRow ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            productRow.forEach { product ->
                                ModernProductCard(
                                    product = product,
                                    price = state.priceOf(product),
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        // Picking a card loads it into the hero, so the
                                        // search field has to let go of focus first —
                                        // otherwise the card the buyer just chose stays
                                        // folded away behind the keyboard.
                                        focusManager.clearFocus()
                                        onAction(BuyMainScreenAction.SelectArticle(product))
                                    },
                                    onNavigateToDetail = {
                                        onNavigateToProductDetail(product.id)
                                    },
                                )
                            }
                            // Fill the last row with empty boxes so cards keep their width
                            repeat(productColumns - productRow.size) {
                                Box(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            } // Column
        }

        // Snackbar for non-editable order notification
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
