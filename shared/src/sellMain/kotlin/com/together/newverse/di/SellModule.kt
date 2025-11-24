package com.together.newverse.di

import com.together.newverse.ui.state.AppViewModel
import com.together.newverse.ui.state.SellAppViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Sell flavor specific module
 * This module overrides the default BuyAppViewModel with SellAppViewModel
 */
val sellModule = module {
    // Sell flavor ViewModel - overrides the default from appModule
    viewModel { SellAppViewModel(get(), get(), get(), get(), get()) } bind AppViewModel::class
}
