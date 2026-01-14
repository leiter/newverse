package com.together.newverse.ui.mainscreen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.together.newverse.domain.model.Article
import com.together.newverse.util.formatPrice
import com.together.newverse.util.rememberKeyboardManager
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.place_holder_landscape
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun HeroProductCard(
    product: Article,
    quantity: Double,
    originalQuantity: Double,
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

    // Check if quantity has changed from original
    val hasChanges = isInBasket && quantity != originalQuantity

    // Local state for text field
    var quantityText by remember(quantity, product.id) {
        mutableStateOf(formatQuantity(quantity, isWeightBased))
    }

    // Keyboard manager for dismissing keyboard on Done (platform-specific)
    val keyboardManager = rememberKeyboardManager()

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // Responsive sizing based on screen width
        val isCompact = maxWidth < 400.dp
        val imageSize = if (isCompact) 80.dp else 100.dp
        val contentPadding = if (isCompact) 16.dp else 20.dp

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
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
                        .fillMaxWidth()
                        .padding(contentPadding)
                ) {
                    // Top section: Product Info (left) + Image (right)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        // Left: Badge + Favorite, Name, Price
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
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

                                IconButton(
                                    onClick = onToggleFavourite,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isFavourite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        contentDescription = if (isFavourite) "Remove from favourites" else "Add to favourites",
                                        tint = if (isFavourite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = product.productName,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
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

                        Spacer(modifier = Modifier.width(12.dp))

                        // Right: Product Image with Favourite button overlay
                        Box(
                            modifier = Modifier
                                .size(imageSize)
                                .clip(RoundedCornerShape(16.dp))
                        ) {
                            if (product.imageUrl.isNotEmpty()) {
                                SubcomposeAsyncImage(
                                    model = product.imageUrl,
                                    contentDescription = product.productName,
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
                                                modifier = Modifier.size(24.dp),
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    },
                                    error = {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(MaterialTheme.colorScheme.surfaceVariant),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Image(
                                                painter = painterResource(Res.drawable.place_holder_landscape),
                                                contentDescription = "Placeholder",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        }
                                    }
                                )
                            } else {
                                // Placeholder when no image URL
                                Image(
                                    painter = painterResource(Res.drawable.place_holder_landscape),
                                    contentDescription = "Placeholder",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Quantity and Add to Cart
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                                    modifier = Modifier.size(
                                        if (!isWeightBased) 36.dp else 16.dp
                                    ),
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
                                    val textFieldMaxWidth = if (isCompact) 56.dp else 80.dp
                                    Box(
                                        modifier = Modifier.widthIn(min = 40.dp, max = textFieldMaxWidth),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        BasicTextField(
                                            value = quantityText,
                                            onValueChange = { newText ->
                                                // Allow only valid input (numbers, comma, dot)
                                                var filtered = newText.filter { it.isDigit() || it == ',' || it == '.' }

                                                // If current text is "0" and user types a non-zero digit, replace the 0
                                                // Handles both "50" (cursor at start) and "05" (cursor at end)
                                                if (quantityText == "0" && filtered.length == 2) {
                                                    val nonZeroDigit = filtered.firstOrNull { it.isDigit() && it != '0' }
                                                    if (nonZeroDigit != null && filtered.count { it == '0' } == 1) {
                                                        filtered = nonZeroDigit.toString()
                                                    }
                                                }

                                                // Remove leading zeros except for decimal numbers like "0.5" or "0,5"
                                                if (filtered.length > 1 && filtered.startsWith("0") && filtered.getOrNull(1)?.isDigit() == true) {
                                                    filtered = filtered.dropWhile { it == '0' }.ifEmpty { "0" }
                                                }

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
                                                keyboardType = if (isWeightBased) KeyboardType.Decimal else KeyboardType.Number,
                                                imeAction = ImeAction.Done
                                            ),
                                            keyboardActions = KeyboardActions(
                                                onDone = {
                                                    keyboardManager.hide()
                                                }
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
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1
                                        )
                                    }
                                }

                                // Show +/- buttons only for piece-based products, but reserve space
                                Box(
                                    modifier = Modifier.size(
                                        if (!isWeightBased) 36.dp else 16.dp
                                    ),
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

                        // Add to Cart or Apply Changes Button
                        if (isInBasket) {
                            // Apply Changes Button (disabled if no changes)
                            Button(
                                onClick = onAddToCart,
                                enabled = hasChanges,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.tertiary,
                                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.height(44.dp)
                            ) {
                                Icon(
                                    if (hasChanges) Icons.Default.Check else Icons.Default.ShoppingCart,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Ändern",
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1
                                )
                            }
                        } else {
                            // Add to Cart Button (for items not in basket)
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
                        // Cancel button (X) - only show when item is in basket
                        if (isInBasket) {
                            IconButton(
                                onClick = {
                                    // Reset to original quantity
                                    onRemoveFromBasket()
                                },
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Cancel changes",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } // End of main Column
            }
        }
    }
}
