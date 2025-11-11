package com.together.newverse.di

import com.together.newverse.ui.MainScreenViewModel
import com.together.newverse.ui.screens.buy.BasketViewModel
import com.together.newverse.ui.screens.buy.ProductsViewModel
import com.together.newverse.ui.screens.common.LoginViewModel
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

    // Main Screen ViewModel
    viewModel { MainScreenViewModel(get(), get(), get(), get()) }

    // Legacy ViewModels - Keep for backwards compatibility during migration
    viewModel { LoginViewModel() }

    // ViewModels - Buy/Customer
    viewModel { ProductsViewModel(get()) }
    viewModel { BasketViewModel(get(), get(), get(), get()) }
    // CustomerProfileViewModel removed - now using UnifiedAppViewModel

    // ViewModels - Sell/Seller
    viewModel { OverviewViewModel(get()) }
    viewModel { OrdersViewModel() }
    viewModel { CreateProductViewModel() }
    viewModel { SellerProfileViewModel() }
}
