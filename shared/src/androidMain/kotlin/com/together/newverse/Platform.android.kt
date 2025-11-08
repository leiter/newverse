package com.together.newverse

actual fun getPlatform(): String = "Android ${android.os.Build.VERSION.SDK_INT}"
