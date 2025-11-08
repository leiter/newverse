package com.together.newverse.data.parser

import com.together.newverse.domain.model.Product

/**
 * Example usage of the BnnParser.
 *
 * This demonstrates how to parse a BNN file and work with the resulting Product objects.
 */
object BnnParserExample {

    /**
     * Example: Parse BNN file content and return products.
     *
     * Usage in your code:
     * ```kotlin
     * val fileContent = File("plf.bnn").readText()
     * val products = BnnParserExample.parseFile(fileContent)
     * ```
     */
    fun parseFile(fileContent: String): List<Product> {
        val parser = BnnParser()
        return parser.parse(fileContent)
    }

    /**
     * Example: Parse and filter products by category.
     */
    fun parseAndFilterByCategory(fileContent: String, category: String): List<Product> {
        val parser = BnnParser()
        val products = parser.parse(fileContent)
        return products.filter { it.category == category }
    }

    /**
     * Example: Parse and filter organic products only.
     */
    fun parseOrganicProducts(fileContent: String): List<Product> {
        val parser = BnnParser()
        val products = parser.parse(fileContent)
        return products.filter { it.isOrganic }
    }

    /**
     * Example: Parse and filter by price range.
     */
    fun parseProductsInPriceRange(
        fileContent: String,
        minPrice: Double,
        maxPrice: Double
    ): List<Product> {
        val parser = BnnParser()
        val products = parser.parse(fileContent)
        return products.filter { it.price in minPrice..maxPrice }
    }

    /**
     * Example: Parse and filter available products only.
     */
    fun parseAvailableProducts(fileContent: String): List<Product> {
        val parser = BnnParser()
        val products = parser.parse(fileContent)
        return products.filter { it.availability }
    }

    /**
     * Example: Parse and group by category.
     */
    fun parseAndGroupByCategory(fileContent: String): Map<String, List<Product>> {
        val parser = BnnParser()
        val products = parser.parse(fileContent)
        return products.groupBy { it.category }
    }

    /**
     * Example: Parse and get product statistics.
     */
    fun getProductStatistics(fileContent: String): ProductStatistics {
        val parser = BnnParser()
        val products = parser.parse(fileContent)

        return ProductStatistics(
            totalProducts = products.size,
            organicProducts = products.count { it.isOrganic },
            availableProducts = products.count { it.availability },
            categories = products.map { it.category }.distinct().sorted(),
            averagePrice = products.map { it.price }.average(),
            priceRange = products.minOfOrNull { it.price } to products.maxOfOrNull { it.price }
        )
    }
}

/**
 * Data class to hold product statistics.
 */
data class ProductStatistics(
    val totalProducts: Int,
    val organicProducts: Int,
    val availableProducts: Int,
    val categories: List<String>,
    val averagePrice: Double,
    val priceRange: Pair<Double?, Double?>
) {
    override fun toString(): String {
        // Format average price to 2 decimal places
        val formattedAvgPrice = (averagePrice * 100).toInt() / 100.0
        return """
            Product Statistics:
            - Total Products: $totalProducts
            - Organic Products: $organicProducts
            - Available Products: $availableProducts
            - Categories: ${categories.joinToString(", ")}
            - Average Price: €$formattedAvgPrice
            - Price Range: €${priceRange.first} - €${priceRange.second}
        """.trimIndent()
    }
}
