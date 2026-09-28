package com.together.newverse.util

import android.content.pm.ApplicationInfo

actual val isDebugBuild: Boolean
    get() = _isDebugBuild

@Volatile
private var _isDebugBuild: Boolean = false

fun initDebugFlag(appInfo: ApplicationInfo) {
    _isDebugBuild = (appInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
}
