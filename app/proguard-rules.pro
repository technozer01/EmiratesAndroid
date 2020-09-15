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

#-dontwarn javax.annotation.Nullable
#-dontwarn okio.**
#-dontwarn javax.annotation.**
#-dontwarn org.apache.poi.**
#-keep class org.apache.poi.** { *; }
#-keepattributes *Annotation*
#-keepattributes SourceFile,LineNumberTable
#-dontwarn javax.annotation.Nullable
-keep public class com.docrecog.scan.ImageOpencv {*;}
-keep public class com.docrecog.scan.OCRCallback {*;}
-keep public class com.docrecog.scan.PrimaryData {*;}
-keep public class com.docrecog.scan.RecogResult {*;}
-keep public class com.inet.facelock.callback.FaceCallback {*;}
-keep public class com.inet.facelock.callback.FaceDetectionResult {*;}
