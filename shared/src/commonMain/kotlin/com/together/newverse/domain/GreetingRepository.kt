package com.together.newverse.domain

import com.together.newverse.getPlatform

class GreetingRepository {
    fun getGreeting(): String {
        return "Hello from Kotlin Multiplatform with Compose!"
    }

    fun getPlatformGreeting(): String {
        return "Platform: ${getPlatform()}"
    }
}
