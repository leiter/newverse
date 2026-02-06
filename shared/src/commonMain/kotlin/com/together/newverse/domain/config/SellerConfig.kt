package com.together.newverse.domain.config

/**
 * Configuration for seller identity.
 * Abstracts the seller ID so ViewModels don't depend on specific repository implementations.
 */
interface SellerConfig {
    val sellerId: String
}
