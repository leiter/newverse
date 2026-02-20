package com.together.newverse.di

import com.together.newverse.data.config.BuyerSellerConfig
import com.together.newverse.data.config.SellerIdStorage
import com.together.newverse.domain.config.MutableSellerConfig
import com.together.newverse.domain.config.SellerConfig
import com.together.newverse.ui.screens.buy.CustomerProfileViewModel
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
    // Seller config: mutable for buyer app, overrides the default SellerConfig binding
    single { BuyerSellerConfig(get<SellerIdStorage>()) }
    single<MutableSellerConfig> { get<BuyerSellerConfig>() }
    single<SellerConfig> { get<BuyerSellerConfig>() }

    // Main Buy App ViewModel
    viewModel {
        BuyAppViewModel(
            articleRepository = get(),
            orderRepository = get(),
            profileRepository = get(),
            authRepository = get(),
            basketRepository = get(),
            sellerConfig = get()
        )
    }

    // Customer Profile ViewModel (FormState-based)
    viewModel {
        CustomerProfileViewModel(
            profileRepository = get()
        )
    }
}

/**
 * Provide the flavor-specific module to commonMain
 */
actual val flavorAppModule: org.koin.core.module.Module = appModule
