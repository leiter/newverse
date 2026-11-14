package com.together.newverse.ui.components

/**
 * What the buyer has typed into a seller's message box but not yet sent, outliving
 * the box itself.
 *
 * [QuickMessageInput] holds its own draft while it is on screen, for the reasons its
 * KDoc gives: a state round-trip per character would recompose the whole top bar, and
 * this feature has already been bitten by handing half-typed input to a repository.
 * But collapsing the row takes the composable out of the tree altogether — the title
 * slot's `AnimatedContent` swaps a `Prompt` for a `Plain` — so a `remember` inside it
 * dies with it, and a half-written wish was lost on every collapse. `rememberSaveable`
 * does not help: it restores state across a configuration change, not across a
 * composable being removed on purpose.
 *
 * So the draft is parked here, one entry per row, by the row's `dismissalKey`:
 * - **Not snapshot state.** A write must not recompose anything — that is the whole
 *   reason the field keeps its own state — and nothing ever reads this map outside a
 *   fresh composition of the field.
 * - **Keyed by `dismissalKey`**, so the home box and the profile box keep separate
 *   drafts, and a question the seller has reworded starts with an empty field rather
 *   than an answer to the question it replaced.
 * - **Remembered by the scaffold**, above the slot that comes and goes, which is also
 *   why a draft survives walking to another screen and back. It is deliberately not
 *   persisted past the process: an unsent sentence is worth keeping for the length of
 *   a session, not resurrecting days later next to a question that may be gone.
 *
 * An empty draft is removed rather than stored, so a box the buyer cleared out leaves
 * nothing behind.
 *
 * See `doc/guides/quick-message-wishbox.md`.
 */
class QuickMessageDrafts {

    private val byKey = mutableMapOf<String, String>()

    /** What is parked for this row, or an empty string. */
    fun of(key: String): String = byKey[key] ?: ""

    /** Parks what the buyer has typed, or forgets the row once they have cleared it. */
    fun put(key: String, text: String) {
        if (text.isEmpty()) byKey.remove(key) else byKey[key] = text
    }
}
