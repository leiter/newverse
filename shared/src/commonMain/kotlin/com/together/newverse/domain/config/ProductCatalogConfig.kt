package com.together.newverse.domain.config

/**
 * Configuration for product catalog options (categories and units).
 * Allows white-labeling the product creation form.
 */
interface ProductCatalogConfig {
    /** All available category display names */
    val categories: List<String>

    /** All available unit display names */
    val units: List<String>

    /** Default category display name for new products */
    val defaultCategory: String

    /** Default unit display name for new products */
    val defaultUnit: String
}
