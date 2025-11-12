package com.together.newverse.di

import com.together.newverse.data.config.FeatureFlags
import com.together.newverse.data.config.FeatureFlagConfig
import com.together.newverse.data.repository.AuthRepositoryFactory
import com.together.newverse.data.repository.FirebaseArticleRepository
import com.together.newverse.data.repository.FirebaseAuthRepository
import com.together.newverse.data.repository.FirebaseOrderRepository
import com.together.newverse.data.repository.FirebaseProfileRepository
import com.together.newverse.data.repository.InMemoryBasketRepository
import com.together.newverse.domain.repository.ArticleRepository
import com.together.newverse.domain.repository.AuthRepository
import com.together.newverse.domain.repository.BasketRepository
import com.together.newverse.domain.repository.OrderRepository
import com.together.newverse.domain.repository.ProfileRepository
import org.koin.dsl.module

/**
 * Android-specific domain module that provides Firebase implementations
 * of repositories for production use.
 *
 * Now supports switching between Firebase and GitLive auth implementations
 * based on feature flags for gradual migration.
 */
val androidDomainModule = module {
    // Initialize feature flags based on build configuration
    // You can change this to test different configurations
    single {
        // Options:
        // FeatureFlagConfig.configureForProduction()     // Use Firebase only
        // FeatureFlagConfig.configureForDevelopment()    // Test with 10% GitLive
        // FeatureFlagConfig.configureForGitLiveTesting() // Use GitLive only
        // FeatureFlagConfig.configureForABTesting(50)    // 50/50 split

        // Default to production (Firebase) for safety
        FeatureFlagConfig.configureForGitLiveTesting()
    }

    // Auth Repository - Now uses factory to switch between implementations
    single<AuthRepository> {
        // Get repository based on feature flags
        // This allows runtime switching between Firebase and GitLive
        AuthRepositoryFactory.getAuthRepository()
    }

    // Basket Repository - Using in-memory implementation
    single<BasketRepository> { InMemoryBasketRepository() }

    // Article Repository - Using Firebase for production
    single<ArticleRepository> { FirebaseArticleRepository() }

    // Order Repository - Using Firebase for production
    single<OrderRepository> { FirebaseOrderRepository() }

    // Profile Repository - Using Firebase for production
    single<ProfileRepository> { FirebaseProfileRepository() }
}