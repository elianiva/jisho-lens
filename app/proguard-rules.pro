# kotlinx.serialization generates companion serializers. R8 must keep the
# serializer accessors used by the deinflection rules and navigation routes.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class **$$serializer {
    *;
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class com.elianiva.jisholens.dictionary.** {
    *** Companion;
}
