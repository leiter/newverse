package com.together.newverse.util

import kotlin.native.Platform

@OptIn(kotlin.experimental.ExperimentalNativeApi::class)
actual val isDebugBuild: Boolean = Platform.isDebugBinary
