package com.together.newverse.data.config

/**
 * Platform-specific storage for an invite token a buyer has received but not yet
 * redeemed — from a QR code, a link, or an invitation.
 *
 * The token is a bearer ticket, not an identity: once redeemed it is cleared, and
 * the buyer is thereafter identified to the seller by their Firebase auth uid. It
 * is held on the device only so redemption can be retried if the buyer has to
 * complete their profile first, or the app is killed mid-flow.
 *
 * Per-user storage: each userId gets their own prefs container.
 * Android: SharedPreferences file `newverse_user_<userId>`, iOS: NSUserDefaults suite.
 */
expect class PendingInviteTokenStorage {
    fun get(): String?
    fun set(token: String)
    fun clear()

    fun setActiveUserId(userId: String)
    fun clearActiveUserId()
    fun renameUserId(fromId: String, toId: String)
}
