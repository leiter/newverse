package com.together.newverse.ui.state.buy

import androidx.lifecycle.viewModelScope
import com.together.newverse.domain.model.DraftBasket
import com.together.newverse.domain.model.Order
import com.together.newverse.domain.model.OrderedProduct
import com.together.newverse.ui.state.BuyAppViewModel
import com.together.newverse.util.Log
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "BasketSync"

/**
 * The basket a buyer has open follows their other device.
 *
 * A basket is one basket: the draft lives on the buyer's profile and a placed order
 * lives in `orders/`, both shared by every device they are signed in on. The screen,
 * though, is fed by [com.together.newverse.data.repository.InMemoryBasketRepository],
 * a list held for the session and seeded once — by `loadCurrentOrder` at start or by
 * `basketScreenLoadOrder` when an order is opened. So an amount changed on one phone
 * stayed there: the other phone had read the same basket earlier and had no reason to
 * look again.
 *
 * This subscribes to whichever of the two the basket currently *is*, and applies what
 * comes back — but only onto an idle screen. The rule is in [canApplyRemoteBasket]:
 * an edit in progress is the buyer's current intent and a remote value is a stale
 * instruction to overwrite it, so the change waits and
 * [com.together.newverse.ui.state.BasketScreenState.remoteBasketChangePending] says
 * so. Applying it anyway is the one behaviour that would lose work the buyer can see
 * on the screen in front of them.
 */
internal sealed interface RemoteBasket {
    /** The buyer's unplaced basket, as their profile holds it. */
    data class Draft(val draft: DraftBasket?) : RemoteBasket

    /** A placed order the basket screen has open, as its trunk holds it. */
    data class Placed(val order: Order?) : RemoteBasket
}

/** What the basket is pointed at: a placed order, or the draft when no order is open. */
private data class BasketSubject(
    val orderId: String?,
    val orderDate: String?,
    val isDemo: Boolean,
)

@OptIn(ExperimentalCoroutinesApi::class)
internal fun BuyAppViewModel.observeOpenBasketRemotely() {
    viewModelScope.launch {
        _state
            .map {
                BasketSubject(
                    orderId = it.basketScreen.orderId,
                    orderDate = it.basketScreen.orderDate,
                    isDemo = it.basketScreen.orderIsDemo,
                )
            }
            .distinctUntilChanged()
            .flatMapLatest { subject ->
                if (subject.orderId.isNullOrEmpty() || subject.orderDate.isNullOrEmpty()) {
                    // No placed order open, so the shared thing is the draft. It rides
                    // on the buyer profile, which the repository keeps mirrored.
                    profileRepository.observeBuyerProfile()
                        .map { RemoteBasket.Draft(it?.draftBasket) }
                } else {
                    // The order's own trunk, from the order — never from the account's
                    // demo mode. See BasketScreenState.orderIsDemo.
                    orderRepository.observeBuyerOrders(
                        sellerId = sellerConfig.sellerId,
                        placedOrderIds = mapOf(subject.orderDate to subject.orderId),
                        isDemo = subject.isDemo,
                    ).map { orders ->
                        RemoteBasket.Placed(orders.firstOrNull { it.id == subject.orderId })
                    }
                }
            }
            // A basket that cannot be re-read is the basket the buyer already has.
            // Failing here would be worse than being briefly out of date.
            .catch { e -> Log.w(TAG) { "observeOpenBasketRemotely: stopped - ${e.message}" } }
            .collect { remote -> applyRemoteBasket(remote) }
    }
}

/**
 * Whether the screen is quiet enough to take a remote basket.
 *
 * Every condition names something the buyer is in the middle of. `hasChanges` is the
 * broad one — an unsaved edit, wherever it was made — and the rest are moments where
 * the basket is being written to or read from and a swap underneath would race it.
 */
private fun BuyAppViewModel.canApplyRemoteBasket(): Boolean {
    val basket = _state.value.basketScreen
    return !basket.hasChanges &&
        !basket.isEditMode &&
        !basket.isCheckingOut &&
        !basket.isLoadingOrder &&
        basket.confirmingDemoOrderId == null
}

/** True when [remote] says something this device does not already show. */
private fun sameItems(current: List<OrderedProduct>, remote: List<OrderedProduct>): Boolean {
    if (current.size != remote.size) return false
    val remoteByProduct = remote.associateBy { it.productId }
    if (remoteByProduct.size != remote.size) return false
    return current.all { item ->
        val other = remoteByProduct[item.productId] ?: return@all false
        other.amountCount == item.amountCount && other.price == item.price
    }
}

internal suspend fun BuyAppViewModel.applyRemoteBasket(remote: RemoteBasket) {
    val items = when (remote) {
        is RemoteBasket.Draft -> remote.draft?.items ?: return
        is RemoteBasket.Placed -> remote.order?.articles ?: return
    }

    // Our own write comes back through the same subscription. Nothing to do, and
    // saying "changed elsewhere" about it would be a lie.
    if (sameItems(basketRepository.observeBasket().value, items)) {
        if (_state.value.basketScreen.remoteBasketChangePending) {
            _state.update { current ->
                current.copy(
                    basketScreen = current.basketScreen.copy(remoteBasketChangePending = false)
                )
            }
        }
        return
    }

    if (!canApplyRemoteBasket()) {
        Log.d(TAG) { "applyRemoteBasket: edits pending, holding the remote change back" }
        _state.update { current ->
            current.copy(
                basketScreen = current.basketScreen.copy(remoteBasketChangePending = true)
            )
        }
        return
    }

    when (remote) {
        is RemoteBasket.Draft -> {
            Log.d(TAG) { "applyRemoteBasket: taking ${items.size} draft items from elsewhere" }
            basketRepository.loadFromProfile(remote.draft!!)
        }
        is RemoteBasket.Placed -> {
            val order = remote.order!!
            val date = _state.value.basketScreen.orderDate ?: return
            Log.d(TAG) { "applyRemoteBasket: taking ${items.size} order items from elsewhere" }
            basketRepository.loadOrderItems(order.articles, order.id, date)
        }
    }

    val applied = basketRepository.observeBasket().value
    _state.update { current ->
        current.copy(
            basketScreen = current.basketScreen.copy(
                items = applied,
                total = applied.sumOf { it.price * it.amountCount },
                // The remote state is the new baseline, so nothing is outstanding
                // against it — this screen's edits were none, which is why it applied.
                originalOrderItems = if (remote is RemoteBasket.Placed) {
                    remote.order!!.articles
                } else {
                    current.basketScreen.originalOrderItems
                },
                hasChanges = false,
                editedOnBasketScreen = false,
                remoteBasketChangePending = false,
            )
        )
    }
}
