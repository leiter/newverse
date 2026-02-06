package com.together.newverse.data.service

import com.together.newverse.data.parser.BnnParser
import com.together.newverse.domain.model.Product
import com.together.newverse.domain.service.ProductImportService

/**
 * Product import service for BNN (Bio-Naturkost-Norm) format files.
 * Delegates to the existing BnnParser for actual parsing logic.
 */
class BnnProductImportService : ProductImportService {

    private val bnnParser = BnnParser()

    override fun parse(fileContent: String): List<Product> {
        return bnnParser.parse(fileContent)
    }

    override val formatName: String = "BNN"
}
