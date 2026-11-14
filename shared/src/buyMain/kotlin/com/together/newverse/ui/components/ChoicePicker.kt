package com.together.newverse.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.together.newverse.domain.model.SelectionMode
import com.together.newverse.ui.theme.LocalBarColors
import com.together.newverse.ui.theme.NewverseTheme
import com.together.newverse.util.formatString
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.a11y_choice
import newverse.shared.generated.resources.choice_close
import newverse.shared.generated.resources.choice_hint_multi
import newverse.shared.generated.resources.choice_hint_single
import newverse.shared.generated.resources.choice_open
import newverse.shared.generated.resources.choice_send
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * The top bar's question mark: the seat a published [SelectionMode] picker opens from.
 *
 * Kept apart from [ChoicePickerPanel] because the two are drawn in different places —
 * this goes among the top bar's actions, the panel goes over the content below — so
 * whether the picker is open is state neither of them can own. `AppScaffold` holds it,
 * the same way it already holds which title slot wins.
 */
@Composable
fun ChoicePickerIcon(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * What to draw.
     *
     * Required rather than defaulted to a question mark: which icon a picker with
     * none of its own gets is `ActionKind.defaultIcon`'s to say, and a default here
     * would be a second opinion on it that nothing keeps in step.
     */
    icon: ImageVector,
) {
    val barColors = LocalBarColors.current
    IconButton(onClick = onClick, modifier = modifier.size(40.dp)) {
        Icon(
            imageVector = icon,
            // What the thing *does*, never which icon the seller picked. A screen
            // reader has nothing else here, and "shopping cart" would describe the
            // drawing rather than the question behind it.
            contentDescription = stringResource(Res.string.choice_open),
            tint = barColors.content
        )
    }
}

/**
 * The seller's question with a fixed set of answers, opened from [ChoicePickerIcon].
 *
 * The other shape of [QuickMessageInput]: that one takes a screen's whole title and
 * asks for a sentence, this one stays behind an icon and asks the buyer to pick from
 * what the seller offered. The difference in seat is the point — a picker is opened,
 * not put in the way — which is why it can coexist with an announcement where a
 * message box cannot, and why **it has no dismissal**. Closing it closes the panel;
 * the icon stays, because nothing was imposed on the buyer to begin with.
 *
 * ### The selection stays here, and is parked when this closes
 *
 * What the buyer has picked is `remember`ed in this composable and leaves it once,
 * when send is pressed — [QuickMessageInput]'s reasoning exactly, and for the same
 * two reasons: a state round-trip per tap would recompose the whole scaffold, and
 * this feature has already been bitten by handing half-finished input to a
 * repository. [sentCount] coming back changed is how this composable learns a send
 * landed and clears itself.
 *
 * Closing the panel, though, removes this composable from the tree — so the selection
 * is also handed out through [onSelectionChange] and read back through
 * [initialSelection], which the scaffold holds in a [ChoiceSelections] above the
 * panel it opens and closes. Without it, a buyer who closed the panel to re-read the
 * question lost every answer they had ticked. The hand-out is a plain function call
 * into a plain map, not a state write, so a tap still recomposes nothing but the
 * panel.
 *
 * Selections are held as the option **labels**, not as positions, so that what this
 * composable hands to [onSend] is already what gets stored — see
 * `SellerInboxMessage.selections`. Duplicate options are collapsed before a row ever
 * reaches here (`SellerUiAction.readyToStore`), so a label is a safe key.
 *
 * Accessibility: in [SelectionMode.SINGLE] the chips are wrapped in a
 * [selectableGroup], so they are announced as one set of radio buttons rather than as
 * eight unrelated checkboxes. The question is marked a heading, and carried on the
 * panel as a [contentDescription] for the reason the message box carries one.
 *
 * No back-press handling, following `OverviewScreen`: Compose Multiplatform's common
 * `BackHandler` is experimental *and* already deprecated in 1.10.3. The close button
 * and a tap outside the card are the ways out, and both are visible.
 *
 * See `doc/guides/quick-message-wishbox.md`.
 */
