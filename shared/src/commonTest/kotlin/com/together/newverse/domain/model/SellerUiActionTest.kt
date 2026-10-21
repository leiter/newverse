package com.together.newverse.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A seller-published button opens where the seller pointed it, and nowhere else.
 *
 * The scheme is the whole subject here. A seller who could store a raw URI could make
 * the buy app open an arbitrary one from inside the app's own chrome, where the
 * buyer's trust cue is the top bar rather than an address bar — so the tests below are
 * mostly about what [ActionKind.normalize] refuses to carry through.
 */
class SellerUiActionTest {

    private fun action(value: String, label: String = "Wünsche") =
        SellerUiAction(id = "a1", label = label, value = value)

    // ===== the scheme is never the seller's to choose =====

    @Test
    fun `a bare host gets https`() {
        assertEquals("https://example.org/wuensche", action("example.org/wuensche").toUri())
    }

    @Test
    fun `a stored https prefix is not doubled`() {
        assertEquals("https://example.org", action("https://example.org").toUri())
    }

    @Test
    fun `a stored http prefix is upgraded, not preserved`() {
        // This is the one place that decides the scheme, and it decides on the secure
        // one. A seller typing http:// does not get their buyers served over http.
        assertEquals("https://example.org", action("http://example.org").toUri())
    }

    @Test
    fun `an intent url cannot be smuggled through`() {
        // The danger case: intent:// reaches another installed app. Normalize does not
        // strip it, so it stays in the path of an https URL and goes nowhere.
        val uri = action("intent://evil#Intent;end").toUri()
        assertTrue(uri.startsWith("https://"), "scheme was not https: $uri")
        assertFalse(uri.startsWith("intent://"))
    }

    @Test
    fun `a protocol-relative value cannot reach another host`() {
        // "//evil.test" would otherwise resolve against the scheme as https://evil.test.
        val uri = action("//evil.test").toUri()
        assertEquals("https://evil.test", uri)
        // Documents the limit of the model: the rules reject a leading slash on write,
        // which is what actually stops this. See firebase/tests/seller-ui-actions.test.js.
    }

    @Test
    fun `surrounding whitespace is not part of the target`() {
        assertEquals("https://example.org", action("  example.org  ").toUri())
    }

    // ===== what counts as showable =====

    @Test
    fun `a button with no label is not shown`() {
        assertFalse(action("example.org", label = "").isUsable)
    }

    @Test
    fun `a button with no target is not shown`() {
        assertFalse(action("", label = "Wünsche").isUsable)
    }

    @Test
    fun `a blank label is not a label`() {
        assertFalse(action("example.org", label = "   ").isUsable)
    }

    // ===== unknown stored values degrade rather than crash =====

    @Test
    fun `an unknown kind reads as nothing`() {
        assertNull(ActionKind.fromStored("RUN_SCRIPT"))
        assertNull(ActionKind.fromStored(null))
        assertEquals(ActionKind.OPEN_URL, ActionKind.fromStored("OPEN_URL"))
    }

    @Test
    fun `an unknown location reads as nothing`() {
        // A record written by a newer app simply does not appear in an older one,
        // rather than landing in some arbitrary slot.
        assertNull(ActionLocation.fromStored("TOPBAR_EVERYWHERE"))
        assertEquals(ActionLocation.TOPBAR_HOME, ActionLocation.fromStored("TOPBAR_HOME"))
    }

    // ===== what one slot shows =====

    @Test
    fun `only the asked-for slot is returned`() {
        val actions = listOf(
            SellerUiAction(id = "a", label = "Home", value = "example.org", location = ActionLocation.TOPBAR_HOME),
            SellerUiAction(id = "b", label = "Profil", value = "example.org", location = ActionLocation.TOPBAR_PROFILE),
        )
        assertEquals(listOf("a"), actions.forLocation(ActionLocation.TOPBAR_HOME, isApproved = true, isDemo = false).map { it.id })
        assertEquals(listOf("b"), actions.forLocation(ActionLocation.TOPBAR_PROFILE, isApproved = true, isDemo = false).map { it.id })
    }

    @Test
    fun `unusable rows are left out`() {
        val actions = listOf(
            SellerUiAction(id = "a", label = "", value = "example.org"),
            SellerUiAction(id = "b", label = "Wünsche", value = ""),
            SellerUiAction(id = "c", label = "Gut", value = "example.org"),
        )
        assertEquals(listOf("c"), actions.forLocation(ActionLocation.TOPBAR_PROFILE, isApproved = true, isDemo = false).map { it.id })
    }

