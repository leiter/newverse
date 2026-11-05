package com.together.newverse.ui.navigation

import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.together.newverse.domain.model.SellerUiAction
import com.together.newverse.ui.components.AppLogoMark
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.a11y_home_brand_about
import org.jetbrains.compose.resources.stringResource

/**
 * The brand mark in the home top bar's held seat.
 *
 * The seat was a blank 48dp spacer. It exists because the stand-in basket arrives
 * exactly as the buyer reaches for the hero card's number pad, and a bar that shuffled
 * sideways under their finger at that moment would be the worst of it — so the width
 * is spent whether the basket is in it or not. Spent on nothing, though: a hole in the
 * bar where every other screen has an affordance.
 *
 * Now it holds the mark, and tapping it opens About — which is where the app says who
 * it is, whose shop this is and how to reach them. That is the question a brand mark
 * invites, and it was previously reachable only through the profile page.
 *
 * **The seat is still the basket's.** While the number pad is up the basket takes this
 * place back, because being able to reach the basket at that moment is worth more than
 * a logo; the crossfade between them is the one the seat already had.
 *
 * [override] is the seller's, and null is the ordinary case rather than an empty one —
 * see `ActionLocation.TOPBAR_HOME_BRAND_ICON`. A published row changes where the tap
 * goes and what a screen reader calls it; the mark itself stays the app's, because
 * nothing in `SellerUiAction` can name an image yet. When something can, it belongs
 * here and nowhere else.
 */
@Composable
internal fun HomeBrandAction(
    override: SellerUiAction?,
    tint: Color,
    onOpenAbout: () -> Unit,
    onOpenOverride: (SellerUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The seller's label, which in this one slot is read rather than drawn: an icon
    // with no name cannot be reached by a screen reader at all, and "Hofladen" is
    // what tells a buyer where the tap goes before they take it.
    val description = override?.label ?: stringResource(Res.string.a11y_home_brand_about)

    IconButton(
        onClick = { if (override != null) onOpenOverride(override) else onOpenAbout() },
        modifier = modifier
    ) {
        AppLogoMark(
            // The mark is 1.789:1, so the width is what sets its size. 26dp leaves
            // it inside the 24dp-icon convention on its long axis and well inside
            // the 48dp touch target, which the IconButton keeps regardless.
            width = 26.dp,
            tint = tint,
            // AppLogoMark is decorative by default — every other screen that draws it
            // spells the app name out underneath. Here there is no such text, so the
            // name is put on the button rather than left unsaid.
            modifier = Modifier.semantics { contentDescription = description }
        )
    }
}
