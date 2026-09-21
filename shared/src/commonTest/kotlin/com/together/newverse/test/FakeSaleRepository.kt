package com.together.newverse.test

import com.together.newverse.domain.model.Sale
import com.together.newverse.domain.repository.SaleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Fake implementation of SaleRepository for testing. Like the real one it only adds:
 * there is no way to change or remove a recorded sale.
 */
class FakeSaleRepository : SaleRepository {

    private val _sales = MutableStateFlow<List<Sale>>(emptyList())
    val sales: List<Sale> get() = _sales.value

    var shouldFailRecord = false
    var shouldFailLookup = false
    var failureMessage = "Test error"

    private var nextId = 1

    /** Seed already-recorded sales; ids are kept if set. */
    fun setSales(sales: List<Sale>) {
        _sales.value = sales.map { if (it.id.isEmpty()) it.copy(id = "fake-sale-${nextId++}") else it }
    }

    fun reset() {
        _sales.value = emptyList()
        shouldFailRecord = false
        shouldFailLookup = false
        failureMessage = "Test error"
        nextId = 1
    }

    override fun observeSales(sellerId: String, fromMillis: Long, untilMillis: Long): Flow<List<Sale>> =
        _sales.map { all ->
            all.filter { it.confirmedAt in fromMillis until untilMillis }.sortedBy { it.confirmedAt }
        }

    override suspend fun salesForOrder(sellerId: String, orderId: String): Result<List<Sale>> {
        if (shouldFailLookup) return Result.failure(Exception(failureMessage))
        return Result.success(_sales.value.filter { it.orderId == orderId }.sortedBy { it.confirmedAt })
    }

    override suspend fun recordSale(sellerId: String, sale: Sale): Result<Sale> {
        if (shouldFailRecord) return Result.failure(Exception(failureMessage))
        require(sale.lines.isNotEmpty()) { "A sale without lines books nothing" }
        val stored = sale.copy(id = "fake-sale-${nextId++}")
        _sales.value = _sales.value + stored
        return Result.success(stored)
    }
}
