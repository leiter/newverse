package com.together.newverse.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.together.newverse.data.firebase.Database
import com.together.newverse.data.firebase.EventType
import com.together.newverse.data.firebase.awaitResult
import com.together.newverse.data.firebase.getSingleValue
import com.together.newverse.data.firebase.model.ArticleDto
import com.together.newverse.data.firebase.observeChildEventsAs
import com.together.newverse.domain.model.Article
import com.together.newverse.domain.repository.ArticleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.scan

/**
 * Firebase implementation of ArticleRepository
 * Provides real-time article data from Firebase Realtime Database
 */
class FirebaseArticleRepository : ArticleRepository {

    init {
        // Initialize Firebase Database with persistence
        Database.initialize()
    }

    /**
     * Observe articles for a specific seller with real-time updates
     * This uses ChildEventListener to get incremental updates (ADDED, CHANGED, REMOVED)
     */
    override fun observeArticles(sellerId: String): Flow<List<Article>> {
        val articlesRef = if (sellerId.isEmpty()) {
            Database.articles()
        } else {
            Database.providerArticles(sellerId)
        }

        // Use child events to build up the list incrementally
        return articlesRef.observeChildEventsAs<ArticleDto>()
            .scan(emptyList<Article>()) { currentList, event ->
                val mutableList = currentList.toMutableList()
                when (event.eventType) {
                    EventType.ADDED -> {
                        mutableList.add(event.data.toDomain(event.id))
                    }
                    EventType.CHANGED -> {
                        val index = mutableList.indexOfFirst { it.id == event.id }
                        if (index >= 0) {
                            mutableList[index] = event.data.toDomain(event.id)
                        }
                    }
                    EventType.REMOVED -> {
                        mutableList.removeAll { it.id == event.id }
                    }
                    EventType.MOVED -> {
                        // For articles, move events are rare, treat as change
                        val index = mutableList.indexOfFirst { it.id == event.id }
                        if (index >= 0) {
                            mutableList[index] = event.data.toDomain(event.id)
                        }
                    }
                }
                mutableList.toList()
            }
    }

    /**
     * Get articles for a specific seller (one-time read)
     */
    override suspend fun getArticles(sellerId: String): Result<List<Article>> {
        return try {
            val articlesRef = if (sellerId.isEmpty()) {
                Database.articles()
            } else {
                Database.providerArticles(sellerId)
            }

            // Get the raw list with IDs
            val snapshot = articlesRef.getSingleValue()
            val articlesList = snapshot.children.mapNotNull { childSnapshot ->
                childSnapshot.getValue(ArticleDto::class.java)?.toDomain(childSnapshot.key ?: "")
            }

            Result.success(articlesList)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get a specific article by ID
     */
    override suspend fun getArticle(sellerId: String, articleId: String): Result<Article> {
        return try {
            val articlesRef = if (sellerId.isEmpty()) {
                Database.articles()
            } else {
                Database.providerArticles(sellerId)
            }

            val snapshot = articlesRef.child(articleId).getSingleValue()
            val dto = snapshot.getValue(ArticleDto::class.java)

            if (dto != null) {
                Result.success(dto.toDomain(articleId))
            } else {
                Result.failure(Exception("Article not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Create or update an article
     */
    override suspend fun saveArticle(sellerId: String, article: Article): Result<Unit> {
        return try {
            val uid = FirebaseAuth.getInstance().currentUser?.uid
                ?: return Result.failure(Exception("User not authenticated"))

            val articlesRef = Database.articles()
            val dto = ArticleDto.fromDomain(article)

            if (article.id.isEmpty()) {
                // Create new article with auto-generated ID
                val newRef = articlesRef.push()
                newRef.setValue(dto).awaitResult()
            } else {
                // Update existing article
                articlesRef.child(article.id).setValue(dto).awaitResult()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Delete an article
     */
    override suspend fun deleteArticle(sellerId: String, articleId: String): Result<Unit> {
        return try {
            val articlesRef = Database.articles()
            articlesRef.child(articleId).removeValue().awaitResult()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
