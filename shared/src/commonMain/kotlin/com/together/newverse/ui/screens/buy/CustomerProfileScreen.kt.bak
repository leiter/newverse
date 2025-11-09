package com.together.newverse.ui.screens.buy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.together.newverse.ui.components.ProfileInputField
import com.together.newverse.ui.theme.BeigeCard
import com.together.newverse.ui.theme.FabGreen

@Composable
fun CustomerProfileScreen(
    onBackClick: () -> Unit = {}
) {
    var displayName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = BeigeCard
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Display Name Field
            ProfileInputField(
                icon = Icons.Default.Person,
                hint = "Anzeigename",
                value = displayName,
                onValueChange = { displayName = it },
                onClear = { displayName = "" }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Email Field
            ProfileInputField(
                icon = Icons.Default.Email,
                hint = "E-Mail-Adresse",
                value = email,
                onValueChange = { email = it },
                onClear = { email = "" }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Phone Field
            ProfileInputField(
                icon = Icons.Default.Phone,
                hint = "Telefonnummer",
                value = phone,
                onValueChange = { phone = it },
                onClear = { phone = "" }
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Market and Pickup Time Section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Market Location
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "Market",
                            tint = FabGreen,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = "Marktplatz",
                            style = MaterialTheme.typography.titleLarge,
                            color = FabGreen,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Ökomarkt im\nHansaviertel",
                        style = MaterialTheme.typography.bodyLarge,
                        color = FabGreen,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 20.sp
                    )
                }

                // Pickup Time
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = "Pickup time",
                            tint = FabGreen,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = "Abholzeit",
                            style = MaterialTheme.typography.titleLarge,
                            color = FabGreen,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "15:45 Uhr",
                        style = MaterialTheme.typography.bodyLarge,
                        color = FabGreen,
                        fontWeight = FontWeight.Medium,
                        fontSize = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Save Button
            Button(
                onClick = { /* Save profile */ },
                modifier = Modifier
                    .padding(horizontal = 64.dp)
                    .padding(bottom = 48.dp)
                    .height(56.dp)
                    .fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = FabGreen,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(28.dp)
            ) {
                Text(
                    text = "SPEICHERN",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        }
    }
}


