package com.together.newverse.ui.state

/**
 * State for authentication screens (Login, Register, ForcedLogin)
 *
 * This is extracted from UnifiedAppState to be the minimal contract
 * needed by common screens in commonMain. Common screens should
 * depend only on this type, not on UnifiedAppState or flavor-specific state.
 */
data class AuthScreenState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSuccess: Boolean = false,
    val mode: AuthMode = AuthMode.LOGIN,
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val name: String = "",
    val validationErrors: Map<String, String> = emptyMap(),
    val passwordResetSent: Boolean = false,
    val showPasswordResetDialog: Boolean = false
)

enum class AuthMode {
    LOGIN,
    REGISTER,
    FORGOT_PASSWORD
}
