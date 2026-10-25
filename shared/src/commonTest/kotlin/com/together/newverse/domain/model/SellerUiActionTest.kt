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

    // ===== the home-title announcement =====

    private fun announcement(text: String, id: String = "n1") = SellerUiAction(
        id = id,
        location = ActionLocation.TOPBAR_HOME_TITLE,
        kind = ActionKind.ANNOUNCE,
        value = text,
    ).readyToStore()

    @Test
    fun `an announcement keeps the seller's sentence intact`() {
        // The URL normalizer would eat the colon and the leading words. Nothing about
        // an announcement is ever handed to a URI handler, so nothing is stripped.
        val text = "Heute ab 9: Bodenschätze aus der Region"
        assertEquals(text, announcement(text).value)
        assertEquals("", ActionKind.ANNOUNCE.scheme)
    }

    @Test
    fun `an announcement is labelled by its opening words`() {
        // The rules require a non-empty label and the seller is not asked for one, so
        // it is derived. Buyers never see it; it is what the row is recognised by.
        val stored = announcement("Heute ab 9 Uhr gibt es frische Bodenschätze")
        assertEquals("Heute ab 9 Uhr gibt es", stored.label)
        assertTrue(stored.label.length <= SellerUiAction.MAX_LABEL_LENGTH)
        assertTrue(stored.isUsable)
    }

    @Test
    fun `an announcement with no text is not shown`() {
        assertFalse(announcement("").isUsable)
    }

    @Test
    fun `the slot decides the kind`() {
        // A row saved against the title slot is an ANNOUNCE whatever the caller said,
        // and one saved against a button slot is an OPEN_URL.
        val titled = SellerUiAction(
            id = "n1",
            location = ActionLocation.TOPBAR_HOME_TITLE,
            kind = ActionKind.OPEN_URL,
            value = "Frische Ware",
        ).readyToStore()
        assertEquals(ActionKind.ANNOUNCE, titled.kind)

        val button = SellerUiAction(
            id = "a1",
            location = ActionLocation.TOPBAR_HOME,
            kind = ActionKind.ANNOUNCE,
            label = "Wünsche",
            value = "example.org",
        ).readyToStore()
        assertEquals(ActionKind.OPEN_URL, button.kind)
    }

    @Test
    fun `a kind that does not belong in its slot is not shown`() {
        // Neither pairing has a sensible rendering: an ANNOUNCE in a button slot has
        // nothing to open, an OPEN_URL in the title has no button to be tapped.
        assertFalse(
            SellerUiAction(
                id = "a1",
                location = ActionLocation.TOPBAR_HOME,
                kind = ActionKind.ANNOUNCE,
                label = "Wünsche",
                value = "example.org",
            ).isUsable
        )
        assertFalse(
            SellerUiAction(
                id = "n1",
                location = ActionLocation.TOPBAR_HOME_TITLE,
                kind = ActionKind.OPEN_URL,
                label = "Neu",
                value = "example.org",
            ).isUsable
        )
    }

    @Test
    fun `the title slot holds exactly one`() {
        assertEquals(1, ActionLocation.TOPBAR_HOME_TITLE.maxActions)
        assertEquals(SellerUiAction.MAX_PER_LOCATION, ActionLocation.TOPBAR_HOME.maxActions)
    }

    @Test
    fun `an announcement is not a button and never renders as one`() {
        // forLocation is the button path. A title row must not reach it even when
        // asked for by name, or it would be drawn as a TextButton labelled with the
        // first few words of a sentence.
        val rows = listOf(announcement("Heute ab 9: Bodenschätze"))
        assertTrue(
            rows.forLocation(ActionLocation.TOPBAR_HOME, isApproved = true, isDemo = false).isEmpty()
        )
    }

    @Test
    fun `the lowest id wins when a seller has published two`() {
        // Deterministic rather than whichever RTDB iterated first: two buyers must
        // not see different announcements, and one buyer must not see them alternate.
        val rows = listOf(announcement("Zweite", id = "n2"), announcement("Erste", id = "n1"))
        val picked = rows.announcementFor(
            ActionLocation.TOPBAR_HOME_TITLE,
            isApproved = true,
            isDemo = false,
        )
        assertEquals("n1", picked?.id)
    }

    @Test
    fun `a narrowed announcement waits for the access status`() {
        // Both flags false means "not loaded yet". Showing it then and taking it away
        // a moment later is the flash canContactSeller exists to avoid.
        val rows = listOf(
            announcement("Nur für Freigeschaltete").copy(audience = ActionAudience.APPROVED_ONLY)
        )
        assertNull(
            rows.announcementFor(ActionLocation.TOPBAR_HOME_TITLE, isApproved = false, isDemo = false)
        )
        assertEquals(
            "n1",
            rows.announcementFor(ActionLocation.TOPBAR_HOME_TITLE, isApproved = true, isDemo = false)?.id
        )
    }

    // ===== dismissal =====

    @Test
    fun `re-wording an announcement brings it back`() {
        // The point of hashing the text into the key. Keyed on the id alone, a seller
        // correcting the wording in place would never reach a buyer who had dismissed
        // the previous version.
        val first = announcement("Heute ab 9 Uhr")
        val corrected = first.copy(value = "Heute ab 10 Uhr")
        assertTrue(first.dismissalKey != corrected.dismissalKey)
    }

    @Test
    fun `the same announcement stays dismissed`() {
        val read = announcement("Heute ab 9 Uhr")
        val again = announcement("Heute ab 9 Uhr")
        assertEquals(read.dismissalKey, again.dismissalKey)
    }

    @Test
    fun `two sellers' identical announcements are dismissed separately`() {
        // Hence the id in the key as well as the text.
        assertTrue(
            announcement("Frische Ware", id = "n1").dismissalKey !=
                announcement("Frische Ware", id = "n2").dismissalKey
        )
    }

    @Test
    fun `a dismissal key holds no newline`() {
        // Platform storage joins the keys with newlines, so one inside a key would
        // split it in two and silently un-dismiss everything after it.
        val key = announcement("Zeile eins\nZeile zwei").dismissalKey
        assertFalse(key.contains("\n"))
    }
}
