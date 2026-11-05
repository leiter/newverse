package com.together.newverse.ui.screens.sell

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import newverse.shared.generated.resources.fulfilment_address_missing
import newverse.shared.generated.resources.fulfilment_delivery
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.together.newverse.domain.model.Order
import com.together.newverse.domain.model.OrderStatus
import com.together.newverse.util.formatString
import com.together.newverse.ui.adaptive.AdaptiveDefaults
import com.together.newverse.ui.adaptive.LocalWindowWidthClass
import com.together.newverse.ui.adaptive.WindowWidthClass
import com.together.newverse.ui.state.core.AsyncState
import com.together.newverse.ui.state.core.AsyncStateContent
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Orders screen for sellers to manage incoming orders.
 * Uses AsyncStateContent for clean loading/error/success handling.
 */
@Composable
fun OrdersScreen(
    viewModel: OrdersViewModel = koinViewModel(),
    onOrderClick: (String) -> Unit = {}
) {
    val ordersState by viewModel.ordersState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val clearButtonLabel = stringResource(Res.string.demo_orders_clear_button)
    val clearResultLabel = stringResource(Res.string.demo_orders_clear_result)
    val clearNoneLabel = stringResource(Res.string.demo_orders_clear_none)

    // On Expanded windows (tablet landscape) show list + detail side by side
    val useTwoPane = LocalWindowWidthClass.current == WindowWidthClass.Expanded
    var selectedOrderId by rememberSaveable { mutableStateOf<String?>(null) }

    // Orders whose pickup day has gone by are the seller's archive, not their work
    // list: hidden until asked for, so the screen opens on what still has to be
    // packed. The choice is per visit, not persisted.
    var showOutdated by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            AsyncStateContent(
                state = ordersState,
                onRetry = { viewModel.refresh() },
                loadingContent = {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                },
                errorContent = { message, retryable ->
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                            )
                            if (retryable) {
                                Button(onClick = { viewModel.refresh() }) {
                                    Text(stringResource(Res.string.retry))
                                }
                            }
                        }
                    }
                }
            ) { orders ->
                val onClearDemoOrders: () -> Unit = {
                    viewModel.clearOldDemoOrders(
                        onSuccess = { count ->
                            scope.launch {
                                val msg = if (count > 0) {
                                    clearResultLabel.replace("%1\$d", count.toString())
                                } else {
                                    clearNoneLabel
                                }
                                snackbarHostState.showSnackbar(msg)
                            }
                        },
                        onError = { error ->
                            scope.launch { snackbarHostState.showSnackbar(error) }
                        }
                    )
                }

                if (useTwoPane) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.weight(0.42f).fillMaxHeight()) {
                            OrdersContent(
                                orders = orders,
                                showOutdated = showOutdated,
                                onToggleShowOutdated = { showOutdated = !showOutdated },
                                onOrderClick = { selectedOrderId = it },
                                onClearDemoOrders = onClearDemoOrders
                            )
                        }
                        VerticalDivider()
                        Box(modifier = Modifier.weight(0.58f).fillMaxHeight()) {
                            val selected = selectedOrderId?.takeIf { id -> orders.any { it.id == id } }
                            if (selected != null) {
                                OrderDetailScreen(
                                    orderId = selected,
                                    onNavigateBack = { selectedOrderId = null },
                                    viewModel = viewModel
                                )
                            } else {
                                OrderDetailPlaceholder()
                            }
                        }
                    }
                } else {
                    OrdersContent(
                        orders = orders,
                        showOutdated = showOutdated,
                        onToggleShowOutdated = { showOutdated = !showOutdated },
                        onOrderClick = onOrderClick,
                        onClearDemoOrders = onClearDemoOrders
                    )
                }
            }
        }
    }
}

@Composable
private fun OrderDetailPlaceholder() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(Res.string.orders_select_prompt),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * An order counts as outdated once its pickup *day* is over, not once its pickup
 * time has passed: an order picked up at 9:00 is still today's work at 14:00, and
 * the seller is standing at the stall with the day's list open.
 */
private fun startOfTodayMillis(): Long {
    val timeZone = TimeZone.currentSystemDefault()
    return kotlin.time.Clock.System.now()
        .toLocalDateTime(timeZone)
        .date
        .atStartOfDayIn(timeZone)
        .toEpochMilliseconds()
}

@Composable
private fun OrdersContent(
    orders: List<Order>,
    showOutdated: Boolean,
    onToggleShowOutdated: () -> Unit,
    onOrderClick: (String) -> Unit,
    onClearDemoOrders: () -> Unit = {}
) {
    val startOfToday = remember { startOfTodayMillis() }
    val outdatedCount = remember(orders, startOfToday) {
        orders.count { it.pickUpDate < startOfToday }
    }
    val visibleOrders = remember(orders, showOutdated, startOfToday) {
        if (showOutdated) orders else orders.filter { it.pickUpDate >= startOfToday }
    }
    val showAllLabel = formatString(
        stringResource(Res.string.orders_show_outdated),
        outdatedCount
    )
    val hideLabel = stringResource(Res.string.orders_hide_outdated)

    Column(modifier = Modifier.fillMaxSize()) {
        if (outdatedCount > 0) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onToggleShowOutdated) {
                    Text(text = if (showOutdated) hideLabel else showAllLabel)
                }
            }
        }

        if (visibleOrders.isEmpty()) {
            // Empty state. "No orders at all" and "nothing current, but there is an
            // archive" are different things to tell the seller.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(
                        if (outdatedCount > 0 && !showOutdated) {
                            Res.string.orders_no_current_message
                        } else {
                            Res.string.no_orders_message
                        }
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { heading() }
                )
            }
        } else {
            // Orders list
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = AdaptiveDefaults.ListItemMinWidth),
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(visibleOrders) { order ->
                    SellerOrderCard(
                        order = order,
                        onClick = { onOrderClick(order.id) }
                    )
                }
            }
        }

        // Demo orders cleanup button
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = onClearDemoOrders,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = stringResource(Res.string.demo_orders_clear_button))
        }
    }
}

