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

@Composable
fun BasketScreen(
    viewModel: BasketViewModel = koinViewModel()
) {
    val basketItems by viewModel.basketItems.collectAsState()
    val total by viewModel.totalAmount.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Shopping Basket",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (basketItems.isEmpty()) {
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
                        text = "Your basket is empty",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Add items from the Products screen",
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
                items(basketItems) { item ->
                    BasketItemCard(
                        productName = item.productName,
                        price = item.price,
                        unit = item.unit,
                        quantity = item.amountCount,
                        onRemove = { viewModel.removeItem(item.productId) },
                        onQuantityChange = { newQty -> viewModel.updateQuantity(item.productId, newQty) }
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
                text = "Total:",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "${total.formatPrice()}€",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { viewModel.checkout() },
            modifier = Modifier.fillMaxWidth(),
            enabled = basketItems.isNotEmpty()
        ) {
            Text("Proceed to Checkout")
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
                    Text("Remove")
                }
            }
        }
    }
}


