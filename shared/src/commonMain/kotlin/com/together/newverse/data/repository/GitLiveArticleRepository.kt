package com.together.newverse.data.repository

import com.together.newverse.domain.model.Article
import com.together.newverse.domain.model.Article.Companion.MODE_ADDED
import com.together.newverse.domain.model.Article.Companion.MODE_CHANGED
import com.together.newverse.domain.model.Article.Companion.MODE_REMOVED
import com.together.newverse.domain.repository.ArticleRepository
import com.together.newverse.domain.repository.AuthRepository
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.database.DataSnapshot
import dev.gitlive.firebase.database.database
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

/**
 * GitLive implementation of ArticleRepository for cross-platform article management.
 * This version uses the correct GitLive Firebase SDK APIs.
 */
class GitLiveArticleRepository(
    private val authRepository: AuthRepository
) : ArticleRepository {

    // GitLive Firebase Database references
    private val database = Firebase.database
    private val articlesRootRef = database.reference("articles")

    // Cache for articles
    private val articlesCache = mutableMapOf<String, MutableMap<String, Article>>()

    /**
     * Observe articles for a specific seller with real-time updates.
     * Emits individual Article events with mode flags (MODE_ADDED, MODE_CHANGED, MODE_REMOVED).
     *
     * Note: Using valueEvents instead of childEvents due to GitLive SDK limitations.
     * We track previous state to detect additions, changes, and deletions.
     *
     * IMPORTANT: Uses a flow-local cache for change detection to avoid race conditions
     * when multiple flow collectors are active (e.g., loadProducts and loadMainScreenArticles).
     */
    override fun observeArticles(sellerId: String): Flow<Article> = flow {
        println("🔐 GitLiveArticleRepository.observeArticles: START with sellerId='$sellerId'")

        try {
            // Determine seller ID
            val targetSellerId = if (sellerId.isEmpty()) {
                // Buyer mode: Get first available seller
                val firstSellerId = getFirstSellerId()
                println("🔐 GitLiveArticleRepository.observeArticles: Using first seller: $firstSellerId")
                firstSellerId
            } else {
                // Seller mode: Use provided ID
                sellerId
            }

            // Create reference to seller's articles
            val articlesRef = articlesRootRef.child(targetSellerId)

            // Flow-local cache for change detection - avoids race conditions with shared cache
            // Each flow collector gets its own previousArticles for accurate change detection
            var previousArticles = mapOf<String, Article>()

            // Listen for value changes
            articlesRef.valueEvents.collect { snapshot ->
                // Get current articles from snapshot
                val currentArticles = mutableMapOf<String, Article>()
                snapshot.children.forEach { childSnapshot ->
                    val article = mapSnapshotToArticle(childSnapshot)
                    if (article != null) {
                        currentArticles[article.id] = article
                    }
                }

                val currentArticleIds = currentArticles.keys
                val previousArticleIds = previousArticles.keys

                // Find deleted articles (were in previous, not in current)
                val deletedIds = previousArticleIds - currentArticleIds
                deletedIds.forEach { deletedId ->
                    // Get from local previous state for accurate data
                    val deletedArticle = previousArticles[deletedId]
                    if (deletedArticle != null) {
                        println("🗑️ GitLiveArticleRepository: Article REMOVED '${deletedArticle.productName}'")
                        emit(deletedArticle.copy(mode = MODE_REMOVED))
                    } else {
                        // Create a minimal article for removal if not found
                        println("🗑️ GitLiveArticleRepository: Article REMOVED (id=$deletedId)")
                        emit(Article(id = deletedId, mode = MODE_REMOVED))
                    }
                }

                // Find new articles (in current, not in previous)
                val addedIds = currentArticleIds - previousArticleIds
                addedIds.forEach { addedId ->
                    val article = currentArticles[addedId]!!
                    println("➕ GitLiveArticleRepository: Article ADDED '${article.productName}'")
                    emit(article.copy(mode = MODE_ADDED))
                }

                // Find changed articles (in both, but might have different data)
                val existingIds = currentArticleIds.intersect(previousArticleIds)
                existingIds.forEach { existingId ->
                    val article = currentArticles[existingId]!!
                    val previousArticle = previousArticles[existingId]
                    // Compare with local previous state, not shared cache
                    if (previousArticle != null && previousArticle != article) {
                        println("✏️ GitLiveArticleRepository: Article CHANGED '${article.productName}' (available: ${previousArticle.available} -> ${article.available})")
                        emit(article.copy(mode = MODE_CHANGED))
                    } else if (previousArticle == null) {
                        // First load - emit as added
                        println("➕ GitLiveArticleRepository: Article ADDED '${article.productName}'")
                        emit(article.copy(mode = MODE_ADDED))
                    }
                }

                // Update flow-local cache for next comparison
                previousArticles = currentArticles.toMap()

                // Also update shared cache for getArticle() optimization
                articlesCache[targetSellerId] = currentArticles.toMutableMap()
            }
        } catch (e: Exception) {
            println("❌ GitLiveArticleRepository.observeArticles: Error - ${e.message}")
            throw e
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

            // Determine seller ID (same logic as observeArticles)
            val targetSellerId = if (sellerId.isEmpty()) {
                val firstSellerId = getFirstSellerId()
                println("🔐 GitLiveArticleRepository.getArticle: Using first seller: $firstSellerId")
                firstSellerId
            } else {
                sellerId
            }

            // Check cache first
            val cachedArticle = articlesCache[targetSellerId]?.get(articleId)
            if (cachedArticle != null) {
                println("✅ GitLiveArticleRepository.getArticle: Found in cache")
                return Result.success(cachedArticle)
            }

            // Fetch from GitLive Firebase
            val articleRef = articlesRootRef.child(targetSellerId).child(articleId)
            val snapshot = articleRef.valueEvents.first()

            if (snapshot.exists) {
                val article = mapSnapshotToArticle(snapshot)
                if (article != null) {
                    // Update cache
                    articlesCache.getOrPut(targetSellerId) { mutableMapOf() }[articleId] = article

                    println("✅ GitLiveArticleRepository.getArticle: Fetched from Firebase - price=${article.price}")
                    Result.success(article)
                } else {
                    Result.failure(Exception("Failed to parse article data"))
                }
            } else {
                println("❌ GitLiveArticleRepository.getArticle: Article not found for id=$articleId")
                Result.failure(Exception("Article not found"))
            }

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

            // Generate new ID if article.id is empty (new article)
            val sellerArticlesRef = articlesRootRef.child(sellerId)
            val articleId = if (article.id.isEmpty()) {
                // Use push() to generate a unique Firebase ID
                val newRef = sellerArticlesRef.push()
                newRef.key ?: return Result.failure(Exception("Failed to generate article ID"))
            } else {
                article.id
            }

            // Create article with the ID
            val articleWithId = article.copy(id = articleId)

            // Convert to map for Firebase
            val articleMap = articleToMap(articleWithId)

            // Save to GitLive Firebase
            val articleRef = sellerArticlesRef.child(articleId)
            articleRef.setValue(articleMap)

            // Update cache
            articlesCache.getOrPut(sellerId) { mutableMapOf() }[articleId] = articleWithId

            println("✅ GitLiveArticleRepository.saveArticle: Success with ID=$articleId")
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

            // Remove from GitLive Firebase
            val articleRef = articlesRootRef.child(sellerId).child(articleId)
            articleRef.removeValue()

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
     * Get the default seller ID.
     * TODO: In production, this should come from app configuration or user's connected seller.
     */
    private fun getFirstSellerId(): String {
        return DEFAULT_SELLER_ID
    }

    companion object {
        @Deprecated(
            "Use SellerConfig.sellerId instead. Inject SellerConfig via DI.",
            ReplaceWith("sellerConfig.sellerId")
        )
        const val DEFAULT_SELLER_ID = "cPkcZSiF3LMXjWoqW6AqpA9paoO2"
    }

    /**
     * Map a DataSnapshot to an Article domain model.
     */
    private fun mapSnapshotToArticle(snapshot: DataSnapshot): Article? {
        val articleId = snapshot.key ?: return null
        val value = snapshot.value

        return when (value) {
            is Map<*, *> -> {
                Article(
                    id = articleId,
                    productId = value["productId"] as? String ?: "",
                    productName = value["productName"] as? String ?: "",
                    available = value["available"] as? Boolean == true,
                    unit = value["unit"] as? String ?: "",
                    price = (value["price"] as? Number)?.toDouble() ?: 0.0,
                    weightPerPiece = (value["weightPerPiece"] as? Number)?.toDouble() ?: 0.0,
                    imageUrl = value["imageUrl"] as? String ?: "",
                    category = value["category"] as? String ?: "",
                    searchTerms = value["searchTerms"] as? String ?: "",
                    detailInfo = value["detailInfo"] as? String ?: ""
                )
            }
            else -> null
        }
    }

    /**
     * Convert an Article to a map for Firebase storage.
     */
    private fun articleToMap(article: Article): Map<String, Any?> {
        return mapOf(
            "productId" to article.productId,
            "productName" to article.productName,
            "available" to article.available,
            "unit" to article.unit,
            "price" to article.price,
            "weightPerPiece" to article.weightPerPiece,
            "imageUrl" to article.imageUrl,
            "category" to article.category,
            "searchTerms" to article.searchTerms,
            "detailInfo" to article.detailInfo
        )
    }
}