# Readium ships no shrinker rules and uses JavaScript bridges, Parcelable and JSON by name:
# keep it whole rather than risk a book that won't open in the release build.
-keep class org.readium.** { *; }
-keepclassmembers class * { @android.webkit.JavascriptInterface <methods>; }
-dontwarn org.readium.**

# L7 format libraries: optional loggers and extras they don't need on Android.
-dontwarn org.slf4j.**
-dontwarn org.brotli.**
-dontwarn com.github.luben.zstd.**
-dontwarn org.objectweb.asm.**
-dontwarn javax.annotation.**
-keep class com.github.junrar.** { *; }
-keep class org.apache.commons.compress.** { *; }
-keep class org.tukaani.xz.** { *; }
