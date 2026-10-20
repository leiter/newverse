package com.together.newverse.ui.screens.sell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.together.newverse.domain.model.ArticleDemand
import com.together.newverse.domain.model.MeasuredQuantity
import com.together.newverse.domain.model.ProductPricing
import com.together.newverse.domain.model.QuantitySource
import com.together.newverse.domain.model.Order
import com.together.newverse.domain.model.RefillNeed
import com.together.newverse.domain.model.RefillState
import com.together.newverse.domain.model.SellerArticle
import com.together.newverse.domain.model.StockLevel
import com.together.newverse.domain.model.StockMovement
import com.together.newverse.domain.model.OrderShortfall
import com.together.newverse.domain.model.StockMovementKind
import com.together.newverse.domain.model.draftShortfallMessage
import com.together.newverse.domain.model.shortfallsAgainst
import com.together.newverse.domain.model.availabilityCorrections
import com.together.newverse.domain.model.committedDemand
import com.together.newverse.domain.model.refillNeeds
import com.together.newverse.domain.model.refillStanding
import com.together.newverse.domain.repository.AuthRepository
import com.together.newverse.domain.repository.MessageRepository
import com.together.newverse.domain.repository.OrderRepository
import com.together.newverse.domain.repository.SellerArticleRepository
import com.together.newverse.domain.repository.StockRepository
import com.together.newverse.data.config.StockReminderStorage
import com.together.newverse.domain.reminder.StockReminderScheduler
import com.together.newverse.domain.reminder.StockReminderSnapshots
import com.together.newverse.domain.scale.ScaleStatus
import com.together.newverse.domain.scale.WeightSource
import com.together.newverse.util.OrderDateUtils
import com.together.newverse.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * What is in storage, what buyers have already bought out of it, what needs
 * refilling, and the bookings that change it.
 *
 * The screen's purpose is the refill list: the articles that have been oversold, run
 * out, run low, or have never been counted. Everything else — the full standing, the
 * bookings — exists to keep that list honest.
 *
 * Outstanding orders are read as well as stock, because a list built on what is left
 * in storage alone warns about last week's leftovers and stays silent when ten buyers
 * have ordered goods that are not there. See
 * `doc/design/stock-driven-availability-design.md` §2.
 *
 * Quantities arrive either from the keyboard or from a connected scale, and the
 * difference is recorded: see [MeasuredQuantity] and [QuantitySource]. With no scale
 * bound (the case today, and permanently on iOS) [WeightSource] reports
 * [ScaleStatus.Unsupported] and the screen simply never offers to weigh.
 */
