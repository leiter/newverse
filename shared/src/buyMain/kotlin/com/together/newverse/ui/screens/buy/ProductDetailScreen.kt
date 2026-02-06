package com.together.newverse.ui.screens.buy

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.together.newverse.domain.model.Article
import com.together.newverse.domain.model.OrderedProduct
import com.together.newverse.util.formatPrice
import com.together.newverse.util.rememberKeyboardManager
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.place_holder_landscape
import newverse.shared.generated.resources.products_detail_add_to_cart
import newverse.shared.generated.resources.products_detail_category
import newverse.shared.generated.resources.products_detail_description
import newverse.shared.generated.resources.products_detail_not_found
import newverse.shared.generated.resources.products_detail_quantity
import newverse.shared.generated.resources.products_detail_remove_from_cart
import newverse.shared.generated.resources.products_detail_title
import newverse.shared.generated.resources.products_detail_update_cart
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailScreen(
    article: Article?,
    basketItems: List<OrderedProduct>,
    favouriteArticles: List<String>,
    onAddToCart: (Article, Double) -> Unit,
    onRemoveFromCart: (String) -> Unit,
    onToggleFavourite: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    if (article == null) {
        // Product not found state
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(Res.string.products_detail_title)) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(Res.string.products_detail_not_found),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    // Check if this product is in the basket
    val basketItem = basketItems.find { it.id == article.id || it.productId == article.id }
    val isInBasket = basketItem != null
    val originalQuantity = basketItem?.amountCount ?: 0.0
    val isFavourite = favouriteArticles.contains(article.id)

    // Helper function to check if unit is weight-based
    val isWeightBased = article.unit.lowercase() in listOf("kg", "g", "kilogramm", "gramm")

    // Local state for quantity
    var quantity by remember(article.id, originalQuantity) {
        mutableStateOf(if (isInBasket) originalQuantity else 1.0)
    }
    var quantityText by remember(quantity, article.id) {
        mutableStateOf(formatQuantityForDisplay(quantity, isWeightBased))
    }

    val keyboardManager = rememberKeyboardManager()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.products_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { onToggleFavourite(article.id) }) {
                        Icon(
                            imageVector = if (isFavourite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = if (isFavourite) "Remove from favourites" else "Add to favourites",
                            tint = if (isFavourite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // Large Product Image
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
            ) {
                if (article.imageUrl.isNotEmpty()) {
                    SubcomposeAsyncImage(
                        model = article.imageUrl,
                        contentDescription = article.productName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        loading = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(48.dp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        error = {
                            Image(
                                painter = painterResource(Res.drawable.place_holder_landscape),
                                contentDescription = "Placeholder",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    )
                } else {
                    Image(
                        painter = painterResource(Res.drawable.place_holder_landscape),
                        contentDescription = "Placeholder",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            // Product Details
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Product Name
                Text(
                    text = article.productName,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Price
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "${article.price.formatPrice()}€ / ${article.unit}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    if (quantity > 0) {
                        Text(
                            text = "=",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${(article.price * quantity).formatPrice()}€",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Category
                if (article.category.isNotEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(Res.string.products_detail_category) + ": ",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = article.category,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }

                // Description / Detail Info
                if (article.detailInfo.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = stringResource(Res.string.products_detail_description),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = article.detailInfo,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Quantity Selector
                Text(
                    text = stringResource(Res.string.products_detail_quantity),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Minus button for piece-based products
                        if (!isWeightBased) {
                            IconButton(
                                onClick = {
                                    val newQuantity = (quantity - 1.0).coerceAtLeast(0.0)
                                    quantity = newQuantity
                                    quantityText = formatQuantityForDisplay(newQuantity, false)
                                },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Text("-", style = MaterialTheme.typography.headlineMedium)
                            }
                        } else {
                            Spacer(modifier = Modifier.width(48.dp))
                        }

                        // Quantity Text Field
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                BasicTextField(
                                    value = quantityText,
                                    onValueChange = { newText ->
                                        var filtered = newText.filter { it.isDigit() || it == ',' || it == '.' }
                                        if (quantityText == "0" && filtered.length == 2) {
                                            val nonZeroDigit = filtered.firstOrNull { it.isDigit() && it != '0' }
                                            if (nonZeroDigit != null && filtered.count { it == '0' } == 1) {
                                                filtered = nonZeroDigit.toString()
                                            }
                                        }
                                        if (filtered.length > 1 && filtered.startsWith("0") && filtered.getOrNull(1)?.isDigit() == true) {
                                            filtered = filtered.dropWhile { it == '0' }.ifEmpty { "0" }
                                        }
                                        if (filtered.count { it == ',' || it == '.' } <= 1) {
                                            quantityText = filtered
                                            val parsedQuantity = filtered.replace(",", ".").toDoubleOrNull()
                                            if (parsedQuantity != null) {
                                                quantity = parsedQuantity
                                            }
                                        }
                                    },
                                    textStyle = LocalTextStyle.current.copy(
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = MaterialTheme.typography.headlineSmall.fontSize,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center
                                    ),
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = if (isWeightBased) KeyboardType.Decimal else KeyboardType.Number,
                                        imeAction = ImeAction.Done
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onDone = { keyboardManager.hide() }
                                    ),
                                    singleLine = true,
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    modifier = Modifier.width(80.dp)
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                Text(
                                    text = article.unit,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Plus button for piece-based products
                        if (!isWeightBased) {
                            IconButton(
                                onClick = {
                                    val newQuantity = quantity + 1.0
                                    quantity = newQuantity
                                    quantityText = formatQuantityForDisplay(newQuantity, false)
                                },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(24.dp))
                            }
                        } else {
                            Spacer(modifier = Modifier.width(48.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action Buttons
                if (isInBasket) {
                    // Update / Remove buttons for items in basket
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Update button
                        Button(
                            onClick = { onAddToCart(article, quantity) },
                            enabled = quantity != originalQuantity && quantity > 0,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.ShoppingCart, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(Res.string.products_detail_update_cart))
                        }

                        // Remove button
                        OutlinedButton(
                            onClick = { onRemoveFromCart(article.id) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(Res.string.products_detail_remove_from_cart))
                        }
                    }
                } else {
                    // Add to cart button for items not in basket
                    Button(
                        onClick = { onAddToCart(article, quantity) },
                        enabled = quantity > 0,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.ShoppingCart, contentDescription = null, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(Res.string.products_detail_add_to_cart),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

/**
 * Format quantity for display in text field
 */
private fun formatQuantityForDisplay(quantity: Double, isWeightBased: Boolean): String {
    return if (isWeightBased) {
        // For weight-based, show with decimals using comma
        val formatted = quantity.toString()
        if (formatted.endsWith(".0")) {
            formatted.dropLast(2)
        } else {
            formatted.replace(".", ",")
        }
    } else {
        // For piece-based, show as whole number
        quantity.toInt().toString()
    }
}
