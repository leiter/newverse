package com.together.newverse.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.together.newverse.domain.model.SellerContact
import com.together.newverse.domain.model.SellerUiAction
import com.together.newverse.ui.components.AppLogoMark
import com.together.newverse.ui.components.displayName
import com.together.newverse.ui.components.icon
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.a11y_brand_menu
import newverse.shared.generated.resources.a11y_home_brand_about
import newverse.shared.generated.resources.nav_about
import org.jetbrains.compose.resources.stringResource

/**
 * The brand mark in the top bar's held seat, on Home and on Profile.
 *
 * The seat was a blank 48dp spacer on Home. It exists because the stand-in basket
 * arrives exactly as the buyer reaches for the hero card's number pad, and a bar that
 * shuffled sideways under their finger at that moment would be the worst of it — so the
 * width is spent whether the basket is in it or not. Spent on nothing, though: a hole in
 * the bar where every other screen has an affordance.
 *
 * Now it holds the mark, and the mark holds **one idea: who this seller is and how to
 * reach them.** That used to be two adjacent icons — a person glyph for the contact
 * channels and the mark for About — which is one affordance too many for one idea, and
 * the person glyph was the weaker signifier of the two for "the seller's phone number".
 *
 * So there is one seat and one rule:
 *
 * - **No contacts to show** — the buyer is in demo mode or unapproved
 *   ([BuyAppState.topBarContacts][com.together.newverse.ui.state.BuyAppState.topBarContacts]
 *   is gated on approval), or the seller has published none. The tap goes straight to
 *   About, or to [override] when the seller has pointed the mark somewhere. This is
 *   exactly what the seat did before and is deliberately unchanged.
 * - **Contacts to show** — the mark opens a menu: the seller's override first if there
 *   is one, then the channels, then **About last**, under a divider.
 *
 * About stays in the menu rather than being displaced by it, and that is what makes the
 * two cases one rule instead of two. A buyer who learned "the mark opens About" during
 * demo does not lose that the day they are approved — the destination simply moves one
 * tap deeper and the mark still means the same thing. It also keeps the bar's only route
 * into the imprint and terms, which Profile otherwise carries alone.
 *
 * The bar's geometry is identical in both cases, and identical for an approved buyer and
 * a demo one: one icon button's width, always, whoever is looking.
 *
 * **The seat is still the basket's on Home.** While the number pad is up the basket takes
 * this place back, because being able to reach the basket at that moment is worth more
 * than the mark; the crossfade between them is the one the seat already had. The menu
 * goes with it for as long as the basket is standing in.
 *
 * [override] is the seller's, and null is the ordinary case rather than an empty one —
 * see `ActionLocation.TOPBAR_HOME_BRAND_ICON`. It is a **Home** location, so the Profile
 * seat passes null: this change adds no new published location, and a seller who pointed
 * the home mark at their Instagram did not thereby ask for it on the profile page. A
 * published row changes where the tap goes and what a screen reader calls it; the mark
 * itself stays the app's, because nothing in `SellerUiAction` can name an image yet. When
 * something can, it belongs here and nowhere else.
 *
 * Note what this deliberately is **not**: a general `ActionKind.MENU` whose rows the
 * seller publishes per screen. That is a larger feature with its own unsettled questions
 * (where the kind comes from, naming a source rather than copying rows, the audience
 * default, an icon field) and it is parked until a second menu source actually exists.
 * This menu has exactly one source — the contact directory — and resolves it at render,
 * so it cannot go stale and it keeps `resolveFor`'s market union for free.
 */
/**
 * One row the mark can offer.
 *
 * [About] is a row like the others rather than a special case appended at the end,
 * which is what lets the whole rule be one list and one ordering.
 */
internal sealed interface BrandRow {
    data class Override(val action: SellerUiAction) : BrandRow
    data class Contact(val contact: SellerContact) : BrandRow
    data object About : BrandRow
}

/**
 * What the mark does when tapped: go straight somewhere, or open these rows in order.
 */
internal sealed interface BrandSeat {
    data class DirectTap(val target: BrandRow) : BrandSeat
    data class Menu(val rows: List<BrandRow>) : BrandSeat
}

