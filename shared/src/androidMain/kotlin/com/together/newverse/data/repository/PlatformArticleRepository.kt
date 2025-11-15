package com.together.newverse.data.repository

import com.together.newverse.data.config.FeatureFlags
import com.together.newverse.data.config.AuthProvider
import com.together.newverse.domain.model.Article
import com.together.newverse.domain.repository.ArticleRepository
import com.together.newverse.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow

/**
 * Android-specific implementation of ArticleRepository that properly handles
 * switching between Firebase and GitLive implementations.
 */
class PlatformArticleRepository(
    private val authRepository: AuthRepository
) : ArticleRepository {

    private val actualRepository: ArticleRepository by lazy {
        when (FeatureFlags.authProvider) {
            AuthProvider.FIREBASE -> {
                println("🏭 PlatformArticleRepository: Using Firebase (Android native)")
                FirebaseArticleRepository()
            }
            AuthProvider.GITLIVE -> {
                println("🏭 PlatformArticleRepository: Using GitLive (cross-platform)")
                GitLiveArticleRepository(authRepository)
            }
            AuthProvider.AUTO -> {
                // Match auth provider for consistency
                if (FeatureFlags.gitLiveRolloutPercentage >= 100) {
                    println("🏭 PlatformArticleRepository: Using GitLive (100% rollout)")
                    GitLiveArticleRepository(authRepository)
                } else {
                    println("🏭 PlatformArticleRepository: Using Firebase (Android default)")
                    FirebaseArticleRepository()
                }
            }
        }
    }

    override fun observeArticles(sellerId: String): Flow<Article> {
        return actualRepository.observeArticles(sellerId)
    }

    override fun getArticles(sellerId: String): Flow<Article> {
        return actualRepository.getArticles(sellerId)
    }

    override suspend fun getArticle(sellerId: String, articleId: String): Result<Article> {
        return actualRepository.getArticle(sellerId, articleId)
    }

    override suspend fun saveArticle(sellerId: String, article: Article): Result<Unit> {
        return actualRepository.saveArticle(sellerId, article)
    }

    override suspend fun deleteArticle(sellerId: String, articleId: String): Result<Unit> {
        return actualRepository.deleteArticle(sellerId, articleId)
    }
}