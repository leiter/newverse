package com.together.newverse.data.config

import kotlin.native.Platform

// iOS has no BuildConfig, so this keys off the same debug/release distinction that
// `copy-firebase-plist.sh` uses to choose the GoogleService-Info plist. The two must
// agree: a Release binary reads the production project and needs the production seller.
// Keep both ids in step with DEV_SELLER_ID / PROD_SELLER_ID in shared/build.gradle.kts.
@OptIn(kotlin.experimental.ExperimentalNativeApi::class)
actual val defaultSellerId: String =
    if (Platform.isDebugBinary) "cPkcZSiF3LMXjWoqW6AqpA9paoO2"  // fire-one-58ddc
    else "x64pN9m4wcYlZqB2qMTxpwIm9DD2"                          // bodenschaetze-a988e
