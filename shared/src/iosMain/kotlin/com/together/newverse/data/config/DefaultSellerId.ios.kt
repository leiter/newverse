package com.together.newverse.data.config

import kotlin.native.Platform

// iOS has no BuildConfig, so this keys off the same debug/release distinction that
// `copy-firebase-plist.sh` uses to choose the GoogleService-Info plist. The two must
// agree: a Release binary reads the production project and needs the production seller.
actual val defaultSellerId: String =
    if (Platform.isDebugBinary) "cPkcZSiF3LMXjWoqW6AqpA9paoO2"  // fire-one-58ddc
    else "2e2h2VdsyqM7QakqUfCVLkFCsUh1"                          // bodenschaetze-a988e
