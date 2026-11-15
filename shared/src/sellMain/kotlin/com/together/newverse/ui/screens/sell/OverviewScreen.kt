package com.together.newverse.ui.screens.sell

import androidx.compose.foundation.layout.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.width
import com.together.newverse.ui.components.CategoryChipRow
import com.together.newverse.ui.components.ProductListItem
import com.together.newverse.ui.components.SellerSearchField
import com.together.newverse.ui.state.core.AsyncState
import com.together.newverse.ui.state.core.AsyncStateContent
import com.together.newverse.util.formatPrice
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import com.together.newverse.ui.state.SellAppViewModel

@Composable
fun OverviewScreen(
    viewModel: OverviewViewModel = koinViewModel(),
    sellAppViewModel: SellAppViewModel = koinViewModel(),
    isSelectionMode: Boolean = false,
    onSelectionModeChange: (Boolean) -> Unit = {},
    isAvailabilityMode: Boolean = false,
    onAvailabilityModeChange: (Boolean) -> Unit = {},
    onNavigateToImportPreview: () -> Unit = {},
    onEditArticle: (String) -> Unit = {},
    onCreateArticle: () -> Unit = {}
) {
    val overviewState by viewModel.overviewState.collectAsState()
    val importState by viewModel.importState.collectAsState()

    // The search field is in the top bar and writes to SellAppViewModel, the one view
    // model the bar and this screen share. Mirror it into the list's own view model
    // so all three filters are applied in one testable place.
    val isSearchExpanded by sellAppViewModel.searchExpanded.collectAsState()
    val searchQuery by sellAppViewModel.searchQuery.collectAsState()
    val selectedCategory by sellAppViewModel.selectedCategory.collectAsState()
    LaunchedEffect(searchQuery) { viewModel.setSearchQuery(searchQuery) }
    LaunchedEffect(selectedCategory) { viewModel.setSelectedCategory(selectedCategory) }

    // No back-press handling: Compose Multiplatform's common BackHandler is both
    // experimental and already deprecated in favour of a NavigationEventHandler that
    // 1.10.3 does not ship, and a deprecated API behind a new dependency is a poor
    // trade for a convenience. The expanded field's close button in the top bar is
    // the way out, and collapsing clears the query, so nothing stays hidden.

    // Observe pending import content directly from SellAppViewModel (survives config changes)
    val pendingImportContent by sellAppViewModel.pendingImportContent.collectAsState()

    val selectedArticleIds by viewModel.selectedArticleIds.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showAvailableDialog by remember { mutableStateOf(false) }
    var showImportResultDialog by remember { mutableStateOf(false) }

    // Handle pending import content - parse and navigate to preview
    LaunchedEffect(pendingImportContent) {
        val content = pendingImportContent
        if (content != null) {
            viewModel.parseProducts(content)
            sellAppViewModel.setPendingImportContent(null) // Consume
        }
    }

    // Navigate to preview when products are parsed, or show result dialog
    LaunchedEffect(importState) {
        when (importState) {
            is ImportState.Preview -> {
                onNavigateToImportPreview()
            }
            is ImportState.Success, is ImportState.Error -> {
                showImportResultDialog = true
            }
            else -> {}
        }
    }
    var showUnavailableDialog by remember { mutableStateOf(false) }

    // A mode starting takes a fresh selection and a fresh ordering; a mode ending
    // gives both back. The view model owns the selection because the list's order
    // depends on it.
    val inSelectionMode = isSelectionMode || isAvailabilityMode
    LaunchedEffect(inSelectionMode) {
        if (inSelectionMode) viewModel.beginSelection() else viewModel.clearSelection()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            // No bottom padding: the product list is the last thing in this column
            // and it runs to the bottom bar. Padding here would be a strip of empty
            // surface under a scrollable list -- it reads as the list having ended
            // when it has not. The branches that do not end in a list carry their
            // own bottom padding instead.
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, top = 16.dp, end = 16.dp)
        ) {
            // Content based on state using AsyncStateContent helper
            AsyncStateContent(
                state = overviewState,
                onRetry = { viewModel.refresh() },
                loadingContent = { LoadingContent() },
                errorContent = { message, _ ->
                    ErrorContent(
                        message = message,
                        onRetry = { viewModel.refresh() }
                    )
                }
            ) { data ->
                val currentFilter by viewModel.currentFilter.collectAsState()
                SuccessContent(
                    data = data,
                    currentFilter = currentFilter,
                    onFilterChange = { viewModel.setFilter(it) },
                    isSearchExpanded = isSearchExpanded,
                    onSelectCategory = sellAppViewModel::setSelectedCategory,
                    onResetFilters = {
                        sellAppViewModel.setSearchQuery("")
                        sellAppViewModel.setSelectedCategory(null)
                        viewModel.setFilter(ProductFilter.ALL)
                    },
                    isSelectionMode = inSelectionMode,
                    selectedArticleIds = selectedArticleIds,
                    onArticleSelectionToggle = viewModel::toggleArticleSelection,
                    searchQuery = searchQuery,
                    onSearchQueryChange = sellAppViewModel::setSearchQuery,
                    onEditArticle = onEditArticle,
                    onCreateArticle = onCreateArticle
                )
            }
        }

        // Floating Action Button for delete (only visible in deletion selection mode with selections)
        if (isSelectionMode && !isAvailabilityMode && selectedArticleIds.isNotEmpty()) {
            FloatingActionButton(
                onClick = { showDeleteDialog = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                containerColor = MaterialTheme.colorScheme.error
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(Res.string.overview_delete_selected),
                    tint = MaterialTheme.colorScheme.onError
                )
            }
        }

        // Availability buttons (only visible in availability mode with selections)
        if (isAvailabilityMode && selectedArticleIds.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // "Verfügbar" button
                Button(
                    onClick = { showAvailableDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(stringResource(Res.string.overview_available))
                }

                // "Nicht vorhanden" button
                Button(
                    onClick = { showUnavailableDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(Res.string.overview_not_available))
                }
            }
        }

        // Delete confirmation dialog
        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = {
                    Text(
                        stringResource(Res.string.overview_delete_products_title),
                        modifier = Modifier.semantics { heading() }
                    )
                },
                text = {
                    Text(stringResource(Res.string.overview_delete_products_confirm, selectedArticleIds.size))
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteArticles(selectedArticleIds)
                            showDeleteDialog = false
                            onSelectionModeChange(false)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text(stringResource(Res.string.button_delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text(stringResource(Res.string.button_cancel))
                    }
                }
            )
        }

        // Available confirmation dialog
        if (showAvailableDialog) {
            AlertDialog(
                onDismissRequest = { showAvailableDialog = false },
                title = {
                    Text(
                        stringResource(Res.string.overview_set_available_title),
                        modifier = Modifier.semantics { heading() }
                    )
                },
                text = {
                    Text(stringResource(Res.string.overview_set_available_confirm, selectedArticleIds.size))
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.updateArticlesAvailability(selectedArticleIds, available = true)
                            showAvailableDialog = false
                            onAvailabilityModeChange(false)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(stringResource(Res.string.button_ok))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAvailableDialog = false }) {
                        Text(stringResource(Res.string.button_cancel))
                    }
                }
            )
        }

        // Unavailable confirmation dialog
        if (showUnavailableDialog) {
            AlertDialog(
                onDismissRequest = { showUnavailableDialog = false },
                title = {
                    Text(
                        stringResource(Res.string.overview_set_unavailable_title),
                        modifier = Modifier.semantics { heading() }
                    )
                },
                text = {
                    Text(stringResource(Res.string.overview_set_unavailable_confirm, selectedArticleIds.size))
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.updateArticlesAvailability(selectedArticleIds, available = false)
                            showUnavailableDialog = false
                            onAvailabilityModeChange(false)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text(stringResource(Res.string.button_ok))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showUnavailableDialog = false }) {
                        Text(stringResource(Res.string.button_cancel))
                    }
                }
            )
        }

        // Import result dialog
        if (showImportResultDialog) {
            when (val state = importState) {
                is ImportState.Success -> {
                    AlertDialog(
                        onDismissRequest = {
                            showImportResultDialog = false
                            viewModel.resetImportState()
                        },
                        title = {
                            Text(
                                stringResource(Res.string.overview_import_success),
                                modifier = Modifier.semantics { heading() }
                            )
                        },
                        text = {
                            Text(
                                if (state.errorCount > 0) {
                                    stringResource(Res.string.overview_import_result, state.importedCount, state.errorCount)
                                } else {
                                    stringResource(Res.string.overview_import_result_success, state.importedCount)
                                }
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showImportResultDialog = false
                                    viewModel.resetImportState()
                                }
                            ) {
                                Text(stringResource(Res.string.button_ok))
                            }
                        }
                    )
                }
                is ImportState.Error -> {
                    AlertDialog(
                        onDismissRequest = {
                            showImportResultDialog = false
                            viewModel.resetImportState()
                        },
                        title = {
                            Text(
                                stringResource(Res.string.overview_import_failed),
                                modifier = Modifier.semantics { heading() }
                            )
                        },
                        text = {
                            Text(
                                state.message,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showImportResultDialog = false
                                    viewModel.resetImportState()
                                }
                            ) {
                                Text(stringResource(Res.string.button_ok))
                            }
                        }
                    )
                }
                else -> {}
            }
        }

        // Parsing progress indicator overlay
        if (importState is ImportState.Parsing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Card {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(stringResource(Res.string.overview_parsing))
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator()
            Text(
                text = stringResource(Res.string.overview_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "⚠️",
                style = MaterialTheme.typography.displayLarge,
                modifier = Modifier.clearAndSetSemantics { }
            )
            Text(
                text = stringResource(Res.string.overview_error),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
            Button(onClick = onRetry) {
                Text(stringResource(Res.string.button_retry))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SuccessContent(
    data: OverviewData,
    currentFilter: ProductFilter = ProductFilter.ALL,
    onFilterChange: (ProductFilter) -> Unit = {},
    isSearchExpanded: Boolean = false,
    onSelectCategory: (String?) -> Unit = {},
    onResetFilters: () -> Unit = {},
    isSelectionMode: Boolean = false,
    selectedArticleIds: Set<String> = emptySet(),
    onArticleSelectionToggle: (String) -> Unit = {},
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    onEditArticle: (String) -> Unit = {},
    onCreateArticle: () -> Unit = {}
) {
    var filterExpanded by remember { mutableStateOf(false) }

    // Empty because of a filter, or empty because there is nothing? All three filters
    // count here, including the dropdown — "Nicht verfügbar" with nothing unavailable
    // is a narrowed list, not an empty catalog.
    //
    // totalProducts is what tells the two apart now that the dropdown starts at
    // Verfügbar: a seller with no articles at all is filtered by that default too, and
    // "nothing matched, reset the filters" is the wrong and rather bleak thing to say
    // to someone who has simply not created their first product yet.
    val isFiltered = data.totalProducts > 0 &&
        (data.isSearchNarrowed || currentFilter != ProductFilter.ALL)

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // The chip row rides with the search field: one expand gesture, both appear.
        // It sits here rather than in the scaffold's topBar slot because the
        // categories come from the catalog, which only this screen's view model has.
        AnimatedVisibility(
            visible = isSearchExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column {
                // In a selection mode the field is here rather than in the top bar,
                // and that is the whole reason this branch exists. The bar's title
                // slot is saying *which* mode is running -- deleting articles or
                // changing their availability -- and those two are not
                // interchangeable, so neither can give its title up to a text field.
                // In availability mode nothing else on screen says what is happening
                // until something is ticked, which would leave a bar that looked like
                // ordinary search while taps were silently selecting.
                //
                // Outside a selection mode the bar owns the field and this draws
                // nothing: two fields writing one query would be absurd.
                if (isSelectionMode) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        // autoFocus = false: the seller asked for a selection mode,
                        // not for the keyboard, and the rows they are about to tick
                        // are what the keyboard would cover.
                        SellerSearchField(
                            query = searchQuery,
                            onQueryChange = onSearchQueryChange,
                            modifier = Modifier.fillMaxWidth(),
                            autoFocus = false
                        )
                    }
                }
                CategoryChipRow(
                    categories = data.categories,
                    selected = data.selectedCategory,
                    onSelect = onSelectCategory
                )
            }
        }

        // Stats Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard(
                title = when (currentFilter) {
                    ProductFilter.ALL -> stringResource(Res.string.overview_total_products)
                    ProductFilter.AVAILABLE -> stringResource(Res.string.overview_available_products)
                    ProductFilter.NOT_AVAILABLE -> stringResource(Res.string.overview_unavailable_products)
                },
                value = data.recentArticles.size.toString(),
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = stringResource(Res.string.overview_active_orders),
                value = data.activeOrders.toString(),
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = stringResource(Res.string.overview_total_revenue),
                value = "${data.totalRevenue.formatPrice()}€",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Products Section Header with Filter Dropdown
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCreateArticle) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(Res.string.cd_create_product)
                    )
                }
                Text(
                    text = stringResource(Res.string.overview_your_products),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() }
                )
            }

            // Filter Dropdown
            Box {
                val filterLabel = when (currentFilter) {
                    ProductFilter.ALL -> stringResource(Res.string.overview_filter_all)
                    ProductFilter.AVAILABLE -> stringResource(Res.string.overview_filter_available)
                    ProductFilter.NOT_AVAILABLE -> stringResource(Res.string.overview_filter_not_available)
                }
                val filterButtonDescription =
                    stringResource(Res.string.a11y_product_filter, filterLabel)
                OutlinedButton(
                    onClick = { filterExpanded = true },
                    modifier = Modifier.semantics { contentDescription = filterButtonDescription }
                ) {
                    Text(text = filterLabel)
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null
                    )
                }

                DropdownMenu(
                    expanded = filterExpanded,
                    onDismissRequest = { filterExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.overview_filter_all)) },
                        onClick = {
                            onFilterChange(ProductFilter.ALL)
                            filterExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.overview_filter_available)) },
                        onClick = {
                            onFilterChange(ProductFilter.AVAILABLE)
                            filterExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.overview_filter_not_available)) },
                        onClick = {
                            onFilterChange(ProductFilter.NOT_AVAILABLE)
                            filterExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (data.recentArticles.isEmpty()) {
            // Empty state. Keeps its own bottom padding -- unlike the list, a card
            // is a finished object and should not touch the bottom bar.
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp)
                        .semantics(mergeDescendants = true) { heading() },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "📦",
                        style = MaterialTheme.typography.displayMedium,
                        modifier = Modifier.clearAndSetSemantics { }
                    )
                    // An empty catalog and a filter that matches nothing look the
                    // same but need opposite advice: "create your first product" is
                    // wrong and alarming when the seller has 104 articles and has
                    // simply typed a query that matches none of them.
                    Text(
                        text = stringResource(
                            if (isFiltered) Res.string.products_search_no_results
                            else Res.string.overview_no_products
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(
                            if (isFiltered) Res.string.overview_no_match_hint
                            else Res.string.overview_create_first
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    if (isFiltered) {
                        TextButton(onClick = onResetFilters) {
                            Text(stringResource(Res.string.overview_reset_filters))
                        }
                    }
                }
            }
        } else {
            // The product list takes whatever height is left, down to the bottom bar.
            // It was pinned to 400dp "to show 5 items", which on any phone taller than
            // the one that was measured left a band of empty surface between the last
            // row and the navigation bar -- and gave the seller a 400dp window to
            // scroll a 100-article catalogue through regardless of screen size.
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                items(data.recentArticles) { article ->
                    SelectableProductListItem(
                        productName = article.productName,
                        price = article.price,
                        imageUrl = article.imageUrl,
                        unit = article.unit,
                        isSelectionMode = isSelectionMode,
                        isSelected = selectedArticleIds.contains(article.id),
                        onClick = {
                            if (isSelectionMode) {
                                onArticleSelectionToggle(article.id)
                            } else {
                                onEditArticle(article.id)
                            }
                        },
                        onLongClick = {
                            onEditArticle(article.id)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            // One stop reading "label: value" instead of the bare number first
            modifier = Modifier
                .padding(16.dp)
                .semantics(mergeDescendants = true) { contentDescription = "$title: $value" }
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun SelectableProductListItem(
    productName: String,
    price: Double,
    unit: String,
    imageUrl: String = "",
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Checkbox in selection mode. The whole row toggles selection, so the
        // checkbox is a redundant tap target — hide it from the a11y tree and let
        // the row carry the checked state via ProductListItem's `selected`.
        if (isSelectionMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onClick() },
                modifier = Modifier
                    .padding(start = 8.dp)
                    .clearAndSetSemantics { }
            )
        }

        // Product item
        ProductListItem(
            productName = productName,
            price = price,
            imageUrl = imageUrl,
            unit = unit,
            onClick = onClick,
            onLongClick = onLongClick,
            selected = if (isSelectionMode) isSelected else null,
            modifier = Modifier.weight(1f)
        )
    }
}
