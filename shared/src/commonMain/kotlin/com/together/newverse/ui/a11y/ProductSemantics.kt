package com.together.newverse.ui.a11y

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.together.newverse.util.formatPrice
import com.together.newverse.util.formatString
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.a11y_price_per
import org.jetbrains.compose.resources.stringResource

/**
 * One spoken phrase for a product's price, e.g. "2,50 € pro kg" (DE) / "2,50 € per kg" (EN).
 * Replaces the visual "2,50€ / kg", which a screen reader would read as "… slash kg".
 */
@Composable
fun productPriceLabel(price: Double, unit: String): String =
    formatString(stringResource(Res.string.a11y_price_per), "${price.formatPrice()} €", unit)

/**
 * Collapse a product card into a single accessibility node carrying [description]
 * (typically the product name followed by [productPriceLabel]). Any click action on
 * the same modifier chain is merged in, so a screen reader stops once per card
 * instead of once per inner text / icon.
 *
 * The product image is expected to opt out of semantics on its own (see
 * [com.together.newverse.ui.components.ProductImage]).
 */
fun Modifier.productCardSemantics(description: String): Modifier =
    semantics(mergeDescendants = true) {
        contentDescription = description
    }
