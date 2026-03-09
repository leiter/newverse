package com.together.newverse.ui.screens.buy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.together.newverse.ui.components.QrCodeImage
import com.together.newverse.ui.navigation.PlatformAction
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.contacts_add
import newverse.shared.generated.resources.contacts_enter_id
import newverse.shared.generated.resources.contacts_share
import newverse.shared.generated.resources.contacts_your_id
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AddBuyerContactScreen(
    onNavigateBack: () -> Unit,
    onPlatformAction: (PlatformAction) -> Unit = {},
    viewModel: BuyerContactsViewModel = koinViewModel()
) {
    val myUserId by viewModel.currentUserId.collectAsState()
    var contactIdInput by remember { mutableStateOf("") }
    var contactNameInput by remember { mutableStateOf("") }

    val deepLink = "newverse://contact?buyerId=$myUserId"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
            // Your ID section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(Res.string.contacts_your_id),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(Modifier.height(8.dp))

                    // QR Code
                    if (myUserId.isNotEmpty()) {
                        QrCodeImage(
                            content = deepLink,
                            sizeDp = 200
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = myUserId,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = {
                            onPlatformAction(PlatformAction.ShareText(deepLink))
                        }
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(Modifier.padding(4.dp))
                        Text(stringResource(Res.string.contacts_share))
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Add contact manually
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(Res.string.contacts_enter_id),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    OutlinedTextField(
                        value = contactIdInput,
                        onValueChange = { contactIdInput = it },
                        label = { Text("ID") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = contactNameInput,
                        onValueChange = { contactNameInput = it },
                        label = { Text("Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Button(
                        onClick = {
                            if (contactIdInput.isNotBlank()) {
                                viewModel.addContact(contactIdInput.trim(), contactNameInput.trim())
                                onNavigateBack()
                            }
                        },
                        enabled = contactIdInput.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(Res.string.contacts_add))
                    }
                }
            }
        }
}

