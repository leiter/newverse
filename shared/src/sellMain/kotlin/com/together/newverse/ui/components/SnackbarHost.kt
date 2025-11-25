package com.together.newverse.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.together.newverse.ui.state.SnackbarState
import com.together.newverse.ui.state.SnackbarType

/**
 * Snackbar host for showing messages in seller app
 */
@Composable
fun SellerSnackbarHost(
    snackbarState: SnackbarState?,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = snackbarState != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        if (snackbarState != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = when (snackbarState.type) {
                            SnackbarType.ERROR -> MaterialTheme.colorScheme.error
                            SnackbarType.SUCCESS -> MaterialTheme.colorScheme.primary
                            SnackbarType.WARNING -> MaterialTheme.colorScheme.tertiary
                            SnackbarType.INFO -> MaterialTheme.colorScheme.surface
                        }
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                ) {
                    Text(
                        text = snackbarState.message,
                        color = when (snackbarState.type) {
                            SnackbarType.ERROR -> MaterialTheme.colorScheme.onError
                            SnackbarType.SUCCESS -> MaterialTheme.colorScheme.onPrimary
                            SnackbarType.WARNING -> MaterialTheme.colorScheme.onTertiary
                            SnackbarType.INFO -> MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}
