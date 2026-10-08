package com.together.newverse.data.config

import com.together.newverse.shared.BuildConfig

// Set per build type in shared/build.gradle.kts, alongside the google-services.json
// that selects the matching Firebase project.
actual val defaultSellerId: String = BuildConfig.DEFAULT_SELLER_ID
