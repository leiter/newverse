package com.together.newverse.ui.screens.sell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.together.newverse.domain.model.Money
import com.together.newverse.domain.model.ProductPricing
import com.together.newverse.domain.model.Sale
import com.together.newverse.domain.model.SellerArticle
import com.together.newverse.domain.model.WalkInItem
import com.together.newverse.domain.model.walkInSale
import com.together.newverse.domain.repository.AuthRepository
import com.together.newverse.domain.repository.SaleRepository
import com.together.newverse.domain.repository.SellerArticleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock

/**
 * A sale at the market stall, without an app order: the seller picks articles from
 * the catalog, enters quantities and — prefilled with the catalog price — the price
 * actually charged, and books it as a [Sale].
 */
class WalkInSaleViewModel(
    private val sellerArticleRepository: SellerArticleRepository,
    private val saleRepository: SaleRepository,
    private val authRepository: AuthRepository,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() }
) : ViewModel() {

    private val _state = MutableStateFlow(WalkInUiState())
    val state: StateFlow<WalkInUiState> = _state.asStateFlow()

    private var catalog = listOf<SellerArticle>()

    init {
        viewModelScope.launch {
            val sellerId = authRepository.getCurrentUserId() ?: return@launch
            sellerArticleRepository.observeSellerArticles(sellerId)
                .catch { _state.update { it.copy(message = WalkInMessage.CATALOG_FAILED) } }
                .collect { articles ->
                    // Available articles first, then by name: what is on the stall today.
                    catalog = articles.sortedWith(
                        compareBy<SellerArticle>({ !it.article.available }, { it.article.productName.lowercase() })
                    )
                    _state.update { it.copy(results = search(it.query)) }
                }
        }
    }

    fun setQuery(query: String) {
        _state.update { it.copy(query = query, results = search(query)) }
    }

    private fun search(query: String): List<SellerArticle> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return catalog
        return catalog.filter { article ->
            article.article.productName.lowercase().contains(q) ||
                article.article.searchTerms.lowercase().contains(q) ||
                article.article.productId.contains(q)
        }
    }

    /** Add an article with quantity 1 at its catalog price; an article already added stays once. */
    fun add(article: SellerArticle) {
        _state.update { state ->
            if (state.lines.any { it.article.id == article.id }) state
            else state.copy(
                message = null,
                lines = state.lines + WalkInLine(
                    article = article,
                    quantityInput = "1",
                    priceInput = Money.formatCents(Money.toCents(article.article.price))
                )
            )
        }
    }

    fun setQuantity(index: Int, input: String) = updateLine(index) { it.copy(quantityInput = input) }

    fun setPrice(index: Int, input: String) = updateLine(index) { it.copy(priceInput = input) }

    fun remove(index: Int) {
        _state.update { state -> state.copy(lines = state.lines.filterIndexed { i, _ -> i != index }, message = null) }
    }

    private fun updateLine(index: Int, change: (WalkInLine) -> WalkInLine) {
        _state.update { state ->
            state.copy(lines = state.lines.mapIndexed { i, line -> if (i == index) change(line) else line }, message = null)
        }
    }

    fun book() {
        val lines = _state.value.lines
        if (lines.isEmpty()) {
            _state.update { it.copy(message = WalkInMessage.NOTHING_SELECTED) }
            return
        }
        val items = lines.map { line ->
            val quantity = ProductPricing.parseDecimal(line.quantityInput)
            val price = ProductPricing.parseDecimal(line.priceInput)
            if (quantity == null || quantity <= 0.0 || price == null || price <= 0.0) {
                _state.update { it.copy(message = WalkInMessage.INVALID_INPUT) }
                return
            }
            WalkInItem(line.article, quantity, price)
        }

        viewModelScope.launch {
            val sellerId = authRepository.getCurrentUserId() ?: return@launch
            _state.update { it.copy(isSaving = true) }
            val at = now()
            val sale = walkInSale(items, confirmedAt = at, orderId = Sale.newWalkInOrderId(at))
            saleRepository.recordSale(sellerId, sale)
                .onSuccess { stored ->
                    _state.update {
                        it.copy(isSaving = false, lines = emptyList(), query = "", results = catalog, booked = stored)
                    }
                }
                .onFailure {
                    _state.update { it.copy(isSaving = false, message = WalkInMessage.SAVE_FAILED) }
                }
        }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    /** The screen has shown the booking; start the next sale from scratch. */
    fun bookingShown() {
        _state.update { it.copy(booked = null) }
    }
}

data class WalkInUiState(
    val query: String = "",
    /** Catalog articles matching [query]. */
    val results: List<SellerArticle> = emptyList(),
    val lines: List<WalkInLine> = emptyList(),
    val isSaving: Boolean = false,
    val message: WalkInMessage? = null,
    /** Set once a sale is booked, until the screen has shown it. */
    val booked: Sale? = null
) {
    /** Running total in cents; lines not yet valid count as nothing. */
    val totalCents: Long
        get() = lines.sumOf { line ->
            val quantity = ProductPricing.parseDecimal(line.quantityInput) ?: 0.0
            val price = ProductPricing.parseDecimal(line.priceInput) ?: 0.0
            Money.roundHalfAwayFromZero(quantity * Money.toCents(price))
        }
}

data class WalkInLine(
    val article: SellerArticle,
    val quantityInput: String,
    /** Gross price per unit as typed; prefilled with the catalog price. */
    val priceInput: String
)

enum class WalkInMessage { NOTHING_SELECTED, INVALID_INPUT, SAVE_FAILED, CATALOG_FAILED }
