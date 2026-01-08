package com.together.newverse.preview

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.activity.ComponentActivity
import com.together.newverse.ui.screens.common.AboutScreenModern
import com.together.newverse.ui.screens.common.LoginScreen
import com.together.newverse.ui.screens.sell.CreateProductScreen
import com.together.newverse.ui.screens.sell.OrdersScreen
import com.together.newverse.ui.screens.sell.OverviewScreen
import com.together.newverse.ui.screens.sell.PickDayScreen
import com.together.newverse.ui.screens.sell.SellerProfileScreen
import com.together.newverse.ui.theme.NewverseTheme
import com.together.newverse.util.ImagePicker
import com.together.newverse.util.LocalImagePicker

// =====================================================================
// Common Screens (shared between buy and sell flavors)
// =====================================================================

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

// =====================================================================
// Sell (Seller) Screens
// =====================================================================

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
            val context = LocalContext.current
            CompositionLocalProvider(
                LocalImagePicker provides ImagePicker(context as ComponentActivity)
            ) {
                CreateProductScreen(
                    onNavigateBack = {}
                )
            }
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

// Note: Buy flavor previews (MainScreen, Basket, CustomerProfile) are in
// buyDebug/kotlin/.../preview/BuyPreviews.kt
