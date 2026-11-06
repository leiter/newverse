package com.together.newverse.data.repository

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * "You may not read that" is how the rules say "there is nothing of yours there".
 *
 * The read rule on a single order is `data.child('buyerId').val() === auth.uid`, and the
 * level above it grants the seller alone. A node that does not exist therefore has no
 * `buyerId` to match, so the read is refused rather than answered empty — and a buyer
 * who looked in the wrong trunk was shown Firebase's own words about permissions for an
 * order that had simply never been there.
 *
 * `readsAsMissingOrder` is what turns that back into absence. It does not excuse looking
 * in the wrong trunk — `basketScreenResolveOrder` is the fix for that — it keeps a wrong
 * guess legible.
 */
class MissingOrderReadingTest {

    @Test
    fun `the emulator's wording reads as missing`() {
        assertTrue(Exception("PERMISSION_DENIED: Permission denied").readsAsMissingOrder())
    }

    @Test
    fun `the client SDK's wording reads as missing`() {
        assertTrue(Exception("Permission denied").readsAsMissingOrder())
        assertTrue(Exception("Client doesn't have permission to access the desired data.")
            .readsAsMissingOrder())
    }

    @Test
    fun `so does a bare denial`() {
        assertTrue(Exception("denied").readsAsMissingOrder())
    }

    @Test
    fun `a real failure is left alone`() {
        // Anything that is not the rules refusing has to keep its own message: a
        // network drop or a parse failure is not an order that moved.
        assertFalse(Exception("Network is unreachable").readsAsMissingOrder())
        assertFalse(Exception("Failed to parse order data").readsAsMissingOrder())
        assertFalse(Exception(ORDER_NOT_FOUND).readsAsMissingOrder())
    }

    @Test
    fun `a failure with nothing to say is not read as anything`() {
        assertFalse(Exception().readsAsMissingOrder())
    }
}
