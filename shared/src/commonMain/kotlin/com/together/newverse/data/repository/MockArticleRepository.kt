package com.together.newverse.data.repository

import com.together.newverse.domain.model.Article
import com.together.newverse.domain.repository.ArticleRepository
import com.together.newverse.preview.PreviewData
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Mock implementation of ArticleRepository for development and testing
 */
class MockArticleRepository : ArticleRepository {

    private val _articles = MutableStateFlow(PreviewData.sampleArticles)

    override fun observeArticles(sellerId: String): Flow<List<Article>> {
        return _articles.asStateFlow()
    }

    override suspend fun getArticles(sellerId: String): Result<List<Article>> {
        return try {
            // Simulate network delay
            delay(500)
            Result.success(PreviewData.sampleArticles)
        } catch (e: Exception) {
            Result.failure(e)
        }
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
            val currentArticles = _articles.value.toMutableList()
            val index = currentArticles.indexOfFirst { it.id == article.id }
            if (index >= 0) {
                currentArticles[index] = article
            } else {
                currentArticles.add(article)
            }
            _articles.value = currentArticles
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteArticle(sellerId: String, articleId: String): Result<Unit> {
        return try {
            delay(300)
            val currentArticles = _articles.value.toMutableList()
            currentArticles.removeAll { it.id == articleId }
            _articles.value = currentArticles
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}