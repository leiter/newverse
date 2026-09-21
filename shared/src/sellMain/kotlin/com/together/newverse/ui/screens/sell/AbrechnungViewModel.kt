package com.together.newverse.ui.screens.sell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.together.newverse.domain.model.BookingPeriod
import com.together.newverse.domain.model.Order
import com.together.newverse.domain.model.OrderStatus
import com.together.newverse.domain.model.OrderedProduct
import com.together.newverse.domain.model.Sale
import com.together.newverse.domain.model.SalesCsv
import com.together.newverse.domain.model.SellerArticle
import com.together.newverse.domain.model.TaxRate
import com.together.newverse.domain.model.articleFor
import com.together.newverse.domain.repository.AuthRepository
import com.together.newverse.domain.repository.OrderRepository
import com.together.newverse.domain.repository.SaleRepository
import com.together.newverse.domain.repository.SellerArticleRepository
import com.together.newverse.ui.state.core.AsyncState
import com.together.newverse.util.OrderDateUtils
import com.together.newverse.util.TextFileSharer
import kotlin.time.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class AbrechnungViewModel(
    private val sellerArticleRepository: SellerArticleRepository,
    private val orderRepository: OrderRepository,
    private val authRepository: AuthRepository,
    private val saleRepository: SaleRepository,
    private val fileSharer: TextFileSharer,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    private val today: () -> LocalDate = { Clock.System.now().toLocalDateTime(timeZone).date }
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(AbrechnungTab.PICKUP)
    val selectedTab: StateFlow<AbrechnungTab> = _selectedTab.asStateFlow()

    /** The booking period shown in the "Zeitraum" tab; starts at the current month. */
    private val _period = MutableStateFlow<BookingPeriod>(BookingPeriod.monthOf(today()))
    val period: StateFlow<BookingPeriod> = _period.asStateFlow()

    private val _pickupSummary = MutableStateFlow<AsyncState<PickupSummary>>(AsyncState.Loading)
    val pickupSummary: StateFlow<AsyncState<PickupSummary>> = _pickupSummary.asStateFlow()

    private val _periodSummary = MutableStateFlow<AsyncState<PeriodSummary>>(AsyncState.Loading)
    val periodSummary: StateFlow<AsyncState<PeriodSummary>> = _periodSummary.asStateFlow()

    // Catalog for tax rate and purchase price lookups, by article id
    private var articlesById = mapOf<String, SellerArticle>()
    private var allOrders = listOf<Order>()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val sellerId = authRepository.getCurrentUserId() ?: run {
                _pickupSummary.value = AsyncState.Error("Not authenticated")
                _periodSummary.value = AsyncState.Error("Not authenticated")
                return@launch
            }

            launch {
                // Article lookup is best-effort; a missing article falls back to 7 %
                // and an unknown purchase price
                sellerArticleRepository.observeSellerArticles(sellerId)
                    .catch { }
                    .collect { catalog ->
                        articlesById = catalog.associateBy { it.id }
                        recalculate()
                    }
            }

            launch {
                orderRepository.observeSellerOrders(sellerId)
                    .catch {
                        _pickupSummary.value = AsyncState.Error("Bestellungen konnten nicht geladen werden")
                    }
                    .collect { orders ->
                        allOrders = orders.filter { !it.isDemoOrder }
                        recalculate()
                    }
            }

            launch {
                // The books: sales of the selected period, re-read when it changes
                _period
                    .flatMapLatest { period ->
                        saleRepository.observeSales(sellerId, period.startMillis(timeZone), period.endMillis(timeZone))
                            .map<List<Sale>, AsyncState<PeriodSummary>> { sales ->
                                AsyncState.Success(summarize(period, sales))
                            }
                            .onStart { emit(AsyncState.Loading) }
                            .catch { emit(AsyncState.Error("Buchungen konnten nicht geladen werden")) }
                    }
                    .collect { _periodSummary.value = it }
            }
        }
    }

    private fun recalculate() {
        calculatePickupSummary()
    }

    private fun lookupArticle(item: OrderedProduct): SellerArticle? = articlesById.articleFor(item)

    private fun calculatePickupSummary() {
        val tz = TimeZone.currentSystemDefault()
        val nextPickupInstant = OrderDateUtils.calculateNextPickupDate()
        val nextPickupDate = nextPickupInstant.toLocalDateTime(tz).date

        val pickupOrders = allOrders.filter { order ->
            val isActive = order.status == OrderStatus.PLACED || order.status == OrderStatus.LOCKED
            if (!isActive) return@filter false
            val orderDate = Instant.fromEpochMilliseconds(order.pickUpDate).toLocalDateTime(tz).date
            orderDate == nextPickupDate
        }

        val aggregated = aggregateItems(pickupOrders)

        _pickupSummary.value = AsyncState.Success(
            PickupSummary(
                pickupDateMs = nextPickupInstant.toEpochMilliseconds(),
                orderCount = pickupOrders.size,
                customerCount = pickupOrders.map { it.buyerProfile.id }.filter { it.isNotEmpty() }.toSet().size,
                aggregatedItems = aggregated.sortedBy { it.productName },
                financials = computeFinancials(aggregated)
            )
        )
    }

    fun selectTab(tab: AbrechnungTab) {
        _selectedTab.value = tab
    }

    // ----- Export -----

    private val _export = MutableStateFlow(ExportState())
    val export: StateFlow<ExportState> = _export.asStateFlow()

    /** Writes the shown period's sales as CSV and opens the share sheet. */
    fun exportPeriod() {
        if (_export.value.isExporting) return
        val period = _period.value
        viewModelScope.launch {
            val sellerId = authRepository.getCurrentUserId() ?: return@launch
            _export.value = ExportState(isExporting = true)
            try {
                // Read afresh rather than reuse the summary: the export must be complete.
                val sales = saleRepository
                    .observeSales(sellerId, period.startMillis(timeZone), period.endMillis(timeZone))
                    .first()
                if (sales.isEmpty()) {
                    _export.value = ExportState(message = ExportMessage.NOTHING_TO_EXPORT)
                    return@launch
                }
                val result = fileSharer.shareTextFile(
                    fileName = SalesCsv.fileName(period),
                    mimeType = "text/csv",
                    content = SalesCsv.write(sales, timeZone)
                )
                _export.value = ExportState(message = if (result.isSuccess) null else ExportMessage.FAILED)
            } catch (e: Exception) {
                println("❌ AbrechnungViewModel.exportPeriod: ${e.message}")
                _export.value = ExportState(message = ExportMessage.FAILED)
            }
        }
    }

    fun clearExportMessage() {
        _export.value = _export.value.copy(message = null)
    }

    // ----- Period navigation -----

    fun setPeriodType(type: PeriodType) {
        val current = _period.value
        // Keep the view where it is: the week or month containing the current start,
        // or today if the current period is the running one.
        val anchor = today().takeIf { it in current } ?: current.start
        _period.value = when (type) {
            PeriodType.WEEK -> BookingPeriod.weekOf(anchor)
            PeriodType.MONTH -> BookingPeriod.monthOf(anchor)
        }
    }

    fun previousPeriod() {
        _period.value = _period.value.previous()
    }

    fun nextPeriod() {
        if (canGoNext(_period.value)) _period.value = _period.value.next()
    }

    /** No periods in the future: there is nothing booked there yet. */
    fun canGoNext(period: BookingPeriod): Boolean = period.next().start <= today()

    private fun summarize(period: BookingPeriod, sales: List<Sale>): PeriodSummary =
        PeriodSummary(
            period = period,
            saleCount = sales.count { !it.isReversal },
            cancellationCount = sales.count { it.isReversal },
            financials = sales.toFinancials()
        )

    private fun aggregateItems(orders: List<Order>): List<AggregatedItem> {
        val grouped = mutableMapOf<String, AggregatedItem>()
        for (order in orders) {
            for (op in order.articles) {
                val article = lookupArticle(op)
                val taxRate = article?.article?.taxRate ?: TaxRate.REDUCED.rate
                val acquirePricePerUnit = article?.sellerData
                    ?.takeIf { it.hasAcquirePrice }
                    ?.acquirePrice
                val lineTotal = op.getTotalPrice()

                val existing = grouped[op.productId]
                if (existing == null) {
                    grouped[op.productId] = AggregatedItem(
                        productId = op.productId,
                        productName = op.productName,
                        unit = op.unit,
                        pricePerUnit = op.price,
                        totalQuantity = op.amountCount,
                        totalGross = lineTotal,
                        taxRate = taxRate,
                        acquirePricePerUnit = acquirePricePerUnit
                    )
                } else {
                    grouped[op.productId] = existing.copy(
                        totalQuantity = existing.totalQuantity + op.amountCount,
                        totalGross = existing.totalGross + lineTotal
                    )
                }
            }
        }
        return grouped.values.toList()
    }

    private fun computeFinancials(items: List<AggregatedItem>): OrderFinancials {
        var grossTotal = 0.0
        var vatAmount7 = 0.0
        var vatAmount19 = 0.0
        var acquireCost = 0.0
        var itemsWithoutAcquirePrice = 0

        for (item in items) {
            grossTotal += item.totalGross
            // Prices are gross (brutto); extract embedded VAT component
            val vat = item.totalGross * item.taxRate / (1.0 + item.taxRate)
            when (TaxRate.fromRate(item.taxRate)) {
                TaxRate.REDUCED -> vatAmount7 += vat
                TaxRate.STANDARD -> vatAmount19 += vat
                TaxRate.ZERO -> Unit
            }
            val acquirePrice = item.acquirePricePerUnit
            if (acquirePrice != null) {
                acquireCost += acquirePrice * item.totalQuantity
            } else {
                itemsWithoutAcquirePrice++
            }
        }

        return OrderFinancials(
            grossTotal = grossTotal,
            vatAmount7 = vatAmount7,
            vatAmount19 = vatAmount19,
            netTotal = grossTotal - vatAmount7 - vatAmount19,
            acquireCost = acquireCost,
            grossProfit = (grossTotal - vatAmount7 - vatAmount19) - acquireCost,
            itemsWithoutAcquirePrice = itemsWithoutAcquirePrice
        )
    }
}

