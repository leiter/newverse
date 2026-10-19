package com.together.newverse.domain.model

/**
 * Represents a draft basket that hasn't been placed as an order yet.
 * Persisted to BuyerProfile for offline access and cross-device sync.
 */
data class DraftBasket(
    val items: List<OrderedProduct> = emptyList(),
    val selectedPickupDate: String? = null,
    /**
     * The market of the chosen slot, stored beside its date.
     *
     * Empty on every record written before markets had their own day, and on every
     * single-market seller, where there is nothing to choose — so an absent value
     * means the same thing as it always did and needs no conversion. Together with
     * [selectedPickupDate] this is the stored `PickupSlot`.
     */
    val selectedMarketId: String = "",
    val lastModified: Long = 0L
) {
    /**
     * Check if the draft basket has any items
     */
    fun isEmpty(): Boolean = items.isEmpty()

    /**
     * Check if the draft basket has items
     */
    fun isNotEmpty(): Boolean = items.isNotEmpty()

    /**
     * Get total price of all items in the basket
     */
    fun getTotal(): Double = items.sumOf { it.price * it.amountCount }

    /**
     * Get total item count
     */
    fun getItemCount(): Int = items.size
}
