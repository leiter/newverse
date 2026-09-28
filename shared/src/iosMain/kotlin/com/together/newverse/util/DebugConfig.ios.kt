package com.together.newverse.util

import kotlin.native.Platform

actual val isDebugBuild: Boolean = Platform.isDebugBinary
