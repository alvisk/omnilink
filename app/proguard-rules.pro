# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# ═══════════════════════════════════════════════════════════════════════════
# 🚀 CACTUS SDK - Keep all classes (uses JNI/native code)
# ═══════════════════════════════════════════════════════════════════════════
-keep class com.cactus.** { *; }
-keepclassmembers class com.cactus.** { *; }
-dontwarn com.cactus.**

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep data classes used for JSON serialization
-keep class com.example.omni_link.data.** { *; }
-keep class com.example.omni_link.ai.** { *; }

# Gson serialization
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# ═══════════════════════════════════════════════════════════════════════════

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