class StockViewModel(
    private val sellerArticleRepository: SellerArticleRepository,
    private val stockRepository: StockRepository,
    private val orderRepository: OrderRepository,
    private val authRepository: AuthRepository,
    private val weightSource: WeightSource,
    /** Null in tests that do not exercise telling a buyer about a shortfall. */
    private val messageRepository: MessageRepository? = null,
    /** Null where no platform reminder exists; the refill list itself still works. */
    private val reminderStorage: StockReminderStorage? = null,
    private val reminderScheduler: StockReminderScheduler? = null,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() }
) : ViewModel() {

    private companion object {
        private const val TAG = "StockViewModel"
    }

    private val _state = MutableStateFlow(StockUiState())
    val state: StateFlow<StockUiState> = _state.asStateFlow()

    private var catalog = listOf<SellerArticle>()
    private var levels = mapOf<String, StockLevel>()
    private var demand = mapOf<String, ArticleDemand>()
    private var orders = listOf<Order>()

    /** The last set of corrections written or attempted; see [correctAvailability]. */
    private var attempted = mapOf<String, Boolean>()

    init {
        reminderStorage?.let { storage ->
            _state.update { it.copy(reminderEnabled = storage.load().enabled) }
        }
        observeStock()
        observeScale()
    }

    private fun observeStock() {
        viewModelScope.launch {
            val sellerId = authRepository.getCurrentUserId() ?: return@launch
            // Orders are observed alongside stock rather than polled: committed
            // demand rises without the seller touching anything, so the refill list
            // has to re-derive when an order arrives, not only when a movement does.
            combine(
                sellerArticleRepository.observeSellerArticles(sellerId),
                stockRepository.observeLevels(sellerId),
                orderRepository.observeSellerOrders(sellerId)
            ) { articles, byArticle, orders -> Triple(articles, byArticle, orders) }
                .catch { error ->
                    Log.w(TAG) { "observeStock: ${error.message}" }
                    _state.update { it.copy(isLoading = false, message = StockMessage.LOAD_FAILED) }
                }
                .collect { (articles, byArticle, sellerOrders) ->
                    catalog = articles
                    levels = byArticle
                    orders = sellerOrders
                    // Every outstanding order, not one pickup slot: this is the list
                    // the seller buys from, and the ordering rules only ever open the
                    // next slot per market anyway.
                    demand = sellerOrders.committedDemand()
                    _state.update { it.withStock() }
                    correctAvailability()
                    cacheReminder(sellerOrders)
                }
        }
    }

    /**
     * Brings the public availability flag of gated articles in line with the ledger.
     *
     * Runs here rather than in a service because only the seller's device may write
     * `/articles` (database rules), and because stock only moves when this app writes
     * a movement. Demand is the exception — a buyer ordering raises it while the app
     * is closed — which is why this runs on every catalogue, level *and* order
     * emission, not only after a booking.
     *
     * Writes only differences, so it settles: the write re-emits the catalogue, and
     * the next pass finds nothing to correct. [attempted] keeps a failed write from
     * being retried in a tight loop against a listener that keeps re-emitting; the
     * next genuine change clears it.
     */
    private fun correctAvailability() {
        val corrections = catalog.availabilityCorrections(levels, demand)
        if (corrections.isEmpty()) {
            attempted = emptyMap()
            return
        }
        if (corrections == attempted) return
        attempted = corrections

        viewModelScope.launch {
            val sellerId = authRepository.getCurrentUserId() ?: return@launch
            sellerArticleRepository.setArticleAvailability(sellerId, corrections)
                .onSuccess {
                    Log.d(TAG) { "correctAvailability: ${corrections.size} article(s) -> $corrections" }
                }
                .onFailure { error ->
                    // Not surfaced: the seller did not ask for this, and the refill
                    // list they are looking at is still correct. The next emission
                    // tries again.
                    Log.w(TAG) { "correctAvailability: ${error.message}" }
                }
        }
    }

    /**
     * Leaves the coming market's buy list where the background worker can find it,
     * and arms the reminder for it.
     *
     * The worker wakes with no database (see [StockReminderStorage]), so the numbers
     * have to be written while the app is running. Re-armed on every recalculation
     * so what is scheduled always matches what it would say.
     *
     * The day named is the next pickup the seller has an order for, or — with no
     * orders yet — the next one the schedule produces. The list itself is every
     * shortfall, not only that day's: it is a shopping list, and the seller buys for
     * the whole week at once. `doc/design/stock-driven-availability-design.md` §2.
     */
    private fun cacheReminder(orders: List<Order>) {
        val storage = reminderStorage ?: return
        storage.saveSnapshot(
            StockReminderSnapshots.of(
                needs = _state.value.refillNeeds,
                orders = orders,
                nowMillis = now()
            )
        )
        reminderScheduler?.reschedule()
    }

    /** Whether the seller wants the refill list before each market day. */
    fun setReminderEnabled(enabled: Boolean) {
        val storage = reminderStorage ?: return
        storage.save(storage.load().copy(enabled = enabled))
        _state.update { it.copy(reminderEnabled = enabled) }
        if (enabled) reminderScheduler?.reschedule() else reminderScheduler?.cancel()
    }

    // ----- Telling a buyer about a shortfall -----

    /**
     * Opens an editable draft of the message to one affected buyer.
     *
     * A draft rather than a send: a shortfall is usually still fixable, and an
     * automatic "we cannot deliver" is a message the seller has to walk back. The
     * decision — substitute, part-fill, call them — stays theirs.
     * `doc/design/stock-driven-availability-design.md` §5.
     */
    fun startShortfallMessage(orderId: String) {
        val shortfall = _state.value.shortfalls.firstOrNull { it.orderId == orderId } ?: return
        if (shortfall.buyerId.isEmpty()) {
            // An order from before buyer ids were stored, or a walk-in: there is
            // nobody to send it to, and a draft that cannot be sent is a dead end.
            _state.update { it.copy(message = StockMessage.NO_BUYER_TO_MESSAGE) }
            return
        }
        _state.update {
            it.copy(
                message = null,
                messageDraft = ShortfallDraft(
                    orderId = shortfall.orderId,
                    buyerId = shortfall.buyerId,
                    buyerName = shortfall.buyerName,
                    text = draftShortfallMessage(shortfall)
                )
            )
        }
    }

    fun setShortfallMessage(text: String) {
        _state.update { state -> state.copy(messageDraft = state.messageDraft?.copy(text = text)) }
    }

    fun dismissShortfallMessage() {
        _state.update { it.copy(messageDraft = null) }
    }

    /** Sends the draft as it now stands, into the conversation with that buyer. */
    fun sendShortfallMessage() {
        val draft = _state.value.messageDraft ?: return
        val repository = messageRepository ?: return
        val text = draft.text.trim()
        if (text.isEmpty()) return

        viewModelScope.launch {
            val sellerId = authRepository.getCurrentUserId() ?: return@launch
            _state.update { it.copy(isSendingMessage = true) }

            // The conversation may not exist yet — this is often the first thing the
            // seller ever writes to this buyer.
            repository.getOrCreateConversation(
                userAId = sellerId,
                userAName = "",
                userBId = draft.buyerId,
                userBName = draft.buyerName
            ).onSuccess { conversation ->
                repository.sendMessage(
                    conversationId = conversation.id,
                    senderId = sellerId,
                    senderName = "",
                    recipientId = draft.buyerId,
                    recipientName = draft.buyerName,
                    text = text
                ).onSuccess {
                    _state.update {
                        it.copy(isSendingMessage = false, messageDraft = null, message = StockMessage.MESSAGE_SENT)
                    }
                    Log.d(TAG) { "sendShortfallMessage: sent for order ${draft.orderId}" }
                }.onFailure { error ->
                    Log.w(TAG) { "sendShortfallMessage: ${error.message}" }
                    _state.update { it.copy(isSendingMessage = false, message = StockMessage.MESSAGE_FAILED) }
                }
            }.onFailure { error ->
                Log.w(TAG) { "sendShortfallMessage: conversation - ${error.message}" }
                _state.update { it.copy(isSendingMessage = false, message = StockMessage.MESSAGE_FAILED) }
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
        refillNeeds = catalog.refillNeeds(levels, demand),
        standing = catalog.refillStanding(levels, demand)
            .sortedBy { it.productName.lowercase() },
        // Who is actually waiting for what is missing. Derived from the same needs
        // the list above shows, so the two can never disagree.
        shortfalls = orders.shortfallsAgainst(catalog.refillNeeds(levels, demand)),
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
    /** Outstanding orders holding something that is short, soonest pickup first. */
    val shortfalls: List<OrderShortfall> = emptyList(),
    /** The message being written to one affected buyer, if any. */
    val messageDraft: ShortfallDraft? = null,
    /** False when no article has a reorder level, which is a different empty state. */
    val hasWatchedArticles: Boolean = false,
    val query: String = "",
    val editor: StockEditor? = null,
    val scaleStatus: ScaleStatus = ScaleStatus.Unsupported,
    val scaleReading: MeasuredQuantity? = null,
    val isSaving: Boolean = false,
    /** Whether a message to a buyer is on its way; separate from [isSaving]. */
    val isSendingMessage: Boolean = false,
    /** Whether the refill reminder is switched on; false where no platform has one. */
    val reminderEnabled: Boolean = false,
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

/** A message to one buyer about their order, as the seller is editing it. */
data class ShortfallDraft(
    val orderId: String,
    val buyerId: String,
    val buyerName: String,
    val text: String
)

enum class StockMessage {
    LOAD_FAILED,
    INVALID_QUANTITY,
    SAVE_FAILED,
    SCALE_NOT_READY,
    NO_BUYER_TO_MESSAGE,
    MESSAGE_SENT,
    MESSAGE_FAILED
}
