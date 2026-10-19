package com.together.newverse.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SellerContactTest {

    // ===== The scheme comes from the channel, never from stored data =====

    @Test
    fun `phone strips everything a dialler cannot use`() {
        val contact = SellerContact(ContactChannel.PHONE, "030 - 2023 9477")
        assertEquals("tel:03020239477", contact.toUri())
    }

    @Test
    fun `a leading plus survives normalization`() {
        assertEquals("+493020239477", ContactChannel.PHONE.normalize("+49 (30) 2023 9477"))
    }

    @Test
    fun `web drops a scheme the seller typed so it is never doubled`() {
        val contact = SellerContact(ContactChannel.WEB, "http://example.org")
        assertEquals("https://example.org", contact.toUri())
    }

    @Test
    fun `instagram drops the at sign`() {
        assertEquals("https://instagram.com/stall", SellerContact(ContactChannel.INSTAGRAM, "@stall").toUri())
    }

    // ===== Unknown channels degrade rather than fail =====

    @Test
    fun `a channel written by a newer version reads back as null`() {
        assertNull(ContactChannel.fromStored("TELEPATHY"))
        assertNull(ContactChannel.fromStored(null))
        assertEquals(ContactChannel.EMAIL, ContactChannel.fromStored("EMAIL"))
    }

    // ===== Union, not override =====

    private val office = SellerContact(ContactChannel.PHONE, "03020239477", "Büro")
    private val mail = SellerContact(ContactChannel.EMAIL, "hallo@example.org")
    private val stallMobile = SellerContact(ContactChannel.PHONE, "01701234567", "Stand Kreuzberg")

    private val directory = SellerContactDirectory(
        sellerContacts = listOf(office, mail),
        marketContacts = mapOf("m1" to listOf(stallMobile)),
        marketNames = mapOf("m1" to "Kreuzberg")
    )

    @Test
    fun `a market's own number adds to the seller's rather than replacing it`() {
        val resolved = directory.resolveFor("m1")
        assertEquals(listOf(office, mail, stallMobile), resolved)
    }

    @Test
    fun `with no contextual market only the seller's contacts show`() {
        assertEquals(listOf(office, mail), directory.resolveFor(null))
    }

    @Test
    fun `an unknown market id falls back to the seller's contacts`() {
        assertEquals(listOf(office, mail), directory.resolveFor("nope"))
    }

    @Test
    fun `a market repeating the seller's number is not shown twice`() {
        val repeated = directory.copy(
            marketContacts = mapOf("m1" to listOf(SellerContact(ContactChannel.PHONE, "030 2023 9477", "Stand")))
        )
        // Same channel, same number once normalized: one row, the seller's.
        assertEquals(listOf(office, mail), repeated.resolveFor("m1"))
    }

    @Test
    fun `the same number under a different channel is a separate row`() {
        val whatsapp = SellerContact(ContactChannel.WHATSAPP, "03020239477")
        val withWhatsapp = directory.copy(marketContacts = mapOf("m1" to listOf(whatsapp)))
        assertEquals(listOf(office, mail, whatsapp), withWhatsapp.resolveFor("m1"))
    }

    @Test
    fun `blank values never reach the menu`() {
        val withBlank = SellerContactDirectory(
            sellerContacts = listOf(office, SellerContact(ContactChannel.EMAIL, "   "))
        )
        assertEquals(listOf(office), withBlank.resolveFor(null))
    }

    // ===== Emptiness drives whether the icon is drawn at all =====

    @Test
    fun `a directory of blanks counts as empty`() {
        val blanks = SellerContactDirectory(
            sellerContacts = listOf(SellerContact(ContactChannel.PHONE, "")),
            marketContacts = mapOf("m1" to listOf(SellerContact(ContactChannel.EMAIL, "")))
        )
        assertTrue(blanks.isEmpty)
    }

    @Test
    fun `a market-only contact makes the directory non-empty`() {
        val marketOnly = SellerContactDirectory(marketContacts = mapOf("m1" to listOf(stallMobile)))
        assertFalse(marketOnly.isEmpty)
    }

    // ===== The seller's own profile reads back the same shape =====

    @Test
    fun `a profile converts to the directory a buyer would load`() {
        val profile = SellerProfile(
            sellerId = "s1",
            contacts = listOf(office),
            markets = listOf(
                Market(id = "m1", name = "Kreuzberg", contacts = listOf(stallMobile)),
                Market(id = "m2", name = "Neukölln")
            )
        )
        val directory = profile.toContactDirectory()

        assertEquals(listOf(office), directory.sellerContacts)
        // Only markets that carry contacts take a row; names stay for every market.
        assertEquals(mapOf("m1" to listOf(stallMobile)), directory.marketContacts)
        assertEquals(mapOf("m1" to "Kreuzberg", "m2" to "Neukölln"), directory.marketNames)
    }
}
