package com.together.newverse.di

import com.together.newverse.ui.screens.buy.BasketViewModel
import com.together.newverse.ui.screens.sell.CreateProductViewModel
import com.together.newverse.ui.screens.sell.OrdersViewModel
import com.together.newverse.ui.screens.sell.OverviewViewModel
import com.together.newverse.ui.screens.sell.SellerProfileViewModel
import com.together.newverse.ui.state.AppViewModel
import com.together.newverse.ui.state.BuyAppViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Common app module with shared dependencies
 * This is used by both buy and sell flavors
 */
val appModule = module {
    // Buy flavor ViewModel - default for commonMain/buy flavor
    // The sell flavor will override this with SellAppViewModel
    viewModel { BuyAppViewModel(get(), get(), get(), get(), get()) } bind AppViewModel::class

    // ViewModels - Buy/Customer
    viewModel { BasketViewModel(get(), get(), get(), get()) }

    // ViewModels - Sell/Seller
    viewModel { OverviewViewModel(get(), get(), get()) }
    viewModel { OrdersViewModel() }
    viewModel { CreateProductViewModel(get(), get(), get()) }
    viewModel { SellerProfileViewModel() }
}
