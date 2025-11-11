package com.together.newverse.di

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
 */
val androidDomainModule = module {
    // Auth Repository - Using Firebase for production
    single<AuthRepository> { FirebaseAuthRepository() }

    // Basket Repository - Using in-memory implementation
    single<BasketRepository> { InMemoryBasketRepository() }

    // Article Repository - Using Firebase for production
    single<ArticleRepository> { FirebaseArticleRepository() }

    // Order Repository - Using Firebase for production
    single<OrderRepository> { FirebaseOrderRepository() }

    // Profile Repository - Using Firebase for production
    single<ProfileRepository> { FirebaseProfileRepository() }
}