# Libraries (Kotlin, kotlinx, Compose, Firebase, Koin, Coil, WorkManager) ship their
# own consumer R8 rules. Do not add package-wide `-keep class lib.** { *; }` rules here:
# they disable shrinking/obfuscation for the whole library (Play "App-Optimierung: Niedrig").

# Firebase Android SDK reads OrderDto by reflection
# (sell: ListenerService -> getValue(OrderDto::class.java))
-keep class com.together.newverse.data.firebase.model.** { *; }

# Move obfuscated classes into a single package (smaller DEX)
-repackageclasses

-dontwarn androidx.compose.**
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**
-dontwarn coil3.**
