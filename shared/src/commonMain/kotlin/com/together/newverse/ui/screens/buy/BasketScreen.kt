package com.together.newverse.ui.screens.buy

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.together.newverse.util.formatPrice
import org.koin.compose.viewmodel.koinViewModel
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/**
 * Basket Screen - Stateful composable with ViewModel
 */
@Composable
fun BasketScreen(
    viewModel: BasketViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()

    BasketContent(
        state = state,
        onAction = viewModel::onAction
    )
}

/**
 * Basket Content - Stateless composable
 *
 * @param state The screen state
 * @param onAction Callback for user actions
 */
@Composable
fun BasketContent(
    state: BasketScreenState,
    onAction: (BasketAction) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(Res.string.basket_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (state.items.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(Res.string.basket_empty_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(Res.string.basket_empty_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.items) { item ->
                    BasketItemCard(
                        productName = item.productName,
                        price = item.price,
                        unit = item.unit,
                        quantity = item.amountCount,
                        onRemove = { onAction(BasketAction.RemoveItem(item.productId)) },
                        onQuantityChange = { newQty ->
                            onAction(BasketAction.UpdateQuantity(item.productId, newQty))
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        HorizontalDivider()

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(Res.string.label_total),
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = stringResource(Res.string.format_price_euro, state.total.formatPrice()),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { onAction(BasketAction.Checkout) },
            modifier = Modifier.fillMaxWidth(),
            enabled = state.items.isNotEmpty() && !state.isCheckingOut
        ) {
            Text(if (state.isCheckingOut) stringResource(Res.string.basket_checkout_processing) else stringResource(Res.string.basket_checkout_proceed))
        }
    }
}

@Composable
private fun BasketItemCard(
    productName: String,
    price: Double,
    unit: String,
    quantity: Double,
    onRemove: () -> Unit = {},
    onQuantityChange: (Double) -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = productName,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "${price.formatPrice()}€/$unit",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "x ${quantity.formatPrice()}",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "${(price * quantity).formatPrice()}€",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onRemove) {
                    Text(stringResource(Res.string.button_remove))
                }
            }
        }
    }
}


