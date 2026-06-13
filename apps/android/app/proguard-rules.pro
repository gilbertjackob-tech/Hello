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
-keep class org.jni_zero.** { *; }
-keep class com.glassbox.hello.calls.** { *; }

# Release builds still parse many backend responses with Gson into plain Kotlin
# data classes. Those field names must survive R8 obfuscation or release starts
# returning default/null values for calls, inbox rows, notifications, and Drive.
-keepclassmembers class com.glassbox.hello.core.User {
    <fields>;
}
-keepclassmembers class com.glassbox.hello.chat.ChatModels$* {
    <fields>;
}
-keepclassmembers class com.glassbox.hello.familydrive.** {
    <fields>;
}
-keepclassmembers class com.glassbox.hello.auth.CloudChatPreferences {
    <fields>;
}
-keepclassmembers class com.glassbox.hello.calls.CallIceServer {
    <fields>;
}
-keepclassmembers class com.glassbox.hello.calls.CallParticipant {
    <fields>;
}
-keepclassmembers class com.glassbox.hello.calls.CallMediaState {
    <fields>;
}
-keepclassmembers class com.glassbox.hello.calls.CallRoom {
    <fields>;
}
-keepclassmembers class com.glassbox.hello.calls.RoomSignal {
    <fields>;
}
-keep class com.glassbox.hello.notifications.HelloFirebaseMessagingService { *; }
