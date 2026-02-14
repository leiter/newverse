package com.together.newverse.ui.screens.buy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.together.newverse.domain.model.BuyerProfile
import com.together.newverse.domain.repository.ProfileRepository
import com.together.newverse.ui.state.core.FormState
import com.together.newverse.ui.state.core.clearFieldError
import com.together.newverse.ui.state.core.formStateOf
import com.together.newverse.ui.state.core.reset
import com.together.newverse.ui.state.core.submitFailure
import com.together.newverse.ui.state.core.submitSuccess
import com.together.newverse.ui.state.core.submitting
import com.together.newverse.ui.state.core.updateField
import com.together.newverse.ui.state.core.withFieldErrors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for CustomerProfileScreen using FormState pattern.
 *
 * Manages profile form state including validation, submission, and cancellation.
 */
class CustomerProfileViewModel(
    private val profileRepository: ProfileRepository
) : ViewModel() {

    private val _formState = MutableStateFlow(formStateOf(ProfileFormData()))
    val formState: StateFlow<FormState<ProfileFormData>> = _formState.asStateFlow()

    private val _isEditing = MutableStateFlow(false)
    val isEditing: StateFlow<Boolean> = _isEditing.asStateFlow()

    private var currentProfile: BuyerProfile? = null

    /**
     * Initialize the form with data from the given profile.
     */
    fun initializeFromProfile(profile: BuyerProfile) {
        currentProfile = profile
        val formData = ProfileFormData.fromProfile(profile)
        _formState.value = formStateOf(formData)
    }

    /**
     * Start editing the profile.
     */
    fun startEditing() {
        _isEditing.value = true
    }

    /**
     * Cancel editing and reset the form to original values.
     */
    fun cancelEditing() {
        _formState.update { it.reset() }
        _isEditing.value = false
    }

    /**
     * Update the display name field.
     */
    fun onDisplayNameChange(value: String) {
        _formState.update { state ->
            state.updateField { data -> data.copy(displayName = value) }
                .clearFieldError(ProfileValidation.FIELD_DISPLAY_NAME)
        }
    }

    /**
     * Update the email field.
     */
    fun onEmailChange(value: String) {
        _formState.update { state ->
            state.updateField { data -> data.copy(email = value) }
                .clearFieldError(ProfileValidation.FIELD_EMAIL)
        }
    }

    /**
     * Update the phone field.
     */
    fun onPhoneChange(value: String) {
        _formState.update { state ->
            state.updateField { data -> data.copy(phone = value) }
                .clearFieldError(ProfileValidation.FIELD_PHONE)
        }
    }

    /**
     * Save the profile.
     * Validates the form data and submits to the repository if valid.
     */
    fun saveProfile() {
        val currentData = _formState.value.data
        val errors = ProfileValidation.validate(currentData)

        if (errors.isNotEmpty()) {
            _formState.update { it.withFieldErrors(errors) }
            return
        }

        viewModelScope.launch {
            _formState.update { it.submitting() }

            val profile = currentProfile
            if (profile == null) {
                _formState.update { it.submitFailure("No profile loaded") }
                return@launch
            }

            val updatedProfile = profile.copy(
                displayName = currentData.displayName,
                emailAddress = currentData.email,
                telephoneNumber = currentData.phone
            )

            profileRepository.saveBuyerProfile(updatedProfile)
                .onSuccess {
                    currentProfile = it
                    _formState.update { state -> state.submitSuccess() }
                    _isEditing.value = false
                }
                .onFailure { error ->
                    _formState.update { state ->
                        state.submitFailure(error.message ?: "Failed to save profile")
                    }
                }
        }
    }

    /**
     * Check if the email field has an error.
     */
    fun isEmailValid(): Boolean {
        val email = _formState.value.data.email
        return ProfileValidation.isValidEmail(email)
    }

    /**
     * Check if the phone field has valid characters.
     */
    fun hasValidPhoneChars(): Boolean {
        val phone = _formState.value.data.phone
        return ProfileValidation.hasValidPhoneChars(phone)
    }

    /**
     * Check if the phone field is valid.
     */
    fun isPhoneValid(): Boolean {
        val phone = _formState.value.data.phone
        return ProfileValidation.hasValidPhoneChars(phone) && ProfileValidation.isValidPhoneNumber(phone)
    }

    /**
     * Check if the form can be saved (all fields are valid).
     */
    fun canSave(): Boolean {
        return isEmailValid() && isPhoneValid()
    }
}
