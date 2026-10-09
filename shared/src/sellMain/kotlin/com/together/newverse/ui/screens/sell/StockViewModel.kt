package com.together.newverse.ui.screens.sell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.together.newverse.domain.model.MeasuredQuantity
import com.together.newverse.domain.model.ProductPricing
import com.together.newverse.domain.model.QuantitySource
import com.together.newverse.domain.model.RefillNeed
import com.together.newverse.domain.model.SellerArticle
import com.together.newverse.domain.model.StockLevel
import com.together.newverse.domain.model.StockMovement
import com.together.newverse.domain.model.StockMovementKind
import com.together.newverse.domain.model.refillNeeds
import com.together.newverse.domain.model.refillStanding
import com.together.newverse.domain.repository.AuthRepository
import com.together.newverse.domain.repository.SellerArticleRepository
import com.together.newverse.domain.repository.StockRepository
import com.together.newverse.domain.scale.ScaleStatus
import com.together.newverse.domain.scale.WeightSource
import com.together.newverse.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock

/**
 * What is in storage, what needs refilling, and the bookings that change it.
 *
 * The screen's purpose is the refill list: the articles the seller watches that have
 * run out, run low, or have never been counted. Everything else — the full standing,
 * the bookings — exists to keep that list honest.
 *
 * Quantities arrive either from the keyboard or from a connected scale, and the
 * difference is recorded: see [MeasuredQuantity] and [QuantitySource]. With no scale
 * bound (the case today, and permanently on iOS) [WeightSource] reports
 * [ScaleStatus.Unsupported] and the screen simply never offers to weigh.
 */
