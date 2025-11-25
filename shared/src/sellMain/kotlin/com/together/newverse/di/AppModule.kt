package com.together.newverse.di

import com.together.newverse.ui.screens.sell.CreateProductViewModel
import com.together.newverse.ui.screens.sell.OrdersViewModel
import com.together.newverse.ui.screens.sell.OverviewViewModel
import com.together.newverse.ui.screens.sell.SellerProfileViewModel
import com.together.newverse.ui.state.SellAppViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin module for Sell/Merchant app
 * Only includes Sell-specific ViewModels and dependencies
 *
 * This is in sellMain source set, so it's ONLY compiled for Sell flavor.
 */
val appModule = module {
    // Main Sell App ViewModel
    viewModel {
        SellAppViewModel(
            articleRepository = get(),
            orderRepository = get(),
            profileRepository = get(),
            authRepository = get(),
            basketRepository = get()
        )
    }

    // Sell-specific ViewModels
    viewModel { OverviewViewModel(get(), get(), get()) }
    viewModel { OrdersViewModel(get(), get()) }
    viewModel { CreateProductViewModel(get(), get(), get()) }
    viewModel { SellerProfileViewModel() }
}

/**
 * Provide the flavor-specific module to commonMain
 */
actual val flavorAppModule: org.koin.core.module.Module = appModule
