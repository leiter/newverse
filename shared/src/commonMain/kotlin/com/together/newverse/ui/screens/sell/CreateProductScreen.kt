package com.together.newverse.ui.screens.sell

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateProductScreen() {
    var productName by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(Res.string.create_product_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )

        OutlinedTextField(
            value = productName,
            onValueChange = { productName = it },
            label = { Text(stringResource(Res.string.create_product_name)) },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = price,
            onValueChange = { price = it },
            label = { Text(stringResource(Res.string.label_price)) },
            modifier = Modifier.fillMaxWidth(),
            prefix = { Text(stringResource(Res.string.create_product_price_prefix)) }
        )

        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text(stringResource(Res.string.label_description)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 4
        )

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = { /* TODO: Save product */ },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(Res.string.create_product_button))
        }

        OutlinedButton(
            onClick = { /* TODO: Cancel */ },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(Res.string.button_cancel))
        }
    }
}

