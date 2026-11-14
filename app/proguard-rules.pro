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

# Kuromoji reads its dictionary with Class.getResourceAsStream, relative to its
# own package. R8 renaming that package makes the lookup fail, and the
# tokenizer then falls back to one token per character.
-keep class com.atilika.kuromoji.** { *; }
-keeppackagenames com.atilika.kuromoji.**
-dontwarn com.atilika.kuromoji.**
