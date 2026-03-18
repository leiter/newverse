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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.together.newverse.domain.model.AccessRequest
import com.together.newverse.domain.model.Invitation
import com.together.newverse.domain.model.Market
import com.together.newverse.domain.model.SellerProfile
import com.together.newverse.ui.components.QrCodeImage
import com.together.newverse.ui.state.core.AsyncState
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Composable
fun SellerProfileScreen(
    profileState: AsyncState<SellerProfile>,
    statsState: ProfileStats,
    dialogState: ProfileDialogState,
    customerState: CustomerManagementState = CustomerManagementState(),
    invitationState: InvitationManagementState = InvitationManagementState(),
    isSaving: Boolean = false,
    onNotificationSettingsClick: () -> Unit = {},
    onLogout: () -> Unit = {},
    onShowPaymentInfo: () -> Unit = {},
    onHidePaymentInfo: () -> Unit = {},
    onShowMarketDialog: (Market?) -> Unit = {},
    onHideMarketDialog: () -> Unit = {},
    onSaveMarket: (Market) -> Unit = {},
    onDeleteMarket: (String) -> Unit = {},
    onBlockCustomer: (String) -> Unit = {},
    onUnblockCustomer: (String) -> Unit = {},
    onGenerateInvitation: (Int) -> Unit = {},
    onSendInvitationToBuyer: (String) -> Unit = {},
    onRevokeInvitation: (String) -> Unit = {},
    accessRequests: List<AccessRequest> = emptyList(),
    generatedBuyerLink: String? = null,
    onGenerateBuyerLink: () -> Unit = {},
    onApproveRequest: (String) -> Unit = {},
    onBlockBuyer: (String) -> Unit = {},
    onBlockApprovedBuyer: (String) -> Unit = {},
    onUnblockApprovedBuyer: (String) -> Unit = {},
    onClearGeneratedLink: () -> Unit = {},
    onRetry: () -> Unit = {}
) {
    // Extract profile from state
    val profile = (profileState as? AsyncState.Success)?.data

    Box(modifier = Modifier.fillMaxSize()) {
        when (profileState) {
            is AsyncState.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            is AsyncState.Error -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = profileState.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onRetry) {
                        Text(stringResource(Res.string.button_retry))
                    }
                }
            }
            is AsyncState.Success, is AsyncState.Initial -> {
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
                        StatCard(stringResource(Res.string.seller_profile_stat_products), statsState.productCount.toString(), Modifier.weight(1f))
                        StatCard(stringResource(Res.string.seller_profile_stat_orders), statsState.orderCount.toString(), Modifier.weight(1f))
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

                    // Invitation-based QR Code Card
                    if (profile != null) {
                        InvitationCard(
                            invitationState = invitationState,
                            onGenerateInvitation = onGenerateInvitation,
                            onRevokeInvitation = onRevokeInvitation,
                            onSendInvitationToBuyer = onSendInvitationToBuyer
                        )
                    }

                    // Connected Customers Card
                    if (customerState.allClientIds.isNotEmpty()) {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = stringResource(Res.string.customer_management_title),
                                    style = MaterialTheme.typography.titleMedium
                                )

                                customerState.knownClientIds.forEach { buyerId ->
                                    CustomerListItem(
                                        buyerId = buyerId,
                                        isBlocked = false,
                                        onBlock = { onBlockCustomer(buyerId) },
                                        onUnblock = {}
                                    )
                                }

                                customerState.blockedClientIds.forEach { buyerId ->
                                    CustomerListItem(
                                        buyerId = buyerId,
                                        isBlocked = true,
                                        onBlock = {},
                                        onUnblock = { onUnblockApprovedBuyer(buyerId) }
                                    )
                                }
                            }
                        }
                    }

                    // Generate Buyer Link Card
                    GenerateBuyerLinkCard(
                        generatedLink = generatedBuyerLink,
                        onGenerateLink = onGenerateBuyerLink,
                        onClearLink = onClearGeneratedLink
                    )

                    // Access Requests Card
                    AccessRequestsCard(
                        requests = accessRequests,
                        onApprove = onApproveRequest,
                        onBlock = onBlockBuyer
                    )

                    // Approved Buyers Card
                    ApprovedBuyersCard(
                        approvedBuyerIds = customerState.approvedBuyerIds,
                        onBlock = onBlockApprovedBuyer
                    )

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
            }
        }

        // Saving overlay
        if (isSaving) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }

    // Payment Info Dialog
    if (dialogState.showPaymentInfo) {
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
    if (dialogState.showMarketDialog) {
        MarketEditDialog(
            market = dialogState.editingMarket,
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

@Composable
private fun CustomerListItem(
    buyerId: String,
    isBlocked: Boolean,
    onBlock: () -> Unit,
    onUnblock: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isBlocked) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
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
                    text = buyerId,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1
                )
                Text(
                    text = if (isBlocked) {
                        stringResource(Res.string.customer_management_blocked)
                    } else {
                        stringResource(Res.string.customer_management_active)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isBlocked) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            }
            if (isBlocked) {
                TextButton(onClick = onUnblock) {
                    Text(stringResource(Res.string.customer_management_unblock))
                }
            } else {
                TextButton(onClick = onBlock) {
                    Text(
                        text = stringResource(Res.string.customer_management_block),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun InvitationCard(
    invitationState: InvitationManagementState,
    onGenerateInvitation: (Int) -> Unit,
    onRevokeInvitation: (String) -> Unit,
    onSendInvitationToBuyer: (String) -> Unit
) {
    var buyerIdInput by remember { mutableStateOf("") }
    var selectedExpiryMinutes by remember { mutableIntStateOf(1440) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(Res.string.seller_connection_share_qr),
                style = MaterialTheme.typography.titleMedium
            )

            // Expiry selection
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(Res.string.invitation_expiry_label),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
                listOf(
                    60 to Res.string.invitation_expiry_1h,
                    1440 to Res.string.invitation_expiry_24h,
                    2880 to Res.string.invitation_expiry_48h
                ).forEach { (minutes, labelRes) ->
                    FilterChip(
                        selected = selectedExpiryMinutes == minutes,
                        onClick = { selectedExpiryMinutes = minutes },
                        label = { Text(stringResource(labelRes)) }
                    )
                }
            }

            // Generate button or current QR
            if (invitationState.currentInvitation != null && invitationState.deepLink != null) {
                // Show QR code with current invitation
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QrCodeImage(
                        content = invitationState.deepLink,
                        sizeDp = 200
                    )
                    Text(
                        text = stringResource(Res.string.seller_connection_qr_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Expiry info
                    val expiresAt = invitationState.currentInvitation.expiresAt
                    val remainingMinutes = ((expiresAt - kotlin.time.Clock.System.now().toEpochMilliseconds()) / 60000).coerceAtLeast(0)
                    val expiryText = when {
                        remainingMinutes > 60 -> "${remainingMinutes / 60}h ${remainingMinutes % 60}min"
                        remainingMinutes > 0 -> "${remainingMinutes}min"
                        else -> stringResource(Res.string.invitation_expired)
                    }
                    Text(
                        text = stringResource(Res.string.invitation_expires, expiryText),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (remainingMinutes <= 0) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Revoke + Regenerate buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onRevokeInvitation(invitationState.currentInvitation.id) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(Res.string.invitation_revoke))
                        }
                        Button(
                            onClick = { onGenerateInvitation(selectedExpiryMinutes) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(Res.string.invitation_generate))
                        }
                    }
                }
            } else {
                // Generate button
                Button(
                    onClick = { onGenerateInvitation(selectedExpiryMinutes) },
                    enabled = !invitationState.isGenerating,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (invitationState.isGenerating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = if (invitationState.isGenerating) {
                            stringResource(Res.string.invitation_generating)
                        } else {
                            stringResource(Res.string.invitation_generate)
                        }
                    )
                }
            }

            HorizontalDivider()

            // Send invitation to specific buyer
            Text(
                text = stringResource(Res.string.invitation_send_to_buyer),
                style = MaterialTheme.typography.titleSmall
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = buyerIdInput,
                    onValueChange = { buyerIdInput = it },
                    label = { Text(stringResource(Res.string.invitation_buyer_id_hint)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        onSendInvitationToBuyer(buyerIdInput.trim())
                        buyerIdInput = ""
                    },
                    enabled = buyerIdInput.isNotBlank() && !invitationState.isSendingToBuyer
                ) {
                    Text(stringResource(Res.string.invitation_send))
                }
            }

            // Show last sent confirmation
            invitationState.lastSentInvitation?.let { sent ->
                Text(
                    text = "${stringResource(Res.string.invitation_sent)}: ${sent.buyerId}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
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
@Suppress("DEPRECATION")
@Composable
fun SellerProfileScreen(
    onNotificationSettingsClick: () -> Unit = {},
    onLogout: () -> Unit = {}
) {
    SellerProfileScreen(
        profileState = AsyncState.Initial,
        statsState = ProfileStats(),
        dialogState = ProfileDialogState(),
        onNotificationSettingsClick = onNotificationSettingsClick,
        onLogout = onLogout
    )
}

@Composable
private fun GenerateBuyerLinkCard(
    generatedLink: String?,
    onGenerateLink: () -> Unit,
    onClearLink: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    // Auto-copy to clipboard whenever a new link is generated
    LaunchedEffect(generatedLink) {
        if (generatedLink != null) {
            clipboardManager.setText(AnnotatedString(generatedLink))
            copied = true
        } else {
            copied = false
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Generate Buyer Link",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Share this link with a buyer to give them access to your store.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onGenerateLink,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Generate new link")
            }
            if (generatedLink != null) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QrCodeImage(
                        content = generatedLink,
                        sizeDp = 200
                    )
                    Text(
                        text = generatedLink,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(generatedLink))
                                copied = true
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (copied) "Copied!" else "Copy")
                        }
                        OutlinedButton(
                            onClick = { onClearLink() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Clear")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun formatRelativeTime(epochMillis: Long): String {
    val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
    val diffMinutes = ((now - epochMillis) / 60_000).toInt()
    val diffHours = diffMinutes / 60
    val diffDays = diffHours / 24
    return when {
        diffMinutes < 1 -> stringResource(Res.string.time_just_now)
        diffMinutes < 60 -> stringResource(Res.string.time_minutes_ago, diffMinutes)
        diffHours < 24 -> stringResource(Res.string.time_hours_ago, diffHours)
        else -> stringResource(Res.string.time_days_ago, diffDays)
    }
}

@Composable
private fun AccessRequestsCard(
    requests: List<AccessRequest>,
    onApprove: (String) -> Unit,
    onBlock: (String) -> Unit
) {
    var buyerToBlock by remember { mutableStateOf<String?>(null) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(Res.string.access_requests_title, requests.size),
                style = MaterialTheme.typography.titleMedium
            )
            if (requests.isEmpty()) {
                Text(
                    text = stringResource(Res.string.access_requests_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                requests.forEach { request ->
                    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = request.buyerDisplayName.ifEmpty { stringResource(Res.string.access_requests_anonymous) },
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = request.buyerUUID,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (request.requestedAt > 0) {
                                Text(
                                    text = formatRelativeTime(request.requestedAt),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { onApprove(request.buyerUUID) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(stringResource(Res.string.access_requests_approve))
                                }
                                OutlinedButton(
                                    onClick = { buyerToBlock = request.buyerUUID },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Text(stringResource(Res.string.access_requests_block))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Block confirmation dialog
    buyerToBlock?.let { buyerId ->
        AlertDialog(
            onDismissRequest = { buyerToBlock = null },
            title = { Text(stringResource(Res.string.access_requests_block_title)) },
            text = { Text(stringResource(Res.string.access_requests_block_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onBlock(buyerId)
                        buyerToBlock = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(Res.string.access_requests_block))
                }
            },
            dismissButton = {
                TextButton(onClick = { buyerToBlock = null }) {
                    Text(stringResource(Res.string.button_cancel))
                }
            }
        )
    }
}

@Composable
private fun ApprovedBuyersCard(
    approvedBuyerIds: List<String>,
    onBlock: (String) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Approved Buyers (${approvedBuyerIds.size})",
                style = MaterialTheme.typography.titleMedium
            )
            if (approvedBuyerIds.isEmpty()) {
                Text(
                    text = "No approved buyers yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                approvedBuyerIds.forEach { buyerUUID ->
                    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = buyerUUID.take(16) + "…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1
                                )
                                Text(
                                    text = "Approved",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            TextButton(onClick = { onBlock(buyerUUID) }) {
                                Text(
                                    text = "Block",
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// Legacy overload for backward compatibility
@Suppress("DEPRECATION")
@Deprecated("Use the version with separate state flows")
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
    // Convert legacy state to new state format
    val profileState: AsyncState<SellerProfile> = when {
        uiState.isLoading -> AsyncState.Loading
        uiState.error != null -> AsyncState.Error(uiState.error)
        uiState.profile != null -> AsyncState.Success(uiState.profile)
        else -> AsyncState.Initial
    }

    SellerProfileScreen(
        profileState = profileState,
        statsState = ProfileStats(
            productCount = uiState.productCount,
            orderCount = uiState.orderCount
        ),
        dialogState = ProfileDialogState(
            showMarketDialog = uiState.showMarketDialog,
            editingMarket = uiState.editingMarket,
            showPaymentInfo = uiState.showPaymentInfo
        ),
        isSaving = false,
        onNotificationSettingsClick = onNotificationSettingsClick,
        onLogout = onLogout,
        onShowPaymentInfo = onShowPaymentInfo,
        onHidePaymentInfo = onHidePaymentInfo,
        onShowMarketDialog = onShowMarketDialog,
        onHideMarketDialog = onHideMarketDialog,
        onSaveMarket = onSaveMarket,
        onDeleteMarket = onDeleteMarket
    )
}
