package com.together.newverse.ui.mainscreen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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

    // While the buyer is searching, the hero card folds away so the field, the chips
    // and as many product rows as possible are what the keyboard leaves on screen.
    //
    // Set when the field takes focus and cleared when it gives focus up -- but not
    // only then. Focus is not a reliable way *out* on a tablet: a tap on the page
    // does not take focus off the field, so a fold driven by focus alone left the
    // buyer searching with no way to ask for the card back. So the flag is also
    // cleared by the things that mean the search is over: emptying the field with
    // its X, and picking a product, which is what the card is for.
    //
    // The flag stays local rather than going through BuyMainScreenAction, unlike
    // isQuantityInputFocused, which is in the state only because AppScaffold reads it.
    // Nothing outside this screen needs to know the buyer is searching.
    var isSearching by remember { mutableStateOf(false) }

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

    val widthClass = LocalWindowWidthClass.current

    // Two product columns on phones, three on tablets (feed width is capped below)
    val productColumns = when (widthClass) {
        WindowWidthClass.Compact -> 2
        else -> 3
    }

    // Tablets get the hero beside the search field and its chips instead of a card
    // stretched across the whole feed width -- Medium (portrait, >= 600dp) as well as
    // Expanded (landscape), so a tablet's top section does not change shape when it is
    // turned. Keyed off the width class, never off orientation. Only Compact stacks
    // them, where half a phone's width would leave neither pane usable.
    val isTwoPaneHeader = widthClass != WindowWidthClass.Compact

    // Crossing the Compact breakpoint rebuilds the header -- the hero folds sideways
    // in one shape and vertically in the other -- and a search in progress does not
    // survive the move: the field is rebuilt and the focus event that would end the
    // fold never comes. So the flag is cleared on the way across, leaving the card
    // visible, which is the state a buyer can always recover from. Rotating a tablet
    // does not trip this: both of its widths are two-pane.
    LaunchedEffect(isTwoPaneHeader) { isSearching = false }

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
                    if (isTwoPaneHeader) {
                        // Left half the hero, right half the search field with the
                        // chips below it -- and while the field holds focus the hero
                        // folds away sideways and the search column takes the whole
                        // width, the same fold the stacked layout does vertically.
                        //
                        // The hero is given a width rather than a weight: a weight
                        // would keep its half of the row reserved while it was gone,
                        // leaving the buyer searching next to an empty pane.
                        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                            val paneGap = 16.dp
                            val heroPaneWidth = (maxWidth - paneGap) / 2
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top,
                            ) {
                                AnimatedVisibility(
                                    visible = !isSearching,
                                    enter = expandHorizontally() + fadeIn(),
                                    exit = shrinkHorizontally() + fadeOut(),
                                ) {
                                    HeroSection(
                                        state = state,
                                        onAction = onAction,
                                        onNavigateToProductDetail = onNavigateToProductDetail,
                                        // The gap belongs to the hero's slot, so it
                                        // goes with the card instead of leaving a
                                        // 16.dp hole in front of the search field.
                                        modifier = Modifier
                                            .width(heroPaneWidth + paneGap)
                                            .padding(end = paneGap),
                                    )
                                }
                                SearchAndFilters(
                                    state = state,
                                    onAction = onAction,
                                    onSearchFocusChanged = { isSearching = it },
                                    onSearchDismissed = { isSearching = false },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    } else {
                        AnimatedVisibility(
                            visible = !isSearching,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut(),
                        ) {
                            Column {
                                HeroSection(
                                    state = state,
                                    onAction = onAction,
                                    onNavigateToProductDetail = onNavigateToProductDetail,
                                )

                                // Inside the collapsing block, so the gap goes with the
                                // card instead of leaving a 16.dp hole above the field.
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }

                        SearchAndFilters(
                            state = state,
                            onAction = onAction,
                            onSearchFocusChanged = { isSearching = it },
                            onSearchDismissed = { isSearching = false },
                        )
                    }
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
                                        // Picking a card loads it into the hero, which
                                        // is folded away while the field holds focus --
                                        // in either layout. So the field has to let go
                                        // first, or the card the buyer just chose stays
                                        // hidden.
                                        focusManager.clearFocus()
                                        // Not left to the blur: on a tablet the field
                                        // may keep focus through this, and the card the
                                        // buyer just picked has to be on screen.
                                        isSearching = false
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

/**
 * The selected article's hero card, its loading skeleton, or nothing at all when no
 * article is selected. Extracted so the stacked (phone/portrait) and the side-by-side
 * (landscape tablet) header can share one definition.
 */
@Composable
private fun HeroSection(
    state: MainScreenState,
    onAction: (BuyAction) -> Unit,
    onNavigateToProductDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedProduct = state.selectedArticle
    Box(modifier = modifier) {
        if (state.isLoading && selectedProduct == null) {
            HeroProductCardSkeleton()
        } else {
            selectedProduct?.let { product ->
                // Match by id (Firebase key when loaded from order) or productId
                // (Firebase key when freshly added)
                val basketItem = state.basketItems.find {
                    it.id == product.id || it.productId == product.id
                }
                HeroProductCard(
                    product = product,
                    price = state.priceOf(product),
                    quantity = state.selectedQuantity,
                    originalQuantity = basketItem?.amountCount ?: 0.0,
                    isInBasket = basketItem != null,
                    isFavourite = state.favouriteArticles.contains(product.id),
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
    }
}

/** The search field with the category chips underneath it. */
@Composable
private fun SearchAndFilters(
    state: MainScreenState,
    onAction: (BuyAction) -> Unit,
    onSearchFocusChanged: (Boolean) -> Unit,
    /** The buyer emptied the field with its X: the search is over whatever focus says. */
    onSearchDismissed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        ProductSearchField(
            query = state.searchQuery,
            onQueryChange = { onAction(BuyMainScreenAction.UpdateSearchQuery(it)) },
            onClear = {
                onAction(BuyMainScreenAction.ClearSearchQuery)
                onSearchDismissed()
            },
            onFocusChanged = onSearchFocusChanged,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        CategoryChips(
            filterState = state.filterState,
            onCycleFavourite = { onAction(BuyMainScreenAction.CycleFavouriteFilter) },
            onToggleCategory = { category ->
                onAction(BuyMainScreenAction.ToggleCategoryFilter(category))
            },
            onReset = { onAction(BuyMainScreenAction.ResetFilters) }
        )
    }
}
