package com.together.newverse.ui.state

import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.change_password_failed
import newverse.shared.generated.resources.change_password_no_password_account
import newverse.shared.generated.resources.change_password_requires_recent_login
import newverse.shared.generated.resources.error_no_internet
import newverse.shared.generated.resources.error_too_many_attempts
import newverse.shared.generated.resources.error_weak_password
import newverse.shared.generated.resources.error_wrong_password
import org.jetbrains.compose.resources.StringResource

/**
 * Which message a failure from `reauthenticateWithPassword` or
 * `updatePassword` should become.
 *
 * `GitLiveAuthRepository` returns hardcoded English, as it does for every other
 * auth operation, so this is where that becomes something a user can read. One
 * function for both flavors: a buyer and the seller should not be told
 * different things about the same Firebase error.
 *
 * Returns the resource, not the text. Resolving it needs a resource
 * environment, which is the dialog's job and not the ViewModel's.
 */
fun changePasswordError(error: Throwable): StringResource {
    val raw = error.message ?: return Res.string.change_password_failed
    return when {
        raw.contains("Incorrect password", true) -> Res.string.error_wrong_password
        raw.contains("too weak", true) -> Res.string.error_weak_password
        raw.contains("at least 6", true) -> Res.string.error_weak_password
        raw.contains("no password", true) -> Res.string.change_password_no_password_account
        raw.contains("sign in again", true) -> Res.string.change_password_requires_recent_login
        raw.contains("too many", true) -> Res.string.error_too_many_attempts
        raw.contains("Network", true) ||
            raw.contains("Unable to resolve host", true) ||
            raw.contains("No address associated", true) ||
            raw.contains("failed to connect", true) ||
            raw.contains("timeout", true) ||
            raw.contains("UnknownHostException", true) -> Res.string.error_no_internet
        else -> Res.string.change_password_failed
    }
}
