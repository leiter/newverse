package com.together.newverse.domain.model

/**
 * Which required buyer profile fields are still empty.
 *
 * A complete profile is the precondition for leaving demo mode: both
 * `requestAccess()` and `connectWithToken()` refuse to run while anything here
 * is missing. The UI derives its prompts from the same value, so what the buyer
 * is told always matches what the gate checks.
 */
data class MissingProfileData(
    val name: Boolean = false,
    val pickupTime: Boolean = false,
    val street: Boolean = false,     // only relevant if !isSelfPickup
    val houseNumber: Boolean = false  // only relevant if !isSelfPickup
) {
    val isComplete: Boolean get() = !name && !pickupTime && !street && !houseNumber
    val allMissing: Boolean get() = name && pickupTime && (street || houseNumber)
}

/**
 * The single completeness rule. A self-pickup buyer needs no delivery address,
 * and a profile that has not loaded yet counts as missing everything.
 */
fun BuyerProfile?.missingProfileData(): MissingProfileData {
    val isSelfPickup = this?.isSelfPickup ?: false
    return MissingProfileData(
        name = this?.displayName.isNullOrBlank(),
        pickupTime = this?.defaultPickUpTime.isNullOrBlank(),
        street = if (isSelfPickup) false else this?.street.isNullOrBlank(),
        houseNumber = if (isSelfPickup) false else this?.houseNumber.isNullOrBlank()
    )
}
