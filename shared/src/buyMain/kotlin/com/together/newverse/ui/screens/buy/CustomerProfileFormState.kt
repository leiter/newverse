package com.together.newverse.ui.screens.buy

import com.together.newverse.domain.model.BuyerProfile

/**
 * Form data for customer profile editing.
 */
data class ProfileFormData(
    val displayName: String = "",
    val email: String = "",
    val phone: String = ""
) {
    companion object {
        /**
         * Creates ProfileFormData from a BuyerProfile.
         */
        fun fromProfile(profile: BuyerProfile): ProfileFormData = ProfileFormData(
            displayName = profile.displayName,
            email = profile.emailAddress,
            phone = profile.telephoneNumber
        )
    }
}

/**
 * Validation logic for profile form fields.
 */
object ProfileValidation {

    // Field names for error mapping
    const val FIELD_DISPLAY_NAME = "displayName"
    const val FIELD_EMAIL = "email"
    const val FIELD_PHONE = "phone"

    /**
     * Validates all profile form fields and returns a map of field errors.
     * Empty map indicates all fields are valid.
     */
    fun validate(data: ProfileFormData): Map<String, String> {
        val errors = mutableMapOf<String, String>()

        if (!isValidEmail(data.email)) {
            errors[FIELD_EMAIL] = "email_format"
        }

        if (!hasValidPhoneChars(data.phone)) {
            errors[FIELD_PHONE] = "phone_invalid_chars"
        } else if (!isValidPhoneNumber(data.phone)) {
            errors[FIELD_PHONE] = "phone_format"
        }

        return errors
    }

    /**
     * Validates email format.
     * Empty email is considered valid (optional field).
     */
    fun isValidEmail(email: String): Boolean {
        if (email.isEmpty()) return true
        val emailRegex = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
        return emailRegex.matches(email)
    }

    /**
     * Validates phone number format.
     * Empty phone is considered valid (optional field).
     * Requires at least 10 digits after removing formatting characters.
     */
    fun isValidPhoneNumber(phone: String): Boolean {
        if (phone.isEmpty()) return true
        // Allow digits, spaces, +, -, (, )
        val cleanedPhone = phone.replace(Regex("[\\s+\\-()]"), "")
        return cleanedPhone.length >= 10 && cleanedPhone.all { it.isDigit() }
    }

    /**
     * Checks if phone number contains only valid characters.
     */
    fun hasValidPhoneChars(phone: String): Boolean {
        if (phone.isEmpty()) return true
        return phone.all { it.isDigit() || it.isWhitespace() || it == '+' || it == '-' || it == '(' || it == ')' }
    }
}
