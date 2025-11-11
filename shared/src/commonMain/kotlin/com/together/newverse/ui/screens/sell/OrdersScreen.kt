package com.together.newverse.ui.screens.sell

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.together.newverse.preview.PreviewData
import com.together.newverse.util.formatPrice
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun OrdersScreen() {
    val orders = PreviewData.sampleOrders

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(Res.string.orders_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(orders) { order ->
                OrderCard(
                    orderId = order.id,
                    customerName = order.buyerProfile.displayName,
                    itemCount = order.articles.size,
                    total = order.articles.sumOf { it.price * it.amountCount },
                    message = order.message
                )
            }
        }
    }
}

@Composable
private fun OrderCard(
    orderId: String,
    customerName: String,
    itemCount: Int,
    total: Double,
    message: String
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(Res.string.format_order_id, orderId.take(8)),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = stringResource(Res.string.format_price_euro, total.formatPrice()),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Text(
                text = customerName,
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = stringResource(Res.string.format_item_count, itemCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (message.isNotEmpty()) {
                Text(
                    text = stringResource(Res.string.format_note, message),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

