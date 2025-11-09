package com.together.newverse.data.repository

import com.together.newverse.domain.model.Article
import com.together.newverse.domain.model.Article.Companion.MODE_ADDED
import com.together.newverse.domain.repository.ArticleRepository
import com.together.newverse.preview.PreviewData
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Mock implementation of ArticleRepository for development and testing
 */
class MockArticleRepository : ArticleRepository {

    /**
     * Observe articles - emits each article with MODE_ADDED flag
     */
    override fun observeArticles(sellerId: String): Flow<Article> = flow {
        delay(500) // Simulate network delay
        // Emit each article as an individual event with ADDED mode
        PreviewData.sampleArticles.forEach { article ->
            emit(article.copy(mode = MODE_ADDED))
            delay(50) // Small delay between emissions
        }
    }

    /**
     * Get articles - same as observeArticles for mock
     */
    override fun getArticles(sellerId: String): Flow<Article> {
        return observeArticles(sellerId)
    }

    override suspend fun getArticle(sellerId: String, articleId: String): Result<Article> {
        return try {
            delay(300)
            val article = PreviewData.sampleArticles.find { it.id == articleId }
            if (article != null) {
                Result.success(article)
            } else {
                Result.failure(Exception("Article not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun saveArticle(sellerId: String, article: Article): Result<Unit> {
        return try {
            delay(300)
            // For mock implementation, just simulate success
            // In real implementation, this would trigger Firebase write and emit CHANGED/ADDED event
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteArticle(sellerId: String, articleId: String): Result<Unit> {
        return try {
            delay(300)
            // For mock implementation, just simulate success
            // In real implementation, this would trigger Firebase delete and emit REMOVED event
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}