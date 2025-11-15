package com.together.newverse.data.repository

import com.together.newverse.data.config.FeatureFlags
import com.together.newverse.data.config.AuthProvider
import com.together.newverse.domain.model.Article
import com.together.newverse.domain.repository.ArticleRepository
import com.together.newverse.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow

/**
 * iOS-specific implementation of ArticleRepository.
 *
 * Currently iOS uses GitLive exclusively for cross-platform Firebase support.
 */
class PlatformArticleRepository(
    private val authRepository: AuthRepository
) : ArticleRepository {

    private val actualRepository: ArticleRepository by lazy {
        when (FeatureFlags.authProvider) {
            AuthProvider.FIREBASE -> {
                // iOS doesn't have native Firebase wrapper yet, fall back to GitLive
                println("🏭 PlatformArticleRepository (iOS): Firebase requested but using GitLive")
                GitLiveArticleRepository(authRepository)
            }
            AuthProvider.GITLIVE -> {
                println("🏭 PlatformArticleRepository (iOS): Using GitLive (cross-platform)")
                GitLiveArticleRepository(authRepository)
            }
            AuthProvider.AUTO -> {
                // iOS defaults to GitLive
                println("🏭 PlatformArticleRepository (iOS): Using GitLive (iOS default)")
                GitLiveArticleRepository(authRepository)
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
