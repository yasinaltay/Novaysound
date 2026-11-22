# NewPipeExtractor (reflection + Rhino JS motoru)
-keep class org.schabi.newpipe.extractor.** { *; }
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.**
-dontwarn org.mozilla.classfile.**
-dontwarn java.beans.**
-dontwarn javax.script.**
-dontwarn jdk.dynalink.**

# Chromecast seçenek sağlayıcısı (manifestten isimle yüklenir)
-keep class com.novay.sound.playback.CastOptionsProvider { *; }

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class com.novay.sound.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
