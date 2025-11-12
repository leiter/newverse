package com.together.newverse.di

import com.together.newverse.data.config.FeatureFlags
import com.together.newverse.data.config.FeatureFlagConfig
import com.together.newverse.data.repository.AuthRepositoryFactory
import com.together.newverse.data.repository.FirebaseArticleRepository
import com.together.newverse.data.repository.FirebaseAuthRepository
import com.together.newverse.data.repository.FirebaseOrderRepository
import com.together.newverse.data.repository.FirebaseProfileRepository
import com.together.newverse.data.repository.InMemoryBasketRepository
import com.together.newverse.data.repository.PlatformArticleRepository
import com.together.newverse.data.repository.PlatformAuthRepository
import com.together.newverse.data.repository.PlatformOrderRepository
import com.together.newverse.data.repository.PlatformProfileRepository
import com.together.newverse.data.repository.ProfileRepositoryFactory
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
        // Change this to test different configurations:
        // FeatureFlagConfig.configureForProduction()     // Firebase only (stable)
        // FeatureFlagConfig.configureForGitLiveTesting()  // GitLive only (testing)
        // FeatureFlagConfig.configureForDevelopment()     // Mixed mode
        FeatureFlagConfig.configureForProduction()
    }

    // Auth Repository - Uses platform-specific implementation that handles switching
    single<AuthRepository> {
        // Platform-specific implementation that properly loads Firebase or GitLive
        PlatformAuthRepository()
    }

    // Basket Repository - Using in-memory implementation
    single<BasketRepository> { InMemoryBasketRepository() }

    // Article Repository - Uses platform-specific implementation that handles switching
    single<ArticleRepository> {
        // Get auth repository (needed for GitLive implementation)
        val authRepository = get<AuthRepository>()
        // Platform-specific implementation that properly loads Firebase or GitLive
        PlatformArticleRepository(authRepository)
    }

    // Order Repository - Uses platform-specific implementation that handles switching
    single<OrderRepository> {
        // Get required dependencies
        val authRepository = get<AuthRepository>()
        val profileRepository = get<ProfileRepository>()
        // Platform-specific implementation that properly loads Firebase or GitLive
        PlatformOrderRepository(authRepository, profileRepository)
    }

    // Profile Repository - Uses platform-specific implementation that handles switching
    single<ProfileRepository> {
        // Get auth repository first (needed for GitLive implementation)
        val authRepository = get<AuthRepository>()
        // Platform-specific implementation that properly loads Firebase or GitLive
        PlatformProfileRepository(authRepository)
    }
}