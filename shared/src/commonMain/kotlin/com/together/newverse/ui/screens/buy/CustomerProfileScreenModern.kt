package com.together.newverse.ui.screens.buy

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.together.newverse.ui.theme.BrownAccent
import com.together.newverse.ui.theme.DarkGreen
import com.together.newverse.ui.theme.ErrorRed
import com.together.newverse.ui.theme.FabGreen
import com.together.newverse.ui.theme.Gray100
import com.together.newverse.ui.theme.Gray200
import com.together.newverse.ui.theme.Gray300
import com.together.newverse.ui.theme.Gray400
import com.together.newverse.ui.theme.Gray600
import com.together.newverse.ui.theme.Gray700
import com.together.newverse.ui.theme.Gray800
import com.together.newverse.ui.theme.InfoBlue
import com.together.newverse.ui.theme.LeafGreen
import com.together.newverse.ui.theme.LightCream
import com.together.newverse.ui.theme.Orange
import com.together.newverse.ui.theme.OrganicBeige
import com.together.newverse.ui.theme.SuccessGreen
import com.together.newverse.ui.theme.WarningOrange
import com.together.newverse.ui.theme.White

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerProfileScreenModern(
    onBackClick: () -> Unit = {}
) {
    var displayName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var selectedMarket by remember { mutableStateOf("Ökomarkt im Hansaviertel") }
    var pickupTime by remember { mutableStateOf("15:45") }
    var notificationsEnabled by remember { mutableStateOf(true) }
    var newsletterEnabled by remember { mutableStateOf(false) }
    var isEditing by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    Box(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = LightCream
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Background gradient
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    LeafGreen.copy(alpha = 0.05f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(20.dp)
                        .padding(bottom = if (isEditing) 80.dp else 0.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Profile Header Card
                    ProfileHeaderCard(
                        displayName = displayName.ifEmpty { "Neuer Kunde" },
                        email = email.ifEmpty { "Keine E-Mail" },
                        isVerified = email.isNotEmpty()
                    )

                    // Personal Information Card
                    PersonalInfoCard(
                        displayName = displayName,
                        email = email,
                        phone = phone,
                        isEditing = isEditing,
                        onDisplayNameChange = { displayName = it },
                        onEmailChange = { email = it },
                        onPhoneChange = { phone = it }
                    )

                    // Delivery Preferences Card
                    DeliveryPreferencesCard(
                        selectedMarket = selectedMarket,
                        pickupTime = pickupTime,
                        isEditing = isEditing,
                        onMarketChange = { selectedMarket = it },
                        onTimeChange = { pickupTime = it }
                    )

                    // Notification Settings Card
                    NotificationSettingsCard(
                        notificationsEnabled = notificationsEnabled,
                        newsletterEnabled = newsletterEnabled,
                        isEditing = isEditing,
                        onNotificationToggle = { notificationsEnabled = it },
                        onNewsletterToggle = { newsletterEnabled = it }
                    )

                    // Membership Card
                    MembershipCard()

                    // Quick Actions
                    if (!isEditing) {
                        QuickActionsCard()
                    }
                }
            }
        }

        // Bottom Bar for Save/Cancel when editing
        if (isEditing) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            ) {
                SaveBottomBar(
                    onSave = { showSaveDialog = true },
                    onCancel = { isEditing = false }
                )
            }
        }

        // Save Confirmation Dialog
        if (showSaveDialog) {
            SaveConfirmationDialog(
                onConfirm = {
                    showSaveDialog = false
                    isEditing = false
                    // Save logic here
                },
                onDismiss = { showSaveDialog = false }
            )
        }
    }
}

