package com.together.newverse.ui.components

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.together.newverse.ui.theme.LocalBarColors
import com.together.newverse.util.formatString
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.a11y_announcement
import newverse.shared.generated.resources.a11y_announcement_dismiss
import org.jetbrains.compose.resources.stringResource

/**
 * The seller's announcement in place of the home screen's title, scrolling, with a
 * way out.
 *
 * Takes the whole title slot, which is what makes it worth having: a top bar holds a
 * title and about two actions, and an announcement that had to share the row with the
 * app name would fit nothing worth saying.
 *
 * The text scrolls rather than truncating because the seller is writing a sentence,
 * not a label, and half a sentence in a top bar is worse than none. [basicMarquee]
 * animates only when the content actually overflows its box, which is exactly the
 * wanted behaviour and the reason it is used rather than a hand-rolled offset
 * animation: a short announcement sits still, and text that jitters when it already
 * fits reads as a bug.
 *
 * Accessibility: the marquee animates a *visual* offset, so the node is given the
 * whole sentence as its [contentDescription] and marked a heading. A screen reader
 * therefore reads the announcement once, in full, rather than whatever fragment is
 * currently on screen.
 *
 * The close button is part of this composable rather than a top-bar action, because
 * it belongs to the announcement: it has to disappear with it, and an action in the
 * row's action slot would sit beyond the app's own icons and read as one of them.
 *
 * See `doc/guides/seller-ui-actions.md`.
 */
@Composable
fun SellerAnnouncementTitle(
    text: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = formatString(stringResource(Res.string.a11y_announcement), text)

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val topBarColors = LocalBarColors.current
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = topBarColors.content,
            maxLines = 1,
            // Reached only by the frames before the marquee measures; without it a
            // long sentence wraps and changes the bar's height on the first frame.
            overflow = TextOverflow.Clip,
            modifier = Modifier
                .weight(1f)
                .semantics {
                    heading()
                    contentDescription = description
                }
                .basicMarquee(iterations = Int.MAX_VALUE)
        )

        IconButton(
            onClick = onDismiss,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(Res.string.a11y_announcement_dismiss),
                tint = topBarColors.content
            )
        }
    }
}
