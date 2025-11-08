package com.together.newverse.data.parser

import com.together.newverse.domain.model.Product

/**
 * Parser for BNN (Bio-Naturkost-Norm) format data files.
 *
 * BNN Format Specification:
 * - Line 1: Header with metadata (BNN version, supplier info, etc.)
 * - Lines 2+: Product data, semicolon-separated
 *
 * Key Field Positions (0-indexed):
 * 0  = Article Number (productId)
 * 4  = EAN/Barcode
 * 6  = Product Name
 * 7  = Product Description/Detail
 * 10 = Quality Grade (I, II, Bio, Demeter, etc.)
 * 11 = Supplier Code
 * 13 = Origin Country Code
 * 14 = Certification (DD=Demeter, DB=Bioland, EG=EU-Bio, etc.)
 * 22 = Package Description (e.g., "6 KG")
 * 23 = Package Size (numeric, e.g., 6.000)
 * 24 = Unit (KG, ST, BT, SC, etc.)
 * 37 = Price (numeric with comma as decimal separator)
 * 67 = Base Unit for calculation
 * 68 = Weight per piece
 */
class BnnParser {

    companion object {
        private const val FIELD_SEPARATOR = ";"
        private const val DECIMAL_SEPARATOR_DE = ","

        // Field position constants
        private const val POS_PRODUCT_ID = 0
        private const val POS_AVAILABILITY_FLAG = 1
        private const val POS_BARCODE = 4
        private const val POS_PRODUCT_NAME = 6
        private const val POS_PRODUCT_DETAIL = 7
        private const val POS_QUALITY = 10
        private const val POS_SUPPLIER = 11
        private const val POS_ORIGIN = 13
        private const val POS_CERTIFICATION = 14
        private const val POS_PACKAGE_DESC = 22
        private const val POS_PACKAGE_SIZE = 23
        private const val POS_UNIT = 24
        private const val POS_PRICE = 37
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
            val packageDesc = fields.getOrEmpty(POS_PACKAGE_DESC)
            val unit = fields.getOrEmpty(POS_UNIT)

            val packageSize = fields.getOrEmpty(POS_PACKAGE_SIZE)
                .replace(DECIMAL_SEPARATOR_DE, ".")
                .toDoubleOrNull() ?: 0.0

            val price = fields.getOrEmpty(POS_PRICE)
                .replace(DECIMAL_SEPARATOR_DE, ".")
                .toDoubleOrNull() ?: 0.0

            val weightPerPiece = fields.getOrEmpty(POS_WEIGHT_PER_PIECE)
                .replace(DECIMAL_SEPARATOR_DE, ".")
                .toDoubleOrNull() ?: 0.0

            // Determine availability based on flag
            val availabilityFlag = fields.getOrEmpty(POS_AVAILABILITY_FLAG)
            val availability = availabilityFlag == "A" // A = Available, N = New/Not yet available

            // Determine if organic based on certification
            val isOrganic = certification in listOf("DD", "DB", "DN", "IA", "EG")

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
                detailInfo = buildDetailInfo(detailInfo, certification, packageDesc),
                isOrganic = isOrganic,
                barcode = barcode,
                minOrderQuantity = 1.0, // Default to 1
                supplier = supplier,
                stock = 0 // Not provided in BNN format
            )
        } catch (e: Exception) {
            println("Error parsing line: ${e.message}")
            null
        }
    }

    /**
     * Extract category from product name.
     * Common patterns: "Apfel ...", "Birne ...", "Kartoffel ...", etc.
     */
    private fun extractCategory(productName: String): String {
        val firstWord = productName.trim().split(" ").firstOrNull() ?: ""

        return when {
            firstWord in listOf("Apfel", "Äpfel") -> "Obst"
            firstWord in listOf("Birne", "Birnen") -> "Obst"
            firstWord.contains("Orange") -> "Obst"
            firstWord.contains("Zitron") -> "Obst"
            firstWord.contains("Banane") -> "Obst"
            firstWord in listOf("Trauben", "Traube") -> "Obst"
            firstWord in listOf("Pflaumen", "Zwetschgen") -> "Obst"
            firstWord in listOf("Pfirsich", "Nektarine") -> "Obst"
            firstWord.contains("Beeren") -> "Obst"
            firstWord in listOf("Kartoffel", "Speisekartoffel", "Süßkartoffel") -> "Gemüse"
            firstWord in listOf("Möhren", "Karotten", "Bund-Möhren") -> "Gemüse"
            firstWord in listOf("Tomate", "Tomaten") -> "Gemüse"
            firstWord in listOf("Gurke", "Gurken") -> "Gemüse"
            firstWord in listOf("Paprika", "Peperoni") -> "Gemüse"
            firstWord in listOf("Salat", "Kopfsalat") -> "Gemüse"
            firstWord in listOf("Kohl", "Kohlrabi", "Blumenkohl") -> "Gemüse"
            firstWord in listOf("Zwiebel", "Zwiebeln") -> "Gemüse"
            firstWord in listOf("Knoblauch") -> "Gemüse"
            firstWord.contains("Sellerie") -> "Gemüse"
            firstWord.contains("Fenchel") -> "Gemüse"
            firstWord.contains("Rettich") || firstWord.contains("Radieschen") -> "Gemüse"
            firstWord.contains("Bete") -> "Gemüse"
            else -> "Sonstiges"
        }
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
     * Build detailed product information from various fields.
     */
    private fun buildDetailInfo(detail: String, certification: String, packageDesc: String): String {
        val parts = mutableListOf<String>()

        if (detail.isNotBlank()) parts.add(detail)

        val certName = when (certification) {
            "DD" -> "Demeter"
            "DB" -> "Bioland"
            "DN" -> "Naturland"
            "IA" -> "Bio (Italien)"
            "EG" -> "EU-Bio"
            else -> null
        }
        if (certName != null) parts.add(certName)

        if (packageDesc.isNotBlank()) parts.add("Gebinde: $packageDesc")

        return parts.joinToString(" | ")
    }

    /**
     * Helper extension to safely get field or return empty string.
     */
    private fun List<String>.getOrEmpty(index: Int): String {
        return getOrNull(index)?.trim() ?: ""
    }
}