@Composable
private fun ProfileHeaderCard(
    displayName: String,
    email: String,
    isVerified: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = spring()),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Background pattern
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                FabGreen.copy(alpha = 0.1f),
                                LeafGreen.copy(alpha = 0.1f)
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Profile Picture
                Surface(
                    shape = CircleShape,
                    color = Orange,
                    modifier = Modifier
                        .size(100.dp)
                        .border(4.dp, White, CircleShape)
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = White,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Name and verification badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = DarkGreen
                    )
                    if (isVerified) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Verifiziert",
                            tint = SuccessGreen,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Text(
                    text = email,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Gray600
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Member since badge
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = SuccessGreen.copy(alpha = 0.1f)
                ) {
                    Text(
                        text = "Kunde seit 2023",
                        style = MaterialTheme.typography.labelMedium,
                        color = SuccessGreen,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PersonalInfoCard(
    displayName: String,
    email: String,
    phone: String,
    isEditing: Boolean,
    onDisplayNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            SectionHeader(
                icon = Icons.Default.Person,
                title = "Persönliche Daten",
                iconColor = FabGreen
            )

            Spacer(modifier = Modifier.height(20.dp))

            ModernTextField(
                value = displayName,
                onValueChange = onDisplayNameChange,
                label = "Anzeigename",
                leadingIcon = Icons.Default.Person,
                enabled = isEditing,
                isValid = displayName.isNotEmpty()
            )

            Spacer(modifier = Modifier.height(16.dp))

            ModernTextField(
                value = email,
                onValueChange = onEmailChange,
                label = "E-Mail-Adresse",
                leadingIcon = Icons.Default.Email,
                enabled = isEditing,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                isValid = email.contains("@")
            )

            Spacer(modifier = Modifier.height(16.dp))

            ModernTextField(
                value = phone,
                onValueChange = onPhoneChange,
                label = "Telefonnummer",
                leadingIcon = Icons.Default.Phone,
                enabled = isEditing,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                isValid = phone.length >= 10
            )
        }
    }
}

@Composable
private fun DeliveryPreferencesCard(
    selectedMarket: String,
    pickupTime: String,
    isEditing: Boolean,
    onMarketChange: (String) -> Unit,
    onTimeChange: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = OrganicBeige.copy(alpha = 0.3f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            SectionHeader(
                icon = Icons.Default.LocationOn,
                title = "Abholpräferenzen",
                iconColor = BrownAccent
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Market Selection
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = isEditing) { /* Open market selection */ },
                color = if (isEditing) White else Gray100,
                border = BorderStroke(
                    1.dp,
                    if (isEditing) FabGreen else Color.Transparent
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Outlined.LocationOn,
                            contentDescription = null,
                            tint = FabGreen
                        )
                        Column {
                            Text(
                                text = "Marktplatz",
                                style = MaterialTheme.typography.labelMedium,
                                color = Gray600
                            )
                            Text(
                                text = selectedMarket,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = Gray800
                            )
                        }
                    }
                    if (isEditing) {
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = Gray600
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Pickup Time
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = isEditing) { /* Open time picker */ },
                color = if (isEditing) White else Gray100,
                border = BorderStroke(
                    1.dp,
                    if (isEditing) FabGreen else Color.Transparent
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.DateRange,
                            contentDescription = null,
                            tint = FabGreen
                        )
                        Column {
                            Text(
                                text = "Abholzeit",
                                style = MaterialTheme.typography.labelMedium,
                                color = Gray600
                            )
                            Text(
                                text = "$pickupTime Uhr",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = Gray800
                            )
                        }
                    }
                    if (isEditing) {
                        Icon(
                            Icons.Default.DateRange,
                            contentDescription = null,
                            tint = Gray600
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationSettingsCard(
    notificationsEnabled: Boolean,
    newsletterEnabled: Boolean,
    isEditing: Boolean,
    onNotificationToggle: (Boolean) -> Unit,
    onNewsletterToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            SectionHeader(
                icon = Icons.Default.Notifications,
                title = "Benachrichtigungen",
                iconColor = InfoBlue
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Order Notifications
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.Notifications,
                        contentDescription = null,
                        tint = if (notificationsEnabled) SuccessGreen else Gray400,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "Bestellupdates",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = Gray800
                        )
                        Text(
                            text = "Push-Benachrichtigungen erhalten",
                            style = MaterialTheme.typography.bodySmall,
                            color = Gray600
                        )
                    }
                }
                Switch(
                    checked = notificationsEnabled,
                    onCheckedChange = onNotificationToggle,
                    enabled = isEditing,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = White,
                        checkedTrackColor = SuccessGreen
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Newsletter
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.Email,
                        contentDescription = null,
                        tint = if (newsletterEnabled) SuccessGreen else Gray400,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "Newsletter",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = Gray800
                        )
                        Text(
                            text = "Wöchentliche Angebote",
                            style = MaterialTheme.typography.bodySmall,
                            color = Gray600
                        )
                    }
                }
                Switch(
                    checked = newsletterEnabled,
                    onCheckedChange = onNewsletterToggle,
                    enabled = isEditing,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = White,
                        checkedTrackColor = SuccessGreen
                    )
                )
            }
        }
    }
}

