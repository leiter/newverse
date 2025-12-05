package com.together.newverse.di

import com.together.newverse.data.repository.InMemoryBasketRepository
import com.together.newverse.data.repository.PlatformArticleRepository
import com.together.newverse.data.repository.PlatformAuthRepository
import com.together.newverse.data.repository.PlatformOrderRepository
import com.together.newverse.data.repository.PlatformProfileRepository
import com.together.newverse.data.repository.PlatformStorageRepository
import com.together.newverse.domain.repository.ArticleRepository
import com.together.newverse.domain.repository.AuthRepository
import com.together.newverse.domain.repository.BasketRepository
import com.together.newverse.domain.repository.OrderRepository
import com.together.newverse.domain.repository.ProfileRepository
import com.together.newverse.domain.repository.StorageRepository
import org.koin.dsl.module

/**
 * Android-specific domain module that provides GitLive implementations
 * of repositories for production use (cross-platform Firebase support).
 */
val androidDomainModule = module {
    // Feature flags are now initialized in NewverseApp.onCreate() before Koin starts
    // This ensures correct configuration is applied before any dependencies are created

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

    // Storage Repository - Uses platform-specific implementation that handles switching
    single<StorageRepository> {
        // Platform-specific implementation that properly loads Firebase or GitLive
        PlatformStorageRepository()
    }
}