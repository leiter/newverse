package com.together.newverse.di

import com.together.newverse.data.repository.FirebaseArticleRepository
import com.together.newverse.data.repository.FirebaseAuthRepository
import com.together.newverse.data.repository.MockOrderRepository
import com.together.newverse.data.repository.MockProfileRepository
import com.together.newverse.domain.repository.ArticleRepository
import com.together.newverse.domain.repository.AuthRepository
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

    // Article Repository - Using Firebase for production
    single<ArticleRepository> { FirebaseArticleRepository() }

    // Order Repository - Using mock for now, will be replaced with Firebase
    single<OrderRepository> { MockOrderRepository() }

    // Profile Repository - Using mock for now, will be replaced with Firebase
    single<ProfileRepository> { MockProfileRepository() }

    // TODO: Replace mock repositories with Firebase implementations:
    // single<OrderRepository> { FirebaseOrderRepository() }
    // single<ProfileRepository> { FirebaseProfileRepository() }
}