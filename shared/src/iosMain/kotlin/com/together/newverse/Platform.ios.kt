package com.together.newverse

import platform.UIKit.UIDevice

actual fun getPlatform(): String =
    "${UIDevice.currentDevice.systemName()} ${UIDevice.currentDevice.systemVersion}"
