package com.together.newverse.preview

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.together.newverse.ui.MainScreen
import com.together.newverse.ui.screens.buy.BasketScreen
import com.together.newverse.ui.screens.buy.CustomerProfileScreen
import com.together.newverse.ui.screens.buy.ProductsScreen
import com.together.newverse.ui.screens.common.AboutScreen
import com.together.newverse.ui.screens.common.LoginScreen
import com.together.newverse.ui.screens.sell.*
import com.together.newverse.ui.theme.NewverseTheme

// Common Screens

@Preview(name = "Main Screen", showBackground = true)
@Composable
fun MainScreenPreview() {
    NewverseTheme {
        MainScreen()
    }
}

@Preview(name = "About Screen", showBackground = true)
@Composable
fun AboutScreenPreview() {
    NewverseTheme {
        AboutScreen()
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

@Preview(name = "Products Screen", showBackground = true)
@Composable
fun ProductsScreenPreview() {
    NewverseTheme {
        Surface {
            ProductsScreen()
        }
    }
}

@Preview(name = "Basket Screen", showBackground = true)
@Composable
fun BasketScreenPreview() {
    NewverseTheme {
        Surface {
            BasketScreen()
        }
    }
}

@Preview(name = "Customer Profile Screen", showBackground = true)
@Composable
fun CustomerProfileScreenPreview() {
    NewverseTheme {
        CustomerProfileScreen()
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
