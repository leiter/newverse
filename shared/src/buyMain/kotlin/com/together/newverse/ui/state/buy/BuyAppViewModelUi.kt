package com.together.newverse.ui.state.buy

import androidx.lifecycle.viewModelScope
import com.together.newverse.domain.model.ActionLocation
import com.together.newverse.domain.model.ActionPersistence
import com.together.newverse.domain.model.SellerInboxMessage
import com.together.newverse.domain.model.SellerUiAction
import com.together.newverse.ui.state.AuthMode
import com.together.newverse.ui.state.BuyAppViewModel
import com.together.newverse.ui.state.BottomSheetState
import com.together.newverse.ui.state.DialogState
import com.together.newverse.ui.state.SnackbarState
import com.together.newverse.ui.state.SnackbarType
import com.together.newverse.util.Log
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.quick_message_failed
import newverse.shared.generated.resources.quick_message_sent
import org.jetbrains.compose.resources.getString

/**
 * UI management extension functions for BuyAppViewModel
 *
 * Handles snackbars, dialogs, bottom sheets, and refresh state.
 *
 * Extracted functions:
 * - showSnackbar
 * - hideSnackbar
 * - showDialog
 * - hideDialog
 * - showBottomSheet
 * - hideBottomSheet
 * - setRefreshing
 * - showPasswordResetDialog
 * - hidePasswordResetDialog
 */

internal fun BuyAppViewModel.showSnackBar(message: String, type: SnackbarType) {
    _state.update { current ->
        current.copy(
            ui = current.ui.copy(
                snackbar = SnackbarState(message = message, type = type)
            )
        )
    }
}

internal fun BuyAppViewModel.hideSnackBar() {
    _state.update { current ->
        current.copy(
            ui = current.ui.copy(snackbar = null)
        )
    }
}

internal fun BuyAppViewModel.showDialog(dialog: DialogState) {
    _state.update { current ->
        current.copy(
            ui = current.ui.copy(dialog = dialog)
        )
    }
}

internal fun BuyAppViewModel.hideDialog() {
    _state.update { current ->
        current.copy(
            ui = current.ui.copy(dialog = null)
        )
    }
}

internal fun BuyAppViewModel.showBottomSheet(sheet: BottomSheetState) {
    _state.update { current ->
        current.copy(
            ui = current.ui.copy(bottomSheet = sheet)
        )
    }
}

internal fun BuyAppViewModel.hideBottomSheet() {
    _state.update { current ->
        current.copy(
            ui = current.ui.copy(bottomSheet = null)
        )
    }
}

internal fun BuyAppViewModel.setRefreshing(isRefreshing: Boolean) {
    _state.update { current ->
        current.copy(
            ui = current.ui.copy(isRefreshing = isRefreshing)
        )
    }
}

/**
 * Show password reset dialog
 */
internal fun BuyAppViewModel.showPasswordResetDialog() {
    _state.update { current ->
        current.copy(
            auth = current.auth.copy(
                showPasswordResetDialog = true,
                passwordResetSent = false,
                error = null
            )
        )
    }
}

/**
 * Hide password reset dialog
 */
internal fun BuyAppViewModel.hidePasswordResetDialog() {
    _state.update { current ->
        current.copy(
            auth = current.auth.copy(
                showPasswordResetDialog = false,
                error = null
            )
        )
    }
}

/**
 * Set auth screen mode (LOGIN, REGISTER, FORGOT_PASSWORD)
 * Used to switch between login and register screens when shown from AppScaffold
 */
internal fun BuyAppViewModel.setAuthMode(mode: AuthMode) {
    _state.update { current ->
        current.copy(
            auth = current.auth.copy(
                mode = mode,
                error = null,
                isSuccess = false
            )
        )
    }
}

/**
 * Set auth error message to be displayed on login screen
 * Error persists until user dismisses it or tries again
 */
