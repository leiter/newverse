package com.together.newverse.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.a11y_state_collapsed
import newverse.shared.generated.resources.a11y_state_expanded
import newverse.shared.generated.resources.a11y_toggle_section
import org.jetbrains.compose.resources.stringResource

/**
 * A card whose body folds away, for the lists on the seller's profile screen.
 *
 * Those lists have no ceiling — a seller with eighty approved buyers got eighty rows
 * laid out inline, inside a plain `verticalScroll` column that composes all of them
 * whether or not anyone scrolls that far, and everything below was pushed off the
 * end of the screen. Collapsed, the body is not composed at all.
 *
 * [count] is why the header earns its keep: a folded card still has to say what is
 * inside it, or the seller has to open each one in turn to find the one they wanted.
 * It is rendered as "Title (3)" and left out entirely when null.
 *
 * [headerAction] is for a card that owns an action of its own, such as the markets
 * card's add button. It sits between the title and the chevron, inside the header
 * row but *outside* its click target, so tapping the row folds the card and tapping
 * the button does the action — one surface, two unambiguous targets.
 *
 * Deliberately not used for the editor cards (contacts, published actions,
 * closures): those hold half-typed input in plain `remember`, which does not survive
 * leaving composition, so folding one would silently discard what the seller was
 * writing.
 */
@Composable
fun ExpandableCard(
    title: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    count: Int? = null,
    headerAction: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val toggleLabel = stringResource(Res.string.a11y_toggle_section)
    val toggleState = stringResource(
        if (expanded) Res.string.a11y_state_expanded else Res.string.a11y_state_collapsed
    )
    val heading = if (count != null) "$title ($count)" else title

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().animateContentSize(animationSpec = spring())) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        onClickLabel = toggleLabel,
                        role = Role.Button,
                        onClick = { onExpandedChange(!expanded) }
                    )
                    .semantics { stateDescription = toggleState }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = heading,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).semantics { heading() }
                )
                headerAction?.invoke()
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    // The row's click label and state already say both what tapping
                    // does and which way it is folded; naming the chevron too would
                    // make a screen reader say it twice.
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        // No top padding: the header's 16dp already separates them.
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    content = content
                )
            }
        }
    }
}
