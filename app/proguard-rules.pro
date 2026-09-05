# TalkWave release ProGuard/R8 rules
# Goal: obfuscate + shrink the release APK without breaking reflection-based
# libraries (Retrofit/Gson, Room, Socket.IO, Firebase, Credentials, etc).

# ---------- App data / DTO / Entity classes ----------
# Gson (via Retrofit) and Room both use reflection on these classes.
# Keep their fields so serialization/deserialization and DB mapping still work.
-keep class com.muwan.muwanchat.data.** { *; }
-keepclassmembers class com.muwan.muwanchat.data.** { *; }

# Retrofit/Gson DTOs used for chat, groups, auth, users etc. live here.
# These have no @SerializedName annotations — Gson matches JSON keys to the
# exact Kotlin field names, so renaming them during obfuscation breaks
# deserialization (this caused chat/group screen crashes).
-keep class com.muwan.muwanchat.network.** { *; }
-keepclassmembers class com.muwan.muwanchat.network.** { *; }

# UI-layer message model that mirrors network/DB fields — also Gson-parsed
# in places (message reactions), so keep it safe too.
-keep class com.muwan.muwanchat.screens.ChatMessage { *; }
-keepclassmembers class com.muwan.muwanchat.screens.ChatMessage { *; }

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

# Gson's official R8 rule for TypeToken (e.g. object : TypeToken<List<X>>() {}).
# Without this, R8 strips generic signature info from the anonymous subclass
# and Gson throws "java.lang.Class cannot be cast to java.lang.reflect.ParameterizedType"
# at runtime wherever a TypeToken is used (this caused the reactions-parsing crash).
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken

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

# ---------- WebRTC (voice/video calling) ----------
# WebRTC ki native (JNI/C++) library Java classes aur methods ko EXACT naam
# se reference karti hai (PeerConnectionFactory, SdpObserver callbacks, video
# encoder/decoder factories, etc). R8 obfuscation/shrinking in naamon ko badal
# ya strip kar deta hai kyunki koi Java code sidha inhe "use" hota nahi dikhta
# (sirf native side se call hota hai) -- isse release build me CallScreen
# crash hota tha jabki beta/debug (unminified) build me theek chalta tha.
-keep class org.webrtc.** { *; }
-keepclassmembers class org.webrtc.** { *; }
-dontwarn org.webrtc.**

# Apna calling package bhi safe rakhte hain -- CallManager WebRTC callbacks
# (SdpObserver, PeerConnection.Observer) implement karta hai jo runtime pe
# native side se invoke hote hain.
-keep class com.muwan.muwanchat.calling.** { *; }
-keepclassmembers class com.muwan.muwanchat.calling.** { *; }

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
