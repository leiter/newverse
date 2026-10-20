package com.together.newverse.ui.state.buy

import androidx.lifecycle.viewModelScope
import com.together.newverse.ui.state.BuyAppViewModel
import com.together.newverse.ui.state.SnackbarType
import com.together.newverse.util.Log
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "BuyVMMarket"

/**
 * Where the buyer collects, asked once rather than inferred from what they happened
 * to tap.
 *
 * Prices follow the market, so this one answer decides which prices the buyer ever
 * sees, what their access request asks for, and therefore which market the seller
 * approves them at. It used to be a side effect of opening the slot picker on the way
 * to checkout — the most consequential field in the access request, filled in by a
 * buyer doing something else for another reason. See
 * `doc/guides/market-pricing-and-access-design.md` §2a.
 *
 * This is also the write path `BuyerProfile.defaultMarket` never had: the field was
 * read by `contextualMarketId` and persisted by the profile repository, and nothing in
 * either app ever set it.
 */
internal fun BuyAppViewModel.showMarketPicker() {
    _state.update { it.copy(showMarketPicker = true) }
}

/**
 * Closed without answering.
 *
 * [com.together.newverse.ui.state.BuyAppState.marketChoiceDeferred] is not persisted,
 * so the question returns next launch. Until it is answered the old behaviour stands:
 * the catalogue prices against no market and the first slot pick settles it.
 */
internal fun BuyAppViewModel.dismissMarketPicker() {
    _state.update { it.copy(showMarketPicker = false, marketChoiceDeferred = true) }
}

/**
 * Record where the buyer collects, and make the prices follow.
 *
 * Three things have to move together, or the answer is worse than no answer:
 *
 * 1. `BuyerProfile.defaultMarket` is persisted, so `contextualMarketId` resolves to it
 *    on this and every later launch, and `requestAccess` asks for this market.
 * 2. The basket is repriced, because the catalogue the items were added from was
 *    priced against whatever was in context before — for a buyer answering during
 *    onboarding, no market at all. Skipped when the basket already names a market of
 *    its own: a slot the buyer chose is a stronger claim than a stated default, which
 *    is the resolution order `contextualMarketId` uses, and repricing to the default
 *    would quietly overrule it.
 * 3. The offered slots are rebuilt, since they are drawn per market.
 */
internal fun BuyAppViewModel.chooseDefaultMarket(marketId: String) {
    val state = _state.value

    // Not trusted from the UI: the list can have changed under an open dialog if the
    // seller narrowed this buyer's scope while it was up.
    val market = state.eligibleMarkets.firstOrNull { it.id == marketId }
    if (market == null) {
        Log.w(TAG) { "chooseDefaultMarket: $marketId is not an eligible market, ignored" }
        _state.update { it.copy(showMarketPicker = false) }
        return
    }

    val profile = state.customerProfile.profile
    if (profile == null) {
        // Nothing to write it onto yet. Deferring rather than inventing a profile here:
        // the prompt comes back, and the profile load is what it is waiting for.
        Log.w(TAG) { "chooseDefaultMarket: no buyer profile loaded yet, deferring" }
        _state.update { it.copy(showMarketPicker = false) }
        return
    }

    Log.d(TAG) { "chooseDefaultMarket: ${market.name} (${market.id})" }

    val updated = profile.copy(defaultMarket = market.id)

    // Applied locally first so the catalogue reprices immediately: the write is a
    // round trip, and a buyer who just answered should not watch old prices while it
    // completes. A failed write is reported and the local value stands for the
    // session — the prompt returns next launch, which is the same place a dismissal
    // leaves them.
    _state.update { current ->
        current.copy(
            showMarketPicker = false,
            marketChoiceDeferred = false,
            customerProfile = current.customerProfile.copy(profile = updated)
        )
    }

    viewModelScope.launch {
        if (_state.value.basketScreen.selectedMarketId.isEmpty()) {
            basketRepository.repriceItems(_state.value.basketPricesAt(market.id))
        }
        refreshDatesForClosures()

        profileRepository.saveBuyerProfile(updated)
            .onFailure { e ->
                Log.e(TAG) { "chooseDefaultMarket: save failed - ${e.message}" }
                showSnackBar(
                    "Abholort konnte nicht gespeichert werden",
                    SnackbarType.WARNING
                )
            }
    }
}
