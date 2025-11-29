# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# ═══════════════════════════════════════════════════════════════════════════
# 📋 PRODUCTION CRASH REPORTING - Preserve line numbers for stack traces
# ═══════════════════════════════════════════════════════════════════════════
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ═══════════════════════════════════════════════════════════════════════════
# 🚀 CACTUS SDK - Keep all classes (uses JNI/native code)
# ═══════════════════════════════════════════════════════════════════════════
-keep class com.cactus.** { *; }
-keepclassmembers class com.cactus.** { *; }
-dontwarn com.cactus.**

# ═══════════════════════════════════════════════════════════════════════════
# 💫 NOTHING GLYPH SDK - Keep all classes for LED matrix control
# ═══════════════════════════════════════════════════════════════════════════
-keep class com.nothing.ketchum.** { *; }
-keepclassmembers class com.nothing.ketchum.** { *; }
-keep class com.nothing.thirdparty.** { *; }
-keepclassmembers class com.nothing.thirdparty.** { *; }
-dontwarn com.nothing.**

# ═══════════════════════════════════════════════════════════════════════════
# 🔍 ML KIT - Keep classes for text recognition
# ═══════════════════════════════════════════════════════════════════════════
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# ═══════════════════════════════════════════════════════════════════════════
# 💾 ROOM DATABASE - Keep entities and DAOs
# ═══════════════════════════════════════════════════════════════════════════
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Keep data classes used for JSON serialization
-keep class com.example.omni_link.data.** { *; }
-keep class com.example.omni_link.ai.** { *; }

# ═══════════════════════════════════════════════════════════════════════════
# 📦 GSON SERIALIZATION
# ═══════════════════════════════════════════════════════════════════════════
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# ═══════════════════════════════════════════════════════════════════════════
# 🎨 JETPACK COMPOSE - Keep Composable metadata
# ═══════════════════════════════════════════════════════════════════════════
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ═══════════════════════════════════════════════════════════════════════════
# ♿ ACCESSIBILITY SERVICE - Keep service classes
# ═══════════════════════════════════════════════════════════════════════════
-keep class com.example.omni_link.service.** { *; }

# ═══════════════════════════════════════════════════════════════════════════
# 🔒 KOTLIN COROUTINES
# ═══════════════════════════════════════════════════════════════════════════
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**
