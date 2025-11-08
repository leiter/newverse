package com.together.newverse.di

import org.koin.dsl.module

/**
 * Domain layer module for repositories
 *
 * Note: Repository implementations should be provided in platform-specific modules
 * (androidMain for Firebase implementations, etc.)
 */
val domainModule = module {
    // Repository implementations will be provided in platform-specific modules
    // Example for Android with Firebase:
    // single<ArticleRepository> { FirebaseArticleRepository() }
    // single<OrderRepository> { FirebaseOrderRepository() }
    // single<ProfileRepository> { FirebaseProfileRepository() }
    // single<AuthRepository> { FirebaseAuthRepository() }
}
