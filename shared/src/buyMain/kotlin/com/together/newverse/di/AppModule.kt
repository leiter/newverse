package com.together.newverse.di

import com.together.newverse.ui.screens.buy.BasketViewModel
import com.together.newverse.ui.state.BuyAppViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin module for Buy/Customer app
 * Only includes Buy-specific ViewModels and dependencies
 *
 * This is in buyMain source set, so it's ONLY compiled for Buy flavor.
 */
val appModule = module {
    // Main Buy App ViewModel
    viewModel {
        BuyAppViewModel(
            articleRepository = get(),
            orderRepository = get(),
            profileRepository = get(),
            authRepository = get(),
            basketRepository = get()
        )
    }

    // Buy-specific ViewModels
    viewModel { BasketViewModel(get(), get(), get(), get()) }
}

/**
 * Provide the flavor-specific module to commonMain
 */
actual val flavorAppModule: org.koin.core.module.Module = appModule
