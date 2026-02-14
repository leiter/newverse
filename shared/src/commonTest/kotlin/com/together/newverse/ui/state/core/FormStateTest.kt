package com.together.newverse.ui.state.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for FormState and its extension functions
 */
class FormStateTest {

    // Sample data class for testing
    data class TestFormData(
        val name: String = "",
        val email: String = "",
        val age: Int = 0
    )

    // ===== Initial State Tests =====

    @Test
    fun `formStateOf creates form with initial data`() {
        val data = TestFormData(name = "John", email = "john@example.com")
        val state = formStateOf(data)

        assertEquals(data, state.data)
        assertEquals(data, state.originalData)
        assertFalse(state.isSubmitting)
        assertFalse(state.isDirty)
        assertFalse(state.hasAttemptedSubmit)
        assertNull(state.submitError)
        assertTrue(state.fieldErrors.isEmpty())
    }

    @Test
    fun `new FormState is valid by default`() {
        val state = formStateOf(TestFormData())

        assertTrue(state.isValid)
        assertTrue(state.canSubmit)
        assertFalse(state.hasErrors)
    }

    // ===== updateData Tests =====

    @Test
    fun `updateData changes data and marks dirty`() {
        val original = TestFormData(name = "John")
        val state = formStateOf(original)

        val updated = state.updateData(TestFormData(name = "Jane"))

        assertEquals("Jane", updated.data.name)
        assertTrue(updated.isDirty)
    }

    @Test
    fun `updateData to same value keeps not dirty`() {
        val original = TestFormData(name = "John")
        val state = formStateOf(original)

        val updated = state.updateData(TestFormData(name = "John"))

        assertFalse(updated.isDirty)
    }

    // ===== updateField Tests =====

    @Test
    fun `updateField transforms data correctly`() {
        val state = formStateOf(TestFormData(name = "John", age = 25))

        val updated = state.updateField { it.copy(age = 26) }

        assertEquals(26, updated.data.age)
        assertEquals("John", updated.data.name)
        assertTrue(updated.isDirty)
    }

    // ===== Field Error Tests =====

    @Test
    fun `withFieldError adds error for field`() {
        val state = formStateOf(TestFormData())

        val withError = state.withFieldError("email", "Invalid email format")

        assertEquals("Invalid email format", withError.getFieldError("email"))
        assertTrue(withError.hasFieldError("email"))
        assertTrue(withError.hasErrors)
        assertFalse(withError.isValid)
    }

    @Test
    fun `withFieldErrors sets multiple errors`() {
        val state = formStateOf(TestFormData())
        val errors = mapOf(
            "name" to "Name is required",
            "email" to "Email is required"
        )

        val withErrors = state.withFieldErrors(errors)

        assertEquals("Name is required", withErrors.getFieldError("name"))
        assertEquals("Email is required", withErrors.getFieldError("email"))
        assertEquals(2, withErrors.fieldErrors.size)
    }

    @Test
    fun `clearFieldError removes specific error`() {
        val state = formStateOf(TestFormData())
            .withFieldError("name", "Error 1")
            .withFieldError("email", "Error 2")

        val cleared = state.clearFieldError("name")

        assertFalse(cleared.hasFieldError("name"))
        assertTrue(cleared.hasFieldError("email"))
    }

    @Test
    fun `clearFieldErrors removes all errors`() {
        val state = formStateOf(TestFormData())
            .withFieldError("name", "Error 1")
            .withFieldError("email", "Error 2")

        val cleared = state.clearFieldErrors()

        assertTrue(cleared.fieldErrors.isEmpty())
        assertTrue(cleared.isValid)
    }

    // ===== Submission Tests =====

    @Test
    fun `submitting sets isSubmitting and hasAttemptedSubmit`() {
        val state = formStateOf(TestFormData())

        val submitting = state.submitting()

        assertTrue(submitting.isSubmitting)
        assertTrue(submitting.hasAttemptedSubmit)
        assertNull(submitting.submitError)
        assertFalse(submitting.canSubmit) // Can't submit while submitting
    }

    @Test
    fun `submitSuccess clears submitting and resets dirty`() {
        val original = TestFormData(name = "John")
        val state = formStateOf(original)
            .updateField { it.copy(name = "Jane") }
            .submitting()

        val success = state.submitSuccess()

        assertFalse(success.isSubmitting)
        assertFalse(success.isDirty)
        assertNull(success.submitError)
        assertEquals("Jane", success.originalData?.name) // Original updated to current
    }

    @Test
    fun `submitFailure sets error and clears submitting`() {
        val state = formStateOf(TestFormData()).submitting()

        val failed = state.submitFailure("Server error")

        assertFalse(failed.isSubmitting)
        assertEquals("Server error", failed.submitError)
        assertTrue(failed.hasErrors)
        assertFalse(failed.isValid)
    }

    // ===== Reset Tests =====

    @Test
    fun `reset restores original data and clears state`() {
        val original = TestFormData(name = "John")
        val state = formStateOf(original)
            .updateField { it.copy(name = "Jane") }
            .withFieldError("name", "Error")
            .submitting()
            .submitFailure("Failed")

        val reset = state.reset()

        assertEquals("John", reset.data.name)
        assertFalse(reset.isSubmitting)
        assertNull(reset.submitError)
        assertTrue(reset.fieldErrors.isEmpty())
        assertFalse(reset.isDirty)
        assertFalse(reset.hasAttemptedSubmit)
    }

    // ===== Validate Tests =====

    @Test
    fun `validate applies validator function`() {
        val state = formStateOf(TestFormData(name = "", email = "invalid"))

        val validated = state.validate { data ->
            buildMap {
                if (data.name.isBlank()) put("name", "Name is required")
                if (!data.email.contains("@")) put("email", "Invalid email")
            }
        }

        assertEquals("Name is required", validated.getFieldError("name"))
        assertEquals("Invalid email", validated.getFieldError("email"))
    }

    @Test
    fun `validate returns empty map for valid data`() {
        val state = formStateOf(TestFormData(name = "John", email = "john@example.com"))

        val validated = state.validate { data ->
            buildMap {
                if (data.name.isBlank()) put("name", "Name is required")
                if (!data.email.contains("@")) put("email", "Invalid email")
            }
        }

        assertTrue(validated.fieldErrors.isEmpty())
        assertTrue(validated.isValid)
    }

    // ===== Computed Properties Tests =====

    @Test
    fun `canSubmit is false when submitting`() {
        val state = formStateOf(TestFormData()).submitting()
        assertFalse(state.canSubmit)
    }

    @Test
    fun `canSubmit is false when has errors`() {
        val state = formStateOf(TestFormData()).withFieldError("name", "Error")
        assertFalse(state.canSubmit)
    }

    @Test
    fun `canSubmit is true when valid and not submitting`() {
        val state = formStateOf(TestFormData())
        assertTrue(state.canSubmit)
    }
}
