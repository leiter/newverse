package com.together.newverse.ui.state.buy

import androidx.lifecycle.viewModelScope
import com.together.newverse.domain.model.Order
import com.together.newverse.domain.model.canTransitionToProduction
import com.together.newverse.domain.model.isLocalDemo
import com.together.newverse.domain.model.isRemovedNotCancelled
import com.together.newverse.ui.state.BasketScreenState
import com.together.newverse.ui.state.BuyAppViewModel
import com.together.newverse.ui.state.SnackbarType
import com.together.newverse.util.Log
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "BuyVMDemoOrders"

/**
 * Removing a demo order, rather than cancelling it.
 *
 * A demo order the buyer is now approved to make real has exactly two honest endings:
 * **Bestätigen**, which places it again against a slot that is open now
 * (`confirmDemoOrder` → `basketScreenCheckout`), and **Verwerfen**, which is this file.
 * Until this existed, *Storno* was the only thing a buyer could reach — and it is the
 * wrong verb twice over. See `Order.isRemovedNotCancelled`.
 *
 * Both trunks are handled here because a demo order can be in either: the first
 * `DEMO_FIREBASE_WRITE_LIMIT` under `demo_orders/`, everything after the local-mode
 * switch in [com.together.newverse.domain.config.MutableSellerConfig]. The buyer's
 * `placedOrderIds` index is pruned in the same pass, so a removed order does not leave
 * their history looking for a record that is gone.
 */

/**
 * Delete [orders] wherever they are held, and drop them from the buyer's order index.
 *
 * Local removals happen first and cannot fail in a way worth reporting; the remote ones
 * go in one `deleteDemoOrders` call. The index is pruned only for what was actually
 * removed, and a failed remote delete leaves both the record and its index entry alone —
 * an index pointing at a live record is recoverable, the reverse is a history entry the
 * buyer can never get rid of.
 */
internal suspend fun BuyAppViewModel.removeDemoOrders(orders: List<Order>): Result<Unit> {
    // A production order is cancelled, never removed: the seller has it, it is in their
    // books, and there is somebody to tell. Guarded here rather than trusted to the
    // callers, because the trunks were mixed up once already — see orderIsDemo.
    val removable = orders.filter { it.isRemovedNotCancelled() }
    if (removable.size != orders.size) {
        Log.w(TAG) { "removeDemoOrders: refused ${orders.size - removable.size} non-demo order(s)" }
    }
    if (removable.isEmpty()) return Result.success(Unit)

    val (local, remote) = removable.partition { it.isLocalDemo }
    local.forEach { runCatching { sellerConfig.removeDemoOrder(it.id) } }

    if (remote.isNotEmpty()) {
        val byDate = remote.associate { basketScreenFormatDateKey(it.pickUpDate) to it.id }
        val deleted = orderRepository.deleteDemoOrders(sellerConfig.sellerId, byDate)
        if (deleted.isFailure) {
            Log.e(TAG) { "removeDemoOrders: remote delete failed — ${deleted.exceptionOrNull()?.message}" }
            pruneOrderIndex(local.map { it.id }.toSet())
            return Result.failure(deleted.exceptionOrNull() ?: Exception("Löschen fehlgeschlagen"))
        }
    }

    pruneOrderIndex(removable.map { it.id }.toSet())
    Log.d(TAG) { "removeDemoOrders: removed ${local.size} local, ${remote.size} remote" }
    return Result.success(Unit)
}

/** Drop [removedIds] from the buyer profile's `placedOrderIds`, in one save. */
private suspend fun BuyAppViewModel.pruneOrderIndex(removedIds: Set<String>) {
    if (removedIds.isEmpty()) return
    try {
        val profile = profileRepository.getBuyerProfile().getOrNull() ?: return
        val pruned = profile.placedOrderIds.filterValues { it !in removedIds }
        if (pruned.size == profile.placedOrderIds.size) return
        profileRepository.saveBuyerProfile(profile.copy(placedOrderIds = pruned))
        Log.d(TAG) { "pruneOrderIndex: dropped ${profile.placedOrderIds.size - pruned.size} entry/entries" }
    } catch (e: Exception) {
        // The record is already gone; an index entry pointing at nothing reads as
        // absence everywhere (see readsAsMissingOrder) rather than as an error.
        Log.w(TAG) { "pruneOrderIndex: failed — ${e.message}" }
    }
}

/**
 * The buyer has decided they do not want this demo order after all.
 *
 * Reached from the history card and from the basket screen, where the destructive button
 * says *Verwerfen* rather than *Storno* for exactly these orders. If the order being
 * discarded is the one currently open in the basket, the basket is emptied with it —
 * leaving it loaded would offer Save on a record that no longer exists.
 */
internal fun BuyAppViewModel.discardDemoOrder(order: Order) {
    viewModelScope.launch {
        Log.d(TAG) { "discardDemoOrder: ${order.id} (local=${order.isLocalDemo})" }
        removeDemoOrders(listOf(order))
            .onSuccess {
                if (_state.value.basketScreen.orderId == order.id) {
                    basketRepository.clearBasket()
                    val availableDates = _state.value.basketScreen.availablePickupDates
                    _state.update { current ->
                        current.copy(
                            basket = current.basket.copy(
                                currentOrderId = null,
                                currentOrderDate = null
                            ),
                            basketScreen = BasketScreenState(availablePickupDates = availableDates)
                        )
                    }
                }
                // Nothing demo left means the Firebase write allowance can start over if
                // this buyer ever drops back out of approval.
                if (remainingDemoOrders().isEmpty()) sellerConfig.resetDemoOrderState()
                loadOrderHistory()
                showSnackBar("Demo-Bestellung verworfen", SnackbarType.SUCCESS)
            }
            .onFailure {
                showSnackBar("Demo-Bestellung konnte nicht verworfen werden", SnackbarType.ERROR)
            }
    }
}

/**
 * Every demo order this buyer still holds, from both trunks and local storage.
 *
 * De-duplicated by id because an order can briefly be in two of them, and read fresh
 * rather than taken from `orderHistory`, which is loaded asynchronously and may not have
 * caught up with a removal.
 */
internal suspend fun BuyAppViewModel.remainingDemoOrders(): List<Order> {
    val profile = profileRepository.getBuyerProfile().getOrNull()
    val stored = orderRepository
        .getBuyerOrders(sellerConfig.sellerId, profile?.placedOrderIds ?: emptyMap(), isDemo = true)
        .getOrNull()
        .orEmpty()
    return (stored + sellerConfig.loadDemoOrders())
        .distinctBy { it.id }
        .filter { it.origin.isDemo }
}

/**
 * Drop the demo orders that can no longer be confirmed against anything, and return the
 * ones that still can.
 *
 * A demo order whose pickup day has passed describes a pickup that never happened:
 * `canTransitionToProduction` already refuses to move it into `orders/`, and *Bestätigen*
 * cannot help either, because confirming means placing it again against a slot that is
 * open now. Left alone it sits in the history with a button that works and an outcome
 * that is not what the buyer expects — so approval clears it out instead.
 *
 * Deliberately not a general cleanup: only this buyer's own records, only on approval,
 * and only the ones already past use. `deleteOldDemoOrders` is the seller's sweep.
 */
internal suspend fun BuyAppViewModel.dropUnconfirmableDemoOrders(
    candidates: List<Order>
): List<Order> {
    val (live, expired) = candidates.partition { it.canTransitionToProduction() }
    if (expired.isEmpty()) return live

    Log.d(TAG) { "dropUnconfirmableDemoOrders: ${expired.size} past use, ${live.size} still confirmable" }
    removeDemoOrders(expired)
    return live
}
