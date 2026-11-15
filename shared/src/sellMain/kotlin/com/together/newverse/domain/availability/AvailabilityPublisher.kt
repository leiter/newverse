package com.together.newverse.domain.availability

import com.together.newverse.domain.model.ArticlePublication
import com.together.newverse.domain.model.committedDemand
import com.together.newverse.domain.model.publicationCorrections
import com.together.newverse.domain.repository.AuthRepository
import com.together.newverse.domain.repository.OrderRepository
import com.together.newverse.domain.repository.SellerArticleRepository
import com.together.newverse.domain.repository.StockRepository
import com.together.newverse.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Keeps the public half of the catalogue saying what the seller and the shelf say.
 *
 * `/articles.available` and `/articles.scarcity` are derived fields — outputs of
 * [publicationCorrections], not state anyone edits (see
 * `doc/guides/stock-driven-availability-design.md` § "`available` is derived, not
 * edited"). Something has to run that derivation and write the result, and only the
 * seller's device may write `/articles` at all: the database rules give
 * `/seller_articles` and `/stock` to `auth.uid === $sellerId`, so no other party can
 * even read the inputs. See `doc/design/derived-price-and-availability.md` § 2.
 *
 * ### Why this is not in a screen
 *
 * It used to live in `StockViewModel`, which meant the catalogue was only corrected
 * while the seller had the stock screen open. Two of the three inputs do move with
 * the seller's own actions — a movement, an edit — but the third does not: **committed
 * demand rises when a buyer orders**, with no seller app in the loop. So the last two
 * kilos of a gated article could be ordered at 21:00 and the article stayed on sale
 * until someone happened to open that one screen. Here it runs for as long as the app
 * does, whatever the seller is looking at.
 *
 * ### What it does not fix
 *
 * A closed app computes nothing. This narrows the stale window from "while that screen
 * is shut" to "while the app is shut"; it does not close it. Closing it needs the
 * derivation to run somewhere that is always awake — a trusted server process that may
 * read both halves — which this project has no setup for
 * (`doc/design/derived-price-and-availability.md` § 3). Until then availability is
 * eventually consistent with a bound of *next time the seller app runs*, and that is a
 * property of the product, not an oversight.
 *
 * ### Settling
 *
 * [publicationCorrections] returns only differences, so an evaluation that agrees with
 * the database writes nothing, and the write it does make re-emits the catalogue whose
 * next evaluation then finds nothing to do. [attempted] keeps a *failed* write from
 * being retried in a tight loop against a listener that keeps re-emitting; the next
 * genuine change clears it.
 */
class AvailabilityPublisher(
    private val sellerArticleRepository: SellerArticleRepository,
    private val stockRepository: StockRepository,
    private val orderRepository: OrderRepository,
    private val authRepository: AuthRepository,
    /**
     * The scope the watching runs in. [Dispatchers.Main] rather than `Default`
     * because that is what `viewModelScope` gave this work before it moved here, and
     * the work itself is a few comparisons over a catalogue — the listeners it waits
     * on do their own IO elsewhere. Overridden in tests, where `Dispatchers.setMain`
     * is what a test scheduler can drive.
     */
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
) {

    private companion object {
        private const val TAG = "AvailabilityPublisher"
    }
    private var job: Job? = null

    /** The last set of corrections written or attempted; see the class comment. */
    private var attempted = mapOf<String, ArticlePublication>()

    /**
     * Starts watching, idempotently: calling it twice leaves one watcher.
     *
     * Driven by [AuthRepository.observeAuthState] rather than a one-off
     * `getCurrentUserId()`, because this outlives any one session — a sign-out has to
     * stop the writes, and a sign-in as someone else must not carry the previous
     * seller's corrections over.
     */
    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            authRepository.observeAuthState()
                .distinctUntilChanged()
                .flatMapLatest { sellerId ->
                    attempted = emptyMap()
                    if (sellerId.isNullOrEmpty()) {
                        emptyFlow()
                    } else {
                        corrections(sellerId).map { sellerId to it }
                    }
                }
                .collect { (sellerId, corrections) -> publish(sellerId, corrections) }
        }
    }

    /** Stops watching. The catalogue is left exactly as it is. */
    fun stop() {
        job?.cancel()
        job = null
        attempted = emptyMap()
    }

    /** Releases the scope for good; the publisher cannot be started again. */
    fun close() {
        job = null
        attempted = emptyMap()
        scope.cancel()
    }

    /**
     * What the catalogue should say, recomputed on every change to any of its inputs.
     *
     * Orders are in here for the reason the class comment gives — they are the input
     * the seller does not control. The failure is caught per seller rather than on the
     * outer flow: a dropped listener would otherwise terminate the collector and the
     * publisher would stay silent for the rest of the app's life, where this way the
     * next sign-in state change starts it again.
     */
    private fun corrections(sellerId: String) = combine(
        sellerArticleRepository.observeSellerArticles(sellerId),
        stockRepository.observeLevels(sellerId),
        orderRepository.observeSellerOrders(sellerId)
    ) { articles, levels, orders ->
        articles.publicationCorrections(levels, orders.committedDemand())
    }.catch { error ->
        Log.w(TAG) { "corrections: ${error.message}" }
    }

    private suspend fun publish(sellerId: String, corrections: Map<String, ArticlePublication>) {
        if (corrections.isEmpty()) {
            attempted = emptyMap()
            return
        }
        if (corrections == attempted) return
        attempted = corrections

        sellerArticleRepository.setArticlePublication(sellerId, corrections)
            .onSuccess {
                Log.d(TAG) { "publish: ${corrections.size} article(s) -> $corrections" }
            }
            .onFailure { error ->
                // Not surfaced: the seller did not ask for this, and nothing they are
                // looking at is wrong because of it. The next emission tries again.
                Log.w(TAG) { "publish: ${error.message}" }
            }
    }
}
