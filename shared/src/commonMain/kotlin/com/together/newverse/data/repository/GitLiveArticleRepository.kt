package com.together.newverse.data.repository

import com.together.newverse.domain.model.Article
import com.together.newverse.domain.model.Article.Companion.MODE_ADDED
import com.together.newverse.domain.model.Article.Companion.MODE_CHANGED
import com.together.newverse.domain.model.Article.Companion.MODE_MOVED
import com.together.newverse.domain.model.Article.Companion.MODE_REMOVED
import com.together.newverse.domain.repository.ArticleRepository
import com.together.newverse.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
// TODO: Import GitLive SDK classes when ready
// import dev.gitlive.firebase.database.DatabaseReference
// import dev.gitlive.firebase.database.Firebase
// import dev.gitlive.firebase.database.database
// import dev.gitlive.firebase.database.ChildEvent

/**
 * GitLive implementation of ArticleRepository for cross-platform article management.
 *
 * This implementation will use GitLive's Firebase SDK to provide:
 * - Cross-platform support (Android, iOS, Web, Desktop)
 * - Real-time article synchronization
 * - Product catalog management
 * - Image storage integration
 *
 * Data structure in Firebase:
 * - /articles/{sellerId}/{articleId} - Article data
 * - /seller_profiles/ - List of sellers
 */
