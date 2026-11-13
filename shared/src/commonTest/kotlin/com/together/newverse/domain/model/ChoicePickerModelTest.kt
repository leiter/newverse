package com.together.newverse.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What a published choice picker is allowed to be, and what its dismissal is keyed on.
 *
 * The decisions pinned here are the ones that are expensive to get wrong later: a
 * picker nobody can answer must not reach a buyer, and a seller rewriting the answers
 * must reach a buyer who already answered the old ones.
 */
class ChoicePickerModelTest {

    private fun picker(
        options: List<String> = listOf("Eier", "Honig"),
        mode: SelectionMode = SelectionMode.MULTI,
        value: String = "Was fehlt euch?",
    ) = SellerUiAction(
        id = "act_1",
        kind = ActionKind.CHOICE,
        label = "Was fehlt",
        value = value,
        location = ActionLocation.TOPBAR_HOME_CHOICE,
        options = options,
        selectionMode = mode,
    )

    // --- what counts as a finished picker ------------------------------------

    @Test
    fun `a picker with two answers is complete`() {
        assertTrue(picker().isComplete)
    }

    @Test
    fun `a picker with one answer is not a question`() {
        assertFalse(picker(options = listOf("Eier")).isComplete)
    }

    @Test
    fun `a picker whose answers are the same answer twice is not complete`() {
        // Case-insensitively the same, so the duplicate collapses and one is left.
        assertFalse(picker(options = listOf("Eier", "eier")).isComplete)
    }

    @Test
    fun `blank answers do not count towards the minimum`() {
        assertFalse(picker(options = listOf("Eier", "   ")).isComplete)
    }

    // --- what gets stored -----------------------------------------------------

    @Test
    fun `readyToStore trims, drops blanks and collapses duplicates`() {
        val stored = picker(options = listOf(" Eier ", "Eier", "", "Honig")).readyToStore()
        assertEquals(listOf("Eier", "Honig"), stored.options)
    }

    @Test
    fun `readyToStore keeps the seller's order`() {
        val stored = picker(options = listOf("Honig", "Eier", "Mehl")).readyToStore()
        assertEquals(listOf("Honig", "Eier", "Mehl"), stored.options)
    }

    @Test
    fun `readyToStore caps the number of answers`() {
        val many = (1..20).map { "Antwort $it" }
        assertEquals(SellerUiAction.MAX_OPTIONS, picker(options = many).readyToStore().options.size)
    }

    @Test
    fun `readyToStore clears options from a kind that has none`() {
        // The rules refuse an `options` child on anything but a CHOICE, and would
        // reject the whole row rather than the one field.
        val announcement = SellerUiAction(
            id = "act_2",
            kind = ActionKind.ANNOUNCE,
            value = "Heute ab 9 Uhr",
            location = ActionLocation.TOPBAR_HOME_TITLE,
            options = listOf("Eier"),
        )
        assertTrue(announcement.readyToStore().options.isEmpty())
    }

    @Test
    fun `a picker may hide once it has been answered`() {
        val stored = picker().copy(persistence = ActionPersistence.HIDE_ON_ACTION).readyToStore()
        assertEquals(ActionPersistence.HIDE_ON_ACTION, stored.persistence)
        assertTrue(stored.hidesAfterUse)
    }

    // --- the dismissal key ----------------------------------------------------

    @Test
    fun `rewriting the answers asks the buyer again`() {
        // The failure this prevents: a seller corrects the options in place and never
        // reaches anyone who answered the old set.
        val before = picker(options = listOf("Eier", "Honig"))
        val after = picker(options = listOf("Eier", "Mehl"))
        assertNotEquals(before.dismissalKey, after.dismissalKey)
    }

    @Test
    fun `reordering the answers asks the buyer again`() {
        val before = picker(options = listOf("Eier", "Honig"))
        val after = picker(options = listOf("Honig", "Eier"))
        assertNotEquals(before.dismissalKey, after.dismissalKey)
    }

    @Test
    fun `the same picker keeps the same key`() {
        assertEquals(picker().dismissalKey, picker().dismissalKey)
    }

    @Test
    fun `a row with no options keys exactly as it did before pickers existed`() {
        // Every announcement and message box a buyer has already dismissed has to
        // stay dismissed across this change.
        val announcement = SellerUiAction(
            id = "act_3",
            kind = ActionKind.ANNOUNCE,
            value = "Heute ab 9 Uhr",
            location = ActionLocation.TOPBAR_HOME_TITLE,
        )
        assertEquals("act_3:${"Heute ab 9 Uhr".stableHash()}", announcement.dismissalKey)
    }

    // --- the slots ------------------------------------------------------------

    @Test
    fun `a choice slot is neither a button slot nor an input`() {
        val slot = ActionLocation.TOPBAR_HOME_CHOICE
        assertTrue(slot.isChoice)
        assertFalse(slot.isButtonSlot)
        assertFalse(slot.isInput)
        assertFalse(slot.isIconSeat)
    }

    @Test
    fun `a picker is never drawn as a button`() {
        assertTrue(
            listOf(picker()).forLocation(
                ActionLocation.TOPBAR_HOME_CHOICE,
                isApproved = true,
                isDemo = false
            ).isEmpty()
        )
    }

    @Test
    fun `choiceFor returns the picker for its slot`() {
        val found = listOf(picker()).choiceFor(
            ActionLocation.TOPBAR_HOME_CHOICE,
            isApproved = true,
            isDemo = false
        )
        assertEquals("act_1", found?.id)
    }

    @Test
    fun `choiceFor skips a picker nobody could answer`() {
        // isUsable requires isComplete, so an unanswerable row never reaches a buyer
        // as an icon that opens an empty panel.
        assertNull(
            listOf(picker(options = listOf("Eier"))).choiceFor(
                ActionLocation.TOPBAR_HOME_CHOICE,
                isApproved = true,
                isDemo = false
            )
        )
    }

    // --- the stored selection mode -------------------------------------------

    @Test
    fun `an absent selection mode reads as the narrower rule`() {
        assertEquals(SelectionMode.SINGLE, SelectionMode.stored(null))
    }

    @Test
    fun `an unknown selection mode skips the row`() {
        assertNull(SelectionMode.stored("RANKED"))
    }
}