/**
 * The financial summary of booked sales, cancellations included. Everything is summed
 * in cents from the sale lines — the same amounts the export shows — and converted
 * to euros only for display.
 */
internal fun List<Sale>.toFinancials(): OrderFinancials {
    val lines = flatMap { it.lines }
    fun vatAt(rate: Double) = lines.filter { it.taxRate == rate }.sumOf { it.vatCents }
    val grossCents = lines.sumOf { it.grossCents }
    val netCents = lines.sumOf { it.netCents }
    val acquireCents = lines.sumOf { it.acquireCostCents ?: 0L }
    // A cancelled line cancels its count too, so the net count is what is still booked.
    val withoutAcquirePrice = lines.filter { it.acquirePriceCents == null }
        .sumOf { if (it.quantity < 0) -1 else 1 }
        .coerceAtLeast(0)
    return OrderFinancials(
        grossTotal = grossCents / 100.0,
        vatAmount7 = vatAt(TaxRate.REDUCED.rate) / 100.0,
        vatAmount19 = vatAt(TaxRate.STANDARD.rate) / 100.0,
        netTotal = netCents / 100.0,
        acquireCost = acquireCents / 100.0,
        grossProfit = (netCents - acquireCents) / 100.0,
        itemsWithoutAcquirePrice = withoutAcquirePrice
    )
}

