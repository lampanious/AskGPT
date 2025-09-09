# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep MainActivity and all activities
-keep class com.example.askgpt.MainActivity { *; }
-keep class * extends androidx.activity.ComponentActivity { *; }

# Keep Application class
-keep class com.example.askgpt.AskGPTApplication { *; }
-keep class * extends android.app.Application { *; }

# Keep all services
-keep class com.example.askgpt.services.** { *; }

# Keep accessibility service specifically
-keep class com.example.askgpt.services.AskGPTAccessibilityService { *; }
-keep class * extends android.accessibilityservice.AccessibilityService { *; }

# Keep data classes
-keep class com.example.askgpt.data.** { *; }

# Keep utils
-keep class com.example.askgpt.utils.** { *; }

# Keep Compose classes
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Keep all public classes in the main package
-keep public class com.example.askgpt.** { *; }

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile