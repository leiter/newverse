package com.together.newverse.preview

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.together.newverse.ui.MainScreenModern
import com.together.newverse.ui.screens.buy.BasketContent
import com.together.newverse.ui.screens.buy.CustomerProfileScreenModern
import com.together.newverse.ui.state.BasketScreenState
import com.together.newverse.ui.theme.NewverseTheme

// =====================================================================
// Buy Flavor Previews
// =====================================================================

// ===== Device Comparison Previews =====

@Preview(
    name = "Main Screen - iPhone Xs (375dp)",
    showBackground = true,
    widthDp = 375,
    heightDp = 812
)
@Composable
fun MainScreenIPhoneXsPreview() {
    NewverseTheme {
        MainScreenModern(
            state = PreviewData.sampleMainScreenState,
            onAction = {}
        )
    }
}

@Preview(
    name = "Main Screen - Pixel 2 (411dp)",
    showBackground = true,
    widthDp = 411,
    heightDp = 731
)
@Composable
fun MainScreenPixel2Preview() {
    NewverseTheme {
        MainScreenModern(
            state = PreviewData.sampleMainScreenState,
            onAction = {}
        )
    }
}

@Preview(
    name = "Main Screen - iPhone Xs - Long Product Name",
    showBackground = true,
    widthDp = 375,
    heightDp = 812
)
@Composable
fun MainScreenIPhoneXsLongNamePreview() {
    NewverseTheme {
        MainScreenModern(
            state = PreviewData.sampleMainScreenState.copy(
                selectedArticle = PreviewData.sampleArticles[0].copy(
                    productName = "Bio Feigenbananen aus Peru (Demeter zertifiziert)"
                )
            ),
            onAction = {}
        )
    }
}

@Preview(
    name = "Main Screen - iPhone Xs - In Basket",
    showBackground = true,
    widthDp = 375,
    heightDp = 812
)
@Composable
fun MainScreenIPhoneXsInBasketPreview() {
    NewverseTheme {
        MainScreenModern(
            state = PreviewData.sampleMainScreenState.copy(
                selectedQuantity = 2.0
            ),
            onAction = {}
        )
    }
}

// ===== Main Screen Previews =====

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

// ===== Basket Screen Previews =====

@Preview(name = "Basket Screen - Empty", showBackground = true)
@Composable
fun BasketScreenEmptyPreview() {
    NewverseTheme {
        Surface {
            BasketContent(
                state = BasketScreenState(
                    items = emptyList(),
                    total = 0.0
                ),
                currentArticles = emptyList(),
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
            BasketContent(
                state = BasketScreenState(
                    items = PreviewData.sampleOrderedProducts.take(3),
                    total = 12.50
                ),
                currentArticles = emptyList(),
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
            BasketContent(
                state = BasketScreenState(
                    items = PreviewData.sampleOrderedProducts.take(2),
                    total = 8.75,
                    isCheckingOut = true
                ),
                currentArticles = emptyList(),
                onAction = {}
            )
        }
    }
}

// ===== Customer Profile Previews =====

@Preview(name = "Customer Profile Screen", showBackground = true)
@Composable
fun CustomerProfileScreenPreview() {
    NewverseTheme {
        Surface {
            CustomerProfileScreenModern(
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
            CustomerProfileScreenModern(
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
            CustomerProfileScreenModern(
                state = PreviewData.sampleCustomerProfileStateEmpty,
                onAction = {}
            )
        }
    }
}
