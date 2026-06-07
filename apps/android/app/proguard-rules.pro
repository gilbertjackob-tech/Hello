# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Keep metadata that Gson, Retrofit, Firebase, and annotation-driven AndroidX
# integrations use while still allowing R8 to remove unused code.
-keepattributes Signature,*Annotation*
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# Room generates database implementations reflectively; release minification
# must keep their constructors so WorkManager and app databases can initialize.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep class *Database_Impl { *; }

# WebRTC call setup runs in the shipped release APK. Keep the WebRTC surface
# and the app's call stack stable so release behavior matches debug behavior.
-keep class org.webrtc.** { *; }
-keep class com.glassbox.hello.calls.** { *; }
