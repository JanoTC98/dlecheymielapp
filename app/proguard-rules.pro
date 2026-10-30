# App-level R8 rules for the release build (optimization in app/build.gradle.kts).
# Retrofit, OkHttp and kotlinx-serialization ship consumer rules that R8 applies (see mapping/configuration.txt).
# This file only covers what those rules do not.

# Generic signatures, inner classes and annotations are read at runtime by Retrofit (service method return types)
# and by kotlinx-serialization (serializer lookup on companions).
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*

# Response models that only appear as the return type of a Retrofit service method (for example DesistResult,
# RestoreResult, Rescheduled) are referenced only from generic signatures. The library rule lets R8 drop such classes
# even while the method stays, so the converter would decode them as Object and fail. Keep every @Serializable model
# with its members, including Companion.serializer(). Class names stay as they are: kotlinx-serialization uses the
# fully qualified name as the default discriminator for polymorphic types.
-keep @kotlinx.serialization.Serializable class com.grupo4.dlecheymielapp.** { *; }
