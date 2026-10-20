package com.together.newverse.domain.model

import com.together.newverse.util.OrderDateUtils
import com.together.newverse.util.OrderWindowStatus
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime

/**
 * Represents a customer order
 */
data class Order(
    val id: String = "",
    val buyerProfile: BuyerProfile = BuyerProfile(),
    val createdDate: Long = 0L,
    val sellerId: String = "",
    val marketId: String = "",
    /**
     * The [Market.pricingRevision] the articles were priced against.
     *
     * Snapshotted so a price that was charged can always be told apart from the price
     * the same basket would cost today: the seller may edit conditions at any time, and
     * `/sales` has to reconcile against the order it came from. `0L` means the order
     * predates per-market pricing, or was placed at a market with no conditions.
     */
    val pricingRevision: Long = 0L,
    val pickUpDate: Long = 0L,
    val message: String = "",
    val notFavourite: Boolean = true,
    val articles: List<OrderedProduct> = emptyList(),
    val status: OrderStatus = OrderStatus.DRAFT,
    val hiddenBySeller: Boolean = false,
    val hiddenByBuyer: Boolean = false,
    val origin: OrderOrigin = OrderOrigin.PRODUCTION
) {
    /**
     * Check if this order can be edited based on current time
     */
    fun canEdit(now: Instant = Clock.System.now()): Boolean {
        // Draft orders can always be edited
        if (status == OrderStatus.DRAFT) return true

        // Finalized orders cannot be edited
        if (status.isFinalized()) return false

        // Before the deadline, which is a fixed distance back from this order's own
        // pickup date — two days at 23:59, i.e. Tuesday for a Thursday pickup and
        // Thursday for a Saturday one. Passing no config is not an oversight: see
        // OrderDateUtils.scheduleConfigFor for why the market's config computes the
        // same instant.
        return OrderDateUtils.canEditOrder(
            pickupDate = Instant.fromEpochMilliseconds(pickUpDate),
            now = now
        )
    }

    /**
     * The edit deadline for this order: two days before its pickup date at 23:59.
     *
     * Tuesday for a Thursday pickup, Thursday for a Saturday one — the distance is the
     * rule, not the weekday. Market-correct without being told the market; see
     * [OrderDateUtils.scheduleConfigFor].
     */
    fun getEditDeadline(): Instant {
        return OrderDateUtils.calculateEditDeadline(
            pickupDate = Instant.fromEpochMilliseconds(pickUpDate)
        )
    }

    /**
     * Get the order window status
     */
    fun getWindowStatus(now: Instant = Clock.System.now()): OrderWindowStatus {
        return OrderDateUtils.getOrderWindowStatus(
            pickupDate = Instant.fromEpochMilliseconds(pickUpDate),
            now = now
        )
    }

    /**
     * Check if this order is for the current ordering cycle
     */
    fun isCurrentCycle(now: Instant = Clock.System.now()): Boolean {
        return OrderDateUtils.isCurrentOrderingCycle(
            pickupDate = Instant.fromEpochMilliseconds(pickUpDate),
            now = now
        )
    }

    /**
     * Get formatted pickup date
     */
    fun getFormattedPickupDate(): String {
        return OrderDateUtils.formatDisplayDate(
            instant = Instant.fromEpochMilliseconds(pickUpDate)
        )
    }

    /**
     * Get formatted deadline
     */
    fun getFormattedDeadline(): String {
        return OrderDateUtils.formatDisplayDateTime(
            instant = getEditDeadline()
        )
    }

    /**
     * Get time remaining until deadline as formatted string
     */
    fun getTimeUntilDeadline(now: Instant = Clock.System.now()): String {
        return OrderDateUtils.formatTimeUntilDeadline(
            pickupDate = Instant.fromEpochMilliseconds(pickUpDate),
            now = now
        )
    }

    /**
     * Get formatted pickup date and time
     * Format: "Abholung DD.MM.YYYY um HH:MM Uhr"
     * Based on universe project's toPickUpText() extension
     */
    fun getFormattedPickupDateAndTime(): String {
        val instant = Instant.fromEpochMilliseconds(pickUpDate)
        val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())

        val day = localDateTime.day.toString().padStart(2, '0')
        val month = localDateTime.month.number.toString().padStart(2, '0')
        val year = localDateTime.year
        val hour = localDateTime.hour.toString().padStart(2, '0')
        val minute = localDateTime.minute.toString().padStart(2, '0')

        return "Abholung $day.$month.$year um $hour:$minute Uhr"
    }

    /**
     * Check if this order is still active (open and not outdated)
     * An order is active if:
     * - Status is not COMPLETED or CANCELLED
     * - Pickup date has not passed
     */
    fun isActiveOrder(now: Instant = Clock.System.now()): Boolean {
        // Check status first
        if (!status.isActive()) return false

        // Check if pickup date has passed
        val pickupInstant = Instant.fromEpochMilliseconds(pickUpDate)
        return now < pickupInstant
    }

    /**
     * Returns an updated order with correct status based on current time.
     * - PLACED -> LOCKED when Tuesday 23:59 deadline passes
     * - LOCKED -> COMPLETED when pickup date passes
     * Returns null if no status change needed.
     */
    fun transitionStatusIfNeeded(now: Instant = Clock.System.now()): Order? {
        val pickupInstant = Instant.fromEpochMilliseconds(pickUpDate)

        return when (status) {
            OrderStatus.PLACED -> {
                // Transition to LOCKED if deadline has passed
                if (!OrderDateUtils.canEditOrder(pickupInstant, now)) {
                    copy(status = OrderStatus.LOCKED)
                } else null
            }
            OrderStatus.LOCKED -> {
                // Transition to COMPLETED if pickup date has passed
                if (now > pickupInstant) {
                    copy(status = OrderStatus.COMPLETED)
                } else null
            }
            else -> null
        }
    }
}