class StockViewModel(
    private val sellerArticleRepository: SellerArticleRepository,
    private val stockRepository: StockRepository,
    private val authRepository: AuthRepository,
    private val weightSource: WeightSource,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() }
) : ViewModel() {

    private companion object {
        private const val TAG = "StockViewModel"
    }

    private val _state = MutableStateFlow(StockUiState())
    val state: StateFlow<StockUiState> = _state.asStateFlow()

    private var catalog = listOf<SellerArticle>()
    private var levels = mapOf<String, StockLevel>()

    init {
        observeStock()
        observeScale()
    }

    private fun observeStock() {
        viewModelScope.launch {
            val sellerId = authRepository.getCurrentUserId() ?: return@launch
            combine(
                sellerArticleRepository.observeSellerArticles(sellerId),
                stockRepository.observeLevels(sellerId)
            ) { articles, byArticle -> articles to byArticle }
                .catch { error ->
                    Log.w(TAG) { "observeStock: ${error.message}" }
                    _state.update { it.copy(isLoading = false, message = StockMessage.LOAD_FAILED) }
                }
                .collect { (articles, byArticle) ->
                    catalog = articles
                    levels = byArticle
                    _state.update { it.withStock() }
                }
        }
    }

    private fun observeScale() {
        viewModelScope.launch {
            weightSource.status.collect { status ->
                _state.update { it.copy(scaleStatus = status) }
            }
        }
        viewModelScope.launch {
            weightSource.readings.collect { reading ->
                _state.update { it.copy(scaleReading = reading) }
            }
        }
    }

    /** Recompute the derived lists from the catalog and levels last seen. */
    private fun StockUiState.withStock(): StockUiState = copy(
        isLoading = false,
        refillNeeds = catalog.refillNeeds(levels),
        standing = catalog.refillStanding(levels)
            .sortedBy { it.productName.lowercase() },
        hasWatchedArticles = catalog.any { it.sellerData?.isWatched == true }
    )

    fun setQuery(query: String) {
        _state.update { it.copy(query = query) }
    }

    // ----- Booking -----

    /** Open the booking editor for one article. */
    fun startBooking(articleId: String, kind: StockMovementKind) {
        val article = catalog.firstOrNull { it.id == articleId } ?: return
        _state.update {
            it.copy(
                message = null,
                editor = StockEditor(
                    articleId = articleId,
                    productName = article.article.productName,
                    unit = article.article.unit,
                    kind = kind,
                    // A count starts from what the ledger believes, so the seller
                    // confirms or corrects rather than retyping.
                    quantityInput = if (kind == StockMovementKind.STOCKTAKE) {
                        levels[articleId]?.let { level -> ProductPricing.formatQuantity(level.onHand) } ?: ""
                    } else "",
                    source = QuantitySource.MANUAL
                )
            )
        }
    }

    /** The seller typed, so whatever the scale said no longer describes this value. */
    fun setQuantity(input: String) {
        _state.update { state ->
            state.copy(
                editor = state.editor?.copy(quantityInput = input, source = QuantitySource.MANUAL),
                message = null
            )
        }
    }

    fun setNote(note: String) {
        _state.update { state -> state.copy(editor = state.editor?.copy(note = note)) }
    }

    /**
     * Take the current reading into the quantity field, keeping it marked as weighed.
     *
     * Only a settled reading is accepted: a value still moving on the platform would
     * be booked as fact. See [MeasuredQuantity.isUsable].
     */
    fun fillFromScale() {
        val reading = _state.value.scaleReading
        if (reading == null || !reading.isUsable) {
            _state.update { it.copy(message = StockMessage.SCALE_NOT_READY) }
            return
        }
        _state.update { state ->
            state.copy(
                editor = state.editor?.copy(
                    quantityInput = reading.toInputString(),
                    source = QuantitySource.SCALE
                ),
                message = null
            )
        }
    }

    fun dismissBooking() {
        _state.update { it.copy(editor = null, message = null) }
    }

    fun book() {
        val editor = _state.value.editor ?: return
        val quantity = ProductPricing.parseDecimal(editor.quantityInput)
        // A stocktake may legitimately find nothing; the others move an amount.
        val valid = when (editor.kind) {
            StockMovementKind.STOCKTAKE -> quantity != null && quantity >= 0.0
            else -> quantity != null && quantity > 0.0
        }
        if (!valid) {
            _state.update { it.copy(message = StockMessage.INVALID_QUANTITY) }
            return
        }

        val article = catalog.firstOrNull { it.id == editor.articleId } ?: return
        val at = now()
        val amount = MeasuredQuantity(
            value = quantity!!,
            unit = article.article.unit,
            source = editor.source,
            measuredAt = at
        )
        val movement = when (editor.kind) {
            StockMovementKind.INTAKE -> StockMovement.intake(
                articleId = editor.articleId,
                amount = amount,
                recordedAt = at,
                productId = article.article.productId,
                note = editor.note
            )
            StockMovementKind.STOCKTAKE -> StockMovement.stocktake(
                articleId = editor.articleId,
                found = amount,
                previousOnHand = levels[editor.articleId]?.onHand ?: 0.0,
                recordedAt = at,
                productId = article.article.productId,
                note = editor.note
            )
            StockMovementKind.LOSS -> StockMovement.loss(
                articleId = editor.articleId,
                amount = amount,
                recordedAt = at,
                productId = article.article.productId,
                note = editor.note
            )
            // A sale is booked by selling, never from this screen.
            StockMovementKind.SALE -> return
        }

        viewModelScope.launch {
            val sellerId = authRepository.getCurrentUserId() ?: return@launch
            _state.update { it.copy(isSaving = true) }
            stockRepository.recordMovement(sellerId, movement)
                .onSuccess {
                    // The level arrives through observeLevels; nothing is set here.
                    _state.update { it.copy(isSaving = false, editor = null, booked = editor.kind) }
                }
                .onFailure { error ->
                    Log.w(TAG) { "book: ${error.message}" }
                    _state.update { it.copy(isSaving = false, message = StockMessage.SAVE_FAILED) }
                }
        }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    /** The screen has acknowledged the booking. */
    fun bookingShown() {
        _state.update { it.copy(booked = null) }
    }
}

data class StockUiState(
    val isLoading: Boolean = true,
    /** Watched articles wanting attention, most urgent first. */
    val refillNeeds: List<RefillNeed> = emptyList(),
    /** Every article, by name — the full standing, for looking something up. */
    val standing: List<RefillNeed> = emptyList(),
    /** False when no article has a reorder level, which is a different empty state. */
    val hasWatchedArticles: Boolean = false,
    val query: String = "",
    val editor: StockEditor? = null,
    val scaleStatus: ScaleStatus = ScaleStatus.Unsupported,
    val scaleReading: MeasuredQuantity? = null,
    val isSaving: Boolean = false,
    val message: StockMessage? = null,
    /** Set once a movement is recorded, until the screen has shown it. */
    val booked: StockMovementKind? = null
) {
    /** The standing filtered by [query]; the refill list is never filtered. */
    val matchingStanding: List<RefillNeed>
        get() {
            val q = query.trim().lowercase()
            if (q.isEmpty()) return standing
            return standing.filter { it.productName.lowercase().contains(q) }
        }

    /** Whether offering to weigh makes sense on this device. */
    val canWeigh: Boolean get() = scaleStatus.canWeigh
}

data class StockEditor(
    val articleId: String,
    val productName: String,
    val unit: String,
    val kind: StockMovementKind,
    val quantityInput: String,
    /** Where [quantityInput] came from; becomes the movement's provenance. */
    val source: QuantitySource,
    val note: String = ""
)

enum class StockMessage { LOAD_FAILED, INVALID_QUANTITY, SAVE_FAILED, SCALE_NOT_READY }
