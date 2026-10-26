export interface ProjectFileItem {
  id: string;
  name: string;
  path: string;
  category: 'core' | 'manifest' | 'kotlin' | 'layout' | 'workflow' | 'config';
  language: 'kotlin' | 'xml' | 'groovy' | 'yaml' | 'properties';
  description: string;
  requirementTags: string[];
  content: string;
}

export const PROJECT_FILES: ProjectFileItem[] = [
  {
    id: 'app-build-gradle',
    name: 'app/build.gradle.kts',
    path: 'app/build.gradle.kts',
    category: 'core',
    language: 'kotlin',
    description: 'Gradle configuration for Android 14 (API 34, minSdk 26) with MediaSession & WebKit dependencies.',
    requirementTags: ['Core Requirement #1', 'API 34 / minSdk 26', 'MediaSessionCompat'],
    content: `plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.musicbeta.wrapper"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.musicbeta.wrapper"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug") // fallback if release keystore not configured
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs = listOf(
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi"
        )
    }

    buildFeatures {
        viewBinding = true
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    // Core AndroidX & UI
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.activity:activity-ktx:1.9.0")
    implementation("androidx.coordinatorlayout:coordinatorlayout:1.2.0")

    // High-Performance WebView & OkHttp Aggressive Disk Cache Interceptor
    implementation("androidx.webkit:webkit:1.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // MediaSession & Foreground Media Notification (Android 14+ compliant)
    implementation("androidx.media:media:1.7.0")

    // Coroutines & Lifecycle
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-service:2.7.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}`
  },
  {
    id: 'android-manifest',
    name: 'AndroidManifest.xml',
    path: 'app/src/main/AndroidManifest.xml',
    category: 'manifest',
    language: 'xml',
    description: 'Hardware acceleration enabled, zero-reload SPA configChanges, Android 14 mediaPlayback foreground service, wake locks, and deep links.',
    requirementTags: ['Core Requirement #1 & #2', 'hardwareAccelerated="true"', 'Zero-Reload configChanges', 'foregroundServiceType="mediaPlayback"'],
    content: `<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <!-- Network & Streaming Permissions -->
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
    <uses-permission android:name="android.permission.CHANGE_WIFI_MULTICAST_STATE" />

    <!-- Screen-Off Background Audio & Power Management -->
    <uses-permission android:name="android.permission.WAKE_LOCK" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <!-- Android 14+ (API 34) Foreground Service type requirement -->
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
    <!-- Android 13+ (API 33) Notification permission -->
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />

    <application
        android:allowBackup="true"
        android:hardwareAccelerated="true"
        android:largeHeap="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.MusicBetaWrapper"
        android:usesCleartextTraffic="false"
        tools:targetApi="34">

        <!-- Main Wrapper Activity with Edge-to-Edge, High Refresh Rate & Zero-Reload SPA Lifecycle -->
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:hardwareAccelerated="true"
            android:launchMode="singleTask"
            android:configChanges="orientation|screenSize|smallestScreenSize|screenLayout|keyboard|keyboardHidden|navigation|uiMode|density|fontScale"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>

            <!-- Deep linking for Apple Music and Beta web links -->
            <intent-filter android:autoVerify="true">
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data
                    android:scheme="https"
                    android:host="beta.music.apple.com" />
            </intent-filter>
            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data
                    android:scheme="https"
                    android:host="music.apple.com" />
            </intent-filter>
        </activity>

        <!-- Media Playback Foreground Service for uninterrupted background streaming -->
        <service
            android:name=".MediaPlaybackService"
            android:enabled="true"
            android:exported="false"
            android:foregroundServiceType="mediaPlayback" />

    </application>

</manifest>`
  },
  {
    id: 'main-activity',
    name: 'MainActivity.kt',
    path: 'app/src/main/java/com/musicbeta/wrapper/MainActivity.kt',
    category: 'kotlin',
    language: 'kotlin',
    description: 'Production WebView for Apple Music Beta with Widevine DRM, MusicKit 256kbps AAC + 32-bit Float Web Audio, Native DynamicsProcessing DSP (Session 0), and Android 14+ Bit-Perfect USB DAC bypass.',
    requirementTags: ['Widevine DRM 256kbps', 'AppleSignatureAudioEngine DSP', 'Android 14 Bit-Perfect USB DAC', 'Opaque Nav Menu & Zero Flicker', '500MB OkHttp Cache'],
    content: `package com.musicbeta.wrapper

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioMixerAttributes
import android.media.audiofx.DynamicsProcessing
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.activity.OnBackPressedCallback
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * MainActivity for MusicBetaWrapper (v1.1.0).
 * High-performance, edge-to-edge native Android 14 wrapper around https://beta.music.apple.com (Apple Music Web Player Beta)
 *
 * Integrated Audio & Visual Systems:
 * 1. Widevine DRM & WebView Audio Optimization:
 *    - Grants PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID in WebChromeClient with a Widevine-compatible Mobile Chrome User-Agent.
 * 2. MusicKit JS High-Bitrate (256kbps AAC) & Web Audio 32-Bit Float Mastering Injection:
 *    - Polls window.MusicKit, sets music.bitrate = MusicKit.PlaybackBitrate.HIGH (256kbps AAC) & localStorage['music.ampwebplay.bitrate'] = '256',
 *      and safely hooks <audio> elements into a 32-bit float AudioContext (65Hz +1.8dB warmth, 13.5kHz +2.0dB air, soft-knee compressor)
 *      with automatic fallback to Native Android DSP if Widevine EME blocks createMediaElementSource CORS.
 * 3. Native Android DynamicsProcessing Mastering Engine (API 28+, Session 0):
 *    - Dedicated AppleSignatureAudioEngine attached to global output session 0 with 5-band Pre-EQ/Post-EQ
 *      (<80Hz +2.0dB, 250Hz -0.5dB, 1kHz 0.0dB, 3.5kHz +1.0dB, >12kHz +2.2dB) and hardware Limiter (-1.0dBFS, 2ms attack, 60ms release, 4:1 ratio).
 * 4. Android 14+ (API 34+) Bit-Perfect USB DAC / Headset Bypass:
 *    - Automatic AudioDeviceCallback listener routing TYPE_USB_HEADSET and TYPE_USB_DEVICE via AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT.
 * 5. Zero Image Flickering & Unclipped Search Header:
 *    - Native Chromium HTTP/2 disk cache for mzstatic.com srcset artwork + opaque #ffffff/#1f1f1f nav drawer + 96px floating mini-player scroll clearance.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MusicBetaWrapper"
        private const val APPLE_MUSIC_BETA_URL = "https://beta.music.apple.com"
        private const val NOTIFICATION_PERMISSION_CODE = 1001
        private const val DISK_CACHE_MAX_BYTES = 500L * 1024L * 1024L // 500 MB

        // Preferences for Light/Dark Mode & Cache Schema Migration
        private const val PREFS_NAME = "music_wrapper_prefs"
        private const val KEY_THEME_MODE = "pref_theme_mode"
        private const val KEY_CACHE_SCHEMA_VERSION = "pref_cache_schema_version"
        private const val CACHE_SCHEMA_VERSION = 4
        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"

        // Widevine-compatible Mobile Chrome on Android 14 User-Agent (NEVER spoof iOS/Safari so Widevine EME works natively)
        const val MOBILE_CHROME_USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

        // Broadcast actions between Activity and MediaPlaybackService
        const val ACTION_MEDIA_COMMAND = "com.musicbeta.wrapper.MEDIA_COMMAND"
        const val EXTRA_COMMAND = "extra_command"
        const val COMMAND_PLAY = "command_play"
        const val COMMAND_PAUSE = "command_pause"
        const val COMMAND_NEXT = "command_next"
        const val COMMAND_PREV = "command_prev"

        // Blocked Telemetry & Analytics Domains/Substrings
        private val BLOCKED_TELEMETRY_PATTERNS = listOf(
            "metrics.apple.com",
            "xp.apple.com",
            "dzc-metrics.mzstatic.com",
            "notes-analytics-events.apple.com",
            "pancake.apple.com",
            "books-analytics-events.apple.com",
            "sentry.io",
            "browser.sentry-cdn.com",
            "/sentry/"
        )
    }

    private lateinit var rootContainer: View
    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var errorLayout: FrameLayout
    private lateinit var insetsController: WindowInsetsControllerCompat
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var okHttpClient: OkHttpClient
    private var appleSignatureAudioEngine: AppleSignatureAudioEngine? = null

    private var currentRefreshRate: Float = 60.0f
    private var isPlayingAudio: Boolean = false
    private var currentThemeMode: String = THEME_SYSTEM
    private var lastTopInsetDp: Int = 0
    private var lastBottomInsetDp: Int = 0
    private var lastLeftInsetDp: Int = 0
    private var lastRightInsetDp: Int = 0

    // Broadcast receiver for hardware media keys forwarded by MediaPlaybackService
    private val mediaCommandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_MEDIA_COMMAND) {
                when (intent.getStringExtra(EXTRA_COMMAND)) {
                    COMMAND_PLAY -> executeJavaScript("dispatchMediaAction('play')")
                    COMMAND_PAUSE -> executeJavaScript("dispatchMediaAction('pause')")
                    COMMAND_NEXT -> executeJavaScript("dispatchMediaAction('next')")
                    COMMAND_PREV -> executeJavaScript("dispatchMediaAction('previous')")
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        currentThemeMode = sharedPreferences.getString(KEY_THEME_MODE, THEME_SYSTEM) ?: THEME_SYSTEM

        // Apply saved theme before view inflation
        applyAppCompatNightMode(currentThemeMode)

        super.onCreate(savedInstanceState)

        // 1. Force High Refresh Rate (120 Hz+)
        enforceMaxDisplayRefreshRate()

        // 2. Setup True Edge-to-Edge display with dynamic status & nav bar tinting
        setupEdgeToEdge()

        setContentView(R.layout.activity_main)

        rootContainer = findViewById(R.id.rootContainer)
        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)
        errorLayout = findViewById(R.id.errorLayout)

        // Setup Safe Area Inset Handling to resolve Status Bar & Camera Notch Overlap
        setupWindowInsetsHandling()

        // Request POST_NOTIFICATIONS on Android 13+ (API 33+) for foreground media notification
        checkNotificationPermission()

        // 3. Initialize Native "Apple Signature Sound" DynamicsProcessing DSP (Session 0) & Android 14+ Bit-Perfect USB DAC Engine
        appleSignatureAudioEngine = AppleSignatureAudioEngine(applicationContext).also { engine ->
            engine.start()
        }

        // 4. Configure WebView with Full Browser Capabilities, 3rd-Party Cookies, Widevine DRM & 500MB Cache
        configureHighPerformanceWebView()

        // 5. Setup Back-press handling to navigate WebView history without reload
        setupBackNavigation()

        // 6. Register Media Command Receiver
        val filter = IntentFilter(ACTION_MEDIA_COMMAND)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(mediaCommandReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(mediaCommandReceiver, filter)
        }

        // 7. Load Apple Music Web Player Beta (https://beta.music.apple.com)
        if (savedInstanceState == null) {
            val incomingUrl = intent?.dataString
            val targetUrl = if (!incomingUrl.isNullOrBlank() && incomingUrl.contains("music.apple.com", ignoreCase = true)) {
                incomingUrl.replace(
                    Regex("^https?://(www\\.)?music\\.apple\\.com", RegexOption.IGNORE_CASE),
                    APPLE_MUSIC_BETA_URL
                )
            } else {
                APPLE_MUSIC_BETA_URL
            }
            webView.loadUrl(targetUrl)
        } else {
            webView.restoreState(savedInstanceState)
        }
    }

    private fun enforceMaxDisplayRefreshRate() {
        try {
            val displayObj: Display? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                display
            } else {
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay
            }

            if (displayObj != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val modes = displayObj.supportedModes
                val bestMode = modes.maxWithOrNull(
                    compareBy<Display.Mode> { it.refreshRate }
                        .thenBy { it.physicalWidth * it.physicalHeight }
                )

                if (bestMode != null) {
                    currentRefreshRate = bestMode.refreshRate
                    val layoutParams = window.attributes
                    layoutParams.preferredDisplayModeId = bestMode.modeId
                    layoutParams.preferredRefreshRate = bestMode.refreshRate
                    window.attributes = layoutParams
                    Log.i(TAG, "Forced display refresh rate to: \${bestMode.refreshRate} Hz (Mode ID: \${bestMode.modeId})")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error configuring 120Hz display refresh mode", e)
        }
    }

    private fun setupEdgeToEdge() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        supportActionBar?.hide()

        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        insetsController = WindowInsetsControllerCompat(window, window.decorView)
        updateSystemBarsForCurrentTheme()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
    }

    private fun setupWindowInsetsHandling() {
        ViewCompat.setOnApplyWindowInsetsListener(rootContainer) { view, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )

            // Apply native hardware safe-area padding so WebView never overlaps status bar or notch
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom)

            val density = resources.displayMetrics.density
            lastTopInsetDp = (insets.top / density).toInt()
            lastBottomInsetDp = (insets.bottom / density).toInt()
            lastLeftInsetDp = (insets.left / density).toInt()
            lastRightInsetDp = (insets.right / density).toInt()

            val progressParams = progressBar.layoutParams as? FrameLayout.LayoutParams
            progressParams?.topMargin = 0
            progressBar.layoutParams = progressParams

            injectSafeAreaInsetsScript()

            windowInsets
        }
    }

    private fun injectSafeAreaInsetsScript() {
        val cssScript = """
            (function() {
                var root = document.documentElement;
                root.style.setProperty('--safe-area-top', '\${lastTopInsetDp}px');
                root.style.setProperty('--safe-area-bottom', '\${lastBottomInsetDp}px');
                root.style.setProperty('--safe-area-left', '\${lastLeftInsetDp}px');
                root.style.setProperty('--safe-area-right', '\${lastRightInsetDp}px');
            })();
        """.trimIndent()
        executeJavaScript(cssScript)
    }

    private fun updateSystemBarsForCurrentTheme() {
        val isDarkMode = isEffectiveDarkMode()
        insetsController.isAppearanceLightStatusBars = !isDarkMode
        insetsController.isAppearanceLightNavigationBars = !isDarkMode
    }

    private fun isEffectiveDarkMode(): Boolean {
        return when (currentThemeMode) {
            THEME_LIGHT -> false
            THEME_DARK -> true
            else -> {
                val currentNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                currentNightMode == Configuration.UI_MODE_NIGHT_YES
            }
        }
    }

    fun setThemeMode(mode: String) {
        currentThemeMode = mode
        sharedPreferences.edit().putString(KEY_THEME_MODE, mode).apply()
        applyAppCompatNightMode(mode)
        updateSystemBarsForCurrentTheme()
        applyWebViewTheme(mode)
        injectUiAndMenuFixes()
    }

    private fun applyAppCompatNightMode(mode: String) {
        val nightMode = when (mode) {
            THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureHighPerformanceWebView() {
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        webView.isVerticalScrollBarEnabled = false
        webView.isHorizontalScrollBarEnabled = false

        val settings = webView.settings

        // 1. Widevine DRM & WebView Audio Optimization + Native Mobile Viewport
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        @Suppress("DEPRECATION")
        settings.databaseEnabled = true
        // Use 1:1 native mobile viewport metrics so top headers and search bars are never scaled or clipped
        settings.useWideViewPort = false
        settings.loadWithOverviewMode = false
        settings.layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL

        // 2. Mixed Content, Autoplay without Gesture & Third-Party Cookies for Apple CDN (mzstatic.com) & MusicKit Auth
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        settings.loadsImagesAutomatically = true
        settings.blockNetworkImage = false
        settings.mediaPlaybackRequiresUserGesture = false
        settings.setSupportMultipleWindows(false)
        settings.allowFileAccess = false
        settings.allowContentAccess = false

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        // 3. 500 MB Dedicated OkHttp Disk Cache & Legacy Cache Purge
        val customCacheDir = File(applicationContext.cacheDir, "music_beta_webview_cache")
        if (!customCacheDir.exists()) {
            customCacheDir.mkdirs()
        }

        val okHttpCacheDir = File(applicationContext.cacheDir, "music_okhttp_cache_v4")
        if (!okHttpCacheDir.exists()) {
            okHttpCacheDir.mkdirs()
        }

        val savedSchema = sharedPreferences.getInt(KEY_CACHE_SCHEMA_VERSION, 1)
        if (savedSchema < CACHE_SCHEMA_VERSION) {
            try {
                File(applicationContext.cacheDir, "music_beta_okhttp_cache").deleteRecursively()
                File(applicationContext.cacheDir, "music_beta_okhttp_cache_v2").deleteRecursively()
                File(applicationContext.cacheDir, "music_okhttp_cache_v3").deleteRecursively()
                webView.clearCache(true)
            } catch (_: Exception) {}
            sharedPreferences.edit().putInt(KEY_CACHE_SCHEMA_VERSION, CACHE_SCHEMA_VERSION).apply()
        }

        okHttpClient = OkHttpClient.Builder()
            .cache(Cache(okHttpCacheDir, DISK_CACHE_MAX_BYTES))
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .addNetworkInterceptor { chain ->
                val response = chain.proceed(chain.request())
                response.newBuilder()
                    .removeHeader("Pragma")
                    .removeHeader("Vary")
                    .header("Cache-Control", "public, max-age=604800, immutable")
                    .build()
            }
            .build()

        settings.cacheMode = if (isNetworkAvailable()) {
            WebSettings.LOAD_DEFAULT
        } else {
            WebSettings.LOAD_CACHE_ELSE_NETWORK
        }
        manageCacheQuota(customCacheDir, DISK_CACHE_MAX_BYTES)

        @Suppress("DEPRECATION")
        settings.setRenderPriority(WebSettings.RenderPriority.HIGH)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            settings.offscreenPreRaster = true
        }

        // 4. Widevine-compatible Mobile Chrome User-Agent (never spoof iOS/Safari so Widevine EME works natively)
        settings.userAgentString = MOBILE_CHROME_USER_AGENT

        // 5. Light/Dark Mode configuration on WebView (called once on init, not on every page finish, to prevent image re-decode flicker)
        applyWebViewTheme(currentThemeMode)

        // 6. JavaScript Interface for Media Session & Background Sync
        webView.addJavascriptInterface(MusicBridge(), "AndroidBridge")

        // 7. Custom WebViewClient & WebChromeClient (with Widevine DRM Permission Grant)
        setupWebViewClients()
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun applyWebViewTheme(mode: String) {
        val isDark = isEffectiveDarkMode()
        val bgColor = if (isDark) Color.parseColor("#1F1F1F") else Color.parseColor("#FFFFFF")
        rootContainer.setBackgroundColor(bgColor)
        webView.setBackgroundColor(bgColor)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                WebSettingsCompat.setAlgorithmicDarkeningAllowed(webView.settings, isDark)
            }
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
            val forceDarkValue = when (mode) {
                THEME_LIGHT -> WebSettingsCompat.FORCE_DARK_OFF
                THEME_DARK -> WebSettingsCompat.FORCE_DARK_ON
                else -> WebSettingsCompat.FORCE_DARK_AUTO
            }
            WebSettingsCompat.setForceDark(webView.settings, forceDarkValue)
        }

        val scheme = if (isDark) "dark" else "light"
        val themeScript = """
            (function() {
                const scheme = '$scheme';
                document.documentElement.style.colorScheme = scheme;
                let meta = document.querySelector('meta[name="color-scheme"]');
                if (!meta) {
                    meta = document.createElement('meta');
                    meta.name = 'color-scheme';
                    document.head.appendChild(meta);
                }
                meta.content = scheme;
            })();
        """.trimIndent()
        webView.evaluateJavascript(themeScript, null)
    }

    private fun setupWebViewClients() {
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                progressBar.visibility = View.VISIBLE
                errorLayout.visibility = View.GONE
            }

            override fun onPageCommitVisible(view: WebView?, url: String?) {
                super.onPageCommitVisible(view, url)
                injectSafeAreaInsetsScript()
                injectUiAndMenuFixes()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.visibility = View.GONE
                CookieManager.getInstance().flush()
                injectSafeAreaInsetsScript()
                injectUiAndMenuFixes()
                injectAppleSignatureWebAudioScript()
                injectAudioBridgeScript()
            }

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val req = request ?: return null
                val rawUrl = req.url?.toString() ?: return null

                // Block background tracking/metrics calls immediately
                if (isTelemetryOrTrackerUrl(rawUrl)) {
                    return createEmptyBlockedResponse()
                }

                // Let Chromium's native HTTP/2 disk cache handle all mzstatic.com images and srcset artwork
                // directly so category cards never blink or re-render when the floating mini-player updates.
                if (rawUrl.contains("mzstatic.com", ignoreCase = true)) {
                    return super.shouldInterceptRequest(view, request)
                }

                // Only intercept GET requests for immutable JS/CSS/font bundles
                if (req.method.equals("GET", ignoreCase = true) && isCacheableStaticBundle(rawUrl)) {
                    fetchFromOkHttpDiskCache(rawUrl, req)?.let { cachedResponse ->
                        return cachedResponse
                    }
                }

                return super.shouldInterceptRequest(view, request)
            }

            override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                super.onReceivedError(view, errorCode, description, failingUrl)
                if (errorCode == ERROR_HOST_LOOKUP || errorCode == ERROR_CONNECT) {
                    errorLayout.visibility = View.VISIBLE
                }
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                val lower = url.lowercase()

                // Block external native app deep links ("Open in Music" / itunes / market intents)
                if (lower.startsWith("music://") ||
                    lower.startsWith("itms://") ||
                    lower.startsWith("itms-apps://") ||
                    lower.startsWith("intent://") ||
                    lower.contains("itunes.apple.com")
                ) {
                    return true
                }

                // Keep navigation inside Apple Music Web Player Beta (https://beta.music.apple.com)
                if (lower.startsWith("https://music.apple.com") || lower.startsWith("http://music.apple.com")) {
                    val betaUrl = url.replaceFirst(
                        Regex("^https?://music\\.apple\\.com", RegexOption.IGNORE_CASE),
                        APPLE_MUSIC_BETA_URL
                    )
                    view?.loadUrl(betaUrl)
                    return true
                }

                return false
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
                if (newProgress >= 100) {
                    progressBar.visibility = View.GONE
                }
            }

            /**
             * System #1: Enable Widevine DRM Audio Playback.
             * Automatically grants PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID so encrypted
             * full-length 256kbps AAC Apple Music streams play without falling back to 30-second previews.
             */
            override fun onPermissionRequest(request: PermissionRequest?) {
                if (request == null) return
                runOnUiThread {
                    try {
                        val requestedResources = request.resources ?: emptyArray()
                        val granted = requestedResources.filter {
                            it == PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID ||
                                it == PermissionRequest.RESOURCE_AUDIO_CAPTURE
                        }.toTypedArray()

                        if (granted.isNotEmpty()) {
                            request.grant(granted)
                            Log.i(TAG, "Granted WebChromeClient Widevine DRM permission: \${granted.joinToString()}")
                        } else {
                            request.grant(requestedResources)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error granting WebChromeClient permission", e)
                    }
                }
            }

            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                Log.d(TAG, "WebView Console: \${consoleMessage?.message()} (line \${consoleMessage?.lineNumber()})")
                return true
            }
        }
    }

    private fun isTelemetryOrTrackerUrl(url: String): Boolean {
        val lower = url.lowercase()
        return BLOCKED_TELEMETRY_PATTERNS.any { lower.contains(it) }
    }

    private fun createEmptyBlockedResponse(): WebResourceResponse {
        return WebResourceResponse(
            "text/plain",
            "UTF-8",
            204,
            "No Content",
            mapOf(
                "Access-Control-Allow-Origin" to "*",
                "Cache-Control" to "max-age=86400"
            ),
            ByteArrayInputStream(ByteArray(0))
        )
    }

    private fun isCacheableStaticBundle(url: String): Boolean {
        val cleanPath = url.substringBefore('?').lowercase()
        return cleanPath.endsWith(".js") ||
            cleanPath.endsWith(".mjs") ||
            cleanPath.endsWith(".css") ||
            cleanPath.endsWith(".woff2") ||
            cleanPath.endsWith(".woff") ||
            cleanPath.endsWith(".ttf")
    }

    private fun fetchFromOkHttpDiskCache(targetUrl: String, webRequest: WebResourceRequest): WebResourceResponse? {
        return try {
            val requestBuilder = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", MOBILE_CHROME_USER_AGENT)

            webRequest.requestHeaders["Accept"]?.let { requestBuilder.header("Accept", it) }
            webRequest.requestHeaders["Referer"]?.let { requestBuilder.header("Referer", it) }

            if (!isNetworkAvailable()) {
                requestBuilder.cacheControl(CacheControl.FORCE_CACHE)
            }

            val response = okHttpClient.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful) {
                response.close()
                return null
            }

            val body = response.body ?: return null
            val contentTypeHeader = response.header("Content-Type", guessMimeType(targetUrl)) ?: "application/octet-stream"
            val mimeType = contentTypeHeader.substringBefore(';').trim()
            val encoding = if (contentTypeHeader.contains("charset=", ignoreCase = true)) {
                contentTypeHeader.substringAfter("charset=", "UTF-8").substringBefore(';').trim()
            } else {
                "UTF-8"
            }

            val responseHeaders = mutableMapOf(
                "Access-Control-Allow-Origin" to "*",
                "Access-Control-Allow-Methods" to "GET, HEAD, OPTIONS",
                "Cache-Control" to "public, max-age=604800, immutable"
            )

            WebResourceResponse(
                mimeType,
                encoding,
                response.code,
                response.message.ifEmpty { "OK" },
                responseHeaders,
                body.byteStream()
            )
        } catch (e: Exception) {
            Log.w(TAG, "OkHttp cache miss/fallback for $targetUrl: \${e.message}")
            null
        }
    }

    private fun guessMimeType(url: String): String {
        val clean = url.substringBefore('?').lowercase()
        return when {
            clean.endsWith(".js") || clean.endsWith(".mjs") -> "application/javascript"
            clean.endsWith(".css") -> "text/css"
            clean.endsWith(".woff2") -> "font/woff2"
            clean.endsWith(".woff") -> "font/woff"
            clean.endsWith(".ttf") -> "font/ttf"
            else -> "application/octet-stream"
        }
    }

    /**
     * Targeted CSS Fix for Apple Music Web Player Beta (https://beta.music.apple.com):
     * - Forces a solid opaque background (#ffffff in light mode / #1f1f1f in dark mode) on the navigation header & open menu drawer
     *   WITHOUT applying 112px bottom-padding to the closed top header bar (which previously clipped the "Search" heading).
     * - Adds 96px bottom padding on main scrollable containers so the floating bottom mini-player never blocks the last row of cards.
     * - Pure CSS rules (no unthrottled MutationObserver on document.documentElement) so mini-player playback progress updates
     *   never trigger layout thrashing or category card image flickering.
     */
    private fun injectUiAndMenuFixes() {
        val isDark = isEffectiveDarkMode()
        val navBg = if (isDark) "#1f1f1f" else "#ffffff"
        val textColor = if (isDark) "#ffffff" else "#000000"

        val fixScript = """
            (function() {
                var navBg = '$navBg';
                var textColor = '$textColor';
                var styleId = '__music_wrapper_ui_fixes';
                var style = document.getElementById(styleId);
                if (!style) {
                    style = document.createElement('style');
                    style.id = styleId;
                    (document.head || document.documentElement).appendChild(style);
                }
                style.textContent = [
                    '/* 1. Solid Opaque Navigation Header & Open Drawer (No closed-header padding-bottom clipping) */',
                    '.navigation, .web-navigation, #navigation, [data-testid="navigation"], .navigation__header, .navigation__content, .web-navigation__content, .web-navigation__list, .header-bar, header[role="banner"], nav[role="navigation"] {',
                    '  background-color: ' + navBg + ' !important;',
                    '  background: ' + navBg + ' !important;',
                    '  color: ' + textColor + ' !important;',
                    '  opacity: 1 !important;',
                    '  z-index: 9999 !important;',
                    '}',
                    '/* Apply bottom clearance ONLY to the inner expanded menu list (never the closed top header bar) */',
                    '.web-navigation__list, .navigation__content, .web-navigation__drawer {',
                    '  padding-bottom: 112px !important;',
                    '}',
                    '/* 2. Smooth vertical scrolling & floating mini-player bottom clearance */',
                    'html, body, main, .scrollable-page, [class*="scrollable-page"] {',
                    '  overflow-y: auto !important;',
                    '  -webkit-overflow-scrolling: touch !important;',
                    '  touch-action: pan-y !important;',
                    '}',
                    'main, .scrollable-page, [class*="scrollable-page"] {',
                    '  padding-bottom: 96px !important;',
                    '}',
                    '/* 3. Strip expensive backdrop-filter blurs on scrolling shelf cards only */',
                    '.shelf-grid__item *, .songs-list-row * {',
                    '  backdrop-filter: none !important;',
                    '  -webkit-backdrop-filter: none !important;',
                    '}',
                    '/* 4. Hide Open in Music & Exit Beta links so user stays inside Apple Music Beta */',
                    '.upsell-banner, [data-testid="upsell-banner"], [data-testid*="exit-beta"], [class*="upsell"], [class*="smart-banner"], .native-upsell, a[href*="itunes.apple.com"], a[href*="music://"], a[href*="itms://"], a[href*="exit-beta"], button[aria-label*="Open in Music"], button[aria-label*="Exit Beta"] {',
                    '  display: none !important;',
                    '  height: 0 !important;',
                    '  max-height: 0 !important;',
                    '  visibility: hidden !important;',
                    '  pointer-events: none !important;',
                    '  overflow: hidden !important;',
                    '}'
                ].join('\\n');
            })();
        """.trimIndent()
        executeJavaScript(fixScript)
    }

    /**
     * System #2: MusicKit JS High-Bitrate (256kbps AAC) & Web Audio 32-Bit Float Mastering Injection.
     * - Sets localStorage['music.ampwebplay.bitrate'] = '256' and polls window.MusicKit to enforce
     *   music.bitrate = MusicKit.PlaybackBitrate.HIGH (256kbps AAC).
     * - Safely attempts to attach a 32-bit float Web Audio API mastering chain (65Hz low-shelf +1.8dB warmth,
     *   13.5kHz high-shelf +2.0dB air, soft-knee DynamicsCompressor) to <audio> elements inside a try/catch
     *   so if Widevine EME blocks createMediaElementSource CORS, playback continues uninterrupted via the
     *   native Android DynamicsProcessing engine on Session 0.
     */
    private fun injectAppleSignatureWebAudioScript() {
        val script = """
            (function() {
                if (window.__appleSignatureAudioInjected) return;
                window.__appleSignatureAudioInjected = true;

                // 2a. Force 256kbps High-Efficiency AAC in localStorage & MusicKit JS
                try {
                    localStorage.setItem('music.ampwebplay.bitrate', '256');
                } catch (storageErr) {}

                var bitrateAttempts = 0;
                var bitrateTimer = setInterval(function() {
                    bitrateAttempts++;
                    try {
                        if (window.MusicKit && typeof window.MusicKit.getInstance === 'function') {
                            var music = window.MusicKit.getInstance();
                            if (music) {
                                var highValue = (window.MusicKit.PlaybackBitrate && window.MusicKit.PlaybackBitrate.HIGH !== undefined)
                                    ? window.MusicKit.PlaybackBitrate.HIGH
                                    : 256;
                                music.bitrate = highValue;
                                if (!window.__musicKitBitrateHooked && typeof music.addEventListener === 'function') {
                                    window.__musicKitBitrateHooked = true;
                                    music.addEventListener('playbackStateDidChange', function() {
                                        try { music.bitrate = highValue; } catch (_) {}
                                    });
                                }
                                clearInterval(bitrateTimer);
                            }
                        }
                    } catch (mkErr) {}
                    if (bitrateAttempts > 60) {
                        clearInterval(bitrateTimer);
                    }
                }, 1000);

                // 2b. Resilient 32-Bit Float Web Audio Mastering Graph (with EME/CORS fallback to Native Session 0 DSP)
                var audioCtx = null;
                function attachWebAudioMastering(audioEl) {
                    if (!audioEl || audioEl.__appleSignatureAttached) return;
                    audioEl.__appleSignatureAttached = true;

                    try {
                        // If Widevine EME MediaKeys are already bound on this element, skip createMediaElementSource
                        // on devices where EME tainting mutes WebAudio, letting Native Session 0 DynamicsProcessing handle DSP.
                        if (audioEl.mediaKeys) {
                            return;
                        }
                        var AudioCtx = window.AudioContext || window.webkitAudioContext;
                        if (!AudioCtx) return;
                        if (!audioCtx) {
                            audioCtx = new AudioCtx({ latencyHint: 'playback' });
                        }
                        if (audioCtx.state === 'suspended') {
                            audioCtx.resume().catch(function() {});
                        }

                        var source = audioCtx.createMediaElementSource(audioEl);

                        // 65Hz Low-Shelf (+1.8 dB sub-bass warmth)
                        var lowShelf = audioCtx.createBiquadFilter();
                        lowShelf.type = 'lowshelf';
                        lowShelf.frequency.value = 65;
                        lowShelf.gain.value = 1.8;

                        // 13.5kHz High-Shelf (+2.0 dB high-resolution air)
                        var highShelf = audioCtx.createBiquadFilter();
                        highShelf.type = 'highshelf';
                        highShelf.frequency.value = 13500;
                        highShelf.gain.value = 2.0;

                        // Soft-Knee Mastering DynamicsCompressor
                        var compressor = audioCtx.createDynamicsCompressor();
                        compressor.threshold.value = -14;
                        compressor.knee.value = 18;
                        compressor.ratio.value = 2.2;
                        compressor.attack.value = 0.008;
                        compressor.release.value = 0.18;

                        source.connect(lowShelf);
                        lowShelf.connect(highShelf);
                        highShelf.connect(compressor);
                        compressor.connect(audioCtx.destination);
                    } catch (webAudioErr) {
                        // Fallback: Widevine EME / CORS blocked createMediaElementSource; Native Android Session 0 DSP handles audio
                        console.log('WebAudio EME fallback to Native Android Session 0 DSP:', webAudioErr);
                    }
                }

                document.addEventListener('play', function(e) {
                    if (e && e.target && e.target.tagName === 'AUDIO') {
                        attachWebAudioMastering(e.target);
                    }
                }, true);
            })();
        """.trimIndent()
        webView.evaluateJavascript(script, null)
    }

    private fun injectAudioBridgeScript() {
        val script = """
            (function() {
                if (window.__musicBetaBridgeInjected) return;
                window.__musicBetaBridgeInjected = true;
                var lastMetaSignature = '';

                window.dispatchMediaAction = function(action) {
                    try {
                        if (action === 'play' || action === 'pause') {
                            const btn = document.querySelector('button[data-testid="play-pause-button"], button[aria-label="Play"], button[aria-label="Pause"], .playback-play-pause-button');
                            if (btn) btn.click();
                        } else if (action === 'next') {
                            const nextBtn = document.querySelector('button[aria-label="Next"], button[data-testid="next-button"], .playback-forward-button');
                            if (nextBtn) nextBtn.click();
                        } else if (action === 'previous') {
                            const prevBtn = document.querySelector('button[aria-label="Previous"], button[data-testid="previous-button"], .playback-backward-button');
                            if (prevBtn) prevBtn.click();
                        }
                    } catch(e) {
                        console.error('Error dispatching media action:', e);
                    }
                };

                const notifyStateChange = function(playing) {
                    let title = "Apple Music Beta";
                    let artist = "Now Playing";
                    let artwork = "";

                    if (navigator.mediaSession && navigator.mediaSession.metadata) {
                        title = navigator.mediaSession.metadata.title || title;
                        artist = navigator.mediaSession.metadata.artist || artist;
                        if (navigator.mediaSession.metadata.artwork && navigator.mediaSession.metadata.artwork.length > 0) {
                            artwork = navigator.mediaSession.metadata.artwork[0].src || "";
                        }
                    }

                    if (window.AndroidBridge) {
                        window.AndroidBridge.onPlaybackStateChanged(playing, title, artist, artwork);
                    }
                };

                document.addEventListener('play', function(e) {
                    if (e.target.tagName === 'AUDIO') {
                        notifyStateChange(true);
                    }
                }, true);

                document.addEventListener('pause', function(e) {
                    if (e.target.tagName === 'AUDIO') {
                        notifyStateChange(false);
                    }
                }, true);

                if ('mediaSession' in navigator) {
                    setInterval(function() {
                        if (navigator.mediaSession && navigator.mediaSession.metadata) {
                            const meta = navigator.mediaSession.metadata;
                            const isPlaying = navigator.mediaSession.playbackState === 'playing';
                            const title = meta.title || '';
                            const artist = meta.artist || '';
                            const art = (meta.artwork && meta.artwork.length > 0) ? meta.artwork[0].src : '';
                            const signature = title + '|' + artist + '|' + art + '|' + isPlaying;
                            if (signature !== lastMetaSignature) {
                                lastMetaSignature = signature;
                                if (window.AndroidBridge) {
                                    window.AndroidBridge.onMetadataUpdated(title, artist, art, isPlaying);
                                }
                            }
                        }
                    }, 2000);
                }
            })();
        """.trimIndent()

        webView.evaluateJavascript(script, null)
    }

    private fun executeJavaScript(code: String) {
        runOnUiThread {
            webView.evaluateJavascript(code, null)
        }
    }

    private fun manageCacheQuota(directory: File, maxSizeBytes: Long) {
        Thread {
            try {
                var totalSize = getDirSize(directory)
                if (totalSize > maxSizeBytes) {
                    val files = directory.listFiles()?.sortedBy { it.lastModified() } ?: return@Thread
                    for (file in files) {
                        val size = file.length()
                        if (file.delete()) {
                            totalSize -= size
                        }
                        if (totalSize <= maxSizeBytes * 0.85) {
                            break
                        }
                    }
                    Log.i(TAG, "Pruned cache directory to \${totalSize / (1024 * 1024)} MB")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Cache pruning error", e)
            }
        }.start()
    }

    private fun getDirSize(dir: File): Long {
        var size: Long = 0
        val files = dir.listFiles() ?: return 0
        for (f in files) {
            size += if (f.isDirectory) getDirSize(f) else f.length()
        }
        return size
    }

    inner class MusicBridge {
        @JavascriptInterface
        fun onPlaybackStateChanged(isPlaying: Boolean, title: String, artist: String, artworkUrl: String) {
            runOnUiThread {
                this@MainActivity.isPlayingAudio = isPlaying
                val serviceIntent = Intent(this@MainActivity, MediaPlaybackService::class.java).apply {
                    action = if (isPlaying) MediaPlaybackService.ACTION_START_PLAYBACK else MediaPlaybackService.ACTION_PAUSE_PLAYBACK
                    putExtra(MediaPlaybackService.EXTRA_TITLE, title)
                    putExtra(MediaPlaybackService.EXTRA_ARTIST, artist)
                    putExtra(MediaPlaybackService.EXTRA_ARTWORK_URL, artworkUrl)
                }
                ContextCompat.startForegroundService(this@MainActivity, serviceIntent)
            }
        }

        @JavascriptInterface
        fun onMetadataUpdated(title: String, artist: String, artworkUrl: String, isPlaying: Boolean) {
            runOnUiThread {
                if (isPlaying || this@MainActivity.isPlayingAudio) {
                    val serviceIntent = Intent(this@MainActivity, MediaPlaybackService::class.java).apply {
                        action = MediaPlaybackService.ACTION_UPDATE_METADATA
                        putExtra(MediaPlaybackService.EXTRA_TITLE, title)
                        putExtra(MediaPlaybackService.EXTRA_ARTIST, artist)
                        putExtra(MediaPlaybackService.EXTRA_ARTWORK_URL, artworkUrl)
                    }
                    ContextCompat.startForegroundService(this@MainActivity, serviceIntent)
                }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        enforceMaxDisplayRefreshRate()
        updateSystemBarsForCurrentTheme()
        applyWebViewTheme(currentThemeMode)
        injectSafeAreaInsetsScript()
        injectUiAndMenuFixes()
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause called: Keeping audio pipeline alive for background playback")
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "onStop called: WebView audio preserved for screen-off playback")
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
        Log.d(TAG, "WebView navigation history & state saved to Bundle for seamless back/forward restoration")
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(mediaCommandReceiver)
        } catch (_: Exception) {}
        try {
            appleSignatureAudioEngine?.release()
            appleSignatureAudioEngine = null
        } catch (_: Exception) {}
        webView.destroy()
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                webView.evaluateJavascript(
                    """
                    (function() {
                        if (window.history && window.history.length > 1) {
                            window.history.back();
                            return 'SPA_BACK_HANDLED';
                        }
                        return 'AT_ROOT';
                    })();
                    """.trimIndent()
                ) { result ->
                    if (result?.contains("SPA_BACK_HANDLED") != true) {
                        if (webView.canGoBack()) {
                            webView.goBack()
                        } else {
                            moveTaskToBack(true)
                        }
                    }
                }
            }
        })
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_CODE
                )
            }
        }
    }

    fun onRetryClicked(view: View) {
        errorLayout.visibility = View.GONE
        progressBar.visibility = View.VISIBLE
        webView.reload()
    }
}

/**
 * System #3 & #4: AppleSignatureAudioEngine
 * - Native Android DynamicsProcessing Mastering Engine (API 28+) attached to global audio session 0
 *   so hardware-decoded Widevine DRM WebView streams receive full 5-band Pre-EQ/Post-EQ and Anti-Clip Limiter.
 * - Android 14+ (API 34+) Bit-Perfect USB DAC / Headset Bypass via AudioManager.registerAudioDeviceCallback
 *   and AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT (44.1kHz PCM Float).
 */
class AppleSignatureAudioEngine(context: Context) {

    companion object {
        private const val TAG = "AppleSignatureAudio"
        private const val GLOBAL_AUDIO_SESSION_ID = 0
        private const val CHANNEL_COUNT = 2
        private const val EQ_BAND_COUNT = 5
    }

    private val appContext: Context = context.applicationContext
    private val audioManager: AudioManager? =
        appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var dynamicsProcessing: DynamicsProcessing? = null
    private var audioDeviceCallback: AudioDeviceCallback? = null

    private val mediaAudioAttributes: AudioAttributes by lazy {
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
    }

    fun start() {
        initDynamicsProcessingMasteringEngine()
        registerUsbBitPerfectCallback()
    }

    /**
     * System #3: Native Android DynamicsProcessing Mastering Engine (API 28+) on Session 0.
     * - 5-band Pre-EQ and Post-EQ with VARIANT_FAVOR_FREQUENCY_RESOLUTION:
     *   Band 0 (<80Hz Sub-bass warmth): +2.0f dB
     *   Band 1 (250Hz Lower-mids clarity): -0.5f dB
     *   Band 2 (1000Hz Reference midrange): 0.0f dB
     *   Band 3 (3.5kHz Vocal presence): +1.0f dB
     *   Band 4 (>12kHz High-resolution "Air"): +2.2f dB
     * - Hardware Limiter: -1.0f dBFS threshold, 2.0f ms attack, 60.0f ms release, 4.0f ratio.
     */
    private fun initDynamicsProcessingMasteringEngine() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return

        try {
            dynamicsProcessing?.release()

            val configBuilder = DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                CHANNEL_COUNT,
                true,  // preEqInUse
                EQ_BAND_COUNT,
                false, // mbcInUse
                0,
                true,  // postEqInUse
                EQ_BAND_COUNT,
                true   // limiterInUse
            )

            // 5-Band Pre-EQ (Sub-bass warmth & Lower-mid mud reduction)
            val preEq = DynamicsProcessing.Eq(true, true, EQ_BAND_COUNT).apply {
                setBand(0, DynamicsProcessing.EqBand(true, 80.0f, 2.0f))     // <80Hz Sub-bass warmth (+2.0 dB)
                setBand(1, DynamicsProcessing.EqBand(true, 250.0f, -0.5f))   // 250Hz Lower-mids clarity (-0.5 dB mud reduction)
                setBand(2, DynamicsProcessing.EqBand(true, 1000.0f, 0.0f))   // 1kHz Neutral midrange (0.0 dB)
                setBand(3, DynamicsProcessing.EqBand(true, 3500.0f, 0.0f))   // 3.5kHz Pre-stage flat
                setBand(4, DynamicsProcessing.EqBand(true, 20000.0f, 0.0f))  // >12kHz Pre-stage flat
            }
            configBuilder.setPreEqAllChannelsTo(preEq)

            // 5-Band Post-EQ (Vocal presence & High-resolution Air)
            val postEq = DynamicsProcessing.Eq(true, true, EQ_BAND_COUNT).apply {
                setBand(0, DynamicsProcessing.EqBand(true, 80.0f, 0.0f))     // <80Hz Post-stage flat (shaped in Pre-EQ)
                setBand(1, DynamicsProcessing.EqBand(true, 250.0f, 0.0f))    // 250Hz Post-stage flat (shaped in Pre-EQ)
                setBand(2, DynamicsProcessing.EqBand(true, 1000.0f, 0.0f))   // 1kHz Neutral midrange (0.0 dB)
                setBand(3, DynamicsProcessing.EqBand(true, 3500.0f, 1.0f))   // 3.5kHz Vocal presence (+1.0 dB)
                setBand(4, DynamicsProcessing.EqBand(true, 20000.0f, 2.2f))  // >12kHz High-resolution "Air" (+2.2 dB)
            }
            configBuilder.setPostEqAllChannelsTo(postEq)

            // Hardware Limiter to eliminate Android 44.1kHz -> 48kHz resampler inter-sample clipping
            val limiter = DynamicsProcessing.Limiter(
                true,   // inUse
                true,   // enabled
                0,      // linkGroup
                2.0f,   // attackTime (2.0 ms)
                60.0f,  // releaseTime (60.0 ms)
                4.0f,   // ratio (4.0:1)
                -1.0f,  // threshold (-1.0 dBFS)
                0.0f    // postGain (0.0 dB)
            )
            configBuilder.setLimiterAllChannelsTo(limiter)

            dynamicsProcessing = DynamicsProcessing(0, GLOBAL_AUDIO_SESSION_ID, configBuilder.build()).apply {
                setEnabled(true)
            }
            Log.i(TAG, "AppleSignatureAudioEngine: DynamicsProcessing enabled on Session 0")
        } catch (e: Exception) {
            Log.w(TAG, "DynamicsProcessing Session 0 unavailable on this OEM build: \${e.message}")
        }
    }

    /**
     * System #4: Android 14+ (API 34+) Bit-Perfect USB DAC / Headset Bypass.
     * Listens for TYPE_USB_HEADSET and TYPE_USB_DEVICE connections and activates
     * AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT (preferring ENCODING_PCM_FLOAT at 44100 Hz).
     */
    private fun registerUsbBitPerfectCallback() {
        val manager = audioManager ?: return
        if (audioDeviceCallback != null) return

        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                if (addedDevices == null) return
                for (device in addedDevices) {
                    if (isUsbAudioSink(device)) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            enableBitPerfectUsbOutput(manager, device)
                        }
                    }
                }
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                if (removedDevices == null) return
                for (device in removedDevices) {
                    if (isUsbAudioSink(device)) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            clearBitPerfectUsbOutput(manager, device)
                        }
                    }
                }
            }
        }

        audioDeviceCallback = callback
        try {
            manager.registerAudioDeviceCallback(callback, mainHandler)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register AudioDeviceCallback: \${e.message}")
        }
    }

    private fun isUsbAudioSink(device: AudioDeviceInfo): Boolean {
        if (!device.isSink) return false
        return device.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
            device.type == AudioDeviceInfo.TYPE_USB_DEVICE
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun enableBitPerfectUsbOutput(manager: AudioManager, usbDevice: AudioDeviceInfo) {
        try {
            val supportedAttrs = manager.getSupportedMixerAttributes(usbDevice)
            val preferredBitPerfect = supportedAttrs.firstOrNull { attr ->
                attr.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT &&
                    attr.format.encoding == AudioFormat.ENCODING_PCM_FLOAT &&
                    attr.format.sampleRate == 44100
            } ?: supportedAttrs.firstOrNull { attr ->
                attr.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT
            } ?: run {
                val float44kFormat = AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(44100)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
                AudioMixerAttributes.Builder(float44kFormat)
                    .setMixerBehavior(AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT)
                    .build()
            }

            val applied = manager.setPreferredMixerAttributes(
                mediaAudioAttributes,
                usbDevice,
                preferredBitPerfect
            )
            Log.i(TAG, "Android 14+ Bit-Perfect USB DAC mode applied=\$applied for device=\${usbDevice.productName}")
        } catch (e: Exception) {
            Log.w(TAG, "Bit-Perfect USB mixer configuration not supported by DAC: \${e.message}")
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun clearBitPerfectUsbOutput(manager: AudioManager, usbDevice: AudioDeviceInfo) {
        try {
            manager.clearPreferredMixerAttributes(mediaAudioAttributes, usbDevice)
            Log.i(TAG, "Cleared Bit-Perfect mixer attributes for disconnected USB DAC: \${usbDevice.productName}")
        } catch (_: Exception) {}
    }

    fun release() {
        try {
            audioDeviceCallback?.let { cb ->
                audioManager?.unregisterAudioDeviceCallback(cb)
            }
        } catch (_: Exception) {}
        audioDeviceCallback = null

        try {
            dynamicsProcessing?.setEnabled(false)
            dynamicsProcessing?.release()
        } catch (_: Exception) {}
        dynamicsProcessing = null
    }
}`
  },
  {
    id: 'media-playback-service',
    name: 'MediaPlaybackService.kt',
    path: 'app/src/main/java/com/musicbeta/wrapper/MediaPlaybackService.kt',
    category: 'kotlin',
    language: 'kotlin',
    description: 'Ongoing ForegroundService with MediaSessionCompat notification, PowerManager partial wake lock, and WiFi high-perf lock.',
    requirementTags: ['Core Requirement #2', 'MediaSessionCompat', 'WakeLock & WiFiLock', 'Android 14 MediaPlayback'],
    content: `package com.musicbeta.wrapper

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

/**
 * Ongoing Foreground Service for MusicBetaWrapper.
 *
 * Fulfills:
 * - Ongoing ForegroundService with MediaSessionCompat media notification
 * - PowerManager.PARTIAL_WAKE_LOCK & WifiManager Wi-Fi Lock during playback
 * - Handles Media Key events (Play, Pause, Next, Prev) via MediaSession callbacks
 * - Android 14+ (API 34) Foreground Service Type: mediaPlayback
 */
class MediaPlaybackService : Service() {

    companion object {
        private const val TAG = "MediaPlaybackService"
        private const val CHANNEL_ID = "music_playback_channel"
        private const val NOTIFICATION_ID = 2001
        private const val MEDIA_SESSION_TAG = "MusicBetaMediaSession"

        const val ACTION_START_PLAYBACK = "com.musicbeta.wrapper.ACTION_START_PLAYBACK"
        const val ACTION_PAUSE_PLAYBACK = "com.musicbeta.wrapper.ACTION_PAUSE_PLAYBACK"
        const val ACTION_STOP_PLAYBACK = "com.musicbeta.wrapper.ACTION_STOP_PLAYBACK"
        const val ACTION_UPDATE_METADATA = "com.musicbeta.wrapper.ACTION_UPDATE_METADATA"

        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_ARTIST = "extra_artist"
        const val EXTRA_ARTWORK_URL = "extra_artwork_url"

        // Notification Action Intents
        const val ACTION_NOTIFICATION_PLAY = "com.musicbeta.wrapper.NOTIFICATION_PLAY"
        const val ACTION_NOTIFICATION_PAUSE = "com.musicbeta.wrapper.NOTIFICATION_PAUSE"
        const val ACTION_NOTIFICATION_NEXT = "com.musicbeta.wrapper.NOTIFICATION_NEXT"
        const val ACTION_NOTIFICATION_PREV = "com.musicbeta.wrapper.NOTIFICATION_PREV"
    }

    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var notificationManager: NotificationManager

    // Power & Wi-Fi Locks for uninterrupted screen-off audio streaming
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private var currentTitle: String = "Apple Music"
    private var currentArtist: String = "Playing in Background"
    private var currentArtworkUrl: String = ""
    private var currentArtworkBitmap: Bitmap? = null
    private var isPlaying: Boolean = false

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
        initMediaSession()
        initLocks()
        Log.i(TAG, "MediaPlaybackService initialized with MediaSession & Locks")
    }

    /**
     * Requirement 2: Acquire WakeLock & Wi-Fi Lock so audio streams uninterrupted when screen turns off.
     */
    @SuppressLint("WakelockTimeout")
    private fun initLocks() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "MusicBetaWrapper:PlaybackWakeLock"
            ).apply {
                setReferenceCounted(false)
            }

            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val wifiMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                WifiManager.WIFI_MODE_FULL_LOW_LATENCY
            } else {
                @Suppress("DEPRECATION")
                WifiManager.WIFI_MODE_FULL_HIGH_PERF
            }
            wifiLock = wifiManager.createWifiLock(wifiMode, "MusicBetaWrapper:PlaybackWifiLock").apply {
                setReferenceCounted(false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Power/Wi-Fi locks", e)
        }
    }

    private fun acquireLocks() {
        try {
            wakeLock?.let {
                if (!it.isHeld) {
                    it.acquire()
                    Log.d(TAG, "PARTIAL_WAKE_LOCK acquired")
                }
            }
            wifiLock?.let {
                if (!it.isHeld) {
                    it.acquire()
                    Log.d(TAG, "WIFI_LOCK acquired")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error acquiring locks", e)
        }
    }

    private fun releaseLocks() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.d(TAG, "PARTIAL_WAKE_LOCK released")
                }
            }
            wifiLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.d(TAG, "WIFI_LOCK released")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing locks", e)
        }
    }

    private fun initMediaSession() {
        mediaSession = MediaSessionCompat(this, MEDIA_SESSION_TAG).apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )

            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    handlePlayCommand()
                }

                override fun onPause() {
                    handlePauseCommand()
                }

                override fun onSkipToNext() {
                    handleNextCommand()
                }

                override fun onSkipToPrevious() {
                    handlePreviousCommand()
                }

                override fun onStop() {
                    handleStopCommand()
                }
            })

            isActive = true
        }
    }

    private fun updatePlaybackState(playing: Boolean) {
        val state = if (playing) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        val actions = PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_STOP

        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(actions)
                .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f)
                .build()
        )
    }

    private fun updateMediaMetadata() {
        val builder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentTitle)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, currentArtist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "Apple Music")

        currentArtworkBitmap?.let {
            builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, it)
            builder.putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, it)
        }

        mediaSession.setMetadata(builder.build())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_STICKY

        val title = intent.getStringExtra(EXTRA_TITLE) ?: currentTitle
        val artist = intent.getStringExtra(EXTRA_ARTIST) ?: currentArtist
        val artworkUrl = intent.getStringExtra(EXTRA_ARTWORK_URL) ?: currentArtworkUrl

        if (title.isNotEmpty()) currentTitle = title
        if (artist.isNotEmpty()) currentArtist = artist

        when (action) {
            ACTION_START_PLAYBACK, ACTION_NOTIFICATION_PLAY -> {
                isPlaying = true
                acquireLocks()
                updatePlaybackState(true)
                loadArtworkAndRefresh(artworkUrl)
                startForegroundWithNotification()
                if (action == ACTION_NOTIFICATION_PLAY) {
                    dispatchToActivity(MainActivity.COMMAND_PLAY)
                }
            }

            ACTION_PAUSE_PLAYBACK, ACTION_NOTIFICATION_PAUSE -> {
                isPlaying = false
                releaseLocks()
                updatePlaybackState(false)
                refreshNotification()
                if (action == ACTION_NOTIFICATION_PAUSE) {
                    dispatchToActivity(MainActivity.COMMAND_PAUSE)
                }
            }

            ACTION_STOP_PLAYBACK -> {
                isPlaying = false
                releaseLocks()
                updatePlaybackState(false)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }

            ACTION_UPDATE_METADATA -> {
                loadArtworkAndRefresh(artworkUrl)
            }

            ACTION_NOTIFICATION_NEXT -> {
                handleNextCommand()
            }

            ACTION_NOTIFICATION_PREV -> {
                handlePreviousCommand()
            }
        }

        return START_STICKY
    }

    private fun handlePlayCommand() {
        isPlaying = true
        acquireLocks()
        updatePlaybackState(true)
        refreshNotification()
        dispatchToActivity(MainActivity.COMMAND_PLAY)
    }

    private fun handlePauseCommand() {
        isPlaying = false
        releaseLocks()
        updatePlaybackState(false)
        refreshNotification()
        dispatchToActivity(MainActivity.COMMAND_PAUSE)
    }

    private fun handleNextCommand() {
        dispatchToActivity(MainActivity.COMMAND_NEXT)
    }

    private fun handlePreviousCommand() {
        dispatchToActivity(MainActivity.COMMAND_PREV)
    }

    private fun handleStopCommand() {
        isPlaying = false
        releaseLocks()
        updatePlaybackState(false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun dispatchToActivity(command: String) {
        val intent = Intent(MainActivity.ACTION_MEDIA_COMMAND).apply {
            putExtra(MainActivity.EXTRA_COMMAND, command)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    private fun loadArtworkAndRefresh(url: String) {
        if (url.isNotEmpty() && url != currentArtworkUrl) {
            currentArtworkUrl = url
            serviceScope.launch {
                val bitmap = downloadBitmap(url)
                currentArtworkBitmap = bitmap
                updateMediaMetadata()
                refreshNotification()
            }
        } else {
            updateMediaMetadata()
            refreshNotification()
        }
    }

    private suspend fun downloadBitmap(urlString: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val stream = URL(urlString).openStream()
            BitmapFactory.decodeStream(stream)
        } catch (_: Exception) {
            null
        }
    }

    private fun startForegroundWithNotification() {
        val notification = buildMediaNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun refreshNotification() {
        val notification = buildMediaNotification()
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildMediaNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prevIntent = Intent(this, MediaPlaybackService::class.java).apply {
            action = ACTION_NOTIFICATION_PREV
        }
        val prevPendingIntent = PendingIntent.getService(
            this,
            1,
            prevIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIntent = Intent(this, MediaPlaybackService::class.java).apply {
            action = if (isPlaying) ACTION_NOTIFICATION_PAUSE else ACTION_NOTIFICATION_PLAY
        }
        val playPausePendingIntent = PendingIntent.getService(
            this,
            2,
            playPauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextIntent = Intent(this, MediaPlaybackService::class.java).apply {
            action = ACTION_NOTIFICATION_NEXT
        }
        val nextPendingIntent = PendingIntent.getService(
            this,
            3,
            nextIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val playPauseText = if (isPlaying) "Pause" else "Play"

        val mediaStyle = androidx.media.app.NotificationCompat.MediaStyle()
            .setMediaSession(mediaSession.sessionToken)
            .setShowActionsInCompactView(0, 1, 2)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(currentTitle)
            .setContentText(currentArtist)
            .setSubText("Apple Music Beta")
            .setContentIntent(contentPendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)
            .setAutoCancel(false)
            .setStyle(mediaStyle)
            .addAction(android.R.drawable.ic_media_previous, "Previous", prevPendingIntent)
            .addAction(playPauseIcon, playPauseText, playPausePendingIntent)
            .addAction(android.R.drawable.ic_media_next, "Next", nextPendingIntent)

        currentArtworkBitmap?.let {
            builder.setLargeIcon(it)
        }

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Music Playback Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Ongoing media playback and audio controls for MusicBetaWrapper"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        releaseLocks()
        mediaSession.isActive = false
        mediaSession.release()
        Log.i(TAG, "MediaPlaybackService destroyed, locks released")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}`
  },
  {
    id: 'activity-main-layout',
    name: 'activity_main.xml',
    path: 'app/src/main/res/layout/activity_main.xml',
    category: 'layout',
    language: 'xml',
    description: 'Direct FrameLayout root (NO ScrollView/NestedScrollView) with match_parent hardware-accelerated WebView so Apple Music virtual scrolling calculates row heights accurately.',
    requirementTags: ['Virtual Scroll Fix', 'No ScrollView Wrapper', 'match_parent WebView'],
    content: `<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:id="@+id/rootContainer"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="#FFFFFF"
    android:fitsSystemWindows="false"
    tools:context=".MainActivity">

    <!-- Direct match_parent WebView (never inside a ScrollView/NestedScrollView) for Virtual Scrolling -->
    <WebView
        android:id="@+id/webView"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:background="#FFFFFF"
        android:fitsSystemWindows="false"
        android:layerType="hardware"
        android:overScrollMode="never"
        android:scrollbars="none" />

    <!-- Thin Top Progress Bar for Page Loads -->
    <ProgressBar
        android:id="@+id/progressBar"
        style="?android:attr/progressBarStyleHorizontal"
        android:layout_width="match_parent"
        android:layout_height="3dp"
        android:layout_gravity="top"
        android:indeterminate="false"
        android:max="100"
        android:progressBackgroundTint="#33FFFFFF"
        android:progressTint="#FA2D48"
        android:visibility="gone"
        tools:progress="65"
        tools:visibility="visible" />

    <!-- Offline / Network Error Fallback View -->
    <FrameLayout
        android:id="@+id/errorLayout"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:background="#000000"
        android:clickable="true"
        android:focusable="true"
        android:visibility="gone"
        tools:visibility="gone">

        <LinearLayout
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_gravity="center"
            android:gravity="center"
            android:orientation="vertical"
            android:padding="24dp">

            <ImageView
                android:layout_width="72dp"
                android:layout_height="72dp"
                android:layout_marginBottom="16dp"
                android:contentDescription="@string/network_error_title"
                android:src="@android:drawable/ic_dialog_alert"
                app:tint="#FA2D48" />

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginBottom="8dp"
                android:text="@string/network_error_title"
                android:textColor="#FFFFFF"
                android:textSize="20sp"
                android:textStyle="bold" />

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginBottom="24dp"
                android:gravity="center"
                android:text="@string/network_error_subtitle"
                android:textColor="#9E9E9E"
                android:textSize="14sp" />

            <Button
                android:id="@+id/btnRetry"
                android:layout_width="wrap_content"
                android:layout_height="48dp"
                android:backgroundTint="#FA2D48"
                android:onClick="onRetryClicked"
                android:paddingStart="28dp"
                android:paddingEnd="28dp"
                android:text="@string/action_retry"
                android:textColor="#FFFFFF"
                android:textStyle="bold" />

        </LinearLayout>
    </FrameLayout>

</FrameLayout>`
  },
  {
    id: 'github-workflow-release',
    name: '.github/workflows/release.yml',
    path: '.github/workflows/release.yml',
    category: 'workflow',
    language: 'yaml',
    description: 'Automated CI/CD workflow: runs on push to main, v* tags, or manual click; sets up JDK 17 + Gradle 8.5, builds & signs the release APK, uploads artifact & publishes GitHub Release.',
    requirementTags: ['Core Requirement #5', 'GitHub Releases CI/CD', 'Auto-Signed APK', 'Gradle 8.5'],
    content: `name: Build & Release MusicBetaWrapper APK

on:
  push:
    branches:
      - main
      - master
    tags:
      - 'v*'
  workflow_dispatch:

concurrency:
  group: \${{ github.workflow }}-\${{ github.ref }}
  cancel-in-progress: true

permissions:
  contents: write
  actions: read

jobs:
  build-and-release:
    name: Build & Sign Android APK
    runs-on: ubuntu-latest

    steps:
      - name: Checkout Repository
        uses: actions/checkout@v4
        with:
          fetch-depth: 0

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17'

      - name: Setup Gradle 8.5
        uses: gradle/actions/setup-gradle@v3
        with:
          gradle-version: '8.5'

      - name: Accept Android SDK Licenses
        run: |
          SDKMANAGER=$(find "$ANDROID_HOME" -name sdkmanager -type f 2>/dev/null | head -n 1)
          if [ -n "$SDKMANAGER" ]; then
            yes | "$SDKMANAGER" --licenses > /dev/null 2>&1 || true
          fi

      - name: Build Release & Debug APKs with Gradle
        run: |
          gradle assembleRelease assembleDebug --stacktrace --no-daemon

      - name: Align & Sign Release APK (Secrets or Auto-Generated Keystore)
        id: sign_apk
        env:
          KEYSTORE_BASE64: \${{ secrets.KEYSTORE_BASE64 }}
          KEYSTORE_PASSWORD: \${{ secrets.KEYSTORE_PASSWORD }}
          KEY_ALIAS: \${{ secrets.KEY_ALIAS }}
          KEY_PASSWORD: \${{ secrets.KEY_PASSWORD }}
        run: |
          mkdir -p release-artifacts

          BUILT_APK=$(find app/build/outputs/apk/release -name "*.apk" | head -n 1)
          if [ -z "$BUILT_APK" ]; then
            echo "Error: No APK found in app/build/outputs/apk/release"
            exit 1
          fi
          echo "Found built APK: $BUILT_APK"

          VERSION_LABEL="\${GITHUB_REF_NAME:-v1.1.0}"
          ALIGNED_APK="release-artifacts/aligned.apk"
          DEST_APK="release-artifacts/MusicBetaWrapper-\${VERSION_LABEL}-signed.apk"

          BUILD_TOOLS_DIR=$(ls -d $ANDROID_HOME/build-tools/* | sort -V | tail -n 1)

          if [ -n "$KEYSTORE_BASE64" ] && [ -n "$KEYSTORE_PASSWORD" ] && [ -n "$KEY_ALIAS" ]; then
            echo "Signing APK with provided repository secrets..."
            echo "$KEYSTORE_BASE64" | base64 --decode > release.keystore
            KS_PASS="$KEYSTORE_PASSWORD"
            K_ALIAS="$KEY_ALIAS"
            K_PASS="\${KEY_PASSWORD:-$KEYSTORE_PASSWORD}"
          else
            echo "No signing secrets configured; generating self-contained release keystore so APK installs cleanly on Android..."
            KS_PASS="musicbeta123"
            K_ALIAS="musicbeta"
            K_PASS="musicbeta123"
            keytool -genkeypair -v \\
              -keystore release.keystore \\
              -alias "$K_ALIAS" \\
              -keyalg RSA \\
              -keysize 2048 \\
              -validity 10000 \\
              -storepass "$KS_PASS" \\
              -keypass "$K_PASS" \\
              -dname "CN=MusicBetaWrapper, OU=Mobile, O=MusicBeta, L=Cupertino, ST=CA, C=US"
          fi

          "$BUILD_TOOLS_DIR/zipalign" -f -v -p 4 "$BUILT_APK" "$ALIGNED_APK"
          "$BUILD_TOOLS_DIR/apksigner" sign \\
            --ks release.keystore \\
            --ks-key-alias "$K_ALIAS" \\
            --ks-pass pass:"$KS_PASS" \\
            --key-pass pass:"$K_PASS" \\
            --v1-signing-enabled true \\
            --v2-signing-enabled true \\
            --v3-signing-enabled true \\
            --out "$DEST_APK" \\
            "$ALIGNED_APK"

          rm -f release.keystore "$ALIGNED_APK"
          echo "apk_path=$DEST_APK" >> $GITHUB_OUTPUT

      - name: Upload Signed APK as Workflow Artifact
        uses: actions/upload-artifact@v4
        with:
          name: MusicBetaWrapper-Signed-APK
          path: \${{ steps.sign_apk.outputs.apk_path }}
          if-no-files-found: error

      - name: Publish GitHub Release (on Tag or Main Push)
        continue-on-error: true
        uses: softprops/action-gh-release@v2
        with:
          name: MusicBetaWrapper \${{ github.ref_name }}
          tag_name: \${{ startsWith(github.ref, 'refs/tags/') && github.ref_name || format('v1.1.{0}', github.run_number) }}
          files: \${{ steps.sign_apk.outputs.apk_path }}
          draft: false
          prerelease: false
          generate_release_notes: true
          body: |
            ### 🎵 MusicBetaWrapper Signed Android APK (\${{ github.ref_name }})

            Ready-to-install signed Android 14+ (API 34) APK wrapping \`https://beta.music.apple.com\`.

            #### Included Audio & WebView Systems (v1.1.0):
            - **Widevine DRM & 256kbps AAC**: Grants \`RESOURCE_PROTECTED_MEDIA_ID\` and forces \`MusicKit.PlaybackBitrate.HIGH\` (\`256kbps\`).
            - **AppleSignatureAudioEngine (Session 0 DynamicsProcessing + 32-Bit Float Web Audio)**: 5-band Pre-EQ & Post-EQ (<80Hz +2.0dB, 250Hz -0.5dB, 3.5kHz +1.0dB, >12kHz +2.2dB) and \`-1.0dBFS\` hardware limiter.
            - **Android 14+ (API 34+) Bit-Perfect USB DAC Bypass**: Automatic \`AudioDeviceCallback\` setting \`MIXER_BEHAVIOR_BIT_PERFECT\` (44.1kHz PCM Float).
            - **Zero Image Flicker & Opaque Navigation Drawer**: Direct \`match_parent\` \`WebView\` for virtual scrolling, solid opaque menu background, and 500MB OkHttp disk cache.`
  },
  {
    id: 'root-build-gradle',
    name: 'build.gradle.kts (Root)',
    path: 'build.gradle.kts',
    category: 'config',
    language: 'kotlin',
    description: 'Root Gradle configuration defining Android and Kotlin Gradle plugins.',
    requirementTags: ['Project Config'],
    content: `plugins {
    id("com.android.application") version "8.3.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.23" apply false
}`
  },
  {
    id: 'settings-gradle',
    name: 'settings.gradle.kts',
    path: 'settings.gradle.kts',
    category: 'config',
    language: 'kotlin',
    description: 'Repository management with Google and MavenCentral, project name "MusicBetaWrapper".',
    requirementTags: ['Project Config'],
    content: `pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "MusicBetaWrapper"
include(":app")`
  },
  {
    id: 'gradle-properties',
    name: 'gradle.properties',
    path: 'gradle.properties',
    category: 'config',
    language: 'properties',
    description: 'AndroidX and JVM heap settings required by Android Gradle Plugin 8+.',
    requirementTags: ['Project Config', 'AndroidX'],
    content: `org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
android.enableJetifier=false
kotlin.code.style=official
android.nonTransitiveRClass=true`
  },
  {
    id: 'strings-xml',
    name: 'values/strings.xml',
    path: 'app/src/main/res/values/strings.xml',
    category: 'layout',
    language: 'xml',
    description: 'String resources for app name and offline fallback UI.',
    requirementTags: ['App Resources'],
    content: `<resources>
    <string name="app_name">MusicBetaWrapper</string>
    <string name="network_error_title">Connection Lost</string>
    <string name="network_error_subtitle">Unable to load Apple Music Beta. Check your internet connection and try again.</string>
    <string name="action_retry">Retry</string>
</resources>`
  },
  {
    id: 'themes-xml',
    name: 'themes.xml',
    path: 'app/src/main/res/values/themes.xml',
    category: 'layout',
    language: 'xml',
    description: 'Material theme with transparent system status and navigation bars for Light mode.',
    requirementTags: ['Core Requirement #4', 'Light Mode', 'Edge-to-Edge Theme'],
    content: `<resources xmlns:tools="http://schemas.android.com/tools">
    <style name="Theme.MusicBetaWrapper" parent="Theme.MaterialComponents.DayNight.NoActionBar">
        <!-- Edge-to-Edge System Bars (Light Theme Default) -->
        <item name="android:statusBarColor">@android:color/transparent</item>
        <item name="android:navigationBarColor">@android:color/transparent</item>
        <item name="android:windowDrawsSystemBarBackgrounds">true</item>
        <item name="android:windowBackground">@color/window_background_light</item>
        <item name="android:windowLightStatusBar" tools:targetApi="m">true</item>
        <item name="android:windowLightNavigationBar" tools:targetApi="o_mr1">true</item>
        
        <!-- Primary Brand Colors -->
        <item name="colorPrimary">@color/apple_red</item>
        <item name="colorPrimaryVariant">#D32F2F</item>
        <item name="colorOnPrimary">@color/apple_text_light</item>
        <item name="colorSurface">@color/surface_light</item>
        <item name="colorOnSurface">@color/apple_text_light</item>
    </style>
</resources>`
  },
  {
    id: 'themes-night-xml',
    name: 'values-night/themes.xml',
    path: 'app/src/main/res/values-night/themes.xml',
    category: 'layout',
    language: 'xml',
    description: 'Dark Mode theme configuration with OLED dark background and light system icon tinting.',
    requirementTags: ['Dark Mode', 'Edge-to-Edge Theme', 'Theme.MusicBetaWrapper'],
    content: `<resources xmlns:tools="http://schemas.android.com/tools">
    <style name="Theme.MusicBetaWrapper" parent="Theme.MaterialComponents.DayNight.NoActionBar">
        <!-- Edge-to-Edge System Bars (Dark Mode) -->
        <item name="android:statusBarColor">@android:color/transparent</item>
        <item name="android:navigationBarColor">@android:color/transparent</item>
        <item name="android:windowDrawsSystemBarBackgrounds">true</item>
        <item name="android:windowBackground">@color/window_background_dark</item>
        <item name="android:windowLightStatusBar" tools:targetApi="m">false</item>
        <item name="android:windowLightNavigationBar" tools:targetApi="o_mr1">false</item>
        
        <!-- Primary Brand Colors -->
        <item name="colorPrimary">@color/apple_red</item>
        <item name="colorPrimaryVariant">#D32F2F</item>
        <item name="colorOnPrimary">@color/apple_text_dark</item>
        <item name="colorSurface">@color/surface_dark</item>
        <item name="colorOnSurface">@color/apple_text_dark</item>
    </style>
</resources>`
  },
  {
    id: 'colors-xml',
    name: 'values/colors.xml',
    path: 'app/src/main/res/values/colors.xml',
    category: 'layout',
    language: 'xml',
    description: 'Color resource definitions for Light and Dark modes with Apple Music brand red accent.',
    requirementTags: ['Theme Colors', 'Light & Dark Palette'],
    content: `<resources>
    <!-- Brand Accent -->
    <color name="apple_red">#FA2D48</color>
    <color name="apple_red_dark">#D32F2F</color>

    <!-- Light Theme Palette -->
    <color name="window_background_light">#FFFFFF</color>
    <color name="surface_light">#F2F2F7</color>
    <color name="apple_text_light">#000000</color>
    <color name="apple_text_secondary_light">#6E6E73</color>

    <!-- Dark Theme Palette -->
    <color name="window_background_dark">#000000</color>
    <color name="surface_dark">#1C1C1E</color>
    <color name="apple_text_dark">#FFFFFF</color>
    <color name="apple_text_secondary_dark">#8E8E93</color>
</resources>`
  },
  {
    id: 'ic-launcher-bg',
    name: 'drawable/ic_launcher_background.xml',
    path: 'app/src/main/res/drawable/ic_launcher_background.xml',
    category: 'layout',
    language: 'xml',
    description: 'Adaptive icon OLED black vector background.',
    requirementTags: ['Adaptive Icon'],
    content: `<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#000000"
        android:pathData="M0,0h108v108h-108z" />
</vector>`
  },
  {
    id: 'ic-launcher-fg',
    name: 'drawable/ic_launcher_foreground.xml',
    path: 'app/src/main/res/drawable/ic_launcher_foreground.xml',
    category: 'layout',
    language: 'xml',
    description: 'Adaptive icon Apple Music crimson note vector foreground.',
    requirementTags: ['Adaptive Icon'],
    content: `<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <group
        android:scaleX="1.8"
        android:scaleY="1.8"
        android:translateX="32.4"
        android:translateY="32.4">
        <path
            android:fillColor="#FA2D48"
            android:pathData="M12,3v10.55c-0.59,-0.34 -1.27,-0.55 -2,-0.55 -2.21,0 -4,1.79 -4,4s1.79,4 4,4 4,-1.79 4,-4V7h4V3h-6z" />
    </group>
</vector>`
  },
  {
    id: 'mipmap-ic-launcher',
    name: 'mipmap-anydpi-v26/ic_launcher.xml',
    path: 'app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml',
    category: 'layout',
    language: 'xml',
    description: 'Adaptive launcher icon definition.',
    requirementTags: ['Adaptive Icon'],
    content: `<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>`
  },
  {
    id: 'mipmap-ic-launcher-round',
    name: 'mipmap-anydpi-v26/ic_launcher_round.xml',
    path: 'app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml',
    category: 'layout',
    language: 'xml',
    description: 'Adaptive round launcher icon definition.',
    requirementTags: ['Adaptive Icon'],
    content: `<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>`
  },
  {
    id: 'proguard-rules',
    name: 'proguard-rules.pro',
    path: 'app/proguard-rules.pro',
    category: 'config',
    language: 'properties',
    description: 'Keeps JavascriptInterface, MediaSession, and MediaPlaybackService from R8/ProGuard obfuscation.',
    requirementTags: ['Release Optimization', 'ProGuard / R8'],
    content: `# Keep WebView JavaScript interfaces
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
}`
  }
];
