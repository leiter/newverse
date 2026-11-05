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
     * How this order reaches the buyer, as chosen when it was placed.
     *
     * Snapshotted rather than read from the buyer's profile, which the seller cannot
     * read at all. A buyer who switches to delivery afterwards does not convert an
     * order the seller may already have packed for the stall — they edit the order,
     * which re-snapshots this and respects the deadline.
     */
    val fulfilment: Fulfilment = Fulfilment.PICKUP,
    /** Where to deliver, for a [Fulfilment.DELIVERY] order. Empty for a pickup. */
    val deliveryAddress: DeliveryAddress = DeliveryAddress.NONE,
    /**
     * When the buyer means to collect, as `HH:mm`. Empty for "whenever I get there".
     *
     * Per order and snapshotted, for the same reason as [deliveryAddress]: the seller
     * cannot read `buyer_profile`, so a time that lives only in the profile reaches
     * them not at all. The profile's `defaultPickUpTime` supplies the default and is
     * not rewritten by this — a buyer who comes later one week has not moved every
     * future order.
     *
     * Optional on purpose. It is a courtesy between two people who will meet at a
     * stall, not an appointment the seller has agreed to, and it used to be a required
     * profile field that blocked a buyer from even asking for access. Empty means the
     * buyer said nothing, which is a complete answer.
     *
     * Only meaningful for a [Fulfilment.PICKUP] order: a delivery arrives when the
     * seller's round reaches it. Written as empty for a delivery rather than carried
     * along, so an order switched from pickup to delivery cannot keep promising a time
     * nobody is keeping.
     */
    val pickupTime: String = "",
    /**
     * Who is collecting this order — or taking it in at the door.
     *
     * Pre-filled with the buyer's own name and editable per order, because the person
     * who placed it is routinely not the person who turns up: a flatmate, a partner,
     * a colleague sent round on the way home. The seller's card used to say the
     * account holder's name and nothing else, so the only way to tell them it would
     * be someone else was the free-text order message, if they thought of it.
     *
     * Empty means the buyer themselves. So does the buyer's own name left as it was
     * pre-filled — see [statedPickupName], which is what the seller's screens read, so
     * an untouched field adds no row saying what the card's title already says.
     *
     * Snapshotted like everything else on an order: the seller cannot read
     * `buyer_profile`, and a buyer who renames themselves afterwards has not changed
     * who is coming for an order already placed.
     */
    val pickupName: String = "",
    /**
     * A number to reach whoever is collecting, for this order.
     *
     * Optional, and the first time a phone number actually reaches the seller.
     * `BuyerProfile.telephoneNumber` has been collected on the profile page for as
     * long as there has been one, and `orderToMap` never wrote it: the seller's detail
     * screen has a phone row that could not fill. Asked here, pre-filled from the
     * profile, it is given for a specific order and can be cleared on any of them.
     *
     * Written only when it is not blank, like the delivery address — a number is not
     * data to scatter into orders that have no use for it. See
     * `doc/reports/buyer-profile-data-safety.md`.
     */
    val pickupPhone: String = "",
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
    /** Whether the seller has to take this order somewhere. */
    val isDelivery: Boolean get() = fulfilment == Fulfilment.DELIVERY

    /**
     * A delivery order the seller cannot actually deliver.
     *
     * Should never reach the database — the checkout gate refuses it — but orders
     * written before `fulfilment` existed carry neither flag nor address, and the
     * seller's list needs to be able to say so rather than show a blank.
     */
    val isDeliveryWithoutAddress: Boolean
        get() = isDelivery && !deliveryAddress.isComplete

    /** A collection time the buyer actually stated, for a pickup that has one. */
    val statedPickupTime: String?
        get() = pickupTime.takeIf { !isDelivery && it.isNotBlank() }

    /**
     * A collector worth naming: someone other than the buyer on the order.
     *
     * The seller's card is already titled with the buyer's name, so a field left as
     * it was pre-filled has nothing to add and is deliberately read as "no, it's me".
     */
    val statedPickupName: String?
        get() = pickupName
            .takeIf { it.isNotBlank() && it.trim() != buyerProfile.displayName.trim() }

    /** A number the buyer gave for this order, if they gave one. */
    val statedPickupPhone: String?
        get() = pickupPhone.takeIf { it.isNotBlank() }

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
     * Get time remaining until deadline as formatted string.
     *
     * English only — see [OrderDateUtils.formatTimeUntilDeadline]. No UI calls this;
     * a screen uses the composable `timeUntilDeadlineText` instead.
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
