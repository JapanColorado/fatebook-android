# Moshi — keep @JsonClass-annotated DTOs and their generated adapters
-keep @com.squareup.moshi.JsonClass class * { *; }
-keep class **JsonAdapter {
    <init>(...);
    <fields>;
}

# Retrofit — keep service interface methods
-keepattributes Signature
-keepattributes *Annotation*
-keep,allowobfuscation interface retrofit2.Call
-keep,allowobfuscation interface retrofit2.Response
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Dao interface *

# Hilt
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.internal.GeneratedComponent

# EncryptedSharedPreferences
-keep class androidx.security.crypto.** { *; }

# WorkManager + Hilt integration
-keep class * extends androidx.work.ListenableWorker { *; }

# Kotlin metadata (needed by Moshi for code-gen verification)
-keep class kotlin.Metadata { *; }

# Remove debug logging in release
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}
