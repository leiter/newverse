package com.together.newverse.ui.theme

import androidx.compose.runtime.Composable

/**
 * Deliberately does nothing — which is **not** the same as iOS being handled.
 *
 * iPhone has no navigation bar (the home indicator adapts itself), but it does
 * have a status bar, and its content colour is a `UIViewController` property
 * (`preferredStatusBarStyle`), not something a Compose side effect can reach.
 * Nothing in `iosApp` sets it today, so it is `.default` — dark content in
 * light mode — over the brown top bar at 2.16:1.
 *
 * See `doc/plans/TODO.md` → Medium Priority: Tablet Layout.
 */
@Composable
actual fun SystemBarIcons(
    lightStatusIcons: Boolean,
    lightNavIcons: Boolean,
) = Unit
