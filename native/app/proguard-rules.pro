# Readium ships no shrinker rules and uses JavaScript bridges, Parcelable and JSON by name:
# keep it whole rather than risk a book that won't open in the release build.
-keep class org.readium.** { *; }
-keepclassmembers class * { @android.webkit.JavascriptInterface <methods>; }
-dontwarn org.readium.**
