# Libraries (Kotlin, kotlinx, Compose, Firebase, Koin, Coil, WorkManager) ship their
# own consumer R8 rules. Do not add package-wide `-keep class lib.** { *; }` rules here:
# they disable shrinking/obfuscation for the whole library (Play "App-Optimierung: Niedrig").

# Firebase Android SDK reads OrderDto by reflection
# (sell: ListenerService -> getValue(OrderDto::class.java))
-keep class com.together.newverse.data.firebase.model.** { *; }

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
