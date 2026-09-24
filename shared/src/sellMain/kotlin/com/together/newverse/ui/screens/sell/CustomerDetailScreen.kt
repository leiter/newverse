package com.together.newverse.ui.screens.sell

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.together.newverse.domain.model.AccessStatus
import com.together.newverse.ui.state.core.AsyncState
import com.together.newverse.util.OrderDateUtils
import com.together.newverse.util.formatPrice
import com.together.newverse.util.formatString
import kotlin.time.Instant
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Note: Does not have its own Scaffold - relies on AppScaffold for top bar/back button.
 */
@Composable
fun CustomerDetailScreen(
    buyerId: String,
    onViewOrders: () -> Unit,
    viewModel: CustomerDetailViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(buyerId) {
        viewModel.load(buyerId)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (val current = state) {
            is AsyncState.Loading, AsyncState.Initial -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            is AsyncState.Error -> {
                Text(
                    text = current.message,
                    modifier = Modifier.align(Alignment.Center).padding(16.dp),
                    color = MaterialTheme.colorScheme.error
                )
            }
            is AsyncState.Success -> {
                CustomerDetailContent(
                    data = current.data,
                    onViewOrders = onViewOrders
                )
            }
        }
    }
}

@Composable
private fun CustomerDetailContent(
    data: CustomerDetailData,
    onViewOrders: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = data.displayName.ifBlank { data.buyerId },
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() }
        )

        val statusLabel = when (data.status) {
            AccessStatus.APPROVED -> stringResource(Res.string.buyer_status_approved)
            AccessStatus.BLOCKED -> stringResource(Res.string.buyer_status_blocked)
            AccessStatus.PENDING -> stringResource(Res.string.buyer_status_pending)
            AccessStatus.NONE -> stringResource(Res.string.buyer_status_none)
        }
        val statusColor = when (data.status) {
            AccessStatus.APPROVED -> MaterialTheme.colorScheme.primary
            AccessStatus.BLOCKED -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        AssistChip(
            onClick = {},
            enabled = false,
            label = { Text(statusLabel) },
            colors = AssistChipDefaults.assistChipColors(disabledLabelColor = statusColor)
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(Res.string.customer_detail_contact_section),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() }
                )
                if (data.emailAddress.isBlank() && data.telephoneNumber.isBlank()) {
                    Text(
                        text = stringResource(Res.string.customer_detail_no_contact),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    if (data.emailAddress.isNotBlank()) {
                        Text(text = data.emailAddress, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (data.telephoneNumber.isNotBlank()) {
                        Text(text = data.telephoneNumber, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(Res.string.customer_detail_orders_section),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() }
                )
                if (data.orders.isEmpty()) {
                    Text(
                        text = stringResource(Res.string.customer_detail_no_orders),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    StatRow(
                        label = formatString(stringResource(Res.string.customer_detail_orders_count), data.orderCount),
                        value = ""
                    )
                    StatRow(
                        label = stringResource(Res.string.customer_detail_total_spent),
                        value = data.totalSpent.formatPrice()
                    )
                    data.lastOrderDate?.let { lastOrderDate ->
                        StatRow(
                            label = stringResource(Res.string.customer_detail_last_order),
                            value = OrderDateUtils.formatDisplayDate(Instant.fromEpochMilliseconds(lastOrderDate))
                        )
                    }
                    OutlinedButton(
                        onClick = onViewOrders,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(Res.string.customer_detail_view_orders))
                    }
                }
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        if (value.isNotEmpty()) {
            Text(text = value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
