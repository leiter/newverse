package com.together.newverse.di

import com.together.newverse.domain.GreetingRepository
import com.together.newverse.ui.MainViewModel
import com.together.newverse.ui.screens.buy.BasketViewModel
import com.together.newverse.ui.screens.buy.CustomerProfileViewModel
import com.together.newverse.ui.screens.buy.ProductsViewModel
import com.together.newverse.ui.screens.common.LoginViewModel
import com.together.newverse.ui.screens.sell.CreateProductViewModel
import com.together.newverse.ui.screens.sell.OrdersViewModel
import com.together.newverse.ui.screens.sell.OverviewViewModel
import com.together.newverse.ui.screens.sell.SellerProfileViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    // Repositories
    single { GreetingRepository() }

    // ViewModels - Common
    viewModel { MainViewModel(get()) }
    viewModel { LoginViewModel() }

    // ViewModels - Buy/Customer
    viewModel { ProductsViewModel() }
    viewModel { BasketViewModel() }
    viewModel { CustomerProfileViewModel() }

    // ViewModels - Sell/Seller
    viewModel { OverviewViewModel() }
    viewModel { OrdersViewModel() }
    viewModel { CreateProductViewModel() }
    viewModel { SellerProfileViewModel() }
}
