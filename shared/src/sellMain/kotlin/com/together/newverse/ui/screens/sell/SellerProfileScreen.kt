package com.together.newverse.ui.screens.sell

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.together.newverse.domain.model.Market
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Composable
fun SellerProfileScreen(
    uiState: SellerProfileUiState,
    onNotificationSettingsClick: () -> Unit = {},
    onLogout: () -> Unit = {},
    onShowPaymentInfo: () -> Unit = {},
    onHidePaymentInfo: () -> Unit = {},
    onShowMarketDialog: (Market?) -> Unit = {},
    onHideMarketDialog: () -> Unit = {},
    onSaveMarket: (Market) -> Unit = {},
    onDeleteMarket: (String) -> Unit = {}
) {
    val profile = uiState.profile

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(Res.string.seller_profile_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )

            // Profile info card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = profile?.displayName ?: stringResource(Res.string.seller_profile_placeholder_name),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    if (profile?.telephoneNumber?.isNotEmpty() == true) {
                        Text(
                            text = profile.telephoneNumber,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    if (profile?.city?.isNotEmpty() == true) {
                        Text(
                            text = "${profile.street} ${profile.houseNumber}, ${profile.zipCode} ${profile.city}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            Text(
                text = stringResource(Res.string.seller_profile_stats_title),
                style = MaterialTheme.typography.titleMedium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatCard(stringResource(Res.string.seller_profile_stat_products), uiState.productCount.toString(), Modifier.weight(1f))
                StatCard(stringResource(Res.string.seller_profile_stat_orders), uiState.orderCount.toString(), Modifier.weight(1f))
            }

            Text(
                text = stringResource(Res.string.seller_profile_settings_title),
                style = MaterialTheme.typography.titleMedium
            )

            // Edit Profile - Markets Section
            OutlinedCard(
                onClick = { onShowMarketDialog(null) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(Res.string.seller_profile_markets),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(Res.string.seller_profile_add_market),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (profile?.markets.isNullOrEmpty()) {
                        Text(
                            text = stringResource(Res.string.seller_profile_no_markets),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        profile?.markets?.forEach { market ->
                            MarketListItem(
                                market = market,
                                onEdit = { onShowMarketDialog(market) },
                                onDelete = { onDeleteMarket(market.id) }
                            )
                        }
                    }
                }
            }

            // Payment Settings - Cash Only Info
            OutlinedCard(
                onClick = onShowPaymentInfo,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(Res.string.seller_profile_payment),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Notification Settings
            OutlinedCard(
                onClick = onNotificationSettingsClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(Res.string.nav_notification_settings),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            OutlinedButton(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(Res.string.button_sign_out))
            }
        }

        // Loading overlay
        if (uiState.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }

    // Payment Info Dialog
    if (uiState.showPaymentInfo) {
        AlertDialog(
            onDismissRequest = onHidePaymentInfo,
            title = { Text(stringResource(Res.string.seller_profile_payment)) },
            text = { Text(stringResource(Res.string.seller_profile_payment_cash_only)) },
            confirmButton = {
                TextButton(onClick = onHidePaymentInfo) {
                    Text(stringResource(Res.string.button_ok))
                }
            }
        )
    }

    // Market Edit Dialog
    if (uiState.showMarketDialog) {
        MarketEditDialog(
            market = uiState.editingMarket,
            onDismiss = onHideMarketDialog,
            onSave = onSaveMarket
        )
    }
}

@Composable
private fun MarketListItem(
    market: Market,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = market.name,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = "${market.dayOfWeek}, ${market.begin} - ${market.end}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${market.street} ${market.houseNumber}, ${market.zipCode} ${market.city}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row {
                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = stringResource(Res.string.market_edit),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(Res.string.market_delete),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalUuidApi::class)
@Composable
private fun MarketEditDialog(
    market: Market?,
    onDismiss: () -> Unit,
    onSave: (Market) -> Unit
) {
    var name by remember { mutableStateOf(market?.name ?: "") }
    var street by remember { mutableStateOf(market?.street ?: "") }
    var houseNumber by remember { mutableStateOf(market?.houseNumber ?: "") }
    var zipCode by remember { mutableStateOf(market?.zipCode ?: "") }
    var city by remember { mutableStateOf(market?.city ?: "") }
    var dayOfWeek by remember { mutableStateOf(market?.dayOfWeek ?: "") }
    var dayIndex by remember { mutableIntStateOf(market?.dayIndex ?: -1) }
    var begin by remember { mutableStateOf(market?.begin ?: "") }
    var end by remember { mutableStateOf(market?.end ?: "") }
    var showDayPicker by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val days = listOf(
        stringResource(Res.string.day_monday) to 0,
        stringResource(Res.string.day_tuesday) to 1,
        stringResource(Res.string.day_wednesday) to 2,
        stringResource(Res.string.day_thursday) to 3,
        stringResource(Res.string.day_friday) to 4,
        stringResource(Res.string.day_saturday) to 5,
        stringResource(Res.string.day_sunday) to 6
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (market == null) stringResource(Res.string.seller_profile_add_market)
                else stringResource(Res.string.market_edit)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(Res.string.market_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = street,
                    onValueChange = { street = it },
                    label = { Text(stringResource(Res.string.market_street)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = houseNumber,
                        onValueChange = { houseNumber = it },
                        label = { Text(stringResource(Res.string.market_house_number)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = zipCode,
                        onValueChange = { zipCode = it },
                        label = { Text(stringResource(Res.string.market_zip_code)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }

                OutlinedTextField(
                    value = city,
                    onValueChange = { city = it },
                    label = { Text(stringResource(Res.string.market_city)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Day picker
                OutlinedTextField(
                    value = dayOfWeek,
                    onValueChange = { },
                    label = { Text(stringResource(Res.string.market_day)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDayPicker = true },
                    readOnly = true,
                    enabled = false
                )

                // Day selection chips
                if (showDayPicker) {
                    Column {
                        days.forEach { (day, index) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        dayOfWeek = day
                                        dayIndex = index
                                        showDayPicker = false
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = dayIndex == index,
                                    onClick = {
                                        dayOfWeek = day
                                        dayIndex = index
                                        showDayPicker = false
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(day)
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = begin,
                        onValueChange = { begin = it },
                        label = { Text(stringResource(Res.string.market_begin)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("09:00") }
                    )
                    OutlinedTextField(
                        value = end,
                        onValueChange = { end = it },
                        label = { Text(stringResource(Res.string.market_end)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("14:00") }
                    )
                }

                validationError?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // Validation
                    when {
                        name.isBlank() || street.isBlank() || houseNumber.isBlank() ||
                        zipCode.isBlank() || city.isBlank() || dayOfWeek.isBlank() ||
                        begin.isBlank() || end.isBlank() -> {
                            validationError = "Alle Felder müssen ausgefüllt werden"
                        }
                        begin >= end -> {
                            validationError = "Der Beginn muss vor dem Ende liegen"
                        }
                        else -> {
                            val newMarket = Market(
                                id = market?.id ?: Uuid.random().toString(),
                                name = name.trim(),
                                street = street.trim(),
                                houseNumber = houseNumber.trim(),
                                city = city.trim(),
                                zipCode = zipCode.trim(),
                                dayOfWeek = dayOfWeek,
                                begin = begin.trim(),
                                end = end.trim(),
                                dayIndex = dayIndex
                            )
                            onSave(newMarket)
                            onDismiss()
                        }
                    }
                }
            ) {
                Text(stringResource(Res.string.market_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.button_cancel))
            }
        }
    )
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

// Backwards compatible version without ViewModel
@Composable
fun SellerProfileScreen(
    onNotificationSettingsClick: () -> Unit = {},
    onLogout: () -> Unit = {}
) {
    SellerProfileScreen(
        uiState = SellerProfileUiState(),
        onNotificationSettingsClick = onNotificationSettingsClick,
        onLogout = onLogout
    )
}