enum class AbrechnungTab { PICKUP, PERIOD }

enum class PeriodType { WEEK, MONTH }

data class ExportState(
    val isExporting: Boolean = false,
    val message: ExportMessage? = null
)

enum class ExportMessage { NOTHING_TO_EXPORT, FAILED }

data class AggregatedItem(
    val productId: String,
    val productName: String,
    val unit: String,
    val pricePerUnit: Double,
    val totalQuantity: Double,
    val totalGross: Double,
    val taxRate: Double,
    val acquirePricePerUnit: Double?    // null = purchase price unknown
)

data class OrderFinancials(
    val grossTotal: Double,
    val vatAmount7: Double,
    val vatAmount19: Double,
    val netTotal: Double,
    val acquireCost: Double,            // Sum over items with a known purchase price
    val grossProfit: Double,
    val itemsWithoutAcquirePrice: Int = 0
) {
    val hasAcquireCost: Boolean get() = acquireCost > 0.01

    /** Gross profit leaves out items whose purchase price is unknown, overstating it. */
    val isGrossProfitIncomplete: Boolean get() = itemsWithoutAcquirePrice > 0
}

data class PickupSummary(
    val pickupDateMs: Long,
    val orderCount: Int,
    val customerCount: Int,
    val aggregatedItems: List<AggregatedItem>,
    val financials: OrderFinancials
)

data class PeriodSummary(
    val period: BookingPeriod,
    /** Sales booked in the period, not counting cancellations. */
    val saleCount: Int,
    val cancellationCount: Int,
    val financials: OrderFinancials
) {
    val isEmpty: Boolean get() = saleCount == 0 && cancellationCount == 0
}
