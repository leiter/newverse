package com.together.newverse.ui.state.core

/**
 * Generic form state management for handling form data, validation, and submission.
 *
 * @param T The type of data held in the form
 */
data class FormState<T>(
    /**
     * The current form data.
     */
    val data: T,

    /**
     * Whether the form is currently being submitted.
     */
    val isSubmitting: Boolean = false,

    /**
     * Global error message for the form submission (not field-specific).
     */
    val submitError: String? = null,

    /**
     * Field-level validation errors. Keys are field names, values are error messages.
     */
    val fieldErrors: Map<String, String> = emptyMap(),

    /**
     * Whether the form has been modified since it was initialized or last saved.
     */
    val isDirty: Boolean = false,

    /**
     * The original data when the form was initialized, for reset functionality.
     */
    val originalData: T? = null,

    /**
     * Whether the form has been submitted at least once (for showing errors).
     */
    val hasAttemptedSubmit: Boolean = false
) {
    /**
     * Returns true if the form has no validation errors.
     */
    val isValid: Boolean get() = fieldErrors.isEmpty() && submitError == null

    /**
     * Returns true if the form can be submitted (valid and not currently submitting).
     */
    val canSubmit: Boolean get() = isValid && !isSubmitting

    /**
     * Returns true if there's any error (field or submit level).
     */
    val hasErrors: Boolean get() = fieldErrors.isNotEmpty() || submitError != null

    /**
     * Gets the error message for a specific field.
     */
    fun getFieldError(fieldName: String): String? = fieldErrors[fieldName]

    /**
     * Returns true if a specific field has an error.
     */
    fun hasFieldError(fieldName: String): Boolean = fieldErrors.containsKey(fieldName)
}

/**
 * Creates a new FormState initialized with the given data.
 */
fun <T> formStateOf(data: T): FormState<T> = FormState(
    data = data,
    originalData = data
)

/**
 * Updates the form data while marking the form as dirty if the data changed.
 */
fun <T> FormState<T>.updateData(newData: T): FormState<T> = copy(
    data = newData,
    isDirty = newData != originalData
)

/**
 * Updates a specific field in the form data using a transform function.
 */
inline fun <T> FormState<T>.updateField(transform: (T) -> T): FormState<T> {
    val newData = transform(data)
    return copy(
        data = newData,
        isDirty = newData != originalData
    )
}

/**
 * Sets validation errors for the form.
 */
fun <T> FormState<T>.withFieldErrors(errors: Map<String, String>): FormState<T> = copy(
    fieldErrors = errors
)

/**
 * Sets a single field error.
 */
fun <T> FormState<T>.withFieldError(fieldName: String, message: String): FormState<T> = copy(
    fieldErrors = fieldErrors + (fieldName to message)
)

/**
 * Clears a specific field error.
 */
fun <T> FormState<T>.clearFieldError(fieldName: String): FormState<T> = copy(
    fieldErrors = fieldErrors - fieldName
)

/**
 * Clears all field errors.
 */
fun <T> FormState<T>.clearFieldErrors(): FormState<T> = copy(
    fieldErrors = emptyMap()
)

/**
 * Marks the form as submitting.
 */
fun <T> FormState<T>.submitting(): FormState<T> = copy(
    isSubmitting = true,
    submitError = null,
    hasAttemptedSubmit = true
)

/**
 * Marks the form submission as successful and resets dirty state.
 */
fun <T> FormState<T>.submitSuccess(): FormState<T> = copy(
    isSubmitting = false,
    submitError = null,
    isDirty = false,
    originalData = data
)

/**
 * Marks the form submission as failed with an error message.
 */
fun <T> FormState<T>.submitFailure(error: String): FormState<T> = copy(
    isSubmitting = false,
    submitError = error
)

/**
 * Resets the form to its original state.
 */
fun <T> FormState<T>.reset(): FormState<T> = copy(
    data = originalData ?: data,
    isSubmitting = false,
    submitError = null,
    fieldErrors = emptyMap(),
    isDirty = false,
    hasAttemptedSubmit = false
)

/**
 * Validates the form using the provided validator function.
 * The validator should return a map of field names to error messages.
 */
inline fun <T> FormState<T>.validate(validator: (T) -> Map<String, String>): FormState<T> {
    val errors = validator(data)
    return copy(fieldErrors = errors)
}
