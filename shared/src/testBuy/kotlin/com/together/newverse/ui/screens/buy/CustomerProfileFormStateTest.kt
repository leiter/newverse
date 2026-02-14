package com.together.newverse.ui.screens.buy

import com.together.newverse.domain.model.BuyerProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CustomerProfileFormStateTest {

    // ===== ProfileFormData Tests =====

    @Test
    fun `ProfileFormData default values are empty`() {
        val data = ProfileFormData()

        assertEquals("", data.displayName)
        assertEquals("", data.email)
        assertEquals("", data.phone)
    }

    @Test
    fun `ProfileFormData fromProfile extracts correct values`() {
        val profile = BuyerProfile(
            id = "user_123",
            displayName = "John Doe",
            emailAddress = "john@example.com",
            telephoneNumber = "+1 234 567 8901"
        )

        val formData = ProfileFormData.fromProfile(profile)

        assertEquals("John Doe", formData.displayName)
        assertEquals("john@example.com", formData.email)
        assertEquals("+1 234 567 8901", formData.phone)
    }

    @Test
    fun `ProfileFormData fromProfile handles empty values`() {
        val profile = BuyerProfile(id = "user_123")

        val formData = ProfileFormData.fromProfile(profile)

        assertEquals("", formData.displayName)
        assertEquals("", formData.email)
        assertEquals("", formData.phone)
    }

    // ===== ProfileValidation.isValidEmail Tests =====

    @Test
    fun `isValidEmail returns true for empty string`() {
        assertTrue(ProfileValidation.isValidEmail(""))
    }

    @Test
    fun `isValidEmail returns true for valid email`() {
        assertTrue(ProfileValidation.isValidEmail("test@example.com"))
        assertTrue(ProfileValidation.isValidEmail("user.name@domain.org"))
        assertTrue(ProfileValidation.isValidEmail("user+tag@example.co.uk"))
        assertTrue(ProfileValidation.isValidEmail("name123@test.io"))
    }

    @Test
    fun `isValidEmail returns false for invalid email`() {
        assertFalse(ProfileValidation.isValidEmail("invalid"))
        assertFalse(ProfileValidation.isValidEmail("missing@domain"))
        assertFalse(ProfileValidation.isValidEmail("@nodomain.com"))
        assertFalse(ProfileValidation.isValidEmail("spaces in@email.com"))
        assertFalse(ProfileValidation.isValidEmail("no.at" + "sign.com"))
    }

    // ===== ProfileValidation.isValidPhoneNumber Tests =====

    @Test
    fun `isValidPhoneNumber returns true for empty string`() {
        assertTrue(ProfileValidation.isValidPhoneNumber(""))
    }

    @Test
    fun `isValidPhoneNumber returns true for valid phone numbers`() {
        assertTrue(ProfileValidation.isValidPhoneNumber("1234567890"))
        assertTrue(ProfileValidation.isValidPhoneNumber("+1 234 567 8901"))
        assertTrue(ProfileValidation.isValidPhoneNumber("(123) 456-7890"))
        assertTrue(ProfileValidation.isValidPhoneNumber("+49 123 456 7890"))
    }

    @Test
    fun `isValidPhoneNumber returns false for too short numbers`() {
        assertFalse(ProfileValidation.isValidPhoneNumber("12345"))
        assertFalse(ProfileValidation.isValidPhoneNumber("123456789"))
    }

    @Test
    fun `isValidPhoneNumber returns false for non-digit content`() {
        assertFalse(ProfileValidation.isValidPhoneNumber("abc1234567890"))
        assertFalse(ProfileValidation.isValidPhoneNumber("123-abc-4567"))
    }

    // ===== ProfileValidation.hasValidPhoneChars Tests =====

    @Test
    fun `hasValidPhoneChars returns true for empty string`() {
        assertTrue(ProfileValidation.hasValidPhoneChars(""))
    }

    @Test
    fun `hasValidPhoneChars returns true for valid characters`() {
        assertTrue(ProfileValidation.hasValidPhoneChars("1234567890"))
        assertTrue(ProfileValidation.hasValidPhoneChars("+1 234 567 8901"))
        assertTrue(ProfileValidation.hasValidPhoneChars("(123) 456-7890"))
        assertTrue(ProfileValidation.hasValidPhoneChars("  123  "))
    }

    @Test
    fun `hasValidPhoneChars returns false for invalid characters`() {
        assertFalse(ProfileValidation.hasValidPhoneChars("abc"))
        assertFalse(ProfileValidation.hasValidPhoneChars("123.456"))
        assertFalse(ProfileValidation.hasValidPhoneChars("123#456"))
        assertFalse(ProfileValidation.hasValidPhoneChars("phone: 123"))
    }

    // ===== ProfileValidation.validate Tests =====

    @Test
    fun `validate returns empty map for valid data`() {
        val data = ProfileFormData(
            displayName = "John Doe",
            email = "john@example.com",
            phone = "+1 234 567 8901"
        )

        val errors = ProfileValidation.validate(data)

        assertTrue(errors.isEmpty())
    }

    @Test
    fun `validate returns empty map for empty optional fields`() {
        val data = ProfileFormData(
            displayName = "John Doe",
            email = "",
            phone = ""
        )

        val errors = ProfileValidation.validate(data)

        assertTrue(errors.isEmpty())
    }

    @Test
    fun `validate returns email error for invalid email`() {
        val data = ProfileFormData(
            displayName = "John Doe",
            email = "invalid-email",
            phone = ""
        )

        val errors = ProfileValidation.validate(data)

        assertEquals(1, errors.size)
        assertEquals("email_format", errors[ProfileValidation.FIELD_EMAIL])
    }

    @Test
    fun `validate returns phone error for invalid characters`() {
        val data = ProfileFormData(
            displayName = "John Doe",
            email = "",
            phone = "abc123"
        )

        val errors = ProfileValidation.validate(data)

        assertEquals(1, errors.size)
        assertEquals("phone_invalid_chars", errors[ProfileValidation.FIELD_PHONE])
    }

    @Test
    fun `validate returns phone error for too short number`() {
        val data = ProfileFormData(
            displayName = "John Doe",
            email = "",
            phone = "12345"
        )

        val errors = ProfileValidation.validate(data)

        assertEquals(1, errors.size)
        assertEquals("phone_format", errors[ProfileValidation.FIELD_PHONE])
    }

    @Test
    fun `validate returns multiple errors for multiple invalid fields`() {
        val data = ProfileFormData(
            displayName = "John Doe",
            email = "invalid-email",
            phone = "abc"
        )

        val errors = ProfileValidation.validate(data)

        assertEquals(2, errors.size)
        assertEquals("email_format", errors[ProfileValidation.FIELD_EMAIL])
        assertEquals("phone_invalid_chars", errors[ProfileValidation.FIELD_PHONE])
    }

    @Test
    fun `validate prioritizes invalid chars over format for phone`() {
        val data = ProfileFormData(
            displayName = "John",
            email = "",
            phone = "abc" // Both invalid chars AND too short
        )

        val errors = ProfileValidation.validate(data)

        // Should report invalid chars, not format error
        assertEquals("phone_invalid_chars", errors[ProfileValidation.FIELD_PHONE])
    }

    // ===== Field Name Constants Tests =====

    @Test
    fun `field name constants have expected values`() {
        assertEquals("displayName", ProfileValidation.FIELD_DISPLAY_NAME)
        assertEquals("email", ProfileValidation.FIELD_EMAIL)
        assertEquals("phone", ProfileValidation.FIELD_PHONE)
    }
}
