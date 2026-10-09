package com.together.newverse.ui.screens.sell

import com.together.newverse.domain.model.Sale
import com.together.newverse.domain.model.SellerArticle
import com.together.newverse.domain.model.stockMovementsForSale
import com.together.newverse.domain.repository.StockRepository
import com.together.newverse.util.Log

private const val TAG = "StockBooking"

/**
 * Takes a booked [sale] out of storage, for the articles the seller watches.
 *
 * Called after the sale itself is recorded, and deliberately unable to fail the
 * caller: the sale is the seller's books and a cancellation is the only way to undo
 * one, so a stock ledger that could refuse would make booking a sale depend on a
 * secondary record. If this write is lost the level drifts until the next
 * stocktake, which sets it to what was counted — the repair is ordinary work, and
 * much cheaper than a sale that failed to book.
 *
 * A cancellation needs no special case: its lines carry negative quantities, so the
 * movements add back what the sale took out.
 */
internal suspend fun StockRepository.bookSale(
    sellerId: String,
    sale: Sale,
    catalog: Map<String, SellerArticle>
) {
    val movements = stockMovementsForSale(sale, catalog)
    if (movements.isEmpty()) return

    recordMovements(sellerId, movements)
        .onSuccess {
            Log.d(TAG) { "bookSale: ${it.size} movement(s) for sale ${sale.id}" }
        }
        .onFailure { error ->
            // Deliberately swallowed; see above. The next stocktake repairs the level.
            Log.w(TAG) { "bookSale: stock not updated for sale ${sale.id} - ${error.message}" }
        }
}
