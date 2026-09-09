package com.together.newverse.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.*
import org.jetbrains.compose.resources.painterResource

/**
 * Product thumbnail / hero image with one consistent loading + fallback treatment.
 *
 * Accessibility: decorative by default. When [contentDescription] is null the whole
 * subtree (image, loading spinner, fallback placeholder) is removed from the semantics
 * tree via [clearAndSetSemantics] — in list and grid cards the product name sits right
 * next to the image, so a screen reader announcing the name again (or "image loading")
 * is pure noise. Pass a non-null [contentDescription] only for a standalone image that
 * has no adjacent text label.
 */
@Composable
fun ProductImage(
    url: String,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    contentScale: ContentScale = ContentScale.Crop,
    contentDescription: String? = null,
) {
    val semanticsModifier = if (contentDescription == null) {
        Modifier.clearAndSetSemantics { }
    } else {
        Modifier
    }

    Box(modifier = modifier.then(semanticsModifier).clip(shape)) {
        if (url.isNotEmpty()) {
            SubcomposeAsyncImage(
                model = url,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
                loading = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp,
                        )
                    }
                },
                error = {
                    Image(
                        painter = painterResource(Res.drawable.place_holder_landscape),
                        contentDescription = contentDescription,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = contentScale,
                    )
                },
            )
        } else {
            Image(
                painter = painterResource(Res.drawable.place_holder_landscape),
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
            )
        }
    }
}
