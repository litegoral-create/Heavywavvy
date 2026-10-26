package com.musicbeta.wrapper

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
                    Regex("^https?://(www\.)?music\.apple\.com", RegexOption.IGNORE_CASE),
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
                    Log.i(TAG, "Forced display refresh rate to: ${bestMode.refreshRate} Hz (Mode ID: ${bestMode.modeId})")
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
                root.style.setProperty('--safe-area-top', '${lastTopInsetDp}px');
                root.style.setProperty('--safe-area-bottom', '${lastBottomInsetDp}px');
                root.style.setProperty('--safe-area-left', '${lastLeftInsetDp}px');
                root.style.setProperty('--safe-area-right', '${lastRightInsetDp}px');
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

        val themeScript = """
            (function() {
                const scheme = '${if (isDark) "dark" else "light"}';
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
                        Regex("^https?://music\.apple\.com", RegexOption.IGNORE_CASE),
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
                            Log.i(TAG, "Granted WebChromeClient Widevine DRM permission: ${granted.joinToString()}")
                        } else {
                            request.grant(requestedResources)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error granting WebChromeClient permission", e)
                    }
                }
            }

            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                Log.d(TAG, "WebView Console: ${consoleMessage?.message()} (line ${consoleMessage?.lineNumber()})")
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
            Log.w(TAG, "OkHttp cache miss/fallback for $targetUrl: ${e.message}")
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
                ].join('\n');
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
                    Log.i(TAG, "Pruned cache directory to ${totalSize / (1024 * 1024)} MB")
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
                DynamicsProcessing.Config.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
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
                enabled = true
            }
            Log.i(TAG, "AppleSignatureAudioEngine: DynamicsProcessing enabled on Session 0")
        } catch (e: Exception) {
            Log.w(TAG, "DynamicsProcessing Session 0 unavailable on this OEM build: ${e.message}")
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
            Log.w(TAG, "Failed to register AudioDeviceCallback: ${e.message}")
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
            Log.i(TAG, "Android 14+ Bit-Perfect USB DAC mode applied=$applied for device=${usbDevice.productName}")
        } catch (e: Exception) {
            Log.w(TAG, "Bit-Perfect USB mixer configuration not supported by DAC: ${e.message}")
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun clearBitPerfectUsbOutput(manager: AudioManager, usbDevice: AudioDeviceInfo) {
        try {
            manager.clearPreferredMixerAttributes(mediaAudioAttributes, usbDevice)
            Log.i(TAG, "Cleared Bit-Perfect mixer attributes for disconnected USB DAC: ${usbDevice.productName}")
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
            dynamicsProcessing?.enabled = false
            dynamicsProcessing?.release()
        } catch (_: Exception) {}
        dynamicsProcessing = null
    }
}