package com.together.newverse.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import com.together.newverse.domain.model.ActionLocation

/**
 * How the top bar changes what it is showing.
 *
 * Two different events share this bar, and they must not look the same.
 *
 * A **route change** relabels the title: the buyer caused it, they already know what
 * they tapped, and a title that flew in would read as a second navigation on top of
 * the one they just made. So it crossfades, and nothing moves.
 *
 * A **remote item** — the seller's announcement, their message box, a button they
 * published — arrives on its own, from someone else, possibly while the buyer is
 * looking at the screen. Appearing instantly is how a tap lands on a thing that was
 * not there when the finger started moving. These drop in from above and lift back
 * out, which says where they came from and gives the eye something to follow.
 *
 * Short on purpose. [ENTER_MILLIS] is long enough to be seen and [EXIT_MILLIS] is
 * shorter still, because the slot has to be free before the next thing can use it —
 * an outgoing announcement that lingers delays the title the buyer is waiting for.
 */
private const val ENTER_MILLIS = 220
private const val EXIT_MILLIS = 160

/**
 * What the title slot is currently showing. The key an [androidx.compose.animation.AnimatedContent]
 * animates between, so it carries everything the slot needs to draw itself and nothing
 * that changes more often than the slot does.
 *
 * In particular the basket's count and total are *not* in here, and neither is the
 * draft in a message box: both change while the slot stays the same thing, and keying
 * on them would restart the transition on every keystroke and every tap of a plus
 * button. The content reads those straight from state instead, which recomposes
 * without animating.
 */
sealed interface TopBarTitleSlot {

    /** The basket's own heading, which carries live figures and so keys on nothing. */
    data object Basket : TopBarTitleSlot

    /** The seller's question, holding the title slot until the buyer answers or closes it. */
    data class Prompt(val location: ActionLocation, val question: String) : TopBarTitleSlot

    /** The seller's sentence to every buyer, in place of the home title. */
    data class Announcement(val text: String) : TopBarTitleSlot

    /** The screen's own name. */
    data class Plain(val text: String) : TopBarTitleSlot

    /** Whether this slot is something the seller pushed, rather than the app's own title. */
    val isRemote: Boolean get() = this is Prompt || this is Announcement
}

/**
 * The transition between two title slots: a drop-in when either side is a remote item,
 * a plain crossfade when it is only the title being relabelled.
 *
 * `SizeTransform(clip = false)` because the slot's two states are rarely the same
 * width — a one-word title against a full-width message box — and clipping to the
 * animating bounds would wipe the incoming content column by column instead of
 * letting it fade in whole.
 */
@OptIn(ExperimentalAnimationApi::class)
fun AnimatedContentTransitionScope<TopBarTitleSlot>.topBarTitleTransition(): ContentTransform {
    val involvesRemote = targetState.isRemote || initialState.isRemote
    return if (involvesRemote) {
        (slideInVertically(tween(ENTER_MILLIS, easing = FastOutSlowInEasing)) { -it / 2 } +
            fadeIn(tween(ENTER_MILLIS))) togetherWith
            (slideOutVertically(tween(EXIT_MILLIS, easing = FastOutSlowInEasing)) { -it / 2 } +
                fadeOut(tween(EXIT_MILLIS))) using SizeTransform(clip = false)
    } else {
        fadeIn(tween(ENTER_MILLIS)) togetherWith
            fadeOut(tween(EXIT_MILLIS)) using SizeTransform(clip = false)
    }
}

/**
 * How a published button or the contact shortcut comes and goes.
 *
 * These sit among the app's own icons, so they grow from their own centre rather than
 * sliding: a slide would push the fixed actions sideways, and the whole point of
 * drawing them inboard is that a seller publishing something never moves an icon the
 * buyer has learned the position of. Scaling from 0.8 rather than 0 keeps it a fade
 * with a hint of arrival instead of a pop.
 */
fun <T> AnimatedContentTransitionScope<T>.topBarActionTransition(): ContentTransform =
    (fadeIn(tween(ENTER_MILLIS)) + scaleIn(tween(ENTER_MILLIS), initialScale = 0.8f)) togetherWith
        (fadeOut(tween(EXIT_MILLIS)) + scaleOut(tween(EXIT_MILLIS), targetScale = 0.8f)) using
        SizeTransform(clip = false)
