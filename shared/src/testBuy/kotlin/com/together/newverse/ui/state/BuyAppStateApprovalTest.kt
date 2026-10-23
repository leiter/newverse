package com.together.newverse.ui.state

import com.together.newverse.domain.model.AccessStatus
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The asymmetry between labelling a buyer and trusting them.
 *
 * `isDemoMode` waits for the status so the UI does not flicker. `isApproved` never
 * assumes: an unknown status is not approval, and it is the one every write must ask.
 */
class BuyAppStateApprovalTest {

    private fun state(loaded: Boolean, status: AccessStatus) =
        BuyAppState(isAccessStatusLoaded = loaded, accessStatus = status)

    @Test
    fun `an unloaded status is not approval`() {
        // The regression. isDemoMode is false here — it waits for the load — so a write
        // keyed on `!isDemoMode` read this as approved and placed a binding order.
        val loading = state(loaded = false, status = AccessStatus.NONE)

        assertFalse(loading.isDemoMode, "nothing is labelled demo until the status lands")
        assertFalse(loading.isApproved, "but nothing is trusted as approved either")
    }

    @Test
    fun `an unloaded status is not approval even if it would turn out to be approved`() {
        assertFalse(state(loaded = false, status = AccessStatus.APPROVED).isApproved)
    }

    @Test
    fun `approval is granted only when loaded and APPROVED`() {
        assertTrue(state(loaded = true, status = AccessStatus.APPROVED).isApproved)
        assertFalse(state(loaded = true, status = AccessStatus.APPROVED).isDemoMode)
    }

    @Test
    fun `every other known status is demo and not approved`() {
        for (status in listOf(AccessStatus.NONE, AccessStatus.PENDING, AccessStatus.BLOCKED)) {
            val s = state(loaded = true, status = status)
            assertTrue(s.isDemoMode, "$status is demo mode")
            assertFalse(s.isApproved, "$status is not approval")
        }
    }

    @Test
    fun `the two are never both true`() {
        for (loaded in listOf(true, false)) {
            for (status in AccessStatus.entries) {
                val s = state(loaded, status)
                assertFalse(s.isDemoMode && s.isApproved, "loaded=$loaded status=$status")
            }
        }
    }
}
