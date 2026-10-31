package com.together.newverse.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.together.newverse.domain.model.SellerInboxMessage
import com.together.newverse.ui.theme.LocalBarColors
import com.together.newverse.util.formatString
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.a11y_quick_message
import newverse.shared.generated.resources.quick_message_dismiss
import newverse.shared.generated.resources.quick_message_send
import org.jetbrains.compose.resources.stringResource

/**
 * The seller's question in place of a screen's title, with a field to answer it in and
 * a way out.
 *
 * The other half of [SellerAnnouncementTitle]: that one publishes a sentence to every
 * buyer, this one asks them something and takes the answer back to
 * `seller_inbox/{sellerId}`. Its first use is a wishbox — "Was fehlt euch im
 * Angebot?" — and the seller publishes it, moves it, rewords it or removes it without
 * an app release, the same way they do a button.
 *
 * Takes the whole title slot for the announcement's reason, more sharply: a field
 * squeezed in beside the app name fits nothing worth sending.
 *
 * ### The draft stays here
 *
 * What the buyer types is `remember`ed in this composable and leaves it once, when
 * send is pressed. Not because state could not hold it, but because every alternative
 * tried in this feature's history was worse: the seller's own editor handed each
 * keystroke to a repository and ate half-typed rows, and a draft in app state would
 * recompose the entire top bar per character. [sentCount] coming back changed is how
 * this composable learns a send landed and clears itself.
 *
 * Accessibility: the field carries the seller's question as its [contentDescription],
 * because the question is drawn as a placeholder and a placeholder is not reliably
 * announced. The send button and the close button are labelled separately — they are
 * opposite answers to the same request and must not be confusable.
 *
 * See `doc/guides/quick-message-wishbox.md`.
 */
@Composable
fun QuickMessageInput(
    /** The seller's question, shown in the empty field. */
    prompt: String,
    onSend: (String) -> Unit,
    onDismiss: () -> Unit,
    /** True between the tap and the write landing; the send button becomes a spinner. */
    isSending: Boolean = false,
    /**
     * How many messages this session has sent. A change clears the field — see the
     * note on the draft above.
     */
    sentCount: Int = 0,
    modifier: Modifier = Modifier,
) {
    var draft by remember { mutableStateOf("") }
    val barColors = LocalBarColors.current
    val keyboard = LocalSoftwareKeyboardController.current
    // Resolved here rather than inside the semantics block, which is not a
    // composable call site.
    val description = formatString(stringResource(Res.string.a11y_quick_message), prompt)

    // A landed send clears the field and puts the keyboard away. Driven by the count
    // rather than by the tap, so the field still holds what the buyer wrote if the
    // write was refused and they want to try again.
    LaunchedEffect(sentCount) {
        if (sentCount > 0) {
            draft = ""
            keyboard?.hide()
        }
    }

    val submit = {
        if (draft.isNotBlank() && !isSending) onSend(draft)
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(end = 4.dp)
        ) {
            // A placeholder rather than a label, and a BasicTextField rather than an
            // OutlinedTextField: a Material text field is taller than a top bar and
            // brings a container colour of its own, which would put a light box in
            // the middle of the brown bar.
            BasicTextField(
                value = draft,
                onValueChange = {
                    // Capped where it is typed as well as where it is sent, so the
                    // buyer finds out at the keyboard rather than at the refusal.
                    draft = it.take(SellerInboxMessage.MAX_TEXT_LENGTH)
                },
                textStyle = LocalTextStyle.current.merge(
                    MaterialTheme.typography.bodyLarge.copy(color = barColors.content)
                ),
                cursorBrush = SolidColor(barColors.content),
                singleLine = true,
                enabled = !isSending,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submit() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = description }
            )
            if (draft.isEmpty()) {
                Text(
                    text = prompt,
                    style = MaterialTheme.typography.bodyLarge,
                    color = barColors.content.copy(alpha = 0.7f),
                    maxLines = 1
                )
            }
        }

        if (isSending) {
            // In the send button's place, not beside it, so the row does not reflow
            // while the write is in flight.
            Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = barColors.content,
                    strokeWidth = 2.dp
                )
            }
        } else {
            IconButton(
                onClick = submit,
                enabled = draft.isNotBlank(),
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = stringResource(Res.string.quick_message_send),
                    // Dimmed rather than hidden while there is nothing to send: a send
                    // button that appears as you type moves the close button under
                    // the finger reaching for it.
                    tint = barColors.content.copy(alpha = if (draft.isBlank()) 0.4f else 1f)
                )
            }
        }

        IconButton(
            onClick = onDismiss,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(Res.string.quick_message_dismiss),
                tint = barColors.content
            )
        }
    }
}
