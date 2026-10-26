package com.together.newverse.ui.theme

import androidx.compose.runtime.Composable

/**
 * Asks the platform to draw the system status and navigation bar icons light or
 * dark.
 *
 * The system bars are transparent and the app paints behind them, so nothing
 * but the app knows what is actually back there. It differs per screen: the
 * brown top bar is behind the status bar on every route inside the scaffold,
 * but the navigation bar only has the brown bottom bar behind it on Home,
 * Warenkorb and Profil — everywhere else, and on every route at Expanded width
 * where the rail replaces the bottom bar, it is the page itself.
 *
 * Get this wrong and the icons vanish: white on paper measures 1.5:1, against
 * the 3:1 a control needs.
 *
 * Android is the only platform that implements it. iOS manages the status bar
 * through the view controller and has no navigation bar; the web target has
 * neither.
 *
 * @param lightStatusIcons true when the status bar sits on something dark
 * @param lightNavIcons true when the navigation bar sits on something dark
 */
@Composable
expect fun SystemBarIcons(
    lightStatusIcons: Boolean,
    lightNavIcons: Boolean,
)
