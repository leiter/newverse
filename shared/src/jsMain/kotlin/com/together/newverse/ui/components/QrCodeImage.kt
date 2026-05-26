package com.together.newverse.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
actual fun QrCodeImage(content: String, modifier: Modifier, sizeDp: Int) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(sizeDp.dp).border(1.dp, Color.Gray)
    ) {
        Text("QR", fontSize = 12.sp, color = Color.Gray)
    }
}
