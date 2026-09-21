package com.together.newverse.test

import com.together.newverse.domain.model.SellerArticle
import com.together.newverse.domain.repository.SellerArticleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Fake implementation of SellerArticleRepository for testing.
 *
 * Mirrors the real repository's contract: saving with `sellerData = null` keeps the
 * stored private half, so view model tests catch code that would wipe purchase data.
 */
class FakeSellerArticleRepository : SellerArticleRepository {

    private val _articles = MutableStateFlow<List<SellerArticle>>(emptyList())
    val articles: List<SellerArticle> get() = _articles.value

    // Track operations for verification
    private val _savedArticles = mutableListOf<Pair<String, SellerArticle>>()
    val savedArticles: List<Pair<String, SellerArticle>> get() = _savedArticles.toList()

    private val _deletedArticles = mutableListOf<Pair<String, String>>()
    val deletedArticles: List<Pair<String, String>> get() = _deletedArticles.toList()

    // Configuration for test scenarios
    var shouldFailSave = false
    var shouldFailDelete = false
    var shouldFailGetArticle = false
    var failureMessage = "Test error"

    private var nextId = 1

    /**
     * Replace the whole catalog, as if both listeners delivered a new snapshot.
     * Use this to simulate the gap by passing articles with `sellerData = null`.
     */
    fun setArticles(articles: List<SellerArticle>) {
        _articles.value = articles
    }

    fun reset() {
        _articles.value = emptyList()
        _savedArticles.clear()
        _deletedArticles.clear()
        shouldFailSave = false
        shouldFailDelete = false
        shouldFailGetArticle = false
        failureMessage = "Test error"
        nextId = 1
    }

    override fun observeSellerArticles(sellerId: String): Flow<List<SellerArticle>> =
        _articles.asStateFlow()

    override suspend fun getSellerArticle(sellerId: String, articleId: String): Result<SellerArticle> {
        if (shouldFailGetArticle) return Result.failure(Exception(failureMessage))
        val article = _articles.value.find { it.id == articleId }
            ?: return Result.failure(Exception("Article not found"))
        return Result.success(article)
    }

    override suspend fun saveSellerArticle(sellerId: String, article: SellerArticle): Result<String> {
        if (shouldFailSave) return Result.failure(Exception(failureMessage))
        return Result.success(store(sellerId, article))
    }

    override suspend fun saveSellerArticles(
        sellerId: String,
        articles: List<SellerArticle>
    ): Result<List<String>> {
        if (shouldFailSave) return Result.failure(Exception(failureMessage))
        return Result.success(articles.map { store(sellerId, it) })
    }

    override suspend fun deleteSellerArticle(sellerId: String, articleId: String): Result<Unit> {
        if (shouldFailDelete) return Result.failure(Exception(failureMessage))
        _deletedArticles.add(sellerId to articleId)
        _articles.value = _articles.value.filterNot { it.id == articleId }
        return Result.success(Unit)
    }

    private fun store(sellerId: String, article: SellerArticle): String {
        val id = article.id.ifEmpty { "fake-article-${nextId++}" }
        val existing = _articles.value.find { it.id == id }
        val stored = SellerArticle(
            article = article.article.copy(id = id),
            sellerData = article.sellerData ?: existing?.sellerData
        )
        _savedArticles.add(sellerId to stored)
        _articles.value = if (existing == null) {
            _articles.value + stored
        } else {
            _articles.value.map { if (it.id == id) stored else it }
        }
        return id
    }
}
