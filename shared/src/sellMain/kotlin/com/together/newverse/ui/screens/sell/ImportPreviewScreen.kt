package com.together.newverse.ui.screens.sell

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.together.newverse.domain.model.Product
import com.together.newverse.ui.a11y.productPriceLabel
import com.together.newverse.util.formatPrice
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/**
 * Screen for previewing and selecting products to import from BNN file
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportPreviewScreen(
    products: List<Product>,
    isImporting: Boolean,
    onImportSelected: (List<Product>) -> Unit,
    onCancel: () -> Unit
) {
    var selectedProducts by remember { mutableStateOf(products.toSet()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.import_preview_title),
                        modifier = Modifier.semantics { heading() }
                    )
                },
                actions = {
                    // Select All / Deselect All toggle
                    TextButton(
                        onClick = {
                            selectedProducts = if (selectedProducts.size == products.size) {
                                emptySet()
                            } else {
                                products.toSet()
                            }
                        }
                    ) {
                        Text(
                            if (selectedProducts.size == products.size) stringResource(Res.string.import_select_none)
                            else stringResource(Res.string.import_select_all)
                        )
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                        enabled = !isImporting
                    ) {
                        Text(stringResource(Res.string.button_cancel))
                    }
                    Button(
                        onClick = { onImportSelected(selectedProducts.toList()) },
                        modifier = Modifier.weight(1f),
                        enabled = selectedProducts.isNotEmpty() && !isImporting
                    ) {
                        if (isImporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(Res.string.import_count_button, selectedProducts.size))
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Summary
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                val foundLabel = stringResource(Res.string.import_products_found_label)
                val selectedLabel = stringResource(Res.string.import_selected_label)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Merge count + caption so a screen reader hears "42 Produkte gefunden",
                    // not a bare "42" followed by a separate label.
                    Column(
                        modifier = Modifier.semantics(mergeDescendants = true) {
                            contentDescription = "${products.size} $foundLabel"
                        }
                    ) {
                        Text(
                            text = "${products.size}",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = foundLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    // The selected count changes as rows are toggled — announce it politely.
                    Column(
                        horizontalAlignment = Alignment.End,
                        modifier = Modifier.semantics(mergeDescendants = true) {
                            contentDescription = "${selectedProducts.size} $selectedLabel"
                            liveRegion = LiveRegionMode.Polite
                        }
                    ) {
                        Text(
                            text = "${selectedProducts.size}",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = selectedLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // Product list
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(products, key = { it.productId }) { product ->
                    ImportProductItem(
                        product = product,
                        isSelected = selectedProducts.contains(product),
                        onToggle = {
                            selectedProducts = if (selectedProducts.contains(product)) {
                                selectedProducts - product
                            } else {
                                selectedProducts + product
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ImportProductItem(
    product: Product,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    val priceLabel = productPriceLabel(product.price, product.unit)
    val selectedStateText =
        if (isSelected) stringResource(Res.string.a11y_state_selected)
        else stringResource(Res.string.a11y_state_unselected)
    val rowDescription = buildString {
        append(product.productName)
        append(", "); append(product.category)
        if (product.origin.isNotEmpty()) { append(", "); append(product.origin) }
        append(", "); append(priceLabel)
    }

    Card(
        onClick = onToggle,
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = rowDescription
                selected = isSelected
                stateDescription = selectedStateText
            },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Redundant with the row's selected state — hide from the a11y tree.
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggle() },
                modifier = Modifier.clearAndSetSemantics { }
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = product.productName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = product.category,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (product.origin.isNotEmpty()) {
                        Text(
                            text = product.origin,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${product.price.formatPrice()}€",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "/${product.unit}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
