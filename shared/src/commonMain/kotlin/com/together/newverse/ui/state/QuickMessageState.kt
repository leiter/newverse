package com.together.newverse.ui.state

import org.jetbrains.compose.resources.StringResource

/**
 * Where a message the buyer is sending through a published box currently stands.
 *
 * **What the buyer has typed is not in here.** The draft lives in the field, which is
 * the one place that needs it, and reaches the ViewModel once when send is pressed —
 * the lesson the seller's own editor learned the hard way, where a row handed itself
 * to the repository on every keystroke and vanished mid-word. A state round-trip per
 * character would also recompose the whole top bar.
 *
 * So this holds only what the field cannot know by itself: whether a send is in
 * flight, whether the last one failed, and that one has landed.
 *
 * The error is a [StringResource] rather than a resolved string for
 * [ChangePasswordState]'s reason: a ViewModel has no resource environment to resolve
 * one in, and `getString` throws in a plain JVM test.
 */
data class QuickMessageState(
    /** True between the tap and the write landing; the send button waits on it. */
    val isSending: Boolean = false,
    /**
     * Why the last send failed, or null.
     *
     * Said out loud rather than swallowed, unlike a failed *dismissal*: a wish that
     * silently went nowhere is the one failure the buyer cannot see and would not
     * retry.
     */
    val error: StringResource? = null,
    /**
     * How many messages this session has sent, and nothing more.
     *
     * A counter rather than the id or key of the last message, because its only job
     * is to tell the field that a send landed so it can clear itself — and a buyer
     * sending a second wish through the same box would not change a key.
     */
    val sentCount: Int = 0,
)