@Composable
private fun MembershipCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = LeafGreen.copy(alpha = 0.1f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = SuccessGreen,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        tint = White,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                    )
                }

                Column {
                    Text(
                        text = "Stammkunde",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = FabGreen
                    )
                    Text(
                        text = "5% Rabatt auf alle Bestellungen",
                        style = MaterialTheme.typography.bodySmall,
                        color = Gray600
                    )
                }
            }

            Icon(
                Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = FabGreen
            )
        }
    }
}

@Composable
private fun QuickActionsCard() {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Schnellaktionen",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = DarkGreen
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ActionButton(
                icon = Icons.Default.List,
                text = "Bestellungen",
                color = FabGreen,
                modifier = Modifier.weight(1f)
            ) { /* Navigate to orders */ }

            ActionButton(
                icon = Icons.Outlined.FavoriteBorder,
                text = "Favoriten",
                color = ErrorRed,
                modifier = Modifier.weight(1f)
            ) { /* Navigate to favorites */ }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ActionButton(
                icon = Icons.Default.AccountBox,
                text = "Zahlung",
                color = InfoBlue,
                modifier = Modifier.weight(1f)
            ) { /* Navigate to payment */ }

            ActionButton(
                icon = Icons.Default.Info,
                text = "Hilfe",
                color = WarningOrange,
                modifier = Modifier.weight(1f)
            ) { /* Navigate to help */ }
        }
    }
}

@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = Gray700
            )
        }
    }
}

@Composable
private fun SaveBottomBar(
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = 8.dp,
        color = White
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Gray600
                )
            ) {
                Text("Abbrechen")
            }

            Button(
                onClick = onSave,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SuccessGreen
                )
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Speichern")
            }
        }
    }
}

@Composable
private fun SaveConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = SuccessGreen
                )
            ) {
                Text("Bestätigen")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        },
        icon = {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = SuccessGreen,
                modifier = Modifier.size(48.dp)
            )
        },
        title = {
            Text(
                "Änderungen speichern?",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Text(
                "Ihre Profiländerungen werden gespeichert.",
                textAlign = TextAlign.Center
            )
        }
    )
}

@Composable
private fun SectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    iconColor: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = iconColor.copy(alpha = 0.1f),
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp)
            )
        }

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = DarkGreen
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModernTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    isValid: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = {
            Icon(
                leadingIcon,
                contentDescription = null,
                tint = if (enabled && isValid) FabGreen else Gray400
            )
        },
        trailingIcon = {
            if (enabled && value.isNotEmpty()) {
                Icon(
                    if (isValid) Icons.Default.Check else Icons.Default.Close,
                    contentDescription = null,
                    tint = if (isValid) SuccessGreen else ErrorRed,
                    modifier = Modifier.size(20.dp)
                )
            }
        },
        enabled = enabled,
        singleLine = true,
        keyboardOptions = keyboardOptions,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = FabGreen,
            unfocusedBorderColor = Gray300,
            disabledBorderColor = Gray200,
            focusedLabelColor = FabGreen
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    )
}