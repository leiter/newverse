package com.together.newverse.ui.screens.sell

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.width
import com.together.newverse.ui.components.ProductListItem
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
    onNavigateToImportPreview: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val importState by viewModel.importState.collectAsState()

    // Observe pending import content directly from SellAppViewModel (survives config changes)
    val pendingImportContent by sellAppViewModel.pendingImportContent.collectAsState()

    var selectedArticleIds by remember { mutableStateOf(setOf<String>()) }
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

    // Clear selections when exiting selection modes
    LaunchedEffect(isSelectionMode, isAvailabilityMode) {
        if (!isSelectionMode && !isAvailabilityMode) {
            selectedArticleIds = setOf()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Content based on state
            when (val state = uiState) {
                is OverviewUiState.Loading -> {
                    LoadingContent()
                }
                is OverviewUiState.Error -> {
                    ErrorContent(
                        message = state.message,
                        onRetry = { viewModel.refresh() }
                    )
                }
                is OverviewUiState.Success -> {
                    val currentFilter by viewModel.currentFilter.collectAsState()
                    SuccessContent(
                        state = state,
                        currentFilter = currentFilter,
                        onFilterChange = { viewModel.setFilter(it) },
                        isSelectionMode = isSelectionMode || isAvailabilityMode,
                        selectedArticleIds = selectedArticleIds,
                        onArticleSelectionToggle = { articleId ->
                            selectedArticleIds = if (selectedArticleIds.contains(articleId)) {
                                selectedArticleIds - articleId
                            } else {
                                selectedArticleIds + articleId
                            }
                        }
                    )
                }
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
                title = { Text(stringResource(Res.string.overview_delete_products_title)) },
                text = {
                    Text(stringResource(Res.string.overview_delete_products_confirm, selectedArticleIds.size))
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteArticles(selectedArticleIds)
                            showDeleteDialog = false
                            selectedArticleIds = setOf()
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
                title = { Text(stringResource(Res.string.overview_set_available_title)) },
                text = {
                    Text(stringResource(Res.string.overview_set_available_confirm, selectedArticleIds.size))
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.updateArticlesAvailability(selectedArticleIds, available = true)
                            showAvailableDialog = false
                            selectedArticleIds = setOf()
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
                title = { Text(stringResource(Res.string.overview_set_unavailable_title)) },
                text = {
                    Text(stringResource(Res.string.overview_set_unavailable_confirm, selectedArticleIds.size))
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.updateArticlesAvailability(selectedArticleIds, available = false)
                            showUnavailableDialog = false
                            selectedArticleIds = setOf()
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
                        title = { Text(stringResource(Res.string.overview_import_success)) },
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
                        title = { Text(stringResource(Res.string.overview_import_failed)) },
                        text = { Text(state.message) },
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
                style = MaterialTheme.typography.displayLarge
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
                textAlign = TextAlign.Center
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
    state: OverviewUiState.Success,
    currentFilter: ProductFilter = ProductFilter.ALL,
    onFilterChange: (ProductFilter) -> Unit = {},
    isSelectionMode: Boolean = false,
    selectedArticleIds: Set<String> = emptySet(),
    onArticleSelectionToggle: (String) -> Unit = {}
) {
    var filterExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
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
                value = state.recentArticles.size.toString(),
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = stringResource(Res.string.overview_active_orders),
                value = state.activeOrders.toString(),
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = stringResource(Res.string.overview_total_revenue),
                value = "${state.totalRevenue.formatPrice()}€",
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
            Text(
                text = stringResource(Res.string.overview_your_products),
                style = MaterialTheme.typography.titleMedium
            )

            // Filter Dropdown
            Box {
                OutlinedButton(
                    onClick = { filterExpanded = true }
                ) {
                    Text(
                        text = when (currentFilter) {
                            ProductFilter.ALL -> stringResource(Res.string.overview_filter_all)
                            ProductFilter.AVAILABLE -> stringResource(Res.string.overview_filter_available)
                            ProductFilter.NOT_AVAILABLE -> stringResource(Res.string.overview_filter_not_available)
                        }
                    )
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

        if (state.recentArticles.isEmpty()) {
            // Empty state
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "📦",
                        style = MaterialTheme.typography.displayMedium
                    )
                    Text(
                        text = stringResource(Res.string.overview_no_products),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(Res.string.overview_create_first),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            // Product list (height constrained to show 5 items)
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                items(state.recentArticles) { article ->
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
                                // TODO: Edit product
                            }
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
            modifier = Modifier.padding(16.dp)
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
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Checkbox in selection mode
        if (isSelectionMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onClick() },
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        // Product item
        ProductListItem(
            productName = productName,
            price = price,
            imageUrl = imageUrl,
            unit = unit,
            onClick = onClick,
            modifier = Modifier.weight(1f)
        )
    }
}
