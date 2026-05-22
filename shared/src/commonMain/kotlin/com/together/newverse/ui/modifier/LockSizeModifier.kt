package com.together.newverse.ui.modifier

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.IntSize

fun Modifier.lockSizeAfterFirstMeasure(): Modifier = composed {
    // Stores the frozen size once captured
    var frozenSize by remember { mutableStateOf<IntSize?>(null) }

    this.layout { measurable, constraints ->
        // If we already have a frozen size, force those constraints
        val localizedConstraints = frozenSize?.let {
            constraints.copy(
                minWidth = it.width,
                maxWidth = it.width,
                minHeight = it.height,
                maxHeight = it.height
            )
        } ?: constraints

        val placeable = measurable.measure(localizedConstraints)

        // Capture the size on the very first pass
        if (frozenSize == null) {
            frozenSize = IntSize(placeable.width, placeable.height)
        }

        layout(placeable.width, placeable.height) {
            placeable.placeRelative(0, 0)
        }
    }
}