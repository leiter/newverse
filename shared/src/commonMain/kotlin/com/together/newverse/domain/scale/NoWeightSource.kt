package com.together.newverse.domain.scale

import com.together.newverse.domain.model.MeasuredQuantity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * The [WeightSource] for a build with no scale support: reports [ScaleStatus.Unsupported]
 * and never emits a reading.
 *
 * This is what the seller app binds today, and what iOS binds permanently. Screens
 * written against [WeightSource] work unchanged against it — they hide the weigh
 * affordance because [ScaleStatus.isPossibleHere] is false, and the seller types the
 * quantity as before.
 */
object NoWeightSource : WeightSource {

    override val status: StateFlow<ScaleStatus> = MutableStateFlow(ScaleStatus.Unsupported)

    override val readings: Flow<MeasuredQuantity> = emptyFlow()

    override suspend fun tare(): Result<Unit> = Result.failure(NoScaleConnected)

    override suspend fun zero(): Result<Unit> = Result.failure(NoScaleConnected)
}

/** Raised when a scale command is sent with no scale to send it to. */
object NoScaleConnected : IllegalStateException("No scale is connected")
