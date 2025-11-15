package com.together.newverse.ui.navigation

import newverse.shared.generated.resources.*
import org.jetbrains.compose.resources.StringResource

/**
 * Navigation routes for the Newverse app
 *
 * Organized by feature area (Common, Buy, Sell)
 */
sealed class NavRoutes(val route: String) {

    // Common routes
    data object Home : NavRoutes("home")
    data object Login : NavRoutes("login")
    data object Register : NavRoutes("register")
    data object About : NavRoutes("about")
    // Note: NoInternet removed - no screen implementation exists

    // Buy (Customer) routes
    sealed class Buy(route: String) : NavRoutes(route) {
        // Note: Products browsing is handled by MainScreenModern (Home route)
        data object Basket : Buy("buy/basket") {
            fun createRoute(orderId: String? = null, orderDate: String? = null): String {
                return if (orderId != null && orderDate != null) {
                    "buy/basket?orderId=$orderId&orderDate=$orderDate"
                } else {
                    "buy/basket"
                }
            }
        }
        data object Profile : Buy("buy/profile")
        data object OrderHistory : Buy("buy/order_history")
    }

    // Sell (Merchant) routes
    sealed class Sell(route: String) : NavRoutes(route) {
        data object Overview : Sell("sell/overview")
        data object Orders : Sell("sell/orders")
        data object Create : Sell("sell/create")
        data object Profile : Sell("sell/profile")
        data object PickDay : Sell("sell/pick_day")
    }

    companion object {
        // Get all routes (unfiltered - for internal use)
        private fun getAllRoutesUnfiltered(): List<NavRoutes> = listOf(
            Home,
            // Common
            Login,
            Register,
            About,
            // Buy routes
            Buy.Basket,
            Buy.Profile,
            Buy.OrderHistory,
            // Sell routes
            Sell.Overview,
            Sell.Orders,
            Sell.Create,
            Sell.Profile,
            Sell.PickDay
        )

        // Get all routes for navigation drawer (filtered by build flavor)
        fun getAllRoutes(): List<NavRoutes> {
            return getAllRoutesUnfiltered()
        }

        // Get routes filtered by current build flavor
        fun getRoutesForCurrentFlavor(): List<NavRoutes> {
            val allRoutes = getAllRoutesUnfiltered()

            return when {
                com.together.newverse.shared.BuildKonfig.IS_BUY_APP -> {
                    // Buy flavor: Show Common and Customer Features only
                    allRoutes.filter { route -> route !is Sell }
                }
                com.together.newverse.shared.BuildKonfig.IS_SELL_APP -> {
                    // Sell flavor: Show Common and Seller Features only
                    allRoutes.filter { route -> route !is Buy }
                }
                else -> {
                    // Combined build (both flags false): show all routes
                    allRoutes
                }
            }
        }

        // Check if this is a combined build (both Buy and Sell features enabled)
        fun isCombinedBuild(): Boolean {
            return !com.together.newverse.shared.BuildKonfig.IS_BUY_APP &&
                   !com.together.newverse.shared.BuildKonfig.IS_SELL_APP
        }

        // Check if Buy features are available in current build
        fun hasBuyFeatures(): Boolean {
            return com.together.newverse.shared.BuildKonfig.IS_BUY_APP || isCombinedBuild()
        }

        // Check if Sell features are available in current build
        fun hasSellFeatures(): Boolean {
            return com.together.newverse.shared.BuildKonfig.IS_SELL_APP || isCombinedBuild()
        }

        // Get display name for route (returns StringResource)
        fun getDisplayNameRes(route: NavRoutes): StringResource = when (route) {
            Home -> Res.string.nav_home
            About -> Res.string.nav_about
            Login -> Res.string.nav_login
            Register -> Res.string.nav_register
            Buy.Basket -> Res.string.nav_shopping_basket
            Buy.Profile -> Res.string.nav_customer_profile
            Buy.OrderHistory -> Res.string.action_orders
            Sell.Overview -> Res.string.nav_product_overview
            Sell.Orders -> Res.string.nav_manage_orders
            Sell.Create -> Res.string.nav_create_product
            Sell.Profile -> Res.string.nav_seller_profile
            Sell.PickDay -> Res.string.nav_pick_delivery_day
        }

        // Get category for grouping in drawer (returns StringResource)
        fun getCategoryRes(route: NavRoutes): StringResource = when (route) {
            Home, About, Login, Register -> Res.string.nav_category_common
            is Buy -> Res.string.nav_category_customer
            is Sell -> Res.string.nav_category_seller
        }
    }
}
