package com.together.newverse.data.repository

import com.together.newverse.data.firebase.SaleNodes
import com.together.newverse.domain.model.Sale
import com.together.newverse.domain.repository.AuthRepository
import com.together.newverse.domain.repository.SaleRepository
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.database.database
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.TimeZone

/**
 * GitLive implementation of [SaleRepository]. Layout and update maps come from
 * [SaleNodes]; a sale and its index entry are written in one multi-path update.
 *
 * @param timeZone The seller's time zone, which decides a sale's booking month.
 */
class GitLiveSaleRepository(
    private val authRepository: AuthRepository,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault()
) : SaleRepository {

    private val database = Firebase.database

    override fun observeSales(sellerId: String, fromMillis: Long, untilMillis: Long): Flow<List<Sale>> {
        require(sellerId.isNotEmpty()) { "sellerId must not be empty" }

        val months = SaleNodes.monthKeys(fromMillis, untilMillis, timeZone).map { month ->
            database.reference(SaleNodes.monthPath(sellerId, month)).valueEvents.map { snapshot ->
                snapshot.children.map { child ->
                    val id = child.key ?: error("Sale without key in month $month")
                    // An unreadable sale fails the whole read: skipping it would make the
                    // totals and the export silently too low.
                    (child.value as? Map<*, *>)?.let { SaleNodes.saleFromMap(id, it) }
                        ?: error("Sale $id in month $month is unreadable")
                }
            }
        }
        if (months.isEmpty()) return flowOf(emptyList())

        return combine(months) { perMonth ->
            perMonth.flatMap { it }
                .filter { it.confirmedAt in fromMillis until untilMillis }
                .sortedBy { it.confirmedAt }
        }
    }

    override suspend fun salesForOrder(sellerId: String, orderId: String): Result<List<Sale>> {
        return try {
            val index = database.reference(SaleNodes.orderIndexPath(sellerId, orderId))
                .valueEvents.first()
            val monthBySaleId = (index.value as? Map<*, *>).orEmpty()
                .mapNotNull { (id, month) -> (id as? String)?.let { it to (month as? String) } }

            val sales = monthBySaleId.map { (saleId, month) ->
                month ?: error("Index entry for sale $saleId has no month")
                val snapshot = database.reference(SaleNodes.salePath(sellerId, month, saleId))
                    .valueEvents.first()
                val value = snapshot.value as? Map<*, *> ?: error("Sale $saleId is missing")
                SaleNodes.saleFromMap(saleId, value) ?: error("Sale $saleId is unreadable")
            }
            Result.success(sales.sortedBy { it.confirmedAt })
        } catch (e: Exception) {
            println("❌ GitLiveSaleRepository.salesForOrder: Error - ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun recordSale(sellerId: String, sale: Sale): Result<Sale> {
        return try {
            if (authRepository.getCurrentUserId() == null) {
                return Result.failure(Exception("User not authenticated"))
            }
            val month = SaleNodes.monthKey(sale.confirmedAt, timeZone)
            val saleId = database.reference(SaleNodes.monthPath(sellerId, month)).push().key
                ?: throw IllegalStateException("Failed to generate sale ID")

            database.reference().updateChildren(SaleNodes.recordUpdate(sellerId, saleId, sale, timeZone))

            println("✅ GitLiveSaleRepository: recorded sale $saleId for order ${sale.orderId} " +
                "(${sale.lines.size} lines, ${sale.grossCents} cents${if (sale.isReversal) ", cancellation" else ""})")
            Result.success(sale.copy(id = saleId))
        } catch (e: Exception) {
            println("❌ GitLiveSaleRepository.recordSale: Error - ${e.message}")
            Result.failure(e)
        }
    }
}
