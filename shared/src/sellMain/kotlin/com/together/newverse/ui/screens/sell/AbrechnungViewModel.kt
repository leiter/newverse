package com.together.newverse.ui.screens.sell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.together.newverse.domain.model.Article
import com.together.newverse.domain.model.Article.Companion.MODE_ADDED
import com.together.newverse.domain.model.Article.Companion.MODE_CHANGED
import com.together.newverse.domain.model.Article.Companion.MODE_REMOVED
import com.together.newverse.domain.model.Order
import com.together.newverse.domain.model.OrderStatus
import com.together.newverse.domain.model.TaxRate
import com.together.newverse.domain.repository.ArticleRepository
import com.together.newverse.domain.repository.AuthRepository
import com.together.newverse.domain.repository.OrderRepository
import com.together.newverse.ui.state.core.AsyncState
import com.together.newverse.util.OrderDateUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

class AbrechnungViewModel(
    private val articleRepository: ArticleRepository,
    private val orderRepository: OrderRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(AbrechnungTab.PICKUP)
    val selectedTab: StateFlow<AbrechnungTab> = _selectedTab.asStateFlow()

    private val _selectedPeriod = MutableStateFlow(PeriodFilter.MONTH)
    val selectedPeriod: StateFlow<PeriodFilter> = _selectedPeriod.asStateFlow()

    private val _pickupSummary = MutableStateFlow<AsyncState<PickupSummary>>(AsyncState.Loading)
    val pickupSummary: StateFlow<AsyncState<PickupSummary>> = _pickupSummary.asStateFlow()

    private val _periodSummary = MutableStateFlow<AsyncState<PeriodSummary>>(AsyncState.Loading)
    val periodSummary: StateFlow<AsyncState<PeriodSummary>> = _periodSummary.asStateFlow()

    // productId → Article for tax/acquire cost lookups; id as fallback key
    private val articlesByProductId = mutableMapOf<String, Article>()
    private val articlesById = mutableMapOf<String, Article>()
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
                // Article lookup is best-effort; missing taxRate falls back to 7 %
                articleRepository.getArticles(sellerId)
                    .catch { }
                    .collect { article ->
                        when (article.mode) {
                            MODE_ADDED, MODE_CHANGED -> {
                                articlesByProductId[article.productId] = article
                                articlesById[article.id] = article
                            }
                            MODE_REMOVED -> {
                                articlesByProductId.remove(article.productId)
                                articlesById.remove(article.id)
                            }
                        }
                        recalculate()
                    }
            }

            launch {
                orderRepository.observeSellerOrders(sellerId)
                    .catch {
                        val msg = "Bestellungen konnten nicht geladen werden"
                        _pickupSummary.value = AsyncState.Error(msg)
                        _periodSummary.value = AsyncState.Error(msg)
                    }
                    .collect { orders ->
                        allOrders = orders.filter { !it.isDemoOrder }
                        recalculate()
                    }
            }
        }
    }

    private fun recalculate() {
        calculatePickupSummary()
        calculatePeriodSummary()
    }

    private fun lookupArticle(productId: String): Article? =
        articlesByProductId[productId] ?: articlesById[productId]

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

    fun setPeriod(period: PeriodFilter) {
        _selectedPeriod.value = period
        calculatePeriodSummary()
    }

    private fun calculatePeriodSummary() {
        val nowMs = Clock.System.now().toEpochMilliseconds()
        val cutoffMs: Long = when (_selectedPeriod.value) {
            PeriodFilter.WEEK -> nowMs - 7L * 24 * 3600 * 1000
            PeriodFilter.MONTH -> nowMs - 30L * 24 * 3600 * 1000
            PeriodFilter.ALL -> 0L
        }

        val periodOrders = allOrders.filter { order ->
            order.status == OrderStatus.COMPLETED && order.pickUpDate >= cutoffMs
        }

        val aggregated = aggregateItems(periodOrders)

        _periodSummary.value = AsyncState.Success(
            PeriodSummary(
                orderCount = periodOrders.size,
                customerCount = periodOrders.map { it.buyerProfile.id }.filter { it.isNotEmpty() }.toSet().size,
                financials = computeFinancials(aggregated)
            )
        )
    }

    private fun aggregateItems(orders: List<Order>): List<AggregatedItem> {
        val grouped = mutableMapOf<String, AggregatedItem>()
        for (order in orders) {
            for (op in order.articles) {
                val article = lookupArticle(op.productId)
                val taxRate = article?.taxRate ?: TaxRate.REDUCED.rate
                val acquirePricePerUnit = article?.acquirePrice ?: 0.0
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

        for (item in items) {
            grossTotal += item.totalGross
            // Prices are gross (brutto); extract embedded VAT component
            val vat = item.totalGross * item.taxRate / (1.0 + item.taxRate)
            when (TaxRate.fromRate(item.taxRate)) {
                TaxRate.REDUCED -> vatAmount7 += vat
                TaxRate.STANDARD -> vatAmount19 += vat
                TaxRate.ZERO -> Unit
            }
            acquireCost += item.acquirePricePerUnit * item.totalQuantity
        }

        return OrderFinancials(
            grossTotal = grossTotal,
            vatAmount7 = vatAmount7,
            vatAmount19 = vatAmount19,
            netTotal = grossTotal - vatAmount7 - vatAmount19,
            acquireCost = acquireCost,
            grossProfit = (grossTotal - vatAmount7 - vatAmount19) - acquireCost
        )
    }
}

enum class AbrechnungTab { PICKUP, PERIOD }

enum class PeriodFilter { WEEK, MONTH, ALL }

data class AggregatedItem(
    val productId: String,
    val productName: String,
    val unit: String,
    val pricePerUnit: Double,
    val totalQuantity: Double,
    val totalGross: Double,
    val taxRate: Double,
    val acquirePricePerUnit: Double
)

data class OrderFinancials(
    val grossTotal: Double,
    val vatAmount7: Double,
    val vatAmount19: Double,
    val netTotal: Double,
    val acquireCost: Double,
    val grossProfit: Double
) {
    val hasAcquireCost: Boolean get() = acquireCost > 0.01
}

data class PickupSummary(
    val pickupDateMs: Long,
    val orderCount: Int,
    val customerCount: Int,
    val aggregatedItems: List<AggregatedItem>,
    val financials: OrderFinancials
)

data class PeriodSummary(
    val orderCount: Int,
    val customerCount: Int,
    val financials: OrderFinancials
)
