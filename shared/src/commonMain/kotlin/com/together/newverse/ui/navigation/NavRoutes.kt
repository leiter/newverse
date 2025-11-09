package com.together.newverse.ui.navigation

/**
 * Navigation routes for the Newverse app
 *
 * Organized by feature area (Common, Buy, Sell)
 */
sealed class NavRoutes(val route: String) {

    // Common routes
    data object Home : NavRoutes("home")
    data object About : NavRoutes("about")
    data object Login : NavRoutes("login")
    data object Register : NavRoutes("register")
    data object NoInternet : NavRoutes("no_internet")

    // Buy (Customer) routes
    sealed class Buy(route: String) : NavRoutes(route) {
        data object Products : Buy("buy/products")
        data object Basket : Buy("buy/basket")
        data object Profile : Buy("buy/profile")
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
        // Get all routes for navigation drawer
        fun getAllRoutes(): List<NavRoutes> = listOf(
            Home,
            // Common
            About,
            Login,
            Register,
            // Buy routes
            Buy.Products,
            Buy.Basket,
            Buy.Profile,
            // Sell routes
            Sell.Overview,
            Sell.Orders,
            Sell.Create,
            Sell.Profile,
            Sell.PickDay
        )

        // Get display name for route
        fun getDisplayName(route: NavRoutes): String = when (route) {
            Home -> "Home"
            About -> "About"
            Login -> "Login"
            Register -> "Sign Up"
            NoInternet -> "No Internet"
            Buy.Products -> "Browse Products"
            Buy.Basket -> "Shopping Basket"
            Buy.Profile -> "Customer Profile"
            Sell.Overview -> "Product Overview"
            Sell.Orders -> "Manage Orders"
            Sell.Create -> "Create Product"
            Sell.Profile -> "Seller Profile"
            Sell.PickDay -> "Pick Delivery Day"
        }

        // Get category for grouping in drawer
        fun getCategory(route: NavRoutes): String = when (route) {
            Home, About, Login, Register, NoInternet -> "Common"
            is Buy -> "Customer Features"
            is Sell -> "Seller Features"
        }
    }
}
