package com.together.newverse.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.together.newverse.android.notification.SwitchWorker
import com.together.newverse.android.utils.FILE_CONFIG_NOTIFICATION
import com.together.newverse.android.utils.isListenerServiceRunning
import com.together.newverse.android.utils.startListenerService
import com.together.newverse.android.utils.stopListenerService

/**
 * Settings screen for order notification system
 * Allows sellers to control when they receive order notifications
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val config = remember { SwitchWorker.NotificationConfig(context) }

    var isServiceRunning by remember { mutableStateOf(context.isListenerServiceRunning()) }
    var isAutoScheduleEnabled by remember {
        val prefs = context.getSharedPreferences(FILE_CONFIG_NOTIFICATION, android.content.Context.MODE_PRIVATE)
        mutableStateOf(prefs.getBoolean("auto_schedule_enabled", false))
    }

    var startTime by remember { mutableStateOf(config.getStartTime()) }
    var stopTime by remember { mutableStateOf(config.getStopTime()) }

    var showStartTimePicker by remember { mutableStateOf(false) }
    var showStopTimePicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Benachrichtigungseinstellungen") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Zurück"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Service Status Card
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Bestellbenachrichtigungen",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = if (isServiceRunning) "Aktiv" else "Inaktiv",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isServiceRunning) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = null,
                            tint = if (isServiceRunning) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                context.startListenerService()
                                isServiceRunning = true
                            },
                            enabled = !isServiceRunning,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Einschalten")
                        }

                        OutlinedButton(
                            onClick = {
                                context.stopListenerService()
                                isServiceRunning = false
                            },
                            enabled = isServiceRunning,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Ausschalten")
                        }
                    }
                }
            }

            // Auto Schedule Card
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Automatische Zeitplanung",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Benachrichtigungen automatisch aktivieren/deaktivieren",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Switch(
                            checked = isAutoScheduleEnabled,
                            onCheckedChange = { enabled ->
                                isAutoScheduleEnabled = enabled
                                if (enabled) {
                                    SwitchWorker.enable(context)
                                } else {
                                    SwitchWorker.disable(context)
                                }
                            }
                        )
                    }

                    if (isAutoScheduleEnabled) {
                        HorizontalDivider()

                        // Start Time
                        OutlinedButton(
                            onClick = { showStartTimePicker = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                horizontalAlignment = Alignment.Start
                            ) {
                                Text(
                                    text = "Startzeit",
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Text(
                                    text = startTime,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }

                        // Stop Time
                        OutlinedButton(
                            onClick = { showStopTimePicker = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                horizontalAlignment = Alignment.Start
                            ) {
                                Text(
                                    text = "Endzeit",
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Text(
                                    text = stopTime,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                    }
                }
            }

            // Information Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "ℹ️ Information",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "Der Benachrichtigungsdienst überwacht neue Bestellungen in Echtzeit. " +
                                "Mit der automatischen Zeitplanung können Sie festlegen, wann Sie " +
                                "Benachrichtigungen erhalten möchten.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }

    // Time Pickers would require additional implementation
    // For simplicity, you could use a simple text input dialog
    if (showStartTimePicker) {
        TimePickerDialog(
            initialTime = startTime,
            onDismiss = { showStartTimePicker = false },
            onTimeSelected = { time ->
                startTime = time
                config.setStartTime(time)
                showStartTimePicker = false
                // Restart scheduling if enabled
                if (isAutoScheduleEnabled) {
                    SwitchWorker.disable(context)
                    SwitchWorker.enable(context)
                }
            }
        )
    }

    if (showStopTimePicker) {
        TimePickerDialog(
            initialTime = stopTime,
            onDismiss = { showStopTimePicker = false },
            onTimeSelected = { time ->
                stopTime = time
                config.setStopTime(time)
                showStopTimePicker = false
                // Restart scheduling if enabled
                if (isAutoScheduleEnabled) {
                    SwitchWorker.disable(context)
                    SwitchWorker.enable(context)
                }
            }
        )
    }
}

@Composable
fun TimePickerDialog(
    initialTime: String,
    onDismiss: () -> Unit,
    onTimeSelected: (String) -> Unit
) {
    var timeInput by remember { mutableStateOf(initialTime) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Zeit eingeben") },
        text = {
            Column {
                Text("Format: HH:MM (z.B. 08:00)")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = timeInput,
                    onValueChange = { timeInput = it },
                    label = { Text("Zeit") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // Validate time format (basic validation)
                    if (timeInput.matches(Regex("\\d{1,2}:\\d{2}"))) {
                        onTimeSelected(timeInput)
                    }
                }
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
}
