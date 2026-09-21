package com.together.newverse.data.repository

import com.together.newverse.data.firebase.ArticleNodes
import com.together.newverse.domain.model.SellerArticle
import com.together.newverse.domain.repository.AuthRepository
import com.together.newverse.domain.repository.SellerArticleRepository
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.database.DataSnapshot
import dev.gitlive.firebase.database.database
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * GitLive implementation of [SellerArticleRepository].
 *
 * Reads the two halves with two listeners and joins them in memory; writes both
 * halves through one multi-path update on the database root, which the database
 * applies atomically. Layout and update maps come from [ArticleNodes].
 */
class GitLiveSellerArticleRepository(
    private val authRepository: AuthRepository
) : SellerArticleRepository {

    private val database = Firebase.database
    private val rootRef = database.reference()

    override fun observeSellerArticles(sellerId: String): Flow<List<SellerArticle>> {
        require(sellerId.isNotEmpty()) { "sellerId must not be empty" }

        val publicHalves = database.reference(ArticleNodes.PUBLIC_ROOT).child(sellerId)
            .valueEvents
            .map { snapshot -> snapshot.children.mapToHalves(ArticleNodes::articleFromMap) }

        val privateHalves = database.reference(ArticleNodes.PRIVATE_ROOT).child(sellerId)
            .valueEvents
            .map { snapshot ->
                snapshot.children.mapToHalves { _, value -> ArticleNodes.sellerDataFromMap(value) }
            }
            .catch { e ->
                // Keep the catalog usable if the private half cannot be read (e.g. the
                // database rules for seller_articles are not deployed yet). Articles then
                // show no purchase data, and saves without it leave the private half alone.
                println("❌ GitLiveSellerArticleRepository: private half unavailable - ${e.message}")
                emit(emptyMap())
            }

        // combine waits for the first value of both halves, so the first emission is
        // always complete; later updates to one half may briefly precede the other.
        return combine(publicHalves, privateHalves) { public, private ->
            ArticleNodes.join(public, private)
        }
    }

    override suspend fun getSellerArticle(sellerId: String, articleId: String): Result<SellerArticle> {
        return try {
            val publicSnapshot = database.reference(ArticleNodes.publicPath(sellerId, articleId))
                .valueEvents.first()
            val publicValue = publicSnapshot.value as? Map<*, *>
                ?: return Result.failure(Exception("Article not found"))

            // A failed read of the private half must fail the whole call. Returning
            // sellerData = null would let an edit form save blank purchase data over
            // the real one.
            val privateSnapshot = database.reference(ArticleNodes.privatePath(sellerId, articleId))
                .valueEvents.first()
            val sellerData = (privateSnapshot.value as? Map<*, *>)?.let(ArticleNodes::sellerDataFromMap)

            Result.success(
                SellerArticle(
                    article = ArticleNodes.articleFromMap(articleId, publicValue),
                    sellerData = sellerData
                )
            )
        } catch (e: Exception) {
            println("❌ GitLiveSellerArticleRepository.getSellerArticle: Error - ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun saveSellerArticle(sellerId: String, article: SellerArticle): Result<String> =
        saveSellerArticles(sellerId, listOf(article)).map { it.single() }

    override suspend fun saveSellerArticles(
        sellerId: String,
        articles: List<SellerArticle>
    ): Result<List<String>> {
        return try {
            if (authRepository.getCurrentUserId() == null) {
                return Result.failure(Exception("User not authenticated"))
            }
            if (articles.isEmpty()) return Result.success(emptyList())

            val ids = articles.map { it.id.ifEmpty { newArticleId(sellerId) } }
            val update = articles.zip(ids).fold(emptyMap<String, Any?>()) { acc, (article, id) ->
                acc + ArticleNodes.saveUpdate(sellerId, id, article)
            }
            rootRef.updateChildren(update)

            println("✅ GitLiveSellerArticleRepository: saved ${ids.size} article(s)")
            Result.success(ids)
        } catch (e: Exception) {
            println("❌ GitLiveSellerArticleRepository.saveSellerArticles: Error - ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun deleteSellerArticle(sellerId: String, articleId: String): Result<Unit> {
        return try {
            if (authRepository.getCurrentUserId() == null) {
                return Result.failure(Exception("User not authenticated"))
            }
            rootRef.updateChildren(ArticleNodes.deleteUpdate(sellerId, articleId))
            Result.success(Unit)
        } catch (e: Exception) {
            println("❌ GitLiveSellerArticleRepository.deleteSellerArticle: Error - ${e.message}")
            Result.failure(e)
        }
    }

    /** A new push id, generated locally without writing anything. */
    private fun newArticleId(sellerId: String): String =
        database.reference(ArticleNodes.PUBLIC_ROOT).child(sellerId).push().key
            ?: throw IllegalStateException("Failed to generate article ID")

    /** Children as id → parsed value, in snapshot order, skipping malformed entries. */
    private fun <T> Iterable<DataSnapshot>.mapToHalves(
        parse: (String, Map<*, *>) -> T
    ): Map<String, T> = mapNotNull { child ->
        val id = child.key ?: return@mapNotNull null
        val value = child.value as? Map<*, *> ?: return@mapNotNull null
        id to parse(id, value)
    }.toMap()
}