    @Test
    fun `the order does not depend on key iteration`() {
        // Two buttons that swap places between launches read as a bug, so the order
        // is the label's and not the database's.
        val actions = listOf(
            SellerUiAction(id = "z", label = "Zweitens", value = "example.org"),
            SellerUiAction(id = "a", label = "Erstens", value = "example.org"),
        )
        assertEquals(listOf("a", "z"), actions.forLocation(ActionLocation.TOPBAR_PROFILE, isApproved = true, isDemo = false).map { it.id })
    }

    // ===== who sees it =====

    @Test
    fun `an everyone button is shown on both sides of approval`() {
        val a = listOf(SellerUiAction(id = "a", label = "Alle", value = "example.org"))
        assertEquals(1, a.forLocation(ActionLocation.TOPBAR_PROFILE, isApproved = true, isDemo = false).size)
        assertEquals(1, a.forLocation(ActionLocation.TOPBAR_PROFILE, isApproved = false, isDemo = true).size)
    }

    @Test
    fun `a demo-only button is hidden from an approved buyer`() {
        val a = listOf(
            SellerUiAction(id = "a", label = "Freischalten", value = "example.org", audience = ActionAudience.DEMO_ONLY)
        )
        assertEquals(1, a.forLocation(ActionLocation.TOPBAR_PROFILE, isApproved = false, isDemo = true).size)
        assertTrue(a.forLocation(ActionLocation.TOPBAR_PROFILE, isApproved = true, isDemo = false).isEmpty())
    }

    @Test
    fun `an approved-only button is hidden from a demo buyer`() {
        val a = listOf(
            SellerUiAction(id = "a", label = "Wochenliste", value = "example.org", audience = ActionAudience.APPROVED_ONLY)
        )
        assertEquals(1, a.forLocation(ActionLocation.TOPBAR_PROFILE, isApproved = true, isDemo = false).size)
        assertTrue(a.forLocation(ActionLocation.TOPBAR_PROFILE, isApproved = false, isDemo = true).isEmpty())
    }

    @Test
    fun `a narrowed button stays hidden while the status is still loading`() {
        // Both flags false is what "not known yet" looks like. Showing a button here
        // and removing it a moment later is the flash canContactSeller avoids.
        val a = listOf(
            SellerUiAction(id = "a", label = "Demo", value = "example.org", audience = ActionAudience.DEMO_ONLY),
            SellerUiAction(id = "b", label = "Zu", value = "example.org", audience = ActionAudience.APPROVED_ONLY),
            SellerUiAction(id = "c", label = "Alle", value = "example.org"),
        )
        assertEquals(
            listOf("c"),
            a.forLocation(ActionLocation.TOPBAR_PROFILE, isApproved = false, isDemo = false).map { it.id }
        )
    }

    @Test
    fun `an absent audience reads as everyone`() {
        // Records written before audiences existed were shown to everyone and go on
        // being shown to everyone.
        assertEquals(ActionAudience.EVERYONE, ActionAudience.stored(null))
        assertEquals(ActionAudience.DEMO_ONLY, ActionAudience.stored("DEMO_ONLY"))
    }

    @Test
    fun `an unknown audience skips the row`() {
        assertNull(ActionAudience.stored("SOME_FUTURE_GROUP"))
    }

    @Test
    fun `a hidden button does not use up a place`() {
        // The cap applies after the audience filter, or a seller targeting three
        // demo-only buttons would leave an approved buyer with nothing.
        val a = (1..3).map {
            SellerUiAction(id = "d$it", label = "Demo $it", value = "example.org", audience = ActionAudience.DEMO_ONLY)
        } + SellerUiAction(id = "z", label = "Alle", value = "example.org")
        assertEquals(
            listOf("z"),
            a.forLocation(ActionLocation.TOPBAR_PROFILE, isApproved = true, isDemo = false).map { it.id }
        )
    }

    @Test
    fun `a slot shows no more than it has room for`() {
        val actions = (1..10).map {
            SellerUiAction(id = "a$it", label = "Knopf $it", value = "example.org")
        }
        assertEquals(
            SellerUiAction.MAX_PER_LOCATION,
            actions.forLocation(ActionLocation.TOPBAR_PROFILE, isApproved = true, isDemo = false).size
        )
    }
}