/**
 * The rule, as a function of what the buyer has rather than of what is on screen.
 *
 * Pulled out of the composable so the decisions in it — that About is always present
 * and always last, that the seller's override keeps the first seat rather than being
 * displaced by a contact, and that no contacts means the seat behaves exactly as it
 * did before contacts existed — are pinned by ordinary unit tests. `BrandActionTest`.
 */
internal fun brandSeat(
    override: SellerUiAction?,
    contacts: List<SellerContact>,
): BrandSeat = if (contacts.isEmpty()) {
    // Unchanged from before the contacts existed: the override owns the tap if there
    // is one, otherwise About does.
    BrandSeat.DirectTap(override?.let { BrandRow.Override(it) } ?: BrandRow.About)
} else {
    BrandSeat.Menu(
        buildList {
            override?.let { add(BrandRow.Override(it)) }
            contacts.forEach { add(BrandRow.Contact(it)) }
            add(BrandRow.About)
        }
    )
}

@Composable
internal fun BrandAction(
    override: SellerUiAction?,
    contacts: List<SellerContact>,
    tint: Color,
    onOpenAbout: () -> Unit,
    onOpenContact: (SellerContact) -> Unit,
    onOpenOverride: (SellerUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    // One definition of the rule, shared with the tests. The composable only draws it.
    when (val seat = brandSeat(override, contacts)) {
        is BrandSeat.DirectTap -> {
            // The seller's label, which in this one slot is read rather than drawn: an
            // icon with no name cannot be reached by a screen reader at all, and
            // "Hofladen" is what tells a buyer where the tap goes before they take it.
            val target = seat.target
            val description = if (target is BrandRow.Override) {
                target.action.label
            } else {
                stringResource(Res.string.a11y_home_brand_about)
            }
            IconButton(
                onClick = {
                    if (target is BrandRow.Override) onOpenOverride(target.action) else onOpenAbout()
                },
                modifier = modifier
            ) {
                BrandMark(tint = tint, description = description)
            }
        }

        is BrandSeat.Menu -> {
            var expanded by remember { mutableStateOf(false) }
            // The description says the menu's subject rather than its rows: the rows are
            // the seller's and change under the buyer, and a label that listed them would
            // be wrong the moment a number is added.
            val description = stringResource(Res.string.a11y_brand_menu)

            Box(modifier = modifier) {
                IconButton(onClick = { expanded = true }) {
                    BrandMark(tint = tint, description = description)
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    seat.rows.forEach { row ->
                        // About is the app's own row among the seller's, and the one
                        // that is always there, so a line sets it apart from them.
                        if (row is BrandRow.About) HorizontalDivider()
                        BrandMenuRow(
                            row = row,
                            onOpen = {
                                when (row) {
                                    is BrandRow.Override -> onOpenOverride(row.action)
                                    is BrandRow.Contact -> onOpenContact(row.contact)
                                    BrandRow.About -> onOpenAbout()
                                }
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BrandMenuRow(row: BrandRow, onOpen: () -> Unit) {
    val label = when (row) {
        // The seller's own row carries no icon, because a published row cannot name
        // one — see `ActionLocation.TOPBAR_HOME_BRAND_ICON`.
        is BrandRow.Override -> row.action.label
        is BrandRow.Contact -> row.contact.label.ifBlank { row.contact.channel.displayName() }
        BrandRow.About -> stringResource(Res.string.nav_about)
    }
    val icon = when (row) {
        is BrandRow.Override -> null
        is BrandRow.Contact -> row.contact.channel.icon()
        BrandRow.About -> Icons.Default.Info
    }

    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = icon?.let {
            {
                Icon(
                    imageVector = it,
                    // The row's own text names it; the icon would only say it twice.
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        },
        onClick = onOpen
    )
}

@Composable
private fun BrandMark(tint: Color, description: String) {
    AppLogoMark(
        // The mark is 1.789:1, so the width is what sets its size. 26dp leaves it
        // inside the 24dp-icon convention on its long axis and well inside the 48dp
        // touch target, which the IconButton keeps regardless.
        width = 26.dp,
        tint = tint,
        // AppLogoMark is decorative by default — every other screen that draws it
        // spells the app name out underneath. Here there is no such text, so the
        // name is put on the button rather than left unsaid.
        modifier = Modifier.semantics { contentDescription = description }
    )
}
