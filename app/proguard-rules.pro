# TalkWave release ProGuard/R8 rules
# Goal: obfuscate + shrink the release APK without breaking reflection-based
# libraries (Retrofit/Gson, Room, Socket.IO, Firebase, Credentials, etc).

# ---------- App data / DTO / Entity classes ----------
# Gson (via Retrofit) and Room both use reflection on these classes.
# Keep their fields so serialization/deserialization and DB mapping still work.
-keep class com.muwan.muwanchat.data.** { *; }
-keepclassmembers class com.muwan.muwanchat.data.** { *; }

# ---------- Retrofit ----------
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes Exceptions
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**

# ---------- Gson ----------
-keepattributes EnclosingMethod
-keepattributes InnerClasses
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ---------- Room ----------
-keep class androidx.room.** { *; }
-dontwarn androidx.room.paging.**

# ---------- Socket.IO / OkHttp / Engine.IO ----------
-keep class io.socket.** { *; }
-dontwarn io.socket.**
-keep class org.json.** { *; }

# ---------- Firebase (Messaging + Crashlytics) ----------
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception

# ---------- Credentials / Google Identity (Sign-in) ----------
-keep class androidx.credentials.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }
-dontwarn androidx.credentials.**

# ---------- Security Crypto (EncryptedSharedPreferences / DataStore) ----------
-keep class androidx.security.crypto.** { *; }
-dontwarn androidx.security.crypto.**

# ---------- Media3 / ExoPlayer ----------
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# ---------- Coil (image/video/gif loading) ----------
-keep class coil.** { *; }
-dontwarn coil.**

# ---------- Kotlin Coroutines ----------
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# ---------- Kotlin metadata (keeps data class copy/componentN working) ----------
-keep class kotlin.Metadata { *; }
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations

# ---------- Jetpack Compose (AGP default consumer rules normally cover this,
# these are extra safety nets) ----------
-dontwarn androidx.compose.**

# ---------- Enum safety (Gson / general enum reflection) ----------
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ---------- Parcelable (Android reflection via CREATOR field) ----------
-keepclassmembers class * implements android.os.Parcelable {
    static ** CREATOR;
}
