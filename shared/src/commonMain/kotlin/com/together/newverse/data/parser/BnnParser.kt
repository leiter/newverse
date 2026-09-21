package com.together.newverse.data.parser

import com.together.newverse.domain.model.Product
import com.together.newverse.domain.model.ProductCategory
import com.together.newverse.domain.model.ProductPricing
import com.together.newverse.domain.model.TaxRate

/**
 * Parser for BNN (Bio-Naturkost-Norm) format data files.
 *
 * BNN Format Specification:
 * - Line 1: Header with metadata (BNN version, supplier info, etc.)
 * - Lines 2+: Product data, semicolon-separated
 *
 * Key Field Positions (0-indexed, BNN v3):
 * 0  = Article Number (productId)
 * 4  = EAN/Barcode
 * 6  = Product Name
 * 7  = Product Description/Detail
 * 9  = Quality Grade (I, II, Bio, Demeter, etc.)
 * 10 = Supplier Code
 * 12 = Origin Country Code
 * 13 = Certification, BNN identification code / IK (DD=Demeter, DB=Bioland,
 *      EG=EU-Öko-Verordnung, etc. — see [BnnCodeTables])
 * 21 = Package Description (e.g., "6 KG")
 * 22 = Package Size (numeric, e.g., 6.000)
 * 23 = Unit (KG, ST, BT, SC, etc.)
 * 33 = VAT code: 1 = reduced (7 %), 2 = standard (19 %)
 * 35 = Recommended retail price — empty (0,00) in Terra's lists, not used
 * 37 = Purchase price: the wholesaler's net price per unit, comma decimal
 * 67 = Base Unit for calculation
 * 68 = Weight per piece
 *
 * The selling price is not in the file: it is derived from the purchase price with
 * [markupFactor] and the VAT rate, the same way the seller's product form does.
 *
 * @param markupFactor Markup applied to the purchase price, e.g. 1.45 for 45 %.
 */
