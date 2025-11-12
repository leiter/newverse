package com.together.newverse.domain.model

import com.together.newverse.util.OrderDateUtils
import com.together.newverse.util.OrderWindowStatus
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Represents a customer order
 */
data class Order(
    val id: String = "",
    val buyerProfile: BuyerProfile = BuyerProfile(),
    val createdDate: Long = 0L,
    val sellerId: String = "",
    val marketId: String = "",
    val pickUpDate: Long = 0L,
    val message: String = "",
    val notFavourite: Boolean = true,
    val articles: List<OrderedProduct> = emptyList(),
    val status: OrderStatus = OrderStatus.DRAFT
) {
    /**
     * Check if this order can be edited based on current time
     */
    fun canEdit(now: Instant = Clock.System.now()): Boolean {
        // Draft orders can always be edited
        if (status == OrderStatus.DRAFT) return true

        // Finalized orders cannot be edited
        if (status.isFinalized()) return false

        // Check if before deadline (Tuesday 23:59)
        return OrderDateUtils.canEditOrder(
            pickupDate = Instant.fromEpochMilliseconds(pickUpDate),
            now = now
        )
    }

    /**
     * Get the edit deadline (Tuesday 23:59) for this order
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
}
