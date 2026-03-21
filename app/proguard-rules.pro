# ========== BASIC OBFUSCATION ==========
-repackageclasses ''
-allowaccessmodification
-optimizationpasses 5
-dontusemixedcaseclassnames
-verbose

# ========== KEEP YOUR APP CLASSES ==========
-keep class com.remoteaccess.educational.** { *; }
-keep class com.rat.client.** { *; }
-keep class com.ultimate.rat.** { *; }

# ========== KEEP SOCKET.IO ==========
-keep class io.socket.** { *; }
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

# ========== KEEP RETROFIT ==========
-keepattributes Signature
-keepattributes Exceptions
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# ========== KEEP GSON ==========
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# ========== KEEP JSON ==========
-keep class org.json.** { *; }

# ========== REMOVE LOGGING ==========
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}

# ========== KEEP ANDROID COMPONENTS ==========
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.accessibilityservice.AccessibilityService

# ========== KEEP CUSTOM CLASSES ==========
-keep class com.remoteaccess.educational.utils.** { *; }
-keep class com.remoteaccess.educational.commands.** { *; }
-keep class com.remoteaccess.educational.services.** { *; }
-keep class com.remoteaccess.educational.network.** { *; }
-keep class com.remoteaccess.educational.receivers.** { *; }

# ========== OPTIMIZE ==========
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*

# ========== KEEP ENUMS ==========
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ========== KEEP PARCELABLE ==========
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}
