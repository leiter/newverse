package com.together.newverse.domain.config

import com.together.newverse.domain.model.TaxRate

/**
 * Configuration for product catalog options (categories, units, and tax rates).
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

    /** Available tax rates for the product tax dropdown */
    val taxRates: List<TaxRate>

    /** Default tax rate for new products */
    val defaultTaxRate: TaxRate
}
