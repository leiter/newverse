package com.together.newverse.ui.screens.sell

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun OrdersScreen() {
    val dummyOrders = listOf(
        OrderItem("Order #1234", "John Doe", "Pending", "$25.50"),
        OrderItem("Order #1235", "Jane Smith", "Processing", "$42.00"),
        OrderItem("Order #1236", "Bob Wilson", "Completed", "$18.75")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Manage Orders",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(dummyOrders) { order ->
                OrderCard(order)
            }
        }
    }
}

data class OrderItem(
    val orderId: String,
    val customerName: String,
    val status: String,
    val total: String
)

@Composable
private fun OrderCard(order: OrderItem) {
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
                    text = order.orderId,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = order.total,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Text(
                text = order.customerName,
                style = MaterialTheme.typography.bodyMedium
            )

            AssistChip(
                onClick = { },
                label = { Text(order.status) }
            )
        }
    }
}
