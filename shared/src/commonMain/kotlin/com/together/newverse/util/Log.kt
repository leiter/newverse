package com.together.newverse.util

enum class LogLevel { DEBUG, INFO, WARN, ERROR }

object Log {

    /**
     * Everything below this is dropped at the call site, message lambda unevaluated.
     *
     * [isDebugBuild] is the *debuggable* flag, not the build type's name and not
     * whether R8 ran: on Android it reads `ApplicationInfo.FLAG_DEBUGGABLE`
     * (`DebugConfig.android.kt`). A release APK sideloaded with
     * `-Pnewverse.skipMinify=true` is therefore unminified, readable in a stack trace,
     * and still silent below [LogLevel.ERROR] -- which is a surprise worth knowing
     * before spending an afternoon wondering where the log lines went. The release
     * block in `androidApp/build.gradle.kts` has the one line to uncomment when a
     * sideloaded build should talk.
     */
    @PublishedApi
    internal var minLevel: LogLevel = if (isDebugBuild) LogLevel.DEBUG else LogLevel.ERROR

    @PublishedApi
    internal var printImpl: (LogLevel, String, String) -> Unit = { level, tag, message ->
        println("[${level.name}] $tag: $message")
    }

    inline fun d(tag: String, message: () -> String) {
        if (minLevel <= LogLevel.DEBUG) printImpl(LogLevel.DEBUG, tag, message())
    }

    inline fun i(tag: String, message: () -> String) {
        if (minLevel <= LogLevel.INFO) printImpl(LogLevel.INFO, tag, message())
    }

    inline fun w(tag: String, message: () -> String) {
        if (minLevel <= LogLevel.WARN) printImpl(LogLevel.WARN, tag, message())
    }

    inline fun e(tag: String, message: () -> String) {
        if (minLevel <= LogLevel.ERROR) printImpl(LogLevel.ERROR, tag, message())
    }

    inline fun e(tag: String, error: Throwable, message: () -> String) {
        if (minLevel <= LogLevel.ERROR) {
            printImpl(LogLevel.ERROR, tag, "${message()}: ${error.message}")
        }
    }
}
