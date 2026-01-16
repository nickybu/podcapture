# Add project specific ProGuard rules here.

# Keep Vosk classes
-keep class org.vosk.** { *; }

# Keep Room entities
-keep class com.podcapture.data.model.** { *; }

# Keep Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
