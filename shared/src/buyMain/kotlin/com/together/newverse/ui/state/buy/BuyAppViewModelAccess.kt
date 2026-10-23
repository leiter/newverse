package com.together.newverse.ui.state.buy

import androidx.lifecycle.viewModelScope
import com.together.newverse.domain.model.AccessStatus
import com.together.newverse.domain.model.MissingProfileData
import com.together.newverse.domain.model.OrderOrigin
import com.together.newverse.ui.state.BuyAppViewModel
import com.together.newverse.ui.state.SnackbarType
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.together.newverse.util.Log


private const val TAG = "BuyVMAccess"

/**
 * The buyer's missing profile fields, read from state so the gate below and the
 * badge/prompts in the UI can never disagree.
 */
internal fun BuyAppViewModel.checkProfileCompleteness(): MissingProfileData =
    _state.value.missingProfileData

/**
 * Build a user-facing message listing which fields are still missing.
 */
internal fun buildMissingFieldsMessage(missing: MissingProfileData): String {
    val fields = mutableListOf<String>()
    if (missing.name) fields.add("Name")
    if (missing.pickupTime) fields.add("Abholzeit")
    if (missing.street) fields.add("Straße")
    if (missing.houseNumber) fields.add("Hausnummer")
    return "Fehlende Angaben: ${fields.joinToString(", ")}"
}

/**
 * Access request extension functions for BuyAppViewModel.
 *
 * Handles:
 * - startObservingAccessStatus — starts real-time Firebase listener for buyer access status
 * - connectWithToken — handles deep link with seller-assigned UUID token
 */

/**
 * Start observing access status from Firebase in real-time.
 * Updates accessStatus (and therefore isDemoMode) automatically.
 * Called after auth+seller connection is confirmed.
 */
internal fun BuyAppViewModel.startObservingAccessStatus() {
    val sellerId = sellerConfig.sellerId
    if (sellerId.isEmpty()) return

    // No token gate: the access record is keyed by this buyer's own uid, so the
    // observer always has something to watch. An absent record reports NONE, which
    // is what puts the buyer in demo mode.
    viewModelScope.launch {
        profileRepository.observeAccessStatus(sellerId)
            .catch { e ->
                Log.e(TAG) { "observeAccessStatus error: ${e.message}" }
                _state.update { it.copy(isAccessStatusLoaded = true) }
            }
            .collect { status ->
                Log.d(TAG) { "observeAccessStatus: status=$status" }
                val wasDemoMode = _state.value.isDemoMode
                _state.update { it.copy(accessStatus = status, isAccessStatusLoaded = true) }
                when {
                    wasDemoMode && status == AccessStatus.APPROVED -> {
                        // Approval does NOT adopt the buyer's demo orders. Moving them
                        // turned a basket the app had called non-binding into a real
                        // order, carrying a pickup date chosen in a session that
                        // committed to nothing — and by then the date may well have
                        // passed. They stay in demo_orders/, where the seller cannot
                        // see them, until the buyer confirms one against a fresh slot.
                        promptToConfirmDemoOrders()
                    }
                    !wasDemoMode && status != AccessStatus.APPROVED -> {
                        // Production → Demo: reset counter so the 2-Firebase-order rule applies again
                        sellerConfig.resetDemoOrderState()
                    }
                }
            }
    }

    // Watched separately from the status: the seller can narrow or widen where a
    // buyer collects without touching their approval, and that has to reach an
    // open app the same way a closure does.
    viewModelScope.launch {
        profileRepository.observeAllowedMarkets(sellerId)
            .catch { e -> Log.e(TAG) { "observeAllowedMarkets error: ${e.message}" } }
            .collect { markets ->
                Log.d(TAG) { "observeAllowedMarkets: ${markets.size} market(s) allowed" }
                _state.update { it.copy(allowedMarkets = markets) }
                // The offered slots are drawn from the markets this buyer may use.
                refreshDatesForClosures()
            }
    }

    // The buttons the seller publishes. Watched rather than read once: the seller adds
    // one while buyers already have the app open, and configuration that needs a
    // restart to appear is not configuration. Found the hard way — the first button
    // published on dev did not show up until the app was killed.
    sellerUiActionRepository?.let { repo ->
        viewModelScope.launch {
            repo.observeActions(sellerId)
                .catch { e -> Log.w(TAG) { "observeActions error: ${e.message}" } }
                .collect { actions ->
                    Log.d(TAG) { "observeActions: ${actions.size} published action(s)" }
                    _state.update { it.copy(sellerUiActions = actions) }
                }
        }
    }
}

