package com.together.newverse.ui.screens.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.together.newverse.ui.theme.*

@Composable
fun AboutScreenModern(
    onBackClick: () -> Unit = {}
) {
    val scrollState = rememberScrollState()
    val uriHandler = LocalUriHandler.current

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
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Hero Section
                HeroSection()

                // Contact Card
                ContactCard()

                // Legal Information Card
                LegalInfoCard()

                // Privacy Policy Card
                PrivacyCard()

                // Mission Statement
                MissionCard()
            }
        }
    }
}

@Composable
private fun HeroSection() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Logo/Icon
            Surface(
                shape = CircleShape,
                color = LeafGreen,
                modifier = Modifier.size(80.dp)
            ) {
                Icon(
                    Icons.Default.Favorite,
                    contentDescription = null,
                    tint = White,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "BODENSCHÄTZE",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = FabGreen
            )

            Text(
                text = "Bio • Regional • Frisch",
                style = MaterialTheme.typography.bodyLarge,
                color = Gray600,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = SuccessGreen.copy(alpha = 0.1f)
            ) {
                Text(
                    text = "Seit 2020",
                    style = MaterialTheme.typography.labelMedium,
                    color = SuccessGreen,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun ContactCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = OrganicBeige.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = FabGreen,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Default.Email,
                        contentDescription = null,
                        tint = White,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp)
                    )
                }

                Text(
                    text = "Kontakt",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = DarkGreen
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Phone
            ContactRow(
                icon = Icons.Default.Phone,
                text = "0172 - 46 23 741",
                iconColor = FabGreen
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Email
            val emailAnnotatedString = buildAnnotatedString {
                pushStringAnnotation(
                    tag = "URL",
                    annotation = "mailto:bodenschaetze@posteo.de"
                )
                withStyle(
                    style = SpanStyle(
                        color = FabGreen,
                        textDecoration = TextDecoration.Underline,
                        fontWeight = FontWeight.Medium
                    )
                ) {
                    append("bodenschaetze@posteo.de")
                }
                pop()
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Email,
                    contentDescription = null,
                    tint = FabGreen,
                    modifier = Modifier.size(20.dp)
                )

                ClickableText(
                    text = emailAnnotatedString,
                    style = MaterialTheme.typography.bodyLarge,
                    onClick = { offset ->
                        emailAnnotatedString.getStringAnnotations(
                            tag = "URL",
                            start = offset,
                            end = offset
                        ).firstOrNull()?.let { annotation ->
                            // Handle email click
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Location
            ContactRow(
                icon = Icons.Default.LocationOn,
                text = "Ökomarkt im Hansaviertel",
                iconColor = FabGreen
            )
        }
    }
}

@Composable
private fun LegalInfoCard() {
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = BrownAccent,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = White,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp)
                    )
                }

                Text(
                    text = "Impressum",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = DarkGreen
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            InfoSection(
                title = "Inhaber",
                content = "Eric Dehn"
            )

            Spacer(modifier = Modifier.height(12.dp))

            InfoSection(
                title = "Anschrift",
                content = "Neue Gartenstraße\n15517 Fürstenwalde / Spree"
            )

            Spacer(modifier = Modifier.height(12.dp))

            InfoSection(
                title = "Vertreten durch",
                content = "Eric Dehn"
            )
        }
    }
}

@Composable
private fun PrivacyCard() {
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = InfoBlue,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        tint = White,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp)
                    )
                }

                Text(
                    text = "Datenschutz",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = DarkGreen
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Wir nehmen den Schutz aller persönlichen Daten sehr ernst.",
                style = MaterialTheme.typography.bodyLarge,
                color = Gray700,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Alle personenbezogenen Informationen werden vertraulich und gemäß den geltenden Datenschutzbestimmungen behandelt. Ihre Daten werden ausschließlich zur Bestellabwicklung und Kommunikation verwendet.",
                style = MaterialTheme.typography.bodyMedium,
                color = Gray600,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = { /* Open privacy policy */ },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = FabGreen
                ),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = Brush.linearGradient(
                        colors = listOf(FabGreen, FabGreen)
                    )
                )
            ) {
                Text("Vollständige Datenschutzerklärung")
            }
        }
    }
}

@Composable
private fun MissionCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = LeafGreen.copy(alpha = 0.1f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Favorite,
                contentDescription = null,
                tint = ErrorRed,
                modifier = Modifier.size(32.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Unsere Mission",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = FabGreen,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Wir bringen frische, biologische und regionale Lebensmittel direkt vom Feld zu Ihnen nach Hause. Für eine nachhaltige Zukunft und gesunde Ernährung.",
                style = MaterialTheme.typography.bodyMedium,
                color = Gray700,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )
        }
    }
}

@Composable
private fun ContactRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    iconColor: Color = Gray600
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = Gray700
        )
    }
}

@Composable
private fun InfoSection(
    title: String,
    content: String
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = Gray600,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = content,
            style = MaterialTheme.typography.bodyLarge,
            color = Gray800,
            lineHeight = 22.sp
        )
    }
}