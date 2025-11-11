package com.together.newverse.preview

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.together.newverse.ui.MainScreenModern
import com.together.newverse.ui.screens.common.AboutScreenModern
import com.together.newverse.ui.screens.common.LoginScreen
import com.together.newverse.ui.screens.sell.CreateProductScreen
import com.together.newverse.ui.screens.sell.OrdersScreen
import com.together.newverse.ui.screens.sell.OverviewScreen
import com.together.newverse.ui.screens.sell.PickDayScreen
import com.together.newverse.ui.screens.sell.SellerProfileScreen
import com.together.newverse.ui.theme.NewverseTheme

// Common Screens

@Preview(name = "Main Screen", showBackground = true)
@Composable
fun MainScreenPreview() {
    NewverseTheme {
        MainScreenModern(
            state = PreviewData.sampleMainScreenState,
            onAction = {}
        )
    }
}

@Preview(name = "Main Screen - Loading", showBackground = true)
@Composable
fun MainScreenLoadingPreview() {
    NewverseTheme {
        MainScreenModern(
            state = PreviewData.sampleMainScreenStateLoading,
            onAction = {}
        )
    }
}

@Preview(name = "Main Screen - Empty", showBackground = true)
@Composable
fun MainScreenEmptyPreview() {
    NewverseTheme {
        MainScreenModern(
            state = PreviewData.sampleMainScreenStateEmpty,
            onAction = {}
        )
    }
}

@Preview(name = "About Screen", showBackground = true)
@Composable
fun AboutScreenPreview() {
    NewverseTheme {
        AboutScreenModern()
    }
}

@Preview(name = "Login Screen", showBackground = true)
@Composable
fun LoginScreenPreview() {
    NewverseTheme {
        LoginScreen()
    }
}

// Buy (Customer) Screens

@Preview(name = "Products Screen - Loading", showBackground = true)
@Composable
fun ProductsScreenLoadingPreview() {
    NewverseTheme {
        Surface {
            com.together.newverse.ui.screens.buy.ProductsContent(
                state = com.together.newverse.ui.screens.buy.ProductsScreenState(
                    isLoading = true
                ),
                onAction = {}
            )
        }
    }
}

@Preview(name = "Products Screen - Success", showBackground = true)
@Composable
fun ProductsScreenSuccessPreview() {
    NewverseTheme {
        Surface {
            com.together.newverse.ui.screens.buy.ProductsContent(
                state = com.together.newverse.ui.screens.buy.ProductsScreenState(
                    isLoading = false,
                    articles = PreviewData.sampleArticles
                ),
                onAction = {}
            )
        }
    }
}

@Preview(name = "Products Screen - Error", showBackground = true)
@Composable
fun ProductsScreenErrorPreview() {
    NewverseTheme {
        Surface {
            com.together.newverse.ui.screens.buy.ProductsContent(
                state = com.together.newverse.ui.screens.buy.ProductsScreenState(
                    isLoading = false,
                    error = "Failed to load products"
                ),
                onAction = {}
            )
        }
    }
}

@Preview(name = "Products Screen - Empty", showBackground = true)
@Composable
fun ProductsScreenEmptyPreview() {
    NewverseTheme {
        Surface {
            com.together.newverse.ui.screens.buy.ProductsContent(
                state = com.together.newverse.ui.screens.buy.ProductsScreenState(
                    isLoading = false,
                    articles = emptyList()
                ),
                onAction = {}
            )
        }
    }
}

@Preview(name = "Basket Screen - Empty", showBackground = true)
@Composable
fun BasketScreenEmptyPreview() {
    NewverseTheme {
        Surface {
            com.together.newverse.ui.screens.buy.BasketContent(
                state = com.together.newverse.ui.screens.buy.BasketScreenState(
                    items = emptyList(),
                    total = 0.0
                ),
                onAction = {}
            )
        }
    }
}

@Preview(name = "Basket Screen - With Items", showBackground = true)
@Composable
fun BasketScreenWithItemsPreview() {
    NewverseTheme {
        Surface {
            com.together.newverse.ui.screens.buy.BasketContent(
                state = com.together.newverse.ui.screens.buy.BasketScreenState(
                    items = PreviewData.sampleOrderedProducts.take(3),
                    total = 12.50
                ),
                onAction = {}
            )
        }
    }
}

@Preview(name = "Basket Screen - Checking Out", showBackground = true)
@Composable
fun BasketScreenCheckingOutPreview() {
    NewverseTheme {
        Surface {
            com.together.newverse.ui.screens.buy.BasketContent(
                state = com.together.newverse.ui.screens.buy.BasketScreenState(
                    items = PreviewData.sampleOrderedProducts.take(2),
                    total = 8.75,
                    isCheckingOut = true
                ),
                onAction = {}
            )
        }
    }
}

@Preview(name = "Customer Profile Screen", showBackground = true)
@Composable
fun CustomerProfileScreenPreview() {
    NewverseTheme {
        Surface {
            com.together.newverse.ui.screens.buy.CustomerProfileScreenModern(
                state = PreviewData.sampleCustomerProfileState,
                onAction = {}
            )
        }
    }
}

@Preview(name = "Customer Profile Screen - Loading", showBackground = true)
@Composable
fun CustomerProfileScreenLoadingPreview() {
    NewverseTheme {
        Surface {
            com.together.newverse.ui.screens.buy.CustomerProfileScreenModern(
                state = PreviewData.sampleCustomerProfileStateLoading,
                onAction = {}
            )
        }
    }
}

@Preview(name = "Customer Profile Screen - Empty", showBackground = true)
@Composable
fun CustomerProfileScreenEmptyPreview() {
    NewverseTheme {
        Surface {
            com.together.newverse.ui.screens.buy.CustomerProfileScreenModern(
                state = PreviewData.sampleCustomerProfileStateEmpty,
                onAction = {}
            )
        }
    }
}

// Sell (Seller) Screens

@Preview(name = "Overview Screen", showBackground = true)
@Composable
fun OverviewScreenPreview() {
    NewverseTheme {
        Surface {
            OverviewScreen()
        }
    }
}

@Preview(name = "Orders Screen", showBackground = true)
@Composable
fun OrdersScreenPreview() {
    NewverseTheme {
        Surface {
            OrdersScreen()
        }
    }
}

@Preview(name = "Create Product Screen", showBackground = true)
@Composable
fun CreateProductScreenPreview() {
    NewverseTheme {
        Surface {
            CreateProductScreen()
        }
    }
}

@Preview(name = "Seller Profile Screen", showBackground = true)
@Composable
fun SellerProfileScreenPreview() {
    NewverseTheme {
        Surface {
            SellerProfileScreen()
        }
    }
}

@Preview(name = "Pick Day Screen", showBackground = true)
@Composable
fun PickDayScreenPreview() {
    NewverseTheme {
        Surface {
            PickDayScreen()
        }
    }
}
