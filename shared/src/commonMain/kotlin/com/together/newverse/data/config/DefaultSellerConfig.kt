package com.together.newverse.data.config

import com.together.newverse.domain.config.SellerConfig

/**
 * Seller configuration backed by the build's own [defaultSellerId], which follows the
 * Firebase project this build talks to.
 */
class DefaultSellerConfig : SellerConfig {
    override val sellerId: String = defaultSellerId
}
