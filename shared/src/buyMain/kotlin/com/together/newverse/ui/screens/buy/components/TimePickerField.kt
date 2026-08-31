package com.together.newverse.ui.screens.buy.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.a11y_choose_time
import newverse.shared.generated.resources.button_cancel
import newverse.shared.generated.resources.button_ok
import org.jetbrains.compose.resources.stringResource

@Composable
fun TimePickerField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    hint: String = "",
    leadingIcon: ImageVector,
    enabled: Boolean = true,
    isValid: Boolean = true,
    errorMessage: String? = null
) {
    var showTimePicker by remember { mutableStateOf(false) }
    val chooseTimeLabel = stringResource(Res.string.a11y_choose_time)

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(
                    width = 1.dp,
                    color = when {
                        errorMessage != null -> MaterialTheme.colorScheme.error
                        isValid && value.isNotEmpty() -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.outline
                    },
                    shape = RoundedCornerShape(12.dp)
                )
                .background(
                    if (enabled) MaterialTheme.colorScheme.surface
                    else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                )
                .clickable(
                    enabled = enabled,
                    onClickLabel = chooseTimeLabel,
                    role = Role.Button
                ) { showTimePicker = true }
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    leadingIcon,
                    contentDescription = null,
                    tint = when {
                        errorMessage != null -> MaterialTheme.colorScheme.error
                        enabled && isValid -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    },
                    modifier = Modifier.size(24.dp)
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = value.ifEmpty { hint },
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (value.isEmpty())
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        else
                            MaterialTheme.colorScheme.onSurface
                    )
                }

                if (enabled && value.isNotEmpty()) {
                    Icon(
                        if (isValid) Icons.Default.Check else Icons.Default.Close,
                        contentDescription = null,
                        tint = if (isValid) MaterialTheme.colorScheme.tertiary
                        else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        if (errorMessage != null) {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .padding(start = 16.dp, top = 4.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
    }

    if (showTimePicker) {
        TimePickerDialog(
            initialTime = value,
            onTimeSelected = { newTime ->
                onValueChange(newTime)
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false }
        )
    }
}

@Composable
private fun TimePickerDialog(
    initialTime: String,
    onTimeSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var hour by remember {
        mutableIntStateOf(
            initialTime.takeIf { it.isNotEmpty() }
                ?.split(":")
                ?.firstOrNull()
                ?.toIntOrNull() ?: 12
        )
    }
    var minute by remember {
        mutableIntStateOf(
            initialTime.takeIf { it.isNotEmpty() }
                ?.split(":")
                ?.getOrNull(1)
                ?.toIntOrNull() ?: 0
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Abholzeit wählen") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Display preview of selected time
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp)),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}",
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    )
                }

                // Time picker controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Hour picker
                    TimeUnitPicker(
                        value = hour,
                        onValueChange = { hour = it },
                        minValue = 7,
                        maxValue = 18,
                        label = "Stunde",
                        modifier = Modifier.weight(1f)
                    )

                    Text(":", style = MaterialTheme.typography.headlineMedium)

                    // Minute picker
                    TimeUnitPicker(
                        value = minute,
                        onValueChange = { minute = it },
                        minValue = 0,
                        maxValue = 59,
                        step = 5,
                        label = "Minute",
                        modifier = Modifier.weight(1f)
                    )
                }

                // Helper text
                Text(
                    text = "Gültig von 7:00 - 18:00 Uhr",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val formattedTime = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
                    onTimeSelected(formattedTime)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary
                )
            ) {
                Text(stringResource(Res.string.button_ok))
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
private fun TimeUnitPicker(
    value: Int,
    onValueChange: (Int) -> Unit,
    minValue: Int,
    maxValue: Int,
    step: Int = 1,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Increment button
        Button(
            onClick = {
                val newValue = value + step
                if (newValue <= maxValue) onValueChange(newValue)
            },
            modifier = Modifier.size(40.dp),
            contentPadding = PaddingValues(0.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("+", color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.headlineSmall)
            }
        }

        // Value display
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp)),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outline
            )
        ) {
            Text(
                text = value.toString().padStart(2, '0'),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            )
        }

        // Decrement button
        Button(
            onClick = {
                val newValue = value - step
                if (newValue >= minValue) onValueChange(newValue)
            },
            modifier = Modifier.size(40.dp),
            contentPadding = PaddingValues(0.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("-", color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.headlineSmall)
            }
        }

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