/**
 * Seller order card displaying detailed order information
 * Based on universe project's item_next_order.xml layout
 */
@Composable
private fun SellerOrderCard(
    order: Order,
    onClick: () -> Unit = {}
) {
    // Check if order is cancelled
    val isCancelled = order.status == OrderStatus.CANCELLED

    // Determine if order is "open" for seller's view
    // Based on universe project: orders are "open" if pickup date hasn't passed yet
    // Simple rule: if pickup date is in the future, the order is still open/active
    val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
    val isOpen = order.pickUpDate > now && !isCancelled

    // Use different visual styling based on order state
    val cardColors = when {
        isCancelled -> CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
        isOpen -> CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
        else -> CardDefaults.cardColors()
    }

    val borderStroke = when {
        isCancelled -> BorderStroke(2.dp, MaterialTheme.colorScheme.error)
        isOpen -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        else -> null
    }

    val cancelledLabel = stringResource(Res.string.order_status_cancelled)
    val productCountLabel = formatString(
        stringResource(Res.string.format_product_count),
        order.articles.size
    )
    val openLabel = stringResource(Res.string.a11y_open_order_details)
    val deliveryLabel = stringResource(Res.string.fulfilment_delivery)
    val pickupTimeLine = order.statedPickupTime?.let { time ->
        formatString(stringResource(Res.string.order_pickup_time_stated), time)
    }
    // Who is coming for it, when that is not the buyer whose name titles the card.
    // The seller handing over a box needs this before the name on their list is the
    // one thing that does not match the person in front of them.
    val collectorLine = order.statedPickupName?.let { name ->
        formatString(
            stringResource(
                if (order.isDelivery) Res.string.order_delivery_name_stated
                else Res.string.order_pickup_name_stated
            ),
            name
        )
    }
    val addressMissingLabel = stringResource(Res.string.fulfilment_address_missing)
    // One stop per card, read in a deliberate order, instead of a stop each for
    // the name, count, pickup date, message and every product row.
    val cardDescription = buildString {
        append(order.buyerProfile.displayName)
        if (isCancelled) { append(", "); append(cancelledLabel) }
        if (order.isDelivery) {
            append(", "); append(deliveryLabel)
            append(", ")
            append(order.deliveryAddress.oneLine().ifBlank { addressMissingLabel })
        }
        append(", "); append(productCountLabel)
        collectorLine?.let { append(", "); append(it) }
        order.statedPickupPhone?.let { append(", "); append(it) }
        append(", "); append(order.getFormattedPickupDateAndTime())
        pickupTimeLine?.let { append(", "); append(it) }
        if (order.message.isNotEmpty()) { append(", "); append(order.message) }
        order.articles.forEach { product ->
            append(", ")
            append(product.getFormattedAmount())
            append(" ")
            append(product.productName)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = cardDescription
                onClick(label = openLabel, action = null)
            },
        onClick = onClick,
        colors = cardColors,
        border = borderStroke
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Customer name and product count
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = order.buyerProfile.displayName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text = productCountLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            // A delivery has to be told apart at a glance: the seller is sorting a
            // day's orders into what stays on the stall and what goes on the van, and
            // the address only lives on the detail screen.
            if (order.isDelivery) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = stringResource(Res.string.fulfilment_delivery),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.tertiaryContainer)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                    Text(
                        text = order.deliveryAddress.oneLine()
                            .ifBlank { stringResource(Res.string.fulfilment_address_missing) },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (order.isDeliveryWithoutAddress) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Pickup date and time
            Text(
                text = order.getFormattedPickupDateAndTime(),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )

            // When the buyer said they would come, if they said. A whole day's
            // pickups sorted by hour is the thing this tells the seller that the
            // pickup date above cannot — and until now the app asked every buyer for
            // it and then showed it to nobody.
            order.statedPickupTime?.let { time ->
                Text(
                    text = formatString(
                        stringResource(Res.string.order_pickup_time_stated),
                        time
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Emphasised rather than greyed: this is the line that contradicts the
            // name at the top of the card, and it is read while handing a box over.
            collectorLine?.let { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Message (if exists)
            if (order.message.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = order.message,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            // Ordered products list
            if (order.articles.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    order.articles.forEach { product ->
                        OrderedProductItem(product = product)
                    }
                }
            }

            // Status indicator at bottom
            Spacer(modifier = Modifier.height(4.dp))

            // Show "Storniert" label for cancelled orders
            if (isCancelled) {
                Text(
                    text = cancelledLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Divider color based on order state
            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(),
                thickness = 4.dp,
                color = if (isCancelled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * Individual ordered product item display
 * Based on universe project's item_ordered_product.xml layout
 */
@Composable
private fun OrderedProductItem(
    product: com.together.newverse.domain.model.OrderedProduct
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Amount
        Text(
            text = product.getFormattedAmount(),
            style = MaterialTheme.typography.bodyLarge
        )

        // Product name
        Text(
            text = product.productName,
            style = MaterialTheme.typography.titleMedium
        )
    }
}