/**
 * Request access from the connected seller.
 * The request is keyed by the buyer's auth uid, so there is no identifier to mint.
 */
@OptIn(ExperimentalUuidApi::class)
internal fun BuyAppViewModel.requestAccess() {
    val sellerId = sellerConfig.sellerId
    if (sellerId.isEmpty()) {
        viewModelScope.launch {
            showSnackBar("Bitte zuerst mit einem Verkäufer verbinden", SnackbarType.ERROR)
        }
        return
    }

    // Check profile completeness before requesting access
    val missing = checkProfileCompleteness()
    if (!missing.isComplete) {
        if (missing.allMissing) {
            _state.update { it.copy(showProfileIncompleteDialog = true) }
        } else {
            viewModelScope.launch {
                showSnackBar(buildMissingFieldsMessage(missing), SnackbarType.WARNING)
            }
        }
        _state.update { it.copy(isRequestingAccess = false) }
        return
    }

    _state.update { it.copy(isRequestingAccess = true) }

    viewModelScope.launch {
        try {
            val displayName = _state.value.customerProfile.profile?.displayName
                ?.takeIf { it.isNotBlank() } ?: "Guest"

            // The request names the stall the buyer wants to collect at, so the seller
            // approves that rather than guessing. `contextualMarketId` resolves to the
            // market they stated during onboarding (`chooseDefaultMarket`), which is
            // the point of asking: this field used to be filled in as a side effect of
            // opening the slot picker. Still empty for a single-market seller, and for
            // a buyer who deferred the question — both of which mean "anywhere".
            val marketId = _state.value.contextualMarketId.orEmpty()

            profileRepository.submitAccessRequest(sellerId, displayName, marketId)
                .onSuccess {
                    _state.update { it.copy(isRequestingAccess = false) }
                    startObservingAccessStatus()
                    showSnackBar("Zugangsanfrage gesendet", SnackbarType.SUCCESS)
                }
                .onFailure { e ->
                    _state.update { it.copy(isRequestingAccess = false) }
                    showSnackBar("Zugangsanfrage fehlgeschlagen: ${e.message}", SnackbarType.ERROR)
                }
        } catch (e: Exception) {
            _state.update { it.copy(isRequestingAccess = false) }
            showSnackBar("Zugangsanfrage fehlgeschlagen: ${e.message}", SnackbarType.ERROR)
        }
    }
}

/**
 * Apply a pre-approved UUID (from invitation or QR link).
 * Sets the UUID locally and in Firebase, then starts observing access status.
 * The seller already wrote APPROVED for this UUID, so the buyer sees it immediately.
 */
/**
 * Dismiss the profile incomplete dialog.
 */
internal fun BuyAppViewModel.dismissProfileIncompleteDialog() {
    _state.update { it.copy(showProfileIncompleteDialog = false) }
}

/**
 * Tell a freshly approved buyer that their demo orders are still theirs to confirm.
 *
 * Approval used to adopt them silently. It no longer does: a demo order is a basket the
 * app called non-binding, placed against a slot chosen in a session that committed to
 * nothing, and by the time the seller gets round to approving the buyer that slot may
 * have passed. The order stays in `demo_orders/`, where the seller cannot see it, and
 * becomes real only when the buyer confirms it against a slot that is open now.
 *
 * Counted from both trunks and local storage together, because a demo order can be in
 * either, and de-duplicated by id because it can briefly be in both.
 */
