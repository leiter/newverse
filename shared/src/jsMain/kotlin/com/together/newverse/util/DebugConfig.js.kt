package com.together.newverse.util

actual val isDebugBuild: Boolean = js("typeof process !== 'undefined' && process.env.NODE_ENV !== 'production'") as Boolean
