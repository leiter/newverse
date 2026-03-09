package com.together.newverse.di

import com.together.newverse.data.config.BuyerSellerConfig
import com.together.newverse.data.config.BuyerUUIDStorage
import com.together.newverse.data.config.SellerIdStorage
import com.together.newverse.data.repository.GitLiveBuyerContactRepository
import com.together.newverse.data.repository.GitLiveInvitationRepository
import com.together.newverse.data.repository.GitLiveMessageRepository
import com.together.newverse.domain.config.MutableSellerConfig
import com.together.newverse.domain.config.SellerConfig
import com.together.newverse.domain.repository.BuyerContactRepository
import com.together.newverse.domain.repository.InvitationRepository
import com.together.newverse.domain.repository.MessageRepository
import com.together.newverse.ui.screens.buy.BuyerContactsViewModel
import com.together.newverse.ui.screens.buy.BuyerConversationViewModel
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

    // Invitation repository
    single<InvitationRepository> { GitLiveInvitationRepository(get()) }

    // Messaging repositories
    single<MessageRepository> { GitLiveMessageRepository() }
    single<BuyerContactRepository> { GitLiveBuyerContactRepository() }

    // Main Buy App ViewModel
    viewModel {
        BuyAppViewModel(
            articleRepository = get(),
            orderRepository = get(),
            profileRepository = get(),
            authRepository = get(),
            basketRepository = get(),
            sellerConfig = get(),
            buyerUUIDStorage = get(),
            invitationRepository = get(),
            messageRepository = get(),
            buyerContactRepository = get()
        )
    }

    // Customer Profile ViewModel (FormState-based)
    viewModel {
        CustomerProfileViewModel(
            profileRepository = get()
        )
    }

    // Buyer Conversation ViewModel
    viewModel {
        BuyerConversationViewModel(
            messageRepository = get(),
            authRepository = get()
        )
    }

    // Buyer Contacts ViewModel
    viewModel {
        BuyerContactsViewModel(
            buyerContactRepository = get(),
            messageRepository = get(),
            authRepository = get()
        )
    }
}

/**
 * Provide the flavor-specific module to commonMain
 */
actual val flavorAppModule: org.koin.core.module.Module = appModule
