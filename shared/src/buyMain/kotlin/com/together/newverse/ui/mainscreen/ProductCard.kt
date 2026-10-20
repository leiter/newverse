package com.together.newverse.ui.mainscreen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.together.newverse.ui.a11y.productCardSemantics
import com.together.newverse.ui.a11y.productPriceLabel
import com.together.newverse.ui.components.ProductImage
import com.together.newverse.util.formatPrice
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.product_scarcity_last_few
import newverse.shared.generated.resources.product_scarcity_limited
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ModernProductCard(
    product: Article,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
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
        append(productPriceLabel(product.price, product.unit))
        scarcityLabel?.let { append(", "); append(it) }
        if (isBio) append(", Bio")
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .productCardSemantics(cardDescription),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
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
    }
}
