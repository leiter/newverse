package com.together.newverse.di

import com.together.newverse.data.repository.InMemoryAuthRepository
import com.together.newverse.data.repository.MockArticleRepository
import com.together.newverse.data.repository.MockOrderRepository
import com.together.newverse.data.repository.MockProfileRepository
import com.together.newverse.domain.repository.ArticleRepository
import com.together.newverse.domain.repository.AuthRepository
import com.together.newverse.domain.repository.OrderRepository
import com.together.newverse.domain.repository.ProfileRepository
import org.koin.dsl.module

/**
 * Domain layer module for repositories
 *
 * Currently using mock implementations for development.
 * Replace with Firebase or other backend implementations for production.
 */
val domainModule = module {
    // Auth Repository - Using in-memory implementation for now
    single<AuthRepository> { InMemoryAuthRepository() }

    // Article Repository - Using mock implementation for now
    single<ArticleRepository> { MockArticleRepository() }

    // Order Repository - Using mock implementation for now
    single<OrderRepository> { MockOrderRepository() }

    // Profile Repository - Using mock implementation for now
    single<ProfileRepository> { MockProfileRepository() }

    // Note: Replace these with Firebase implementations for production:
    // single<ArticleRepository> { FirebaseArticleRepository() }
    // single<OrderRepository> { FirebaseOrderRepository() }
    // single<ProfileRepository> { FirebaseProfileRepository() }
}
