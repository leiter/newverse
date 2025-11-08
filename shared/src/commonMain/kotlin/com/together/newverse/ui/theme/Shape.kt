package com.together.newverse.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val AppShapes = Shapes(
    // Extra small components (chips, etc.)
    extraSmall = RoundedCornerShape(4.dp),

    // Small components (buttons, text fields, cards)
    small = RoundedCornerShape(8.dp),

    // Medium components (dialogs, menus)
    medium = RoundedCornerShape(12.dp),

    // Large components (bottom sheets, navigation drawers)
    large = RoundedCornerShape(16.dp),

    // Extra large components
    extraLarge = RoundedCornerShape(28.dp)
)
