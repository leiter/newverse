package com.together.newverse.config

/**
 * Android implementation of BuildFlavorDetector
 *
 * Detects the current build flavor from BuildConfig
 */
actual object BuildFlavorDetector {
    actual fun getCurrentFlavor(): AppFlavor {
        // Read flavor from BuildConfig
        // This will be set by Gradle build configuration
        return try {
            println("🔍 BuildFlavorDetector: Attempting to detect flavor...")

            // Try androidApp BuildConfig first (where flavor is defined)
            val buildConfigClass = try {
                Class.forName("com.together.newverse.android.BuildConfig")
            } catch (e: ClassNotFoundException) {
                println("⚠️ androidApp BuildConfig not found, trying shared...")
                Class.forName("com.together.newverse.BuildConfig")
            }

            println("✅ BuildConfig class found: ${buildConfigClass.name}")

            val flavorField = buildConfigClass.getField("FLAVOR")
            val flavor = flavorField.get(null) as String

            println("✅ Detected flavor: '$flavor'")

            val appFlavor = when (flavor.lowercase()) {
                "sell" -> {
                    println("🏪 Flavor is SELL")
                    AppFlavor.SELL
                }
                "buy" -> {
                    println("🛒 Flavor is BUY")
                    AppFlavor.BUY
                }
                else -> {
                    println("⚠️ Unknown flavor '$flavor', defaulting to BUY")
                    AppFlavor.BUY
                }
            }

            println("🎯 Final AppFlavor: $appFlavor")
            appFlavor

        } catch (e: ClassNotFoundException) {
            println("❌ BuildConfig class not found: ${e.message}")
            println("❌ Defaulting to BUY flavor")
            AppFlavor.BUY
        } catch (e: NoSuchFieldException) {
            println("❌ FLAVOR field not found in BuildConfig: ${e.message}")
            println("❌ Defaulting to BUY flavor")
            AppFlavor.BUY
        } catch (e: Exception) {
            println("❌ Error detecting build flavor: ${e.javaClass.simpleName} - ${e.message}")
            e.printStackTrace()
            println("❌ Defaulting to BUY flavor")
            AppFlavor.BUY
        }
    }
}
