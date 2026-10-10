# ==============================================================================
# GoBeats R8 / ProGuard Optimization & Keep Rules
# ==============================================================================

# AndroidX Media3 / ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Keep MediaSession and MediaController callbacks
-keepclassmembers class * extends androidx.media3.session.MediaSessionService { *; }
-keepclassmembers class * extends androidx.media3.session.MediaLibraryService { *; }

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory { *; }
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler { *; }
-dontwarn kotlinx.coroutines.**

# Kotlinx Serialization & Reflection
-keepattributes *Annotation*,InnerClasses,Signature
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
    @kotlinx.serialization.Serializable <methods>;
}
-dontwarn kotlinx.serialization.**

# Models & Database Entities
-keep class com.example.gobeats.core.model.** { *; }
-keep class com.example.gobeats.core.backup.** { *; }

# Jetpack Compose
-keepclassmembers class * {
    @androidx.compose.runtime.Composable *;
}

# App Widgets
-keep class com.example.gobeats.feature.widget.** { *; }
-keep public class * extends android.appwidget.AppWidgetProvider { *; }

# Keep native methods and JNI
-keepclasseswithmembernames class * {
    native <methods>;
}
