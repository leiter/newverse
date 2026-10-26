package com.together.newverse.preview

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview

/**
 * Renders a preview twice, once on paper and once on Nachterde.
 *
 * A plain `@Preview` has no `uiMode`, so it always renders light no matter what
 * the IDE or the machine is set to. That makes the dark theme invisible in the
 * preview pane even when it works correctly on a device, so every preview that
 * is about colour should use this annotation instead of `@Preview`.
 */
@Preview(
    name = "Hell",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_NO or Configuration.UI_MODE_TYPE_NORMAL,
)
@Preview(
    name = "Dunkel",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
)
annotation class LightDarkPreview
