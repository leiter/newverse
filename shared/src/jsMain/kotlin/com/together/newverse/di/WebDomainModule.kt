package com.together.newverse.di

import com.together.newverse.data.config.BuyerUUIDStorage
import com.together.newverse.data.config.DefaultOrderScheduleConfig
import com.together.newverse.data.config.DefaultProductCatalogConfig
import com.together.newverse.data.config.DefaultSellerConfig
import com.together.newverse.data.config.DemoOrderStorage
import com.together.newverse.data.config.SellerIdStorage
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

val webDomainModule = module {
    single { SellerIdStorage() }
    single { DemoOrderStorage() }
    single { BuyerUUIDStorage() }
    single<SellerConfig> { DefaultSellerConfig() }
    single<OrderScheduleConfig> { DefaultOrderScheduleConfig() }
    single<ProductCatalogConfig> { DefaultProductCatalogConfig() }

    single<AuthRepository> { GitLiveAuthRepository() }
    single<BasketRepository> { InMemoryBasketRepository() }
    single<ArticleRepository> { GitLiveArticleRepository(get()) }
    single<OrderRepository> { GitLiveOrderRepository(get(), get()) }
    single<ProfileRepository> { GitLiveProfileRepository(get(), get()) }
}
