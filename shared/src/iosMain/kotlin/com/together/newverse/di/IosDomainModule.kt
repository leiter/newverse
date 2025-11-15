package com.together.newverse.di

import com.together.newverse.data.config.FeatureFlagConfig
import com.together.newverse.data.repository.InMemoryBasketRepository
import com.together.newverse.data.repository.PlatformArticleRepository
import com.together.newverse.data.repository.PlatformAuthRepository
import com.together.newverse.data.repository.PlatformOrderRepository
import com.together.newverse.data.repository.PlatformProfileRepository
import com.together.newverse.domain.repository.ArticleRepository
import com.together.newverse.domain.repository.AuthRepository
import com.together.newverse.domain.repository.BasketRepository
import com.together.newverse.domain.repository.OrderRepository
import com.together.newverse.domain.repository.ProfileRepository
import org.koin.dsl.module

/**
 * iOS-specific domain module that provides Firebase implementations
 * of repositories for production use.
 *
 * Supports switching between Firebase and GitLive auth implementations
 * based on feature flags for gradual migration.
 */
val iosDomainModule = module {
    // Initialize feature flags based on build configuration
    single {
        // Options:
        // FeatureFlagConfig.configureForProduction()     // Use Firebase only
        // FeatureFlagConfig.configureForDevelopment()    // Test with 10% GitLive
        // FeatureFlagConfig.configureForGitLiveTesting() // Use GitLive only
        // FeatureFlagConfig.configureForABTesting(50)    // 50/50 split

        // Default to GitLive testing for iOS
        FeatureFlagConfig.configureForGitLiveTesting()
    }

    // Auth Repository - Uses platform-specific implementation that handles switching
    single<AuthRepository> {
        PlatformAuthRepository()
    }

    // Basket Repository - Using in-memory implementation
    single<BasketRepository> { InMemoryBasketRepository() }

    // Article Repository - Uses platform-specific implementation that handles switching
    single<ArticleRepository> {
        val authRepository = get<AuthRepository>()
        PlatformArticleRepository(authRepository)
    }

    // Order Repository - Uses platform-specific implementation that handles switching
    single<OrderRepository> {
        val authRepository = get<AuthRepository>()
        val profileRepository = get<ProfileRepository>()
        PlatformOrderRepository(authRepository, profileRepository)
    }

    // Profile Repository - Uses platform-specific implementation that handles switching
    single<ProfileRepository> {
        val authRepository = get<AuthRepository>()
        PlatformProfileRepository(authRepository)
    }
}
