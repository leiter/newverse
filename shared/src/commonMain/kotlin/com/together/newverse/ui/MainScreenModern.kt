package com.together.newverse.ui

// Using filled icons instead of outlined for better compatibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.together.newverse.domain.model.Article
// Removed hard-coded color imports - will use theme colors instead
import com.together.newverse.util.formatPrice

@Composable
fun MainScreenModern(
    onProfileClick: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    onCartClick: () -> Unit = {}
) {
    // Sample product data
    val products = remember {
        listOf(
            Article(id = "1", productName = "Bio Erdbeeren", price = 2.96, unit = "Schale", category = "Obst"),
            Article(id = "2", productName = "Stangensellerie", price = 2.90, unit = "Stück", category = "Gemüse"),
            Article(id = "3", productName = "Knoblauch", price = 10.69, unit = "kg", category = "Gemüse"),
            Article(id = "4", productName = "Atomic Red Möhren", price = 3.69, unit = "Bund", category = "Gemüse"),
            Article(id = "5", productName = "Linda Kartoffeln", price = 2.30, unit = "kg", category = "Gemüse"),
            Article(id = "6", productName = "Bio Eier", price = 0.60, unit = "Stück", category = "Eier"),
            Article(id = "7", productName = "Feigenbananen", price = 2.30, unit = "kg", category = "Obst"),
            Article(id = "8", productName = "Granny Smith", price = 2.30, unit = "kg", category = "Obst"),
            Article(id = "9", productName = "Siglinde Kartoffeln", price = 2.90, unit = "kg", category = "Gemüse")
        )
    }

    var selectedProduct by remember { mutableStateOf(products[0]) }
    var quantity by remember { mutableStateOf(0) }
    val cartItemCount by remember { mutableStateOf(0) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero Section - Featured Product
            item {
                HeroProductCard(
                    product = selectedProduct,
                    quantity = quantity,
                    onQuantityChange = { quantity = it },
                    onAddToCart = { /* Add to cart logic */ }
                )
            }

            // Category Filter Chips
            item {
                CategoryChips()
            }

            // Section Header
            item {
                SectionHeader(
                    title = "Frisch vom Feld",
                    subtitle = "Heute geerntet"
                )
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
                                selectedProduct = product
                                quantity = 0
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
    quantity: Int,
    onQuantityChange: (Int) -> Unit,
    onAddToCart: () -> Unit,
    modifier: Modifier = Modifier
) {
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

                        Text(
                            text = "${product.price.formatPrice()}€ / ${product.unit}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    // Eco Badge
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                Icons.Default.Favorite,
                                contentDescription = "Bio",
                                tint = MaterialTheme.colorScheme.onSecondary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
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
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { if (quantity > 0) onQuantityChange(quantity - 1) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Text("-", style = MaterialTheme.typography.titleLarge)
                            }

                            Text(
                                text = quantity.toString(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )

                            IconButton(
                                onClick = { onQuantityChange(quantity + 1) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                            }
                        }
                    }

                    // Add to Cart Button
                    Button(
                        onClick = onAddToCart,
                        enabled = quantity > 0,
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

@Composable
private fun CategoryChips(
    modifier: Modifier = Modifier
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
    modifier: Modifier = Modifier
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
    onClick: () -> Unit
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
            // Product Image Placeholder with Category Icon
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