internal fun BuyAppViewModel.setAuthError(error: String) {
    _state.update { current ->
        current.copy(
            auth = current.auth.copy(
                error = error,
                isLoading = false,
                isSuccess = false  // Ensure success state is cleared
            )
        )
    }
}

// ===== The seller's home-title announcement ================================

/**
 * Read this device's dismissed announcement keys into state.
 *
 * Called once from `init`. Synchronous because the underlying storage is
 * SharedPreferences or NSUserDefaults rather than a network call, and because the
 * alternative — rendering before the read lands — would flash an announcement the
 * buyer closed on a previous launch.
 *
 * A failure is swallowed to an empty set. The cost is one announcement shown again;
 * the cost of failing startup over a read of a UI preference is the whole app.
 */
internal fun BuyAppViewModel.loadDismissedAnnouncements() {
    val storage = announcementDismissalStorage ?: return
    val dismissed = try {
        storage.dismissed()
    } catch (e: Exception) {
        Log.w("BuyVMUi") { "loadDismissedAnnouncements: ${e.message}" }
        return
    }
    _state.update { it.copy(dismissedRows = it.dismissedRows + dismissed) }
}

/**
 * Stop showing the announcement currently in the home title.
 *
 * The key is taken from state rather than from the caller, so a tap can only ever
 * dismiss the sentence that was on screen. State is updated whether or not the write
 * succeeds: the buyer asked for the banner to go away, and it going away is the part
 * they can see. A failed write means it comes back next launch, which is a far smaller
 * surprise than a close button that does nothing.
 */
internal fun BuyAppViewModel.dismissHomeAnnouncement() {
    val key = _state.value.homeTitleAnnouncement?.dismissalKey ?: return
    rememberDismissed(key)
}

// ===== The seller's published message box ==================================

/**
 * Stop showing the message box in [location].
 *
 * The buyer's way out of being asked, and the reason the slot needs one at all: an
 * announcement can be read and ignored, while a text field in the title of a screen
 * the buyer opens every day is a standing request. A buyer who does not want to be
 * asked closes it, and it stays closed.
 *
 * Keyed and persisted exactly like a dismissed announcement — the id *and* a hash of
 * the question — so re-wording the question asks again and re-publishing the same
 * question does not. [dismissHomeAnnouncement] explains why state is updated whether
 * or not the write lands; the same holds here.
 */
internal fun BuyAppViewModel.dismissPrompt(location: ActionLocation) {
    val key = _state.value.titlePromptFor(location)?.dismissalKey ?: return
    rememberDismissed(key)
}

/**
 * Send what the buyer typed into the box in [location] to the seller's inbox.
 *
 * Everything except the text is resolved from state rather than taken from the
 * caller: which question was asked, which published row asked it, which kind of box
 * it was, and who is sending. A UI that could name the question could misfile an
 * answer under a question the buyer never saw, and one that could name the sender
 * could send as someone else — the rules would refuse that last one, but the right
 * place to not try it is here.
 *
 * Whether the box goes away once the answer is in is the seller's call, not this
 * function's: a one-off question should go when it is answered, a standing wishbox
 * should stay so a buyer with a second wish can send it. The flag is
 * [ActionPersistence] and it is honoured only on success — a refused send leaves the
 * box, and the buyer's text, exactly where they were.
 *
 * A failure is said out loud, unlike a failed dismissal: a wish that silently went
 * nowhere is the one outcome the buyer cannot see and would not retry.
 */
