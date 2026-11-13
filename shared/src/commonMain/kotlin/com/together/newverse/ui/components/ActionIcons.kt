package com.together.newverse.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.BakeryDining
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Egg
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ShoppingBasket
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.ui.graphics.vector.ImageVector
import com.together.newverse.domain.model.ActionIcon

/**
 * The drawable behind a stored [ActionIcon], with the caller's default for null.
 *
 * A `when` rather than a `Map`, deliberately and not as a style preference.
 * `Icons.Default.*` are lazily built [ImageVector]s: a top-level map would construct
 * **every** icon in it at class-initialisation, on whichever thread first touched the
 * map, to answer one lookup. A `when` builds exactly the one asked for.
 *
 * It is also the shape that survives R8 without a keep rule. This app has already
 * shipped a bug where R8 removed enum constants it could not see used
 * (`139fc2a` — the brand action that only ever opened About), and a branch on a
 * compile-time constant is the version of this that cannot repeat it.
 *
 * It takes no fallback: by the time an [ActionIcon] exists there is nothing left to
 * fall back from. A row with no icon, or one naming an icon this version does not
 * know, has already resolved to its kind's default in
 * [com.together.newverse.domain.model.SellerUiAction.effectiveIcon] — so the default
 * is per kind, where it belongs, rather than one constant here. That matters: the
 * obvious global candidate is worse than generic, because `MoreVert` means "overflow
 * menu" in Material and a buyer would tap it expecting a menu and get a panel.
 */
fun ActionIcon.vector(): ImageVector = when (this) {
    ActionIcon.HELP -> Icons.AutoMirrored.Filled.HelpOutline
    ActionIcon.INFO -> Icons.Filled.Info
    ActionIcon.CART -> Icons.Filled.ShoppingCart
    ActionIcon.BASKET -> Icons.Filled.ShoppingBasket
    ActionIcon.STORE -> Icons.Filled.Storefront
    ActionIcon.LEAF -> Icons.Filled.Spa
    ActionIcon.BREAD -> Icons.Filled.BakeryDining
    ActionIcon.EGG -> Icons.Filled.Egg
    ActionIcon.DRINK -> Icons.Filled.LocalDrink
    ActionIcon.CLOCK -> Icons.Filled.Schedule
    ActionIcon.CALENDAR -> Icons.Filled.CalendarMonth
    ActionIcon.DELIVERY -> Icons.Filled.LocalShipping
    ActionIcon.GIFT -> Icons.Filled.CardGiftcard
    ActionIcon.STAR -> Icons.Filled.StarBorder
    ActionIcon.HEART -> Icons.Filled.FavoriteBorder
    ActionIcon.MESSAGE -> Icons.Filled.ChatBubbleOutline
    ActionIcon.LIST -> Icons.AutoMirrored.Filled.List
    ActionIcon.WARNING -> Icons.Filled.WarningAmber
}
