package com.together.newverse.domain.model

/**
 * Which required buyer profile fields are still empty.
 *
 * A complete profile is the precondition for leaving demo mode: both
 * `requestAccess()` and `connectWithToken()` refuse to run while anything here
 * is missing. The UI derives its prompts from the same value, so what the buyer
 * is told always matches what the gate checks.
 *
 * **The delivery address is deliberately not here.** Asking for a street and house
 * number before the seller has even approved the buyer collects an address from
 * everyone in order to serve the few who want it delivered — and it blocked buyers who
 * only ever intended to collect at the stall, since `BuyerProfile.isSelfPickup`
 * defaults to `false` and so every untouched profile read as needing one. The address
 * is required at the one point it is actually used: placing or editing a *delivery*
 * order, where `DeliveryAddress.isComplete` is the gate and `refuseImpossibleDelivery`
 * enforces it. The profile still holds one, as the thing an order is autofilled from
 * and saved back to, but holding none costs the buyer nothing until they ask to be
 * delivered to.
 */
data class MissingProfileData(
    val name: Boolean = false,
    val pickupTime: Boolean = false
) {
    val isComplete: Boolean get() = !name && !pickupTime
    val allMissing: Boolean get() = name && pickupTime
}

/**
 * The single completeness rule. A profile that has not loaded yet counts as missing
 * everything; the delivery address is not part of this judgement — see
 * [MissingProfileData].
 */
fun BuyerProfile?.missingProfileData(): MissingProfileData = MissingProfileData(
    name = this?.displayName.isNullOrBlank(),
    pickupTime = this?.defaultPickUpTime.isNullOrBlank()
)