class BnnParser(
    private val markupFactor: Double = 1.0
) {

    private val descriptionBuilder = ProductDescriptionBuilder()

    companion object {
        private const val FIELD_SEPARATOR = ";"
        private const val DECIMAL_SEPARATOR_DE = ","

        // Field position constants (BNN v3 format)
        private const val POS_PRODUCT_ID = 0
        private const val POS_AVAILABILITY_FLAG = 1
        private const val POS_BARCODE = 4
        private const val POS_PRODUCT_NAME = 6
        private const val POS_PRODUCT_DETAIL = 7
        private const val POS_QUALITY = 9
        private const val POS_SUPPLIER = 10
        private const val POS_ORIGIN = 12
        private const val POS_CERTIFICATION = 13
        private const val POS_PACKAGE_SIZE = 22
        private const val POS_UNIT = 23
        private const val POS_TAX_CODE = 33
        private const val POS_ACQUIRE_PRICE = 37
        private const val POS_BASE_UNIT = 67
        private const val POS_WEIGHT_PER_PIECE = 68
    }

    /**
     * Parse a BNN file into a list of Product objects.
     *
     * @param fileContent The complete content of the BNN file as a string
     * @return List of parsed Product objects
     */
    fun parse(fileContent: String): List<Product> {
        val lines = fileContent.lines()
        if (lines.isEmpty()) return emptyList()

        // Skip header line (first line contains metadata)
        return lines.drop(1)
            .filter { it.isNotBlank() }
            .mapNotNull { line -> parseLine(line) }
    }

    /**
     * Parse a single BNN data line into a Product object.
     *
     * @param line A semicolon-separated BNN data line
     * @return Product object or null if parsing fails
     */
    private fun parseLine(line: String): Product? {
        return try {
            val fields = line.split(FIELD_SEPARATOR)

            // Ensure we have enough fields
            if (fields.size < 70) {
                println("Warning: Skipping line with insufficient fields (${fields.size})")
                return null
            }

            val productId = fields.getOrEmpty(POS_PRODUCT_ID)
            val productName = fields.getOrEmpty(POS_PRODUCT_NAME)

            // Skip if no product ID or name
            if (productId.isBlank() || productName.isBlank()) {
                return null
            }

            val barcode = fields.getOrEmpty(POS_BARCODE).takeIf { it.isNotBlank() }
            val detailInfo = fields.getOrEmpty(POS_PRODUCT_DETAIL)
            val quality = fields.getOrEmpty(POS_QUALITY)
            val supplier = fields.getOrEmpty(POS_SUPPLIER)
            val origin = fields.getOrEmpty(POS_ORIGIN)
            val certification = fields.getOrEmpty(POS_CERTIFICATION)
            val unit = fields.getOrEmpty(POS_UNIT)

            val packageSize = fields.getOrEmpty(POS_PACKAGE_SIZE)
                .replace(DECIMAL_SEPARATOR_DE, ".")
                .toDoubleOrNull() ?: 0.0

            val acquirePrice = fields.getOrEmpty(POS_ACQUIRE_PRICE)
                .replace(DECIMAL_SEPARATOR_DE, ".")
                .toDoubleOrNull() ?: 0.0

            val taxRate = taxRateFor(fields.getOrEmpty(POS_TAX_CODE))

            // No purchase price, no selling price: the seller sets it in the form.
            val price = if (acquirePrice > 0.0) {
                ProductPricing.sellPrice(acquirePrice, markupFactor, taxRate.rate)
            } else 0.0

            val weightPerPiece = fields.getOrEmpty(POS_WEIGHT_PER_PIECE)
                .replace(DECIMAL_SEPARATOR_DE, ".")
                .toDoubleOrNull() ?: 0.0

            // Determine availability based on flag
            val availabilityFlag = fields.getOrEmpty(POS_AVAILABILITY_FLAG)
            val availability = availabilityFlag == "A" // A = Available, N = New/Not yet available

            // Determine if organic based on certification
            val isOrganic = BnnCodeTables.certificationFor(certification)
                ?.kind
                ?.let { it != BnnCodeTables.CertificationKind.NONE }
                ?: false

            // Build category from product name (first word often indicates category)
            val category = extractCategory(productName)

            // Build search terms from product name and details
            val searchTerms = buildSearchTerms(productName, detailInfo, quality)

            Product(
                id = "", // Will be set by database
                productId = productId,
                productName = productName,
                price = price,
                unit = unit,
                packageSize = packageSize,
                weightPerPiece = weightPerPiece,
                origin = origin,
                quality = quality,
                availability = availability,
                category = category,
                imageUrl = "", // Not provided in BNN format
                searchTerms = searchTerms,
                detailInfo = descriptionBuilder.build(
                    bnnDetail = detailInfo,
                    originCode = origin,
                    certificationCode = certification,
                    producerCode = supplier
                ),
                isOrganic = isOrganic,
                certification = certification.trim(),
                barcode = barcode,
                minOrderQuantity = 1.0, // Default to 1
                supplier = supplier,
                acquirePrice = acquirePrice,
                markupFactor = markupFactor,
                taxRate = taxRate.rate,
                stock = 0 // Not provided in BNN format
            )
        } catch (e: Exception) {
            println("Error parsing line: ${e.message}")
            null
        }
    }

    /**
     * BNN VAT code to rate. An unknown code falls back to the default rate, which
     * is what almost every food item carries.
     */
    private fun taxRateFor(code: String): TaxRate = when (code.trim()) {
        "1" -> TaxRate.REDUCED
        "2" -> TaxRate.STANDARD
        else -> TaxRate.default
    }

    /**
     * Extract category from product name by checking each word against
     * ProductCategory.fromString() which has comprehensive German keyword mappings.
     * Checks all words (not just the first) to handle names like "Rote Bete" or "Bund-Möhren".
     */
    private fun extractCategory(productName: String): String {
        // Split on spaces and hyphens to get individual words
        val words = productName.trim().split(" ", "-")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        // Check each word against ProductCategory mappings
        for (word in words) {
            val category = ProductCategory.fromString(word)
            if (category != ProductCategory.SONSTIGES) {
                return category.displayName
            }
        }

        // Try multi-word combinations (e.g., "Rote Bete", "Lollo Rosso")
        val lowerName = productName.lowercase()
        val multiWordTerms = listOf(
            "rote beete", "rote bete", "lollo rosso", "bund-möhren", "bund möhren"
        )
        for (term in multiWordTerms) {
            if (lowerName.contains(term)) {
                val category = ProductCategory.fromString(term)
                if (category != ProductCategory.SONSTIGES) {
                    return category.displayName
                }
            }
        }

        return ProductCategory.SONSTIGES.displayName
    }

    /**
     * Build search terms from product information.
     */
    private fun buildSearchTerms(name: String, detail: String, quality: String): String {
        val terms = mutableSetOf<String>()

        // Add words from name
        name.split(" ", ",", "-").forEach { word ->
            val cleaned = word.trim().lowercase()
            if (cleaned.length > 2) terms.add(cleaned)
        }

        // Add words from detail
        detail.split(" ", ",", "-").forEach { word ->
            val cleaned = word.trim().lowercase()
            if (cleaned.length > 2) terms.add(cleaned)
        }

        // Add quality
        if (quality.isNotBlank()) terms.add(quality.lowercase())

        return terms.joinToString(",")
    }

    /**
     * Helper extension to safely get field or return empty string.
     */
    private fun List<String>.getOrEmpty(index: Int): String {
        return getOrNull(index)?.trim() ?: ""
    }
}
