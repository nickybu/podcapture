# Add project specific ProGuard rules here.
#
# Libraries that ship their own consumer rules (no project rules needed):
# - kotlinx-serialization: keeps Companion / serializer() of @Serializable classes
#   (META-INF/com.android.tools/r8/kotlinx-serialization-*.pro)
# - Retrofit 2.11: keeps @retrofit2.http interfaces, generic signatures and Continuation
#   for suspend functions (META-INF/proguard/retrofit2.pro)
# - OkHttp, Room, Media3, Navigation, DataStore, Koin, Coil

# Sherpa-ONNX (Whisper transcription). The AAR ships no consumer rules, and the native
# libsherpa-onnx-jni.so reads config fields and builds result objects by name through JNI,
# so the classes and all their members must keep their names.
-keep class com.k2fsa.sherpa.onnx.** { *; }

# Keep Room entities / domain models
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
