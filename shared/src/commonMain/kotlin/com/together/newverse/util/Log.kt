package com.together.newverse.util

enum class LogLevel { DEBUG, INFO, WARN, ERROR }

object Log {

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
