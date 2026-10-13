# Project-specific ProGuard rules

# kotlinx.serialization: keep generated serializers of app classes (save game JSON)
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.idlerpg.game.** {
    *** Companion;
}
-keepclasseswithmembers class com.idlerpg.game.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.idlerpg.game.**$$serializer { *; }
