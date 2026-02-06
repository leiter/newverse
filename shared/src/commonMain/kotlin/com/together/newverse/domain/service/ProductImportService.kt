package com.together.newverse.domain.service

import com.together.newverse.domain.model.Product

/**
 * Service interface for importing products from external file formats.
 * Implementations can support different formats (BNN, CSV, etc.).
 */
interface ProductImportService {
    /**
     * Parse file content into a list of products.
     *
     * @param fileContent The raw file content as a string
     * @return List of parsed Product objects
     */
    fun parse(fileContent: String): List<Product>

    /**
     * Human-readable name of the supported import format.
     */
    val formatName: String
}