internal fun BuyAppViewModel.promptToConfirmDemoOrders() {
    viewModelScope.launch {
        val profile = profileRepository.getBuyerProfile().getOrNull()
        val stored = orderRepository
            .getBuyerOrders(sellerConfig.sellerId, profile?.placedOrderIds ?: emptyMap())
            .getOrNull()
            .orEmpty()
        val waiting = (stored + sellerConfig.loadDemoOrders())
            .distinctBy { it.id }
            .count { it.origin.isDemo }

        // The approval itself changes what the history shows — the badge, and whether
        // an order is still the buyer's to confirm — so refresh it either way.
        loadOrderHistory()

        if (waiting == 0) {
            // Nothing left to adopt, so the demo write allowance can start over if this
            // buyer ever drops back out of approval.
            sellerConfig.resetDemoOrderState()
            return@launch
        }

        Log.d(TAG) { "promptToConfirmDemoOrders: $waiting demo order(s) awaiting confirmation" }
        showSnackBar(
            if (waiting == 1) {
                "Du bist jetzt freigegeben. Deine Demo-Bestellung gilt noch nicht — " +
                    "bestätige sie mit einem neuen Abholtermin."
            } else {
                "Du bist jetzt freigegeben. $waiting Demo-Bestellungen gelten noch nicht — " +
                    "bestätige sie mit einem neuen Abholtermin."
            },
            SnackbarType.INFO
        )
    }
}

/**
 * Move this buyer's demo orders into production when the seller approves them.
 *
 * **Not called on approval any more** — see [promptToConfirmDemoOrders]. Kept because
 * confirming an order is the same move, just initiated by the buyer and against a slot
 * they have just chosen, which is what this will be driven by next.
 *
 * Demo orders sit in two places — the first ones under Firebase `demo_orders/`, everything
 * after the local-mode switch in local storage — and both are handed to the repository in
 * one call, which writes them under `orders/` as [OrderOrigin.TRANSITIONED] in a single
 * multi-path update. Either all of them move or none do; the local copies are cleared only
 * once the write has succeeded, so a failure here leaves the buyer with their demo orders
 * intact and the next approval emission can retry.
 *
 * The buyer's `placedOrderIds` gain the new production ids, which is what makes a moved
 * order show up in their history again — a locally held order is minted a fresh id, and
 * reading a production order from the old one would find nothing. The index is merged,
 * never replaced: it also holds the orders this buyer had all along, and the ones a
 * transition deliberately leaves behind.
 *
 * Orders whose pickup day has already passed are not moved; see `canTransitionToProduction`.
 */
internal fun BuyAppViewModel.transitionDemoOrdersToProduction() {
    viewModelScope.launch {
        val localOrders = sellerConfig.loadDemoOrders()
        val profile = profileRepository.getBuyerProfile().getOrNull()
        val remoteOrderIds = profile?.placedOrderIds ?: emptyMap()

        if (localOrders.isEmpty() && remoteOrderIds.isEmpty()) {
            sellerConfig.clearDemoOrders()
            sellerConfig.resetDemoOrderState()
            return@launch
        }

        Log.d(TAG) { "transitionDemoOrders: ${remoteOrderIds.size} remote, ${localOrders.size} local" }

        // Read fresh rather than taken from state: the approval and the market scope
        // arrive on two separate flows, and this runs the moment the status turns
        // APPROVED — which can be before observeAllowedMarkets has caught up. Using
        // the stale value would let an order through at a market that was just
        // narrowed away.
        val granted = profileRepository.getAllowedMarkets(sellerConfig.sellerId)

        orderRepository.transitionDemoOrdersToProduction(
            sellerId = sellerConfig.sellerId,
            remoteOrderIds = remoteOrderIds,
            localOrders = localOrders,
            allowedMarkets = granted
        ).onSuccess { result ->
            if (profile != null) {
                // Merged, not replaced — see DemoTransitionResult.applyTo. Assigning
                // `result.transitioned` here discarded every order the buyer already
                // had, and emptied the index outright when nothing moved.
                val index = result.applyTo(profile.placedOrderIds)
                if (index != profile.placedOrderIds) {
                    profileRepository.saveBuyerProfile(profile.copy(placedOrderIds = index))
                }
            }
            // Only clear the local demo orders once nothing was refused. A refused
            // order is the buyer's to decide about, and clearing it here would
            // silently throw away the basket they are about to be asked to re-place.
            if (!result.hasRefusals) {
                sellerConfig.clearDemoOrders()
                sellerConfig.resetDemoOrderState()
            }
            loadOrderHistory()
            Log.d(TAG) { "transitionDemoOrders: ${result.transitioned.size} order(s) now in production" }

            if (result.hasRefusals) {
                val names = result.notEligibleMarketIds
                    .map { id -> _state.value.sellerMarkets.firstOrNull { it.id == id }?.name ?: id }
                    .distinct()
                Log.d(TAG) { "transitionDemoOrders: refused for market(s) $names" }
                showSnackBar(
                    "Bestellungen für ${names.joinToString(", ")} wurden nicht übernommen: " +
                        "dieser Markt ist für dich nicht freigegeben.",
                    SnackbarType.WARNING
                )
            }
        }.onFailure { e ->
            // Nothing was written, nothing was cleared — the demo orders are still there.
            Log.e(TAG) { "transitionDemoOrders: failed, demo orders left in place — ${e.message}" }
            showSnackBar(
                "Demo-Bestellungen konnten nicht übernommen werden",
                SnackbarType.WARNING
            )
        }
    }
}

