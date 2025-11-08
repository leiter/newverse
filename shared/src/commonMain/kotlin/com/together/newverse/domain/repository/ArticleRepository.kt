package com.together.newverse.domain.repository

import com.together.newverse.domain.model.Article
import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing product/article data
 */
interface ArticleRepository {
    /**
     * Observe articles for a specific seller
     * @param sellerId The seller's ID
     * @return Flow of articles with real-time updates
     */
    fun observeArticles(sellerId: String): Flow<List<Article>>

    /**
     * Get articles for a specific seller
     * @param sellerId The seller's ID
     * @return List of articles
     */
    suspend fun getArticles(sellerId: String): Result<List<Article>>

    /**
     * Get a specific article by ID
     * @param sellerId The seller's ID
     * @param articleId The article's ID
     * @return Article details
     */
    suspend fun getArticle(sellerId: String, articleId: String): Result<Article>

    /**
     * Create or update an article
     * @param sellerId The seller's ID
     * @param article The article to save
     * @return Success or failure result
     */
    suspend fun saveArticle(sellerId: String, article: Article): Result<Unit>

    /**
     * Delete an article
     * @param sellerId The seller's ID
     * @param articleId The article's ID
     * @return Success or failure result
     */
    suspend fun deleteArticle(sellerId: String, articleId: String): Result<Unit>
}
