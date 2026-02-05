package com.together.newverse.test

import com.together.newverse.domain.model.Article
import com.together.newverse.domain.repository.ArticleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Fake implementation of ArticleRepository for testing.
 * Allows controlling article emissions and tracking operations.
 */
class FakeArticleRepository : ArticleRepository {

    // Flow for emitting articles - tests can emit to this
    private val _articlesFlow = MutableSharedFlow<Article>()

    // Track operations for verification
    private val _savedArticles = mutableListOf<Pair<String, Article>>()
    val savedArticles: List<Pair<String, Article>> get() = _savedArticles.toList()

    private val _deletedArticles = mutableListOf<Pair<String, String>>()
    val deletedArticles: List<Pair<String, String>> get() = _deletedArticles.toList()

    // Configuration for test scenarios
    var shouldFailSave = false
    var shouldFailDelete = false
    var shouldFailGetArticle = false
    var failureMessage = "Test error"

    // Store articles for getArticle lookups
    private val _storedArticles = mutableMapOf<String, Article>()

    /**
     * Emit an article event to observers
     */
    suspend fun emitArticle(article: Article) {
        _articlesFlow.emit(article)
    }

    /**
     * Add an article to storage (for getArticle)
     */
    fun addStoredArticle(article: Article) {
        _storedArticles[article.id] = article
    }

    /**
     * Reset repository state for fresh test
     */
    fun reset() {
        _savedArticles.clear()
        _deletedArticles.clear()
        _storedArticles.clear()
        shouldFailSave = false
        shouldFailDelete = false
        shouldFailGetArticle = false
        failureMessage = "Test error"
    }

    override fun observeArticles(sellerId: String): Flow<Article> {
        return _articlesFlow
    }

    override fun getArticles(sellerId: String): Flow<Article> {
        return _articlesFlow
    }

    override suspend fun getArticle(sellerId: String, articleId: String): Result<Article> {
        if (shouldFailGetArticle) {
            return Result.failure(Exception(failureMessage))
        }

        val article = _storedArticles[articleId]
        return if (article != null) {
            Result.success(article)
        } else {
            Result.failure(Exception("Article not found"))
        }
    }

    override suspend fun saveArticle(sellerId: String, article: Article): Result<Unit> {
        if (shouldFailSave) {
            return Result.failure(Exception(failureMessage))
        }

        _savedArticles.add(sellerId to article)
        _storedArticles[article.id] = article
        return Result.success(Unit)
    }

    override suspend fun deleteArticle(sellerId: String, articleId: String): Result<Unit> {
        if (shouldFailDelete) {
            return Result.failure(Exception(failureMessage))
        }

        _deletedArticles.add(sellerId to articleId)
        _storedArticles.remove(articleId)
        return Result.success(Unit)
    }
}
