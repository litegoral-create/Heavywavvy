# Keep WebView JavaScript interfaces
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep MediaSession, Audio DSP, and NotificationCompat components
-dontwarn android.media.**
-keep class androidx.media.** { *; }
-keep class android.support.v4.media.** { *; }
-keep class com.musicbeta.wrapper.MediaPlaybackService { *; }
-keep class com.musicbeta.wrapper.MainActivity { *; }
-keep class com.musicbeta.wrapper.AppleSignatureAudioEngine { *; }
-keepclassmembers class com.musicbeta.wrapper.MainActivity$MusicBridge {
    public *;
}