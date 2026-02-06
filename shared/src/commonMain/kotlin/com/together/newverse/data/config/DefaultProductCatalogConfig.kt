package com.together.newverse.data.config

import com.together.newverse.domain.config.ProductCatalogConfig
import com.together.newverse.domain.model.ProductCategory
import com.together.newverse.domain.model.ProductUnit

/**
 * Default product catalog configuration backed by the existing ProductCategory and ProductUnit enums.
 */
class DefaultProductCatalogConfig : ProductCatalogConfig {
    override val categories: List<String> = ProductCategory.getAllDisplayNames()
    override val units: List<String> = ProductUnit.getAllDisplayNames()
    override val defaultCategory: String = ProductCategory.GEMUESE.displayName
    override val defaultUnit: String = ProductUnit.KG.displayName
}