class GitLiveArticleRepository(
    private val authRepository: AuthRepository
) : ArticleRepository {

    // TODO: Initialize GitLive Firebase Database when SDK is ready
    // private val database = Firebase.database
    // private val articlesRootRef = database.reference("articles")
    // private val sellersRef = database.reference("seller_profiles")

    // Cache for articles
    private val articlesCache = mutableMapOf<String, MutableMap<String, Article>>()

    /**
     * Observe articles for a specific seller with real-time updates.
     * Emits individual Article events with mode flags.
     */
    override fun observeArticles(sellerId: String): Flow<Article> = flow {
        println("🔐 GitLiveArticleRepository.observeArticles: START with sellerId='$sellerId'")

        // Determine seller ID
        val targetSellerId = if (sellerId.isEmpty()) {
            // Buyer mode: Get first available seller
            getFirstSellerId() ?: "seller_001"
        } else {
            // Seller mode: Use provided ID
            sellerId
        }

        println("🔐 GitLiveArticleRepository.observeArticles: Using sellerId='$targetSellerId'")

        // TODO: Implement real-time listener with GitLive
        // val articlesRef = articlesRootRef.child(targetSellerId)
        // articlesRef.childEvents.collect { event ->
        //     when (event) {
        //         is ChildEvent.Added -> {
        //             val article = event.snapshot.value<ArticleDto>()?.toDomain(event.snapshot.key ?: "")
        //             if (article != null) {
        //                 emit(article.copy(mode = MODE_ADDED))
        //             }
        //         }
        //         is ChildEvent.Changed -> {
        //             val article = event.snapshot.value<ArticleDto>()?.toDomain(event.snapshot.key ?: "")
        //             if (article != null) {
        //                 emit(article.copy(mode = MODE_CHANGED))
        //             }
        //         }
        //         is ChildEvent.Removed -> {
        //             val article = event.snapshot.value<ArticleDto>()?.toDomain(event.snapshot.key ?: "")
        //             if (article != null) {
        //                 emit(article.copy(mode = MODE_REMOVED))
        //             }
        //         }
        //         is ChildEvent.Moved -> {
        //             val article = event.snapshot.value<ArticleDto>()?.toDomain(event.snapshot.key ?: "")
        //             if (article != null) {
        //                 emit(article.copy(mode = MODE_MOVED))
        //             }
        //         }
        //     }
        // }

        // Temporary mock implementation - emit test articles
        val mockArticles = createMockArticles(targetSellerId)

        // Store in cache
        articlesCache.getOrPut(targetSellerId) { mutableMapOf() }.apply {
            mockArticles.forEach { article ->
                put(article.id, article)
            }
        }

        // Emit articles as ADDED events
        mockArticles.forEach { article ->
            println("🔐 GitLiveArticleRepository: Emitting mock article '${article.productName}'")
            emit(article.copy(mode = MODE_ADDED))
        }
    }

    /**
     * Get articles for a specific seller.
     * This is an alias for observeArticles for backwards compatibility.
     */
    override fun getArticles(sellerId: String): Flow<Article> {
        return observeArticles(sellerId)
    }

    /**
     * Get a specific article by ID.
     */
    override suspend fun getArticle(sellerId: String, articleId: String): Result<Article> {
        return try {
            println("🔐 GitLiveArticleRepository.getArticle: START - sellerId=$sellerId, articleId=$articleId")

            // Check cache first
            val cachedArticle = articlesCache[sellerId]?.get(articleId)
            if (cachedArticle != null) {
                println("✅ GitLiveArticleRepository.getArticle: Found in cache")
                return Result.success(cachedArticle)
            }

            // TODO: Implement with GitLive
            // val snapshot = articlesRootRef.child(sellerId).child(articleId).get()
            // val articleDto = snapshot.value<ArticleDto>()
            // val article = articleDto?.toDomain(articleId)

            // For now, return mock article
            val mockArticle = createMockArticle(articleId, sellerId)
            articlesCache.getOrPut(sellerId) { mutableMapOf() }[articleId] = mockArticle

            println("✅ GitLiveArticleRepository.getArticle: Created mock article")
            Result.success(mockArticle)

        } catch (e: Exception) {
            println("❌ GitLiveArticleRepository.getArticle: Error - ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Create or update an article.
     */
    override suspend fun saveArticle(sellerId: String, article: Article): Result<Unit> {
        return try {
            println("🔐 GitLiveArticleRepository.saveArticle: START - ${article.productName}")

            // Validate seller permission
            val currentUserId = authRepository.getCurrentUserId()
            if (currentUserId == null) {
                return Result.failure(Exception("User not authenticated"))
            }

            // TODO: Implement with GitLive
            // val dto = ArticleDto.fromDomain(article)
            // articlesRootRef.child(sellerId).child(article.id).setValue(dto)

            // Update cache
            articlesCache.getOrPut(sellerId) { mutableMapOf() }[article.id] = article

            println("✅ GitLiveArticleRepository.saveArticle: Success - saved to cache")
            Result.success(Unit)

        } catch (e: Exception) {
            println("❌ GitLiveArticleRepository.saveArticle: Error - ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Delete an article.
     */
    override suspend fun deleteArticle(sellerId: String, articleId: String): Result<Unit> {
        return try {
            println("🔐 GitLiveArticleRepository.deleteArticle: START - articleId=$articleId")

            // Validate seller permission
            val currentUserId = authRepository.getCurrentUserId()
            if (currentUserId == null) {
                return Result.failure(Exception("User not authenticated"))
            }

            // TODO: Implement with GitLive
            // articlesRootRef.child(sellerId).child(articleId).removeValue()

            // Remove from cache
            articlesCache[sellerId]?.remove(articleId)

            println("✅ GitLiveArticleRepository.deleteArticle: Success")
            Result.success(Unit)

        } catch (e: Exception) {
            println("❌ GitLiveArticleRepository.deleteArticle: Error - ${e.message}")
            Result.failure(e)
        }
    }

    // Helper functions

    /**
     * Get the first available seller ID.
     * Used when buyer doesn't specify a seller.
     */
    private suspend fun getFirstSellerId(): String? {
        // TODO: Implement with GitLive
        // val snapshot = sellersRef.get()
        // return snapshot.children.firstOrNull()?.key

        // For now, return default test seller
        return "seller_001"
    }

    /**
     * Create mock articles for testing.
     */
    private fun createMockArticles(sellerId: String): List<Article> {
        return listOf(
            Article(
                id = "article_001",
                productId = "PROD001",
                productName = "Fresh Apples (GitLive)",
                available = true,
                unit = "kg",
                price = 2.99,
                weightPerPiece = 0.2,
                imageUrl = "https://example.com/apples.jpg",
                category = "Fruits",
                searchTerms = "apple fruit fresh organic",
                detailInfo = "Crispy and sweet apples from local farms"
            ),
            Article(
                id = "article_002",
                productId = "PROD002",
                productName = "Organic Bananas (GitLive)",
                available = true,
                unit = "kg",
                price = 1.99,
                weightPerPiece = 0.15,
                imageUrl = "https://example.com/bananas.jpg",
                category = "Fruits",
                searchTerms = "banana fruit organic yellow",
                detailInfo = "Fresh organic bananas, perfect for smoothies"
            ),
            Article(
                id = "article_003",
                productId = "PROD003",
                productName = "Farm Eggs (GitLive)",
                available = true,
                unit = "dozen",
                price = 4.50,
                weightPerPiece = 0.6,
                imageUrl = "https://example.com/eggs.jpg",
                category = "Dairy",
                searchTerms = "eggs farm fresh protein",
                detailInfo = "Free-range eggs from happy chickens"
            ),
            Article(
                id = "article_004",
                productId = "PROD004",
                productName = "Whole Milk (GitLive)",
                available = false,
                unit = "liter",
                price = 1.29,
                weightPerPiece = 1.0,
                imageUrl = "https://example.com/milk.jpg",
                category = "Dairy",
                searchTerms = "milk dairy fresh whole",
                detailInfo = "Fresh whole milk from local dairy farms"
            ),
            Article(
                id = "article_005",
                productId = "PROD005",
                productName = "Sourdough Bread (GitLive)",
                available = true,
                unit = "loaf",
                price = 3.50,
                weightPerPiece = 0.5,
                imageUrl = "https://example.com/bread.jpg",
                category = "Bakery",
                searchTerms = "bread sourdough fresh bakery",
                detailInfo = "Artisan sourdough bread baked daily"
            )
        )
    }

    /**
     * Create a single mock article.
     */
    private fun createMockArticle(articleId: String, sellerId: String): Article {
        return Article(
            id = articleId,
            productId = "MOCK_${articleId}",
            productName = "Mock Product (GitLive)",
            available = true,
            unit = "piece",
            price = 9.99,
            weightPerPiece = 1.0,
            imageUrl = "",
            category = "Test",
            searchTerms = "mock test product",
            detailInfo = "This is a mock product for testing GitLive integration"
        )
    }
}

/**
 * Data Transfer Object for Article.
 * Maps between domain model and Firebase structure.
 */
private data class ArticleDto(
    val productId: String = "",
    val productName: String = "",
    val available: Boolean = false,
    val unit: String = "",
    val price: Double = 0.0,
    val weightPerPiece: Double = 0.0,
    val imageUrl: String = "",
    val category: String = "",
    val searchTerms: String = "",
    val detailInfo: String = ""
) {
    fun toDomain(id: String): Article {
        return Article(
            id = id,
            productId = productId,
            productName = productName,
            available = available,
            unit = unit,
            price = price,
            weightPerPiece = weightPerPiece,
            imageUrl = imageUrl,
            category = category,
            searchTerms = searchTerms,
            detailInfo = detailInfo
        )
    }

    companion object {
        fun fromDomain(article: Article): ArticleDto {
            return ArticleDto(
                productId = article.productId,
                productName = article.productName,
                available = article.available,
                unit = article.unit,
                price = article.price,
                weightPerPiece = article.weightPerPiece,
                imageUrl = article.imageUrl,
                category = article.category,
                searchTerms = article.searchTerms,
                detailInfo = article.detailInfo
            )
        }
    }
}