package com.together.newverse.ui.screens.sell

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.together.newverse.ui.components.ProductListItem
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun OverviewScreen(
    viewModel: OverviewViewModel = koinViewModel(),
    isSelectionMode: Boolean = false,
    onSelectionModeChange: (Boolean) -> Unit = {},
    isAvailabilityMode: Boolean = false,
    onAvailabilityModeChange: (Boolean) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedArticleIds by remember { mutableStateOf(setOf<String>()) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showAvailableDialog by remember { mutableStateOf(false) }
    var showUnavailableDialog by remember { mutableStateOf(false) }

    // Clear selections when exiting selection modes
    androidx.compose.runtime.LaunchedEffect(isSelectionMode, isAvailabilityMode) {
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
                    SuccessContent(
                        state = state,
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
                    contentDescription = "Delete selected",
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
                    Text("Verfügbar")
                }

                // "Nicht vorhanden" button
                Button(
                    onClick = { showUnavailableDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Nicht vorhanden")
                }
            }
        }

        // Delete confirmation dialog
        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("Delete Products") },
                text = {
                    Text("Are you sure you want to delete ${selectedArticleIds.size} selected product(s)? This action cannot be undone.")
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
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Available confirmation dialog
        if (showAvailableDialog) {
            AlertDialog(
                onDismissRequest = { showAvailableDialog = false },
                title = { Text("Set Products as Available") },
                text = {
                    Text("Are you sure you want to mark ${selectedArticleIds.size} selected product(s) as available?")
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
                        Text("OK")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAvailableDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Unavailable confirmation dialog
        if (showUnavailableDialog) {
            AlertDialog(
                onDismissRequest = { showUnavailableDialog = false },
                title = { Text("Set Products as Unavailable") },
                text = {
                    Text("Are you sure you want to mark ${selectedArticleIds.size} selected product(s) as unavailable?")
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
                        Text("OK")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showUnavailableDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
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
                text = "Loading overview...",
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
                text = "Error loading overview",
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
                Text("Retry")
            }
        }
    }
}

@Composable
private fun SuccessContent(
    state: OverviewUiState.Success,
    isSelectionMode: Boolean = false,
    selectedArticleIds: Set<String> = emptySet(),
    onArticleSelectionToggle: (String) -> Unit = {}
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Stats Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard(
                title = stringResource(Res.string.overview_total_products),
                value = state.totalProducts.toString(),
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = stringResource(Res.string.overview_active_orders),
                value = state.activeOrders.toString(),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Products Section
        Text(
            text = stringResource(Res.string.overview_your_products),
            style = MaterialTheme.typography.titleMedium
        )

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
                        text = "No products yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Create your first product to get started",
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