/**
 * Retry connecting with a previously stored pending token after profile is completed.
 */
internal fun BuyAppViewModel.retryPendingConnection() {
    val pending = _state.value.pendingConnectToken
    _state.update { it.copy(
        showProfileIncompleteDialog = false,
        pendingConnectToken = null
    ) }
    if (pending != null) {
        connectWithToken(pending.first, pending.second)
    }
}

/**
 * Redeem an invitation, which doubles as its own invite token.
 * Same path as a scanned QR link - see [connectWithToken].
 */
internal fun BuyAppViewModel.applyPreApprovedAccess(token: String) {
    connectWithToken(sellerConfig.sellerId, token)
}

/**
 * Handle a connect deep link: newverse://connect?seller={sellerId}&token={token}
 *
 * The token is a bearer ticket the seller minted before this buyer existed. Redeeming
 * it marks it used and writes an APPROVED access record citing it, which the rules
 * verify against the token's redeemedBy. If the token is already spent, expired or
 * unknown, redemption fails and the buyer falls back to a normal access request.
 */
internal fun BuyAppViewModel.connectWithToken(sellerId: String, token: String) {
    if (sellerId.isEmpty() || token.isEmpty()) return

    val missing = checkProfileCompleteness()
    if (!missing.isComplete) {
        // Hold the token so redemption can be retried once the profile is complete.
        _state.update {
            it.copy(
                pendingConnectToken = Pair(sellerId, token),
                showProfileIncompleteDialog = true
            )
        }
        return
    }

    pendingTokenStorage?.set(token)

    // Bypass the demo mode gate: holding a token means the seller authorised this buyer.
    performConnection(sellerId)

    viewModelScope.launch {
        val displayName = _state.value.customerProfile.profile?.displayName
            ?.takeIf { it.isNotBlank() } ?: "Guest"

        when (profileRepository.getAccessStatus(sellerId)) {
            AccessStatus.APPROVED, AccessStatus.BLOCKED -> {
                // Already settled with this seller; never overwrite it with a redemption.
                Log.d(TAG) { "connectWithToken: access already settled, skipping redemption" }
                pendingTokenStorage?.clear()
            }
            else -> {
                profileRepository.redeemInviteToken(sellerId, token, displayName)
                    .onSuccess {
                        // The ticket is spent; identity is the auth uid from here on.
                        pendingTokenStorage?.clear()
                        Log.d(TAG) { "connectWithToken: token redeemed, access approved" }
                    }
                    .onFailure { e ->
                        Log.w(TAG) { "connectWithToken: redemption failed - ${e.message}" }
                        profileRepository.submitAccessRequest(
                            sellerId, displayName, _state.value.contextualMarketId.orEmpty()
                        )
                            .onSuccess { Log.d(TAG) { "connectWithToken: fell back to an access request" } }
                            .onFailure { err -> Log.e(TAG) { "connectWithToken: request failed - ${err.message}" } }
                    }
            }
        }

        startObservingAccessStatus()
    }
}
