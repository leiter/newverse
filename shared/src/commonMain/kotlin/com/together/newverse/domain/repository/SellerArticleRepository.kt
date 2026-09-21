package com.together.newverse.domain.repository

import com.together.newverse.domain.model.SellerArticle
import kotlinx.coroutines.flow.Flow

/**
 * Seller-side access to articles, public and private halves together.
 *
 * Buyers use [ArticleRepository], which only ever sees the public half.
 * Every write here touches both halves in one atomic database update, so another
 * device never observes one half written without the other.
 */
interface SellerArticleRepository {

    /**
     * The seller's full catalog, re-emitted whenever either half changes.
     *
     * The first emission waits for both halves. After that, an update to one half
     * can arrive shortly before the other; during that gap an article's
     * [SellerArticle.sellerData] may be `null`.
     */
    fun observeSellerArticles(sellerId: String): Flow<List<SellerArticle>>

    /** Reads both halves of one article. Fails if the public half does not exist. */
    suspend fun getSellerArticle(sellerId: String, articleId: String): Result<SellerArticle>

    /**
     * Creates or updates an article and returns its id.
     *
     * An empty [SellerArticle.id] creates a new article. If
     * [SellerArticle.sellerData] is `null` only the public half is written and the
     * stored private half is left as it is.
     */
    suspend fun saveSellerArticle(sellerId: String, article: SellerArticle): Result<String>

    /** [saveSellerArticle] for many articles, as a single atomic update. */
    suspend fun saveSellerArticles(sellerId: String, articles: List<SellerArticle>): Result<List<String>>

    /** Deletes both halves of an article in one atomic update. */
    suspend fun deleteSellerArticle(sellerId: String, articleId: String): Result<Unit>
}
