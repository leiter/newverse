package com.together.newverse.ui.screens.sell

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun SellerProfileScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(Res.string.seller_profile_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )

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
                    text = stringResource(Res.string.seller_profile_placeholder_name),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = stringResource(Res.string.seller_profile_placeholder_email),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
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
            StatCard(stringResource(Res.string.seller_profile_stat_products), stringResource(Res.string.seller_profile_stat_products_count), Modifier.weight(1f))
            StatCard(stringResource(Res.string.seller_profile_stat_orders), stringResource(Res.string.seller_profile_stat_orders_count), Modifier.weight(1f))
        }

        Text(
            text = stringResource(Res.string.seller_profile_settings_title),
            style = MaterialTheme.typography.titleMedium
        )

        OutlinedCard(
            onClick = { /* TODO: Edit profile */ },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(Res.string.seller_profile_edit),
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyLarge
            )
        }

        OutlinedCard(
            onClick = { /* TODO: Payment settings */ },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(Res.string.seller_profile_payment),
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyLarge
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        OutlinedButton(
            onClick = { /* TODO: Logout */ },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(Res.string.button_sign_out))
        }
    }
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


