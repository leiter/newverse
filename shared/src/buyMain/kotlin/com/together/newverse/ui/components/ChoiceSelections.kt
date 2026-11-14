package com.together.newverse.ui.components

/**
 * What the buyer has ticked in a seller's choice picker but not yet sent, outliving
 * the panel.
 *
 * [QuickMessageDrafts] for [ChoicePickerPanel], and for the same reason: the panel
 * holds its own selection while it is open, because a state round-trip per tap would
 * recompose the whole scaffold — but closing it takes the composable out of the tree,
 * so a `remember` inside it dies with it. A buyer who had picked four answers out of
 * eight, closed the panel to re-read what the seller was asking, and opened it again
 * found all four gone.
 *
 * Selections are parked as the option **labels**, the way the panel holds them, one
 * entry per row by the row's `dismissalKey`. Because that key hashes a picker's
 * answers along with its question, a seller who edits the options gets a fresh panel
 * rather than one still holding a label they no longer offer — which is also why
 * nothing here has to be filtered against the current options on the way back in.
 *
 * Not snapshot state, remembered by the scaffold above the panel, and deliberately
 * not persisted past the process — all three for the reasons [QuickMessageDrafts]
 * gives.
 *
 * An empty selection is removed rather than stored, so a picker the buyer emptied
 * leaves nothing behind.
 *
 * See `doc/guides/quick-message-wishbox.md`.
 */
class ChoiceSelections {

    private val byKey = mutableMapOf<String, Set<String>>()

    /** What is parked for this row, or nothing picked. */
    fun of(key: String): Set<String> = byKey[key] ?: emptySet()

    /** Parks what the buyer has ticked, or forgets the row once they have emptied it. */
    fun put(key: String, selected: Set<String>) {
        if (selected.isEmpty()) byKey.remove(key) else byKey[key] = selected
    }
}
