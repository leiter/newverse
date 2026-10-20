package com.together.newverse.ui.screens.sell

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.together.newverse.domain.model.PriceCause
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.padding
import com.together.newverse.domain.model.BelowCostArticle
import com.together.newverse.domain.model.MarketPricing
import com.together.newverse.domain.model.SellerArticle
import com.together.newverse.domain.model.pricingBelowCost
import com.together.newverse.domain.model.resolvePrice
import com.together.newverse.ui.components.PriceBreakdown
import com.together.newverse.ui.components.formatEuro
import kotlin.math.abs
import newverse.shared.generated.resources.price_below_cost_more
import newverse.shared.generated.resources.price_below_cost_row
import newverse.shared.generated.resources.price_below_cost_title
import newverse.shared.generated.resources.price_preview_article
import newverse.shared.generated.resources.price_preview_title
import newverse.shared.generated.resources.price_preview_unchanged
import com.together.newverse.domain.model.PriceCondition
import com.together.newverse.domain.model.PriceEffect
import com.together.newverse.domain.model.PriceScope
import com.together.newverse.ui.components.displayName
import org.jetbrains.compose.resources.stringResource
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.price_condition_add
import newverse.shared.generated.resources.price_condition_amount
import newverse.shared.generated.resources.price_condition_cause
import newverse.shared.generated.resources.price_condition_effect
import newverse.shared.generated.resources.price_condition_note
import newverse.shared.generated.resources.price_condition_remove
import newverse.shared.generated.resources.price_condition_scope
import newverse.shared.generated.resources.price_conditions_title
import newverse.shared.generated.resources.price_effect_discount
import newverse.shared.generated.resources.price_effect_factor
import newverse.shared.generated.resources.price_effect_surcharge
import newverse.shared.generated.resources.price_scope_all
import newverse.shared.generated.resources.price_scope_article
import newverse.shared.generated.resources.price_scope_category

/**
 * What makes this market's prices differ.
 *
 * The seller states causes, not numbers: the buyer's breakdown is rendered from
 * exactly these rows, so an explanation cannot drift away from the price it
 * explains. Lives inside the market dialog because a condition belongs to the
 * stall whose costs it describes.
 */
@Composable
fun PriceConditionEditor(
    conditions: List<PriceCondition>,
    onConditionsChange: (List<PriceCondition>) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * The seller's catalogue with its purchase prices, for the preview and the
     * below-cost warning. Empty is fine — both simply do not appear, which is what a
     * seller who has not costed anything yet gets.
     */
    articles: List<SellerArticle> = emptyList(),
) {
    // Priced against the conditions as they stand in the dialog, not as last saved:
    // the seller is composing, and a preview of the saved state would answer a
    // question they are not asking. Normalized first so a half-typed row — amount
    // still blank — does not read as a 0,00 € surcharge.
    val draftPricing = MarketPricing(conditions = conditions.normalizedForSave())
    val belowCost = remember(conditions, articles) {
        if (articles.isEmpty()) emptyList()
        else pricingBelowCost(articles, draftPricing)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(Res.string.price_conditions_title),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.semantics { heading() }
        )

        conditions.forEachIndexed { index, condition ->
            ConditionRow(
                condition = condition,
                onChange = { updated ->
                    onConditionsChange(conditions.toMutableList().also { it[index] = updated })
                },
                onRemove = {
                    onConditionsChange(conditions.toMutableList().also { it.removeAt(index) })
                }
            )
            if (index != conditions.lastIndex) HorizontalDivider()
        }

        TextButton(onClick = { onConditionsChange(conditions + PriceCondition()) }) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(Res.string.price_condition_add))
        }

        if (belowCost.isNotEmpty()) {
            BelowCostWarning(belowCost)
        }

        if (articles.isNotEmpty() && draftPricing.conditions.isNotEmpty()) {
            PricePreview(articles = articles, pricing = draftPricing)
        }
    }
}

/**
 * What these conditions would sell below cost.
 *
 * A warning, not a block — a loss-leader is the seller's call to make, and a market
 * with a discount condition may be exactly that. The point is that it cannot happen
 * unnoticed. `resolvePrice` cannot do this itself: it also runs in the buy app, where
 * the purchase price deliberately does not exist.
 *
 * Worst shortfall first, and capped: a factor applied to everything can put a whole
 * catalogue under water, and three hundred rows is not a warning, it is a wall.
 */
