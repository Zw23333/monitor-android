# kotlinx.serialization keeps its serializers in companion objects the plugin
# generates; R8 cannot see the reflection-free entry points without these.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class dev.monitorprobe.client.**$$serializer { *; }
-keepclassmembers class dev.monitorprobe.client.** {
    *** Companion;
}
-keepclasseswithmembers class dev.monitorprobe.client.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp warns about the optional Conscrypt and BouncyCastle providers it probes
# for at runtime; neither ships with this app.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
