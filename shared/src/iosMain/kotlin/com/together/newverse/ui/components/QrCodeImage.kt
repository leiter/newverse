package com.together.newverse.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
actual fun QrCodeImage(
    content: String,
    modifier: Modifier,
    sizeDp: Int
) {
    // Placeholder for iOS - CoreImage QR generation can be added later
    Box(
        modifier = modifier.size(sizeDp.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(stringResource(Res.string.cd_qr_code))
    }
}
