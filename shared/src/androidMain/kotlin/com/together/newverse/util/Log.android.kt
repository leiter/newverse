package com.together.newverse.util

/**
 * Route [Log] at android.util.Log. Called from the Application class in the app
 * module, so it cannot be internal — same as [initDebugFlag] beside it.
 */
fun androidLogInit() {
    Log.printImpl = { level, tag, message ->
        when (level) {
            LogLevel.DEBUG -> android.util.Log.d(tag, message)
            LogLevel.INFO -> android.util.Log.i(tag, message)
            LogLevel.WARN -> android.util.Log.w(tag, message)
            LogLevel.ERROR -> android.util.Log.e(tag, message)
        }
    }
}
