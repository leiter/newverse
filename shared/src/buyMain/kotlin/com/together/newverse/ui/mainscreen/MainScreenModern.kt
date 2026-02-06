package com.together.newverse.ui.mainscreen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.together.newverse.ui.state.MainScreenState
import com.together.newverse.ui.state.BuyAction
import com.together.newverse.ui.state.BuyMainScreenAction
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.products_search_placeholder
import newverse.shared.generated.resources.products_search_clear
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

@OptIn(ExperimentalFoundationApi::class)
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

    // Show snackbar when order is not editable and user tries to modify
    LaunchedEffect(state.showNewOrderSnackbar) {
        if (state.showNewOrderSnackbar) {
            val result = snackbarHostState.showSnackbar(
                message = "Bestellung kann nicht mehr geändert werden",
                actionLabel = "Neue Bestellung"
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
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Hero Section - Featured Product (sticky header)
                stickyHeader {
                    Column(
                        modifier = Modifier.background(
                            MaterialTheme.colorScheme.background
                        )
                    ) {
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
                                    quantity = quantity,
                                    originalQuantity = originalQuantity,
                                    isInBasket = isInBasket,
                                    isFavourite = isFavourite,
                                    onQuantityChange = { onAction(BuyMainScreenAction.UpdateQuantity(it)) },
                                    onAddToCart = { onAction(BuyMainScreenAction.AddToCart) },
                                    onRemoveFromBasket = { onAction(BuyMainScreenAction.RemoveFromBasket) },
                                    onToggleFavourite = { onAction(BuyMainScreenAction.ToggleFavourite(product.id)) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Search Bar
                        OutlinedTextField(
                            value = state.searchQuery,
                            onValueChange = { onAction(BuyMainScreenAction.UpdateSearchQuery(it)) },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text(stringResource(Res.string.products_search_placeholder)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            trailingIcon = {
                                if (state.searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { onAction(BuyMainScreenAction.ClearSearchQuery) }) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = stringResource(Res.string.products_search_clear),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Category Filter Chips
                        CategoryChips(
                            activeFilter = state.activeFilter,
                            onFilterSelected = { filter ->
                                onAction(BuyMainScreenAction.SetFilter(filter))
                            }
                        )
                    }
                }

                // Section Header
                item {
                    SectionHeader(
                        title = "Frisch vom Feld",
                        subtitle = "Heute geerntet"
                    )
                }

                // Show loading skeleton
                if (state.isLoading && products.isEmpty()) {
                    // Show skeleton product grid (6 items in 3 rows)
                    items(3) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            ProductCardSkeleton(modifier = Modifier.weight(1f))
                            ProductCardSkeleton(modifier = Modifier.weight(1f))
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
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Product Grid
                items(products.chunked(2)) { productPair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        productPair.forEach { product ->
                            ModernProductCard(
                                product = product,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    // Navigate to product detail screen
                                    onNavigateToProductDetail(product.id)
                                }
                            )
                        }
                        // Add empty box if odd number of products
                        if (productPair.size == 1) {
                            Box(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // Snackbar for non-editable order notification
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
