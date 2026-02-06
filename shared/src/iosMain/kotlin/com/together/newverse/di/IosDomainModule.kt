package com.together.newverse.di

import com.together.newverse.data.config.DefaultOrderScheduleConfig
import com.together.newverse.data.config.DefaultProductCatalogConfig
import com.together.newverse.data.config.DefaultSellerConfig
import com.together.newverse.data.repository.GitLiveArticleRepository
import com.together.newverse.data.repository.GitLiveAuthRepository
import com.together.newverse.data.repository.GitLiveOrderRepository
import com.together.newverse.data.repository.GitLiveProfileRepository
import com.together.newverse.data.repository.InMemoryBasketRepository
import com.together.newverse.domain.config.OrderScheduleConfig
import com.together.newverse.domain.config.ProductCatalogConfig
import com.together.newverse.domain.config.SellerConfig
import com.together.newverse.domain.repository.ArticleRepository
import com.together.newverse.domain.repository.AuthRepository
import com.together.newverse.domain.repository.BasketRepository
import com.together.newverse.domain.repository.OrderRepository
import com.together.newverse.domain.repository.ProfileRepository
import org.koin.dsl.module

/**
 * iOS-specific domain module that provides GitLive implementations
 * of repositories for production use (cross-platform Firebase support).
 */
val iosDomainModule = module {
    // Configs
    single<SellerConfig> { DefaultSellerConfig() }
    single<OrderScheduleConfig> { DefaultOrderScheduleConfig() }
    single<ProductCatalogConfig> { DefaultProductCatalogConfig() }

    // Auth Repository - GitLive cross-platform implementation
    single<AuthRepository> { GitLiveAuthRepository() }

    // Basket Repository - Using in-memory implementation
    single<BasketRepository> { InMemoryBasketRepository() }

    // Article Repository - GitLive cross-platform implementation
    single<ArticleRepository> {
        GitLiveArticleRepository(get<AuthRepository>())
    }

    // Order Repository - GitLive cross-platform implementation
    single<OrderRepository> {
        GitLiveOrderRepository(get<AuthRepository>(), get<ProfileRepository>())
    }

    // Profile Repository - GitLive cross-platform implementation
    single<ProfileRepository> {
        GitLiveProfileRepository(get<AuthRepository>())
    }
}
