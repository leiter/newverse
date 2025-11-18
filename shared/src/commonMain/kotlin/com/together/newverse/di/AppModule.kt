package com.together.newverse.di

import com.together.newverse.ui.screens.buy.BasketViewModel
import com.together.newverse.ui.screens.sell.CreateProductViewModel
import com.together.newverse.ui.screens.sell.OrdersViewModel
import com.together.newverse.ui.screens.sell.OverviewViewModel
import com.together.newverse.ui.screens.sell.SellerProfileViewModel
import com.together.newverse.ui.state.UnifiedAppViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    // Unified ViewModel - Single source of truth
    viewModel { UnifiedAppViewModel(get(), get(), get(), get(), get()) }

    // Legacy ViewModels - Keep for backwards compatibility during migration
    // LoginViewModel removed - now using UnifiedAppViewModel
    // MainScreenViewModel removed - now using UnifiedAppViewModel
    // ProductsViewModel removed - now using UnifiedAppViewModel
    // CustomerProfileViewModel removed - now using UnifiedAppViewModel

    // ViewModels - Buy/Customer
    viewModel { BasketViewModel(get(), get(), get(), get()) }

    // ViewModels - Sell/Seller
    viewModel { OverviewViewModel(get(), get(), get()) }
    viewModel { OrdersViewModel() }
    viewModel { CreateProductViewModel(get(), get(), get()) }
    viewModel { SellerProfileViewModel() }
}