/**
 * Creates a fresh local draft from a historical order, ready to be edited and placed.
 * This function prepares a new Order object with a DRAFT status, effectively creating a new
 * shopping basket from a previous order.
 *
 * The articles from the historical order are carried over to the new draft. Price correction for
 * these articles should be handled by the caller (e.g., in a ViewModel) by comparing against the
 * current product catalog.
 *
 * This function handles cases where the user may or may not have an existing draft basket.
 * The returned Order object is considered the new draft, replacing any previous one. The caller
 * is responsible for persisting this new draft order and removing any prior draft from the
 * data source.
 *
 * @param freshBuyerProfile The buyer's current profile. Any existing `draftBasket` on this
 *        profile is ignored, as the new draft is represented by the returned Order itself.
 * @param origin Provenance of the new draft — [OrderOrigin.DEMO] while the buyer is not
 *        yet approved by the seller.
 * @return A new `Order` instance, ready to be used as a draft for reordering.
 */
fun Order.prepareForReorder(
    freshBuyerProfile: BuyerProfile,
    origin: OrderOrigin = OrderOrigin.PRODUCTION
): Order = copy(
    id = "", // New draft must have a new ID, generated by data source
    buyerProfile = freshBuyerProfile.copy(draftBasket = null), // A draft order itself is the basket
    createdDate = Clock.System.now().toEpochMilliseconds(),
    pickUpDate = 0L, // Must be re-selected by the user
    status = OrderStatus.DRAFT,
    origin = origin
)

/**
 * The lifecycle status this order takes when it moves from `demo_orders/` into `orders/`
 * on the seller approving the buyer.
 *
 * A demo order carries no meaningful lifecycle — it was parked at PLACED (or, in records
 * written before [OrderOrigin] existed, at the former `DEMO_ORDER`). Once it is real, its
 * status has to say where in the week it actually sits, or the seller gets an order that
 * shows up in the list but cannot be booked at pickup.
 *
 * Only orders whose pickup is still ahead are worth moving; see
 * [canTransitionToProduction].
 */
fun Order.statusAfterTransition(now: Instant = Clock.System.now()): OrderStatus = when {
    status.isFinalized() -> status
    canEdit(now) -> OrderStatus.PLACED
    else -> OrderStatus.LOCKED
}

/**
 * Whether this demo order should follow the buyer into production.
 *
 * Only orders the seller can still act on do. A demo order whose pickup day has passed
 * describes a pickup that never happened, and moving it would plant a sale that was never
 * made in the seller's books — so it is dropped with the rest of the demo data instead.
 */
fun Order.canTransitionToProduction(now: Instant = Clock.System.now()): Boolean =
    !status.isFinalized() && pickUpDate > now.toEpochMilliseconds()
