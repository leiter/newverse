package com.together.newverse.domain.scale

import com.together.newverse.domain.model.MeasuredQuantity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * A scale the seller app can read a weight from.
 *
 * Deliberately narrow: a screen that accepts a weight needs the current reading and
 * whether a scale is there at all, nothing about cables, ports or protocols. That
 * keeps every quantity screen writable today, against [NoWeightSource], before any
 * hardware exists — and keeps the platforms that can never host a scale (iOS has no
 * USB host for non-MFi devices) from needing a special case in the UI.
 *
 * Readings arrive continuously while goods settle on the platform, so a collector
 * shows every value but books only a settled one; see [MeasuredQuantity.isUsable].
 */
interface WeightSource {

    /** Whether a scale is connected, and what went wrong if not. */
    val status: StateFlow<ScaleStatus>

    /**
     * Readings as they arrive, in the unit the scale reports.
     *
     * Empty for as long as no scale is connected; a collector stays subscribed
     * across a disconnect and reconnect rather than resubscribing.
     */
    val readings: Flow<MeasuredQuantity>

    /** Sets the current load as the new zero, as the scale's own tare key would. */
    suspend fun tare(): Result<Unit>

    /** Resets the scale to zero with an empty platform. */
    suspend fun zero(): Result<Unit>
}

/** Whether a scale is available, and why not when it is not. */
sealed interface ScaleStatus {

    /** This platform cannot host a scale at all. Never offer the seller the option. */
    data object Unsupported : ScaleStatus

    /** Supported, but nothing is plugged in. */
    data object Disconnected : ScaleStatus

    /** A device is attached; opening the port or waiting for permission. */
    data object Connecting : ScaleStatus

    data class Connected(val deviceName: String) : ScaleStatus

    /**
     * A scale was found but could not be used — permission refused, port busy,
     * unreadable frames. [reason] is for the log and a diagnostics screen, not for
     * the seller, who gets a translated message chosen from this state.
     */
    data class Failed(val reason: String) : ScaleStatus

    /** Whether a weight can be expected right now. */
    val canWeigh: Boolean get() = this is Connected

    /** Whether offering a "weigh" affordance makes sense on this device at all. */
    val isPossibleHere: Boolean get() = this !is Unsupported
}