internal fun BuyAppViewModel.sendPrompt(location: ActionLocation, text: String) {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return

    val prompt = _state.value.titlePromptFor(location)
    if (prompt == null) {
        // The box went away between the tap and the dispatch — the seller deleted the
        // row, or the buyer closed it. Nothing to answer, and nothing to report.
        Log.w("BuyVMUi") { "sendPrompt: no box in $location" }
        return
    }

    val sellerId = _state.value.connectedSellerId
    val repository = sellerInboxRepository
    if (sellerId.isEmpty() || repository == null) {
        _state.update {
            it.copy(quickMessage = it.quickMessage.copy(error = Res.string.quick_message_failed))
        }
        return
    }

    _state.update {
        it.copy(quickMessage = it.quickMessage.copy(isSending = true, error = null))
    }

    viewModelScope.launch {
        val buyerId = authRepository.getCurrentUserId()
        if (buyerId.isNullOrEmpty()) {
            // Only reachable with no session at all, which the buy flavor does not
            // leave a buyer in for long. Said rather than dropped.
            _state.update {
                it.copy(
                    quickMessage = it.quickMessage.copy(
                        isSending = false,
                        error = Res.string.quick_message_failed
                    )
                )
            }
            return@launch
        }

        val result = repository.send(
            SellerInboxMessage(
                sellerId = sellerId,
                buyerId = buyerId,
                // What the buyer calls themselves, if their profile has loaded. Empty
                // for a guest, which is honest: the uid beside it is the true part.
                buyerName = _state.value.customerProfile.profile?.displayName.orEmpty(),
                requestKind = prompt.kind,
                requestId = prompt.id,
                // Snapshotted, not referenced. The seller rewords the question and
                // deletes the row; an answer has to carry what it answered.
                prompt = prompt.value,
                text = trimmed
            )
        )

        result.fold(
            onSuccess = {
                _state.update { current ->
                    current.copy(
                        quickMessage = current.quickMessage.copy(
                            isSending = false,
                            error = null,
                            // The field watches this to clear itself; a counter
                            // rather than an id, so a second wish also clears.
                            sentCount = current.quickMessage.sentCount + 1
                        )
                    )
                }
                // After the state update, so the box is gone and the field cleared in
                // one frame rather than two.
                notePublishedActionUsed(prompt)
                // The message is already sent at this point, so a confirmation label
                // that cannot be resolved — as it cannot in a plain JVM test — must
                // not turn a success into a reported failure.
                runCatching { getString(Res.string.quick_message_sent) }
                    .onSuccess { showSnackBar(it, SnackbarType.SUCCESS) }
            },
            onFailure = { e ->
                Log.w("BuyVMUi") { "sendPrompt: refused - ${e.message}" }
                _state.update {
                    it.copy(
                        quickMessage = it.quickMessage.copy(
                            isSending = false,
                            error = Res.string.quick_message_failed
                        )
                    )
                }
            }
        )
    }
}

/** Forget a send failure the top bar has already put in front of the buyer. */
internal fun BuyAppViewModel.clearPromptError() {
    _state.update { it.copy(quickMessage = it.quickMessage.copy(error = null)) }
}

/**
 * Remember that one published row should not be shown on this device again.
 *
 * The one writer: a closed announcement, a closed message box, a tapped
 * hide-on-action button and a sent-through box all end up here, so there is one
 * answer to "what does this device no longer show" and one place that fails soft.
 *
 * State is updated whether or not the write lands. The buyer asked for the row to go
 * away, and it going away is the part they can see; a failed write means it is back
 * next launch, which is a far smaller surprise than a close button that does nothing.
 */
private fun BuyAppViewModel.rememberDismissed(key: String) {
    _state.update { it.copy(dismissedRows = it.dismissedRows + key) }
    try {
        announcementDismissalStorage?.dismiss(key)
    } catch (e: Exception) {
        Log.w("BuyVMUi") { "rememberDismissed: $key not persisted - ${e.message}" }
    }
}

/**
 * Hide a published row the buyer has just acted on, if the seller asked for that.
 *
 * Every tap comes through here and most of them do nothing, which is the point: the
 * renderer dispatches the fact that a button was used, and this decides what that
 * means. A row left on [ActionPersistence.UNTIL_CANCELLED] stays until the buyer
 * closes it themselves.
 */
internal fun BuyAppViewModel.notePublishedActionUsed(action: SellerUiAction) {
    if (!action.hidesAfterUse) return
    rememberDismissed(action.dismissalKey)
}
