package com.together.newverse.ui.screens.common

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.together.newverse.ui.theme.FabGreen

@Composable
fun AboutScreen(
    onBackClick: () -> Unit = {}
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Title
            Text(
                text = "Impressum",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = FabGreen,
                fontSize = 32.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Address Section
            Text(
                text = "Eric Dehn",
                style = MaterialTheme.typography.bodyLarge,
                color = FabGreen,
                fontSize = 18.sp
            )
            Text(
                text = "Neue Gartenstraße",
                style = MaterialTheme.typography.bodyLarge,
                color = FabGreen,
                fontSize = 18.sp
            )
            Text(
                text = "15517 Fürstenwalde / Spree",
                style = MaterialTheme.typography.bodyLarge,
                color = FabGreen,
                fontSize = 18.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Vertreten durch Section
            Text(
                text = "Vertreten durch:",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = FabGreen,
                fontSize = 18.sp
            )
            Text(
                text = "Eric Dehn",
                style = MaterialTheme.typography.bodyLarge,
                color = FabGreen,
                fontSize = 18.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Kontakt Section
            Text(
                text = "Kontakt:",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = FabGreen,
                fontSize = 18.sp
            )
            Text(
                text = "Telefon: 0172 - 46 23 741",
                style = MaterialTheme.typography.bodyLarge,
                color = FabGreen,
                fontSize = 18.sp
            )

            // Email with underline
            Row {
                Text(
                    text = "E-Mail: ",
                    style = MaterialTheme.typography.bodyLarge,
                    color = FabGreen,
                    fontSize = 18.sp
                )
                val annotatedEmail = buildAnnotatedString {
                    pushStringAnnotation(tag = "email", annotation = "bodenschaetze@posteo.de")
                    withStyle(
                        style = SpanStyle(
                            color = FabGreen,
                            fontSize = 18.sp,
                            textDecoration = TextDecoration.Underline
                        )
                    ) {
                        append("bodenschaetze@posteo.de")
                    }
                    pop()
                }
                ClickableText(
                    text = annotatedEmail,
                    onClick = { offset ->
                        annotatedEmail.getStringAnnotations(tag = "email", start = offset, end = offset)
                            .firstOrNull()?.let { annotation ->
                                // Handle email click (could open email client)
                            }
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Datenschutz Section
            Text(
                text = "Datenschutz",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = FabGreen,
                fontSize = 18.sp
            )

            Text(
                text = "Wir nehmen den Schutz aller persönlichen Daten sehr ernst.",
                style = MaterialTheme.typography.bodyLarge,
                color = FabGreen,
                fontSize = 18.sp,
                lineHeight = 24.sp
            )

            Text(
                text = "Alle personenbezogenen Informationen werden vertraulich und gemäß den gesetzlichen Vorschriften behandelt. Die App kann selbstverständlich genutzt werden, ohne dass Sie persönliche Daten angeben. Wenn jedoch zu irgendeinem Zeitpunkt persönliche Daten wie z.B. Name, Adresse oder E-Mail abgefragt werden, wird dies auf freiwilliger Basis geschehen. Niemals werden von uns erhobene Daten ohne Ihre spezielle Genehmigung an Dritte weitergegeben. Datenübertragung im Internet, wie zum Beispiel über E-Mail, kann immer Sicherheitslücken aufweisen. Der komplette Schutz der Daten ist im Internet nicht möglich.",
                style = MaterialTheme.typography.bodyLarge,
                color = FabGreen,
                fontSize = 18.sp,
                lineHeight = 24.sp
            )

            Text(
                text = "Der Nutzung von im Rahmen der Impressumspflicht veröffentlichten Kontaktdaten durch Dritte zur",
                style = MaterialTheme.typography.bodyLarge,
                color = FabGreen,
                fontSize = 18.sp,
                lineHeight = 24.sp
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}


