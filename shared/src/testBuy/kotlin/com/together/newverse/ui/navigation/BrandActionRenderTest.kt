package com.together.newverse.ui.navigation

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.together.newverse.domain.model.ActionKind
import com.together.newverse.domain.model.ActionLocation
import com.together.newverse.domain.model.ContactChannel
import com.together.newverse.domain.model.SellerContact
import com.together.newverse.domain.model.SellerUiAction
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The brand mark as it actually draws, rather than as [brandSeat] decides.
 *
 * [BrandActionTest] pins the rule; this pins that the rule reaches the screen — that a
 * tap opens the menu rather than navigating, that every row the rule named is drawn and
 * tappable, and that each row calls back with its own target. Those are the parts a
 * pure function cannot vouch for, and the parts a refactor of the composable could
 * quietly break while every rule test stayed green.
 *
 * **This is the project's first Compose UI test.** The infrastructure it needed —
 * Robolectric plus `ui-test-junit4` in `testBuy`, and `isIncludeAndroidResources` so
 * `stringResource` resolves — is in `shared/build.gradle.kts` and is available to any
 * test that wants it now. `doc/guides/testing.md` says how to add one.
 *
 * Locale is pinned to English so the assertions can name the strings. German is the
 * default (`values/`), English is `values-en/`, so "About" here proves the resource was
 * resolved rather than defaulted.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en")
class BrandActionRenderTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val hotline = SellerContact(ContactChannel.PHONE, "03020239477", "Bestellhotline")
    private val mail = SellerContact(ContactChannel.EMAIL, "hof@example.org", "Schreib uns")

    private val brandOverride = SellerUiAction(
        id = "brand",
        kind = ActionKind.OPEN_URL,
        label = "Hofladen",
        value = "example.org/hofladen",
        location = ActionLocation.TOPBAR_HOME_BRAND_ICON,
    )

    /**
     * The mark itself, which draws no text — it is the only clickable node before a
     * menu is open, which is also a small guarantee worth having: the seat is one
     * button, never two.
     */
    private fun tapTheMark() {
        val clickable = composeRule.onAllNodes(hasClickAction())
        assertEquals(1, clickable.fetchSemanticsNodes().size, "the seat drew more than one button")
        clickable.onFirst().performClick()
    }

    private class Taps {
        var about = 0
        val contacts = mutableListOf<SellerContact>()
        val overrides = mutableListOf<SellerUiAction>()
    }

    private fun render(
        override: SellerUiAction? = null,
        contacts: List<SellerContact> = emptyList(),
    ): Taps {
        val taps = Taps()
        composeRule.setContent {
            BrandAction(
                override = override,
                contacts = contacts,
                tint = Color.Black,
                onOpenAbout = { taps.about++ },
                onOpenContact = { taps.contacts += it },
                onOpenOverride = { taps.overrides += it },
            )
        }
        return taps
    }

    @Test
    fun `with no contacts the mark navigates straight to About without opening a menu`() {
        val taps = render(contacts = emptyList())

        tapTheMark()

        assertEquals(1, taps.about)
        // No menu was opened on the way: the row would have been drawn if it had been.
        composeRule.onNodeWithText("About").assertDoesNotExist()
    }

    @Test
    fun `with contacts the mark opens a menu instead of navigating`() {
        val taps = render(contacts = listOf(hotline))

        tapTheMark()

        assertEquals(0, taps.about)
        composeRule.onNodeWithText("Bestellhotline").assertHasClickAction()
        composeRule.onNodeWithText("About").assertHasClickAction()
    }

    @Test
    fun `every row the rule named is drawn, in order, About last`() {
        render(override = brandOverride, contacts = listOf(hotline, mail))

        tapTheMark()

        // Compared by where each row actually sits, not by tree order: a menu whose
        // rows were composed in the right order but drawn in the wrong one would read
        // wrong to the buyer, and that is the thing being promised.
        val tops = listOf("Hofladen", "Bestellhotline", "Schreib uns", "About").map { label ->
            label to composeRule.onNodeWithText(label).fetchSemanticsNode().positionInRoot.y
        }
        assertEquals(
            tops.sortedBy { it.second }.map { it.first },
            tops.map { it.first },
            "rows were drawn out of order: $tops"
        )
    }

    @Test
    fun `tapping a contact row reports that contact and not another`() {
        val taps = render(contacts = listOf(hotline, mail))

        tapTheMark()
        composeRule.onNodeWithText("Schreib uns").performClick()

        assertEquals(listOf(mail), taps.contacts)
        assertEquals(0, taps.about)
    }

    @Test
    fun `tapping About from inside the menu still reaches About`() {
        // The whole point of keeping About in the menu: an approved buyer has not lost
        // the bar's only route to the imprint, it is just one tap deeper.
        val taps = render(contacts = listOf(hotline))

        tapTheMark()
        composeRule.onNodeWithText("About").performClick()

        assertEquals(1, taps.about)
        assertTrue(taps.contacts.isEmpty())
    }

    @Test
    fun `the published override is a row, not the whole tap, once contacts exist`() {
        val taps = render(override = brandOverride, contacts = listOf(hotline))

        tapTheMark()

        // The mark did not fire the override on the way to opening the menu...
        assertTrue(taps.overrides.isEmpty())
        // ...and the seller's number is still reachable beside it.
        composeRule.onNodeWithText("Bestellhotline").assertHasClickAction()

        composeRule.onNodeWithText("Hofladen").performClick()
        assertEquals(listOf(brandOverride), taps.overrides)
    }

    @Test
    fun `with no contacts a published override still owns the whole tap`() {
        val taps = render(override = brandOverride, contacts = emptyList())

        tapTheMark()

        assertEquals(listOf(brandOverride), taps.overrides)
        assertEquals(0, taps.about)
    }
}
