package com.together.newverse.ui.state.buy

import com.together.newverse.domain.model.BuyerProfile
import com.together.newverse.domain.model.Order
import com.together.newverse.ui.state.BuyAppViewModel
import com.together.newverse.ui.state.core.AsyncState
import com.together.newverse.ui.state.core.AuthAwareState
import com.together.newverse.ui.state.core.asAsyncState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Flow-based state management extensions for BuyAppViewModel.
 *
 * These extensions demonstrate the reactive Flow-based approach using AuthFlowCoordinator.
 * They provide auth-aware flows that automatically handle authentication state changes.
 *
 * Usage in Composables:
 * ```
 * @Composable
 * fun ProfileScreen(viewModel: BuyAppViewModel) {
 *     val profileState by viewModel.profileStateFlow.collectAsState()
 *
 *     AuthAwareContent(
 *         state = profileState,
 *         onLogin = { viewModel.dispatch(BuyUserAction.ShowLogin) }
 *     ) { profile ->
 *         ProfileContent(profile = profile)
 *     }
 * }
 * ```
 */

/**
 * Auth-aware profile state flow.
 *
 * Automatically handles:
 * - Auth initialization (shows loading)
 * - Not authenticated (shows login prompt)
 * - Authenticated (loads and observes profile)
 *
 * The flow emits:
 * - AuthAwareState.AwaitingAuth while auth is initializing
 * - AuthAwareState.AuthRequired if not authenticated
 * - AuthAwareState.Authenticated with AsyncState<BuyerProfile> when authenticated
 */
@OptIn(ExperimentalCoroutinesApi::class)
val BuyAppViewModel.profileStateFlow: StateFlow<AuthAwareState<BuyerProfile>>
    get() = authFlowCoordinator.whenAuthenticated { _ ->
        profileRepository.observeBuyerProfile().filterNotNull()
    }.stateIn(
        scope = scope,
        started = SharingStarted.Lazily,
        initialValue = AuthAwareState.AwaitingAuth
    )

/**
 * Auth-aware order history flow.
 *
 * Combines profile observation with order loading:
 * - Waits for auth
 * - Loads profile to get order IDs
 * - Observes orders reactively
 */
@OptIn(ExperimentalCoroutinesApi::class)
val BuyAppViewModel.orderHistoryFlow: StateFlow<AuthAwareState<List<Order>>>
    get() = authFlowCoordinator.whenAuthenticated { _ ->
        // First get profile to get order IDs, then observe orders
        profileRepository.observeBuyerProfile()
            .filterNotNull()
            .flatMapLatest { profile ->
                if (profile.placedOrderIds.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    orderRepository.observeBuyerOrders("", profile.placedOrderIds)
                }
            }
    }.stateIn(
        scope = scope,
        started = SharingStarted.Lazily,
        initialValue = AuthAwareState.AwaitingAuth
    )

/**
 * Combined dashboard data flow.
 *
 * Demonstrates combining multiple auth-aware flows into a single derived state.
 */
data class BuyerDashboardData(
    val profile: BuyerProfile,
    val recentOrders: List<Order>,
    val favouriteCount: Int
)

@OptIn(ExperimentalCoroutinesApi::class)
val BuyAppViewModel.dashboardFlow: StateFlow<AuthAwareState<BuyerDashboardData>>
    get() = authFlowCoordinator.whenAuthenticated { _ ->
        combine(
            profileRepository.observeBuyerProfile().filterNotNull(),
            profileRepository.observeBuyerProfile()
                .filterNotNull()
                .flatMapLatest { profile ->
                    if (profile.placedOrderIds.isEmpty()) {
                        flowOf(emptyList())
                    } else {
                        orderRepository.observeBuyerOrders("", profile.placedOrderIds)
                    }
                }
        ) { profile, orders ->
            BuyerDashboardData(
                profile = profile,
                recentOrders = orders.sortedByDescending { it.pickUpDate }.take(3),
                favouriteCount = profile.favouriteArticles.size
            )
        }
    }.stateIn(
        scope = scope,
        started = SharingStarted.Lazily,
        initialValue = AuthAwareState.AwaitingAuth
    )

/**
 * Simple async profile flow (without auth-aware wrapper).
 *
 * Use this when you want just Loading/Success/Error without auth handling.
 * Useful when auth is already guaranteed by navigation guards.
 */
val BuyAppViewModel.simpleProfileFlow: StateFlow<AsyncState<BuyerProfile>>
    get() = profileRepository.observeBuyerProfile()
        .filterNotNull()
        .asAsyncState()
        .stateIn(
            scope = scope,
            started = SharingStarted.Lazily,
            initialValue = AsyncState.Loading
        )

/**
 * Favourite articles IDs as a flow.
 *
 * Convenient accessor for favourite articles that updates reactively.
 */
val BuyAppViewModel.favouriteIdsFlow: StateFlow<List<String>>
    get() = profileRepository.observeBuyerProfile()
        .map { it?.favouriteArticles ?: emptyList() }
        .stateIn(
            scope = scope,
            started = SharingStarted.Lazily,
            initialValue = emptyList()
        )
