package com.together.newverse.ui.navigation

import com.together.newverse.domain.model.ActionKind
import com.together.newverse.domain.model.ActionLocation
import com.together.newverse.domain.model.ContactChannel
import com.together.newverse.domain.model.SellerContact
import com.together.newverse.domain.model.SellerUiAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * What the top bar's brand mark does when tapped.
 *
 * The mark is one seat on Home and Profile holding one idea — who this seller is and
 * how to reach them. It used to be two icons: a person glyph for the channels and the
 * mark for About. The rule that replaced them has three decisions in it, and this is
 * where they are pinned:
 *
 * 1. **About is always offered, and always last.** It is the bar's only route into the
 *    imprint and terms, so a buyer who gains contacts must not lose it. Keeping it in
 *    the menu is also what makes the two cases one rule rather than two: the direct tap
 *    is simply the menu that has nothing else in it.
 * 2. **The override keeps the first seat.** A seller who points the mark at their own
 *    page must not thereby delete their phone number from the bar.
 * 3. **No contacts means the seat behaves exactly as it did before contacts existed.**
 *    That is the demo / unapproved case, and the whole point of it is that nothing
 *    about the bar changes when a buyer is approved — same icon, same width, same
 *    first destination.
 *
 * The gate that decides which case a buyer is in lives one layer up, in
 * `BuyAppState.topBarContacts`, and is covered by `BuyAppViewModelSellerContactsTest`.
 * Here the contacts are simply handed in.
 */
class BrandActionTest {

    private val phone = SellerContact(ContactChannel.PHONE, "03020239477")
    private val mail = SellerContact(ContactChannel.EMAIL, "hof@example.org")

    private fun override(label: String = "Hofladen") = SellerUiAction(
        id = "brand",
        kind = ActionKind.OPEN_URL,
        label = label,
        value = "example.org/hofladen",
        location = ActionLocation.TOPBAR_HOME_BRAND_ICON,
    )

    // --- The demo / unapproved case: unchanged from before contacts existed ---

    @Test
    fun `with no contacts the mark goes straight to About`() {
        val seat = brandSeat(override = null, contacts = emptyList())

        val direct = assertIs<BrandSeat.DirectTap>(seat)
        assertEquals(BrandRow.About, direct.target)
    }

    @Test
    fun `with no contacts a published override still owns the whole tap`() {
        val action = override()

        val seat = brandSeat(override = action, contacts = emptyList())

        val direct = assertIs<BrandSeat.DirectTap>(seat)
        assertEquals(BrandRow.Override(action), direct.target)
    }

    // --- The approved case: a menu, with About last ---

    @Test
    fun `contacts turn the mark into a menu ending in About`() {
        val seat = brandSeat(override = null, contacts = listOf(phone, mail))

        val menu = assertIs<BrandSeat.Menu>(seat)
        assertEquals(
            listOf(BrandRow.Contact(phone), BrandRow.Contact(mail), BrandRow.About),
            menu.rows
        )
    }

    @Test
    fun `About is last whatever else is in the menu`() {
        val seat = brandSeat(override = override(), contacts = listOf(phone, mail))

        val menu = assertIs<BrandSeat.Menu>(seat)
        assertEquals(BrandRow.About, menu.rows.last())
    }

    @Test
    fun `a single contact still opens a menu rather than dialling on the mark`() {
        // The mark means the seller, not one of their numbers. Tapping it must never
        // place a call by itself, however few channels there are.
        val seat = brandSeat(override = null, contacts = listOf(phone))

        val menu = assertIs<BrandSeat.Menu>(seat)
        assertEquals(listOf(BrandRow.Contact(phone), BrandRow.About), menu.rows)
    }

    // --- The override and the contacts coexist ---

    @Test
    fun `the override takes the first row and does not displace a contact`() {
        val action = override()

        val seat = brandSeat(override = action, contacts = listOf(phone, mail))

        val menu = assertIs<BrandSeat.Menu>(seat)
        assertEquals(BrandRow.Override(action), menu.rows.first())
        assertTrue(BrandRow.Contact(phone) in menu.rows)
        assertTrue(BrandRow.Contact(mail) in menu.rows)
    }

    @Test
    fun `publishing a brand row never takes the seller's number out of the bar`() {
        // The failure this guards is silent: the seller publishes a link to their own
        // page and their phone number quietly stops being reachable from the top bar.
        val withoutOverride = assertIs<BrandSeat.Menu>(
            brandSeat(override = null, contacts = listOf(phone, mail))
        )
        val withOverride = assertIs<BrandSeat.Menu>(
            brandSeat(override = override(), contacts = listOf(phone, mail))
        )

        val lostRows = withoutOverride.rows - withOverride.rows.toSet()
        assertEquals(emptyList(), lostRows)
    }

    // --- Ordering is the seller's, not ours ---

    @Test
    fun `contacts keep the order they were resolved in`() {
        // resolveFor puts the seller's own channels before the market's; re-sorting
        // here would silently undo that.
        val seat = brandSeat(override = null, contacts = listOf(mail, phone))

        val menu = assertIs<BrandSeat.Menu>(seat)
        assertEquals(
            listOf(BrandRow.Contact(mail), BrandRow.Contact(phone), BrandRow.About),
            menu.rows
        )
    }

    @Test
    fun `About appears exactly once`() {
        val seat = brandSeat(override = override(), contacts = listOf(phone, mail))

        val menu = assertIs<BrandSeat.Menu>(seat)
        assertEquals(1, menu.rows.count { it is BrandRow.About })
    }
}
