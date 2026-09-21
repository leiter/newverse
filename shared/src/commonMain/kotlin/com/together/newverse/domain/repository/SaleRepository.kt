package com.together.newverse.domain.repository

import com.together.newverse.domain.model.Sale
import kotlinx.coroutines.flow.Flow

/**
 * The seller's booked sales. Sales are only ever added: a recorded sale cannot be
 * changed or deleted, a mistake is corrected by recording its cancellation
 * ([Sale.reversal]).
 */
interface SaleRepository {

    /**
     * Sales booked in the period [fromMillis, untilMillis), cancellations included,
     * oldest first. Re-emits when a sale in the period is added.
     */
    fun observeSales(sellerId: String, fromMillis: Long, untilMillis: Long): Flow<List<Sale>>

    /**
     * Every sale recorded for one order — the sale, a cancellation of it, a new
     * confirmation after that — oldest first. Empty if the order was never confirmed.
     */
    suspend fun salesForOrder(sellerId: String, orderId: String): Result<List<Sale>>

    /** Records a new sale and returns it with its assigned id. */
    suspend fun recordSale(sellerId: String, sale: Sale): Result<Sale>
}
