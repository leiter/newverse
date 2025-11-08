package com.together.newverse.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Theme Preview Screen
 *
 * Displays all theme colors and typography styles for visual reference.
 * Useful for design review and ensuring theme consistency.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemePreviewScreen() {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Theme Preview") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Color Palette
            Text(
                "Color Palette",
                style = MaterialTheme.typography.headlineSmall
            )

            ColorSwatch("Primary", MaterialTheme.colorScheme.primary)
            ColorSwatch("Secondary", MaterialTheme.colorScheme.secondary)
            ColorSwatch("Tertiary", MaterialTheme.colorScheme.tertiary)
            ColorSwatch("Error", MaterialTheme.colorScheme.error)

            Spacer(modifier = Modifier.height(8.dp))

            // Typography
            Text(
                "Typography Scale",
                style = MaterialTheme.typography.headlineSmall
            )

            TypographyShowcase()

            Spacer(modifier = Modifier.height(8.dp))

            // Components
            Text(
                "Components",
                style = MaterialTheme.typography.headlineSmall
            )

            ComponentShowcase()
        }
    }
}

@Composable
private fun ColorSwatch(
    name: String,
    color: androidx.compose.ui.graphics.Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(color)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun TypographyShowcase() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Display Large", style = MaterialTheme.typography.displayLarge)
        Text("Headline Medium", style = MaterialTheme.typography.headlineMedium)
        Text("Title Large", style = MaterialTheme.typography.titleLarge)
        Text("Body Large", style = MaterialTheme.typography.bodyLarge)
        Text("Body Medium", style = MaterialTheme.typography.bodyMedium)
        Text("Label Large", style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun ComponentShowcase() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = {}) {
            Text("Primary Button")
        }

        OutlinedButton(onClick = {}) {
            Text("Outlined Button")
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Text(
                "Card Content",
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}
