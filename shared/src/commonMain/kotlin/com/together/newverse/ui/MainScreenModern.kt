package com.together.newverse.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.together.newverse.domain.model.Article
import com.together.newverse.util.formatPrice
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.place_holder_landscape
import org.jetbrains.compose.resources.painterResource

@Composable
fun MainScreenModern(
    state: MainScreenState,
    onAction: (MainScreenAction) -> Unit
) {
    MainScreenModernContent(
        state = state,
        onAction = onAction,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MainScreenModernContent(
    state: MainScreenState,
    onAction: (MainScreenAction) -> Unit,
) {
    val products = state.articles
    val selectedProduct = state.selectedArticle
    val quantity = state.selectedQuantity
    val basketItems = state.basketItems

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
                    selectedProduct?.let { product ->
                        val isInBasket = basketItems.any { it.productId == product.id }
                        val isFavourite = state.favouriteArticles.contains(product.id)
                        HeroProductCard(
                            product = product,
                            quantity = quantity,
                            isInBasket = isInBasket,
                            isFavourite = isFavourite,
                            onQuantityChange = { onAction(MainScreenAction.UpdateQuantity(it)) },
                            onAddToCart = { onAction(MainScreenAction.AddToCart) },
                            onRemoveFromBasket = { onAction(MainScreenAction.RemoveFromBasket) },
                            onToggleFavourite = { onAction(MainScreenAction.ToggleFavourite(product.id)) }
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    CategoryChips()

                }
            }

            // Category Filter Chips

            // Section Header
            item {
                SectionHeader(
                    title = "Frisch vom Feld",
                    subtitle = "Heute geerntet"
                )
            }

            // Show loading or error state
            if (state.isLoading && products.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Loading articles...", style = MaterialTheme.typography.bodyLarge)
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
                            state.error,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
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
                                onAction(MainScreenAction.SelectArticle(product))
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
}

@Composable
private fun HeroProductCard(
    product: Article,
    quantity: Double,
    isInBasket: Boolean,
    isFavourite: Boolean,
    onQuantityChange: (Double) -> Unit,
    onAddToCart: () -> Unit,
    onRemoveFromBasket: () -> Unit,
    onToggleFavourite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Helper function to check if unit is weight-based
    val isWeightBased = product.unit.lowercase() in listOf("kg", "g", "kilogramm", "gramm")

    // Local state for text field
    var quantityText by remember(quantity, product.id) {
        mutableStateOf(formatQuantity(quantity, isWeightBased))
    }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Background gradient
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                Color.Transparent
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Product Info
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Badge(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ) {
                            Text(
                                "Tagesfrisch",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = product.productName,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "${product.price.formatPrice()}€ / ${product.unit}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (quantity > 0.0) {
                                Text(
                                    text = "•",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                val totalPrice = product.price * quantity
                                Text(
                                    text = "${totalPrice.formatPrice()}€",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }

                    // Favourite Button
                    IconButton(
                        onClick = onToggleFavourite,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = if (isFavourite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = if (isFavourite) "Remove from favourites" else "Add to favourites",
                            tint = if (isFavourite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Quantity and Add to Cart
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Quantity Selector
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp) // Fixed height to prevent vertical shifts
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight()
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Show +/- buttons only for piece-based products, but reserve space
                            Box(
                                modifier = Modifier.size(36.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!isWeightBased) {
                                    IconButton(
                                        onClick = {
                                            val newQuantity = (quantity - 1.0).coerceAtLeast(0.0)
                                            onQuantityChange(newQuantity)
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Text("-", style = MaterialTheme.typography.titleLarge)
                                    }
                                }
                            }

                            // Editable TextField for quantity with unit display
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier.width(60.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    BasicTextField(
                                        value = quantityText,
                                        onValueChange = { newText ->
                                            // Allow only valid input (numbers, comma, dot)
                                            val filtered = newText.filter { it.isDigit() || it == ',' || it == '.' }
                                            if (filtered.count { it == ',' || it == '.' } <= 1) {
                                                quantityText = filtered
                                                // Parse and update quantity
                                                val parsedQuantity = filtered.replace(",", ".").toDoubleOrNull()
                                                if (parsedQuantity != null) {
                                                    onQuantityChange(parsedQuantity)
                                                }
                                            }
                                        },
                                        textStyle = LocalTextStyle.current.copy(
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontSize = MaterialTheme.typography.titleMedium.fontSize,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center
                                        ),
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = if (isWeightBased) KeyboardType.Decimal else KeyboardType.Number
                                        ),
                                        singleLine = true,
                                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 4.dp)
                                    )
                                }

                                // Unit label - only show for weight-based products
                                if (isWeightBased) {
                                    Spacer(modifier = Modifier.width(4.dp))

                                    Text(
                                        text = product.unit,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            // Show +/- buttons only for piece-based products, but reserve space
                            Box(
                                modifier = Modifier.size(36.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!isWeightBased) {
                                    IconButton(
                                        onClick = {
                                            onQuantityChange(quantity + 1.0)
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null)
                                    }
                                }
                            }
                        }
                    }

                    // Add to Cart or Remove Button
                    if (isInBasket) {
                        // Remove from Basket Button
                        Button(
                            onClick = onRemoveFromBasket,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(44.dp)
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Aus Korb",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    } else {
                        // Add to Cart Button
                        Button(
                            onClick = onAddToCart,
                            enabled = quantity > 0.0,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary,
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(44.dp)
                        ) {
                            Icon(
                                Icons.Default.ShoppingCart,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "In den Korb",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryChips(
    modifier: Modifier = Modifier,
) {
    val categories = listOf("Alle", "Obst", "Gemüse", "Milch", "Eier", "Brot")
    var selectedCategory by remember { mutableStateOf("Alle") }

    androidx.compose.foundation.lazy.LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        items(categories) { category ->
            FilterChip(
                selected = selectedCategory == category,
                onClick = { selectedCategory = category },
                label = { Text(category) },
                leadingIcon = if (selectedCategory == category) {
                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else null,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        subtitle?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ModernProductCard(
    product: Article,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Product Image
            if (product.imageUrl.isNotEmpty()) {
                println("🖼️ ModernProductCard: Loading image for '${product.productName}' from URL: ${product.imageUrl}")
                SubcomposeAsyncImage(
                    model = product.imageUrl,
                    contentDescription = product.productName,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop,
                    loading = {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    error = {
                        // Show landscape placeholder on error
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            // Show portrait placeholder on error
                            androidx.compose.foundation.Image(
                                painter = painterResource(Res.drawable.place_holder_landscape),
                                contentDescription = "Error loading image",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
//                            Icon(
//                                imageVector = Icons.Default.Star,
//                                contentDescription = "Error loading image",
//                                modifier = Modifier.zIndex(2f).size(40.dp),
//                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
//                            )
                        }
                    }
                )
            } else {
                println("🖼️ ModernProductCard: No image URL for '${product.productName}', showing placeholder")
                // Placeholder with Category Icon when no image
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = when (product.category) {
                        "Obst" -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)
                        "Gemüse" -> MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        "Eier" -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = when (product.category) {
                                "Obst" -> Icons.Default.Star
                                "Gemüse" -> Icons.Default.Favorite
                                "Eier" -> Icons.Default.Settings
                                else -> Icons.Default.ShoppingCart
                            },
                            contentDescription = null,
                            tint = when (product.category) {
                                "Obst" -> MaterialTheme.colorScheme.secondary
                                "Gemüse" -> MaterialTheme.colorScheme.primary
                                "Eier" -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.outline
                            },
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Product Name
            Text(
                text = product.productName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Price and Unit
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = "${product.price.formatPrice()}€",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "/ ${product.unit}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Bio Badge if applicable
            if (product.productName.contains("Bio", ignoreCase = true)) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "BIO",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

/**
 * Formats quantity for display based on whether it's weight-based or piece-based
 */
private fun formatQuantity(quantity: Double, isWeightBased: Boolean): String {
    return if (isWeightBased) {
        if (quantity == 0.0) {
            "0"
        } else {
            // Format with 3 decimal places and trim trailing zeros
            val formatted = (quantity * 1000).toInt() / 1000.0
            val parts = formatted.toString().split('.')
            if (parts.size == 2) {
                val intPart = parts[0]
                val decPart = parts[1].take(3).trimEnd('0')
                if (decPart.isEmpty()) intPart else "$intPart.$decPart"
            } else {
                parts[0]
            }
        }
    } else {
        quantity.toInt().toString()
    }
}
