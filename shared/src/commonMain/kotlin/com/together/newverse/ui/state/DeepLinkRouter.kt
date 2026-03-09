package com.together.newverse.ui.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Singleton bridge for routing deep link URLs from native platform code (iOS Swift)
 * into the Kotlin/Compose layer.
 *
 * Swift calls [route] from `.onOpenURL`; AppScaffold observes [pendingUrl] and
 * dispatches the appropriate [BuySellerAction] to [BuyAppViewModel].
 */
object DeepLinkRouter {
    private val _pendingUrl = MutableStateFlow<String?>(null)
    val pendingUrl: StateFlow<String?> = _pendingUrl.asStateFlow()

    fun route(url: String) {
        _pendingUrl.value = url
    }

    fun consume() {
        _pendingUrl.value = null
    }
}
