package com.together.newverse.ui.mainscreen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.together.newverse.domain.model.Article
import com.together.newverse.domain.model.ArticleScarcity
import com.together.newverse.domain.model.ResolvedPrice
import com.together.newverse.ui.a11y.productCardSemantics
import com.together.newverse.ui.a11y.productPriceLabel
import com.together.newverse.ui.components.ProductImage
import com.together.newverse.util.formatPrice
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.cd_open_product_details
import newverse.shared.generated.resources.product_scarcity_last_few
import newverse.shared.generated.resources.product_scarcity_limited
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ModernProductCard(
    product: Article,
    /**
     * What this article costs at the market in context — `MainScreenState.priceOf`.
     * Not `product.price`, which is the seller's base price and quoted a different
     * number from the one the basket charged.
     */
    price: ResolvedPrice,
    modifier: Modifier = Modifier,
    /** Tapping the card itself, which promotes the article into the hero card. */
    onClick: () -> Unit,
    /** The info button in the corner, which opens the full detail screen. */
    onNavigateToDetail: () -> Unit,
) {
    val isBio = product.productName.contains("Bio", ignoreCase = true)
    // Browsing is where `LIMITED` earns its keep, so both bands show here — unlike
    // the basket, which only ever says "nearly gone".
    val scarcityLabel = when (product.scarcity) {
        ArticleScarcity.LAST_FEW -> stringResource(Res.string.product_scarcity_last_few)
        ArticleScarcity.LIMITED -> stringResource(Res.string.product_scarcity_limited)
        ArticleScarcity.PLENTIFUL, ArticleScarcity.UNKNOWN -> null
    }
    val cardDescription = buildString {
        append(product.productName)
        append(", ")
        append(productPriceLabel(price.gross, product.unit))
        scarcityLabel?.let { append(", "); append(it) }
        if (isBio) append(", Bio")
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // The card's click and its merged description sit on this Column rather
            // than on the Card, so the info button is a sibling of the merged node
            // instead of a descendant swallowed by it. A screen reader therefore gets
            // two stops per card -- the card, then the way into its details -- and the
            // two click actions never land on the same node.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onClick() }
                    .productCardSemantics(cardDescription)
                    .padding(12.dp)
            ) {
                // Product Image (decorative — the name below carries the meaning)
                ProductImage(
                    url = product.imageUrl,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    shape = RoundedCornerShape(12.dp),
                )

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
                        text = "${price.gross.formatPrice()}€",
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

                scarcityLabel?.let { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Bio Badge if applicable
                if (isBio) {
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

            // Over the image's top right corner. The backdrop is what keeps it legible:
            // the icon sits on a photograph, not on the card's surface.
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
            ) {
                IconButton(
                    onClick = onNavigateToDetail,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = stringResource(Res.string.cd_open_product_details),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
