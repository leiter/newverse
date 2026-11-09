# Libraries (Kotlin, kotlinx, Compose, Firebase, Koin, Coil, WorkManager) ship their
# own consumer R8 rules. Do not add package-wide `-keep class lib.** { *; }` rules here:
# they disable shrinking/obfuscation for the whole library (Play "App-Optimierung: Niedrig").

# Firebase Android SDK reads OrderDto by reflection
# (sell: ListenerService -> getValue(OrderDto::class.java))
-keep class com.together.newverse.data.firebase.model.** { *; }

# Enum constants are read back from the database by name, and only by name:
#   ContactChannel.entries.firstOrNull { it.name == raw }
# R8 cannot see those constants referenced individually, so it deletes them. In the
# buy release of 2026-11-09, ContactChannel went from nine constants to two (only
# PHONE and LOCATION survived, because those two are named in code), AccessStatus
# lost PENDING, and ArticleScarcity lost two of four. The lookups then return null
# and every caller falls back to its default -- silently. No crash, no log, just a
# seller's contacts parsed into nothing and a top-bar menu that never appeared.
#
# Keep the members of our own enums. Scoped to this package, so it shrinks libraries
# as before; the DEX cost is a few dozen fields. Any enum stored as a name needs this
# -- which is nearly all of them: OrderStatus, OrderOrigin, PriceCause, ActionKind,
# ActionLocation, ActionAudience, SellerEventType, StockMovementKind, QuantitySource,
# InvitationStatus, Fulfilment.
-keepclassmembers enum com.together.newverse.** { *; }

# Move obfuscated classes into a single package (smaller DEX)
-repackageclasses

# Strip debug/info/warn log calls from release builds.
# R8 removes the call sites entirely because the inline lambdas are expanded at each caller.
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int v(...);
}

-dontwarn androidx.compose.**
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**
-dontwarn coil3.**
