package com.together.newverse.ui.screens.sell

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun PickDayScreen() {
    val daysOfWeek = listOf(
        stringResource(Res.string.day_monday),
        stringResource(Res.string.day_tuesday),
        stringResource(Res.string.day_wednesday),
        stringResource(Res.string.day_thursday),
        stringResource(Res.string.day_friday),
        stringResource(Res.string.day_saturday),
        stringResource(Res.string.day_sunday)
    )
    var selectedDays by remember { mutableStateOf(setOf<String>()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(Res.string.pick_day_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(Res.string.pick_day_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(daysOfWeek) { day ->
                DayCard(
                    day = day,
                    isSelected = selectedDays.contains(day),
                    onToggle = {
                        selectedDays = if (selectedDays.contains(day)) {
                            selectedDays - day
                        } else {
                            selectedDays + day
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { /* TODO: Save delivery days */ },
            modifier = Modifier.fillMaxWidth(),
            enabled = selectedDays.isNotEmpty()
        ) {
            Text(stringResource(Res.string.pick_day_save))
        }
    }
}

@Composable
private fun DayCard(
    day: String,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = day,
                style = MaterialTheme.typography.titleMedium,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )

            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggle() }
            )
        }
    }
}