@Composable
fun ChoicePickerPanel(
    /** The seller's question. */
    prompt: String,
    /** The answers offered, already trimmed, capped and deduplicated. */
    options: List<String>,
    selectionMode: SelectionMode,
    /** Handed the picked labels, in the order the seller listed them. */
    onSend: (List<String>) -> Unit,
    /** Close the panel. Not a dismissal: the icon stays. */
    onDismiss: () -> Unit,
    /** True between the tap and the write landing; the send button becomes a spinner. */
    isSending: Boolean = false,
    /** How many answers this session has sent. A change clears the selection. */
    sentCount: Int = 0,
    /**
     * What this buyer had already ticked in this picker and not sent — see the note
     * on the selection above. Read once, when the panel is composed.
     */
    initialSelection: Set<String> = emptySet(),
    /** Hands every change out to whoever is holding the selection while this is shut. */
    onSelectionChange: (Set<String>) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var selected by remember { mutableStateOf(initialSelection) }
    val description = formatString(stringResource(Res.string.a11y_choice), prompt)
    // The scrim and the card both swallow taps, and neither should show a ripple for
    // it: one is a dismissal, the other is a non-event.
    val scrimInteraction = remember { MutableInteractionSource() }
    val cardInteraction = remember { MutableInteractionSource() }

    // What the counter stood at when this panel opened, compared against rather than
    // against zero: a panel reopened after an earlier send composes with the count
    // already past zero, and clearing on that would empty the parked selection the
    // moment it was restored.
    val sentCountAtEntry = remember { sentCount }

    // A landed send clears the selection, for the reason the message box clears its
    // field: driven by the count rather than by the tap, so a refused write leaves
    // the buyer's answer intact to try again.
    LaunchedEffect(sentCount) {
        if (sentCount > sentCountAtEntry) {
            selected = emptySet()
            onSelectionChange(emptySet())
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.32f))
            .clickable(
                interactionSource = scrimInteraction,
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.TopCenter
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            shadowElevation = 6.dp,
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                // Swallows the tap so choosing an answer near the card's edge does
                // not fall through to the scrim and close the panel mid-answer.
                .clickable(
                    interactionSource = cardInteraction,
                    indication = null,
                    onClick = {}
                )
                .semantics { contentDescription = description }
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = prompt,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                            .semantics { heading() }
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(Res.string.choice_close),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Said in words, not left to the chips' shape. Whether a second tap
                // adds an answer or replaces the first is the one thing a buyer
                // cannot discover without trying it.
                Text(
                    text = stringResource(
                        if (selectionMode == SelectionMode.MULTI) {
                            Res.string.choice_hint_multi
                        } else {
                            Res.string.choice_hint_single
                        }
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                )

                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        // Announced as one set of radio buttons when only one answer
                        // is allowed. Omitted for MULTI, where the chips really are
                        // independent checkboxes.
                        .then(
                            if (selectionMode == SelectionMode.SINGLE) {
                                Modifier.selectableGroup()
                            } else {
                                Modifier
                            }
                        ),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    itemVerticalAlignment = Alignment.CenterVertically
                ) {
                    options.forEach { option ->
                        FilterChip(
                            selected = option in selected,
                            onClick = {
                                selected = when {
                                    selectionMode == SelectionMode.SINGLE ->
                                        // Replaces rather than toggles off: a single
                                        // choice the buyer can empty by tapping it
                                        // again leaves them with a send button that
                                        // went dead for no visible reason.
                                        setOf(option)
                                    option in selected -> selected - option
                                    else -> selected + option
                                }
                                onSelectionChange(selected)
                            },
                            enabled = !isSending,
                            label = { Text(option) }
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isSending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Button(
                            onClick = {
                                // Sent in the seller's own order, not in tap order:
                                // the seller reading a column of answers should see
                                // them lined up the way they wrote the question.
                                if (selected.isNotEmpty()) {
                                    onSend(options.filter { it in selected })
                                }
                            },
                            enabled = selected.isNotEmpty()
                        ) {
                            Text(stringResource(Res.string.choice_send))
                        }
                    }
                }
            }
        }
    }
}

@Preview
@Composable
fun ChoicePickerPanelMultiPreview() {
    NewverseTheme {
        ChoicePickerPanel(
            prompt = "Was sollen wir nächste Woche mitbringen?",
            options = listOf(
                "Bergkäse", "Dinkelmehl", "Eier", "Honig", "Rohmilch", "Ziegenfrischkäse"
            ),
            selectionMode = SelectionMode.MULTI,
            onSend = { },
            onDismiss = { }
        )
    }
}

@Preview
@Composable
fun ChoicePickerPanelSinglePreview() {
    NewverseTheme {
        ChoicePickerPanel(
            prompt = "Wann kommt ihr am liebsten an den Stand?",
            options = listOf("Früh, vor 10 Uhr", "Mittags", "Nachmittags", "Kurz vor Schluss"),
            selectionMode = SelectionMode.SINGLE,
            onSend = { },
            onDismiss = { },
            isSending = true
        )
    }
}
