package com.together.newverse.ui.mainscreen

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import com.together.newverse.ui.components.ShimmerBrush as CommonShimmerBrush
import com.together.newverse.ui.components.HeroProductCardSkeleton as CommonHeroProductCardSkeleton
import com.together.newverse.ui.components.ProductCardSkeleton as CommonProductCardSkeleton

/**
 * Buy-flavor skeleton components that delegate to shared commonMain implementations.
 * These are kept internal for use within the mainscreen package.
 */

@Composable
internal fun ShimmerBrush(): Brush = CommonShimmerBrush()

@Composable
internal fun HeroProductCardSkeleton(
    modifier: Modifier = Modifier
) {
    CommonHeroProductCardSkeleton(modifier = modifier)
}

@Composable
internal fun ProductCardSkeleton(
    modifier: Modifier = Modifier
) {
    CommonProductCardSkeleton(modifier = modifier)
}
