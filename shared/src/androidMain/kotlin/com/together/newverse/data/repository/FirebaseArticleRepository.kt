package com.together.newverse.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.together.newverse.data.firebase.Database
import com.together.newverse.data.firebase.awaitResult
import com.together.newverse.data.firebase.getSingleValue
import com.together.newverse.data.firebase.model.ArticleDto
import com.together.newverse.domain.model.Article
import com.together.newverse.domain.model.Article.Companion.MODE_ADDED
import com.together.newverse.domain.model.Article.Companion.MODE_CHANGED
import com.together.newverse.domain.model.Article.Companion.MODE_MOVED
import com.together.newverse.domain.model.Article.Companion.MODE_REMOVED
import com.together.newverse.domain.repository.ArticleRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

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
     * Emits individual Article events with mode flags (ADDED, CHANGED, REMOVED, MOVED)
     * Similar to the RxJava Observable pattern from the universe project
     */
    override fun observeArticles(sellerId: String): Flow<Article> = callbackFlow {
        println("🔥 FirebaseArticleRepository.observeArticles: START with sellerId='$sellerId'")

        var articlesRef: com.google.firebase.database.DatabaseReference? = null
        var listener: ChildEventListener? = null

        // Create the listener
        val articleListener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                println("🔥 FirebaseArticleRepository: onChildAdded - key=${snapshot.key}")
                val dto = snapshot.getValue(ArticleDto::class.java)
                if (dto != null) {
                    val article = dto.toDomain(snapshot.key ?: "").copy(mode = MODE_ADDED)
                    println("🔥 FirebaseArticleRepository: Sending ADDED article '${article.productName}' (id=${article.id})")
                    trySend(article)
                } else {
                    println("⚠️ FirebaseArticleRepository: onChildAdded - dto is null for key=${snapshot.key}")
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                println("🔥 FirebaseArticleRepository: onChildChanged - key=${snapshot.key}")
                val dto = snapshot.getValue(ArticleDto::class.java)
                if (dto != null) {
                    val article = dto.toDomain(snapshot.key ?: "").copy(mode = MODE_CHANGED)
                    println("🔥 FirebaseArticleRepository: Sending CHANGED article '${article.productName}' (id=${article.id})")
                    trySend(article)
                }
            }

            override fun onChildRemoved(snapshot: DataSnapshot) {
                println("🔥 FirebaseArticleRepository: onChildRemoved - key=${snapshot.key}")
                val dto = snapshot.getValue(ArticleDto::class.java)
                if (dto != null) {
                    val article = dto.toDomain(snapshot.key ?: "").copy(mode = MODE_REMOVED)
                    println("🔥 FirebaseArticleRepository: Sending REMOVED article '${article.productName}' (id=${article.id})")
                    trySend(article)
                }
            }

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {
                println("🔥 FirebaseArticleRepository: onChildMoved - key=${snapshot.key}")
                val dto = snapshot.getValue(ArticleDto::class.java)
                if (dto != null) {
                    val article = dto.toDomain(snapshot.key ?: "").copy(mode = MODE_MOVED)
                    println("🔥 FirebaseArticleRepository: Sending MOVED article '${article.productName}' (id=${article.id})")
                    trySend(article)
                }
            }

            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                println("❌ FirebaseArticleRepository: onCancelled - ${error.message}")
                error.toException().printStackTrace()
                close(error.toException())
            }
        }

        // Determine which seller to load articles from
        if (sellerId.isEmpty()) {
            // Buyer mode: Get the first seller from seller_profile list
            println("🔥 FirebaseArticleRepository.observeArticles: sellerId is empty, fetching first seller from database...")

            Database.getFirstSellerIdRef().get().addOnSuccessListener { snapshot ->
                val firstSellerId = snapshot.children.firstOrNull()?.key
                if (firstSellerId != null) {
                    println("🔥 FirebaseArticleRepository.observeArticles: Found first seller ID: $firstSellerId")
                    articlesRef = Database.providerArticles(firstSellerId)
                    println("🔥 FirebaseArticleRepository.observeArticles: Database reference obtained: ${articlesRef!!.path}")
                    println("🔥 FirebaseArticleRepository.observeArticles: Adding ChildEventListener...")
                    listener = articleListener
                    articlesRef!!.addChildEventListener(articleListener)
                    println("🔥 FirebaseArticleRepository.observeArticles: ChildEventListener added, waiting for events...")
                } else {
                    println("❌ FirebaseArticleRepository.observeArticles: No sellers found in seller_profile")
                    close(Exception("No sellers available in the database"))
                }
            }.addOnFailureListener { e ->
                println("❌ FirebaseArticleRepository.observeArticles: ERROR fetching seller ID - ${e.message}")
                e.printStackTrace()
                close(e)
            }
        } else {
            println("🔥 FirebaseArticleRepository.observeArticles: Using provided sellerId: $sellerId")
            articlesRef = Database.providerArticles(sellerId)
            println("🔥 FirebaseArticleRepository.observeArticles: Database reference obtained: ${articlesRef!!.path}")
            println("🔥 FirebaseArticleRepository.observeArticles: Adding ChildEventListener...")
            listener = articleListener
            articlesRef!!.addChildEventListener(articleListener)
            println("🔥 FirebaseArticleRepository.observeArticles: ChildEventListener added, waiting for events...")
        }

        awaitClose {
            println("🔥 FirebaseArticleRepository.observeArticles: Removing ChildEventListener")
            if (articlesRef != null && listener != null) {
                articlesRef!!.removeEventListener(listener!!)
            }
        }
    }

    /**
     * Get articles for a specific seller as a Flow
     * This is the same as observeArticles
     */
    override fun getArticles(sellerId: String): Flow<Article> {
        return observeArticles(sellerId)
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