@Composable
private fun BelowCostWarning(belowCost: List<BelowCostArticle>) {
    val shown = belowCost.take(5)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(12.dp)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = stringResource(Res.string.price_below_cost_title, belowCost.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }

        shown.forEach { entry ->
            Text(
                text = stringResource(
                    Res.string.price_below_cost_row,
                    entry.productName,
                    formatEuro(entry.gross),
                    formatEuro(entry.acquirePrice)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }

        if (belowCost.size > shown.size) {
            Text(
                text = stringResource(
                    Res.string.price_below_cost_more,
                    belowCost.size - shown.size
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

/**
 * What one article ends up costing at this market, and why.
 *
 * The seller was composing causes and amounts with no sight of the result. This is
 * the same [PriceBreakdown] the buyer is meant to see, fed the draft conditions, so
 * the two can never explain the same price differently.
 *
 * Defaults to the article the conditions move most, since a preview of an unaffected
 * article says nothing — but any article can be picked, because "did my category
 * condition miss this one?" is the other question being asked here.
 */
@Composable
private fun PricePreview(
    articles: List<SellerArticle>,
    pricing: MarketPricing,
) {
    val mostAffected = remember(articles, pricing) {
        articles.maxByOrNull { seller ->
            val resolved = resolvePrice(seller.article, pricing)
            abs(resolved.gross - resolved.base)
        }
    }
    var selectedId by remember(mostAffected?.article?.id) {
        mutableStateOf(mostAffected?.article?.id.orEmpty())
    }
    val selected = articles.firstOrNull { it.article.id == selectedId } ?: return
    var pickerOpen by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(Res.string.price_preview_title),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.semantics { heading() }
        )

        Picker(
            label = stringResource(Res.string.price_preview_article),
            value = selected.article.productName,
            open = pickerOpen,
            onOpenChange = { pickerOpen = it },
            modifier = Modifier.fillMaxWidth()
        ) {
            articles.forEach { seller ->
                DropdownMenuItem(
                    text = { Text(seller.article.productName) },
                    onClick = { selectedId = seller.article.id; pickerOpen = false }
                )
            }
        }

        val resolved = resolvePrice(selected.article, pricing)
        if (resolved.isAdjusted) {
            PriceBreakdown(price = resolved)
        } else {
            // Not an error, and worth saying: a seller who scoped a condition to one
            // category needs to see that it leaves everything else alone.
            Text(
                text = stringResource(Res.string.price_preview_unchanged, formatEuro(resolved.gross)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ConditionRow(
    condition: PriceCondition,
    onChange: (PriceCondition) -> Unit,
    onRemove: () -> Unit,
) {
    var causeOpen by remember { mutableStateOf(false) }
    var effectOpen by remember { mutableStateOf(false) }
    var scopeOpen by remember { mutableStateOf(false) }

    val effectLabel = stringResource(
        when (condition.effect) {
            is PriceEffect.Factor -> Res.string.price_effect_factor
            is PriceEffect.Surcharge -> Res.string.price_effect_surcharge
            is PriceEffect.Discount -> Res.string.price_effect_discount
        }
    )
    val scopeLabel = stringResource(
        when (condition.scope) {
            PriceScope.AllArticles -> Res.string.price_scope_all
            is PriceScope.Category -> Res.string.price_scope_category
            is PriceScope.SingleArticle -> Res.string.price_scope_article
        }
    )
    val amount = when (val e = condition.effect) {
        is PriceEffect.Factor -> e.value
        is PriceEffect.Surcharge -> e.amount
        is PriceEffect.Discount -> e.amount
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Picker(
                label = stringResource(Res.string.price_condition_cause),
                value = condition.cause.displayName(),
                open = causeOpen,
                onOpenChange = { causeOpen = it },
                modifier = Modifier.weight(1f)
            ) {
                PriceCause.entries.forEach { cause ->
                    DropdownMenuItem(
                        text = { Text(cause.displayName()) },
                        onClick = { onChange(condition.copy(cause = cause)); causeOpen = false }
                    )
                }
            }

            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(Res.string.price_condition_remove),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Picker(
                label = stringResource(Res.string.price_condition_effect),
                value = effectLabel,
                open = effectOpen,
                onOpenChange = { effectOpen = it },
                modifier = Modifier.weight(1f)
            ) {
                listOf(
                    Res.string.price_effect_factor to PriceEffect.Factor(amount.takeIf { it > 0 } ?: 1.0),
                    Res.string.price_effect_surcharge to PriceEffect.Surcharge(amount),
                    Res.string.price_effect_discount to PriceEffect.Discount(amount)
                ).forEach { (label, effect) ->
                    DropdownMenuItem(
                        text = { Text(stringResource(label)) },
                        onClick = { onChange(condition.copy(effect = effect)); effectOpen = false }
                    )
                }
            }

            OutlinedTextField(
                value = if (amount == 0.0) "" else amount.toString(),
                onValueChange = { typed ->
                    val parsed = typed.replace(',', '.').toDoubleOrNull() ?: 0.0
                    onChange(
                        condition.copy(
                            effect = when (condition.effect) {
                                is PriceEffect.Factor -> PriceEffect.Factor(parsed)
                                is PriceEffect.Surcharge -> PriceEffect.Surcharge(parsed)
                                is PriceEffect.Discount -> PriceEffect.Discount(parsed)
                            }
                        )
                    )
                },
                label = { Text(stringResource(Res.string.price_condition_amount)) },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
        }

        Picker(
            label = stringResource(Res.string.price_condition_scope),
            value = when (val s = condition.scope) {
                PriceScope.AllArticles -> scopeLabel
                is PriceScope.Category -> "$scopeLabel: ${s.name}"
                is PriceScope.SingleArticle -> "$scopeLabel: ${s.articleId}"
            },
            open = scopeOpen,
            onOpenChange = { scopeOpen = it },
            modifier = Modifier.fillMaxWidth()
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.price_scope_all)) },
                onClick = { onChange(condition.copy(scope = PriceScope.AllArticles)); scopeOpen = false }
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.price_scope_category)) },
                onClick = { onChange(condition.copy(scope = PriceScope.Category(""))); scopeOpen = false }
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.price_scope_article)) },
                onClick = { onChange(condition.copy(scope = PriceScope.SingleArticle(""))); scopeOpen = false }
            )
        }

        // A scope has to name what it narrows to, or it would silently apply to
        // nothing — which reads to the seller as the condition not working.
        when (val s = condition.scope) {
            is PriceScope.Category -> ScopeValueField(s.name) {
                onChange(condition.copy(scope = PriceScope.Category(it)))
            }
            is PriceScope.SingleArticle -> ScopeValueField(s.articleId) {
                onChange(condition.copy(scope = PriceScope.SingleArticle(it)))
            }
            PriceScope.AllArticles -> Unit
        }

        OutlinedTextField(
            value = condition.note,
            onValueChange = { onChange(condition.copy(note = it)) },
            label = { Text(stringResource(Res.string.price_condition_note)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
    }
}

@Composable
private fun ScopeValueField(value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(Res.string.price_condition_scope)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        isError = value.isBlank()
    )
}

@Composable
private fun Picker(
    label: String,
    value: String,
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    items: @Composable () -> Unit,
) {
    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            enabled = false,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
            modifier = Modifier.fillMaxWidth().clickable { onOpenChange(true) },
            singleLine = true
        )
        DropdownMenu(expanded = open, onDismissRequest = { onOpenChange(false) }) { items() }
    }
}

/** Drop conditions the seller left incomplete, so nothing silently applies to nothing. */
fun List<PriceCondition>.normalizedForSave(): List<PriceCondition> = filter { condition ->
    val amount = when (val e = condition.effect) {
        is PriceEffect.Factor -> e.value
        is PriceEffect.Surcharge -> e.amount
        is PriceEffect.Discount -> e.amount
    }
    val scopeNamed = when (val s = condition.scope) {
        PriceScope.AllArticles -> true
        is PriceScope.Category -> s.name.isNotBlank()
        is PriceScope.SingleArticle -> s.articleId.isNotBlank()
    }
    amount > 0.0 && scopeNamed
}.map { it.copy(note = it.note.trim()) }
