package com.together.newverse.domain.model

/**
 * Represents a product in the marketplace with essential information
 * for both buyers and sellers.
 *
 * This data class combines:
 * - Original Article fields (for backward compatibility)
 * - Essential marketplace fields (origin, quality, packaging)
 * - Buyer-focused fields (organic certification, min order quantity)
 * - Seller-focused fields (supplier, stock management)
 *
 * Field purposes:
 * - id: Internal database row ID (auto-generated)
 * - productId: External product ID from dataset (e.g., BNN article number)
 */
data class Product(
    // Core Identification
    val id: String = "",                    // Internal database row ID
    val productId: String = "",             // External product ID from dataset (BNN article number)
    val productName: String = "",           // Product name with key details

    // Pricing & Units
    val price: Double = 0.0,                // Price per unit in EUR
    val unit: String = "",                  // Unit: "KG", "ST" (Stück), "BT" (Beutel), "SC" (Schale)
    val packageSize: Double = 0.0,          // Package size (e.g., 6.0 for "6 KG")
    val weightPerPiece: Double = 0.0,       // Weight of individual piece (for calculation)

    // Product Details
    val origin: String = "",                // Country code (DE, IT, ES, FR, etc.)
    val quality: String = "",               // Quality grade (I, II, Bio, Demeter)
    val availability: Boolean = true,       // Is product currently available

    // Marketing & Discovery
    val category: String = "",              // Category for filtering (Obst, Gemüse, etc.)
    val imageUrl: String = "",              // URL to product image
    val searchTerms: String = "",           // Comma-separated search terms
    val detailInfo: String = "",            // Detailed product description
    val isOrganic: Boolean = false,         // Organic certification flag
    val barcode: String? = null,            // EAN barcode if available

    // Buyer-Specific
    val minOrderQuantity: Double = 1.0,     // Minimum order quantity

    // Seller-Specific
    val supplier: String = "",              // Supplier code (BTR, SCH, etc.)
    val stock: Int = 0                      // Available stock quantity
)

/**
 * Extension function to convert legacy Article to new Product
 */
fun Article.toProduct(): Product = Product(
    id = this.id,
    productId = this.productId,
    productName = this.productName,
    price = this.price,
    unit = this.unit,
    weightPerPiece = this.weightPerPiece,
    availability = this.available,
    category = this.category,
    imageUrl = this.imageUrl,
    searchTerms = this.searchTerms,
    detailInfo = this.detailInfo
)

/**
 * Extension function to convert Product to legacy Article (for backward compatibility)
 */
fun Product.toArticle(): Article = Article(
    id = this.id,
    productId = this.productId,
    productName = this.productName,
    available = this.availability,
    unit = this.unit,
    price = this.price,
    weightPerPiece = this.weightPerPiece,
    imageUrl = this.imageUrl,
    category = this.category,
    searchTerms = this.searchTerms,
    detailInfo = this.detailInfo
)
