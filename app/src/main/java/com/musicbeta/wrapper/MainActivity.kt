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
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
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
 * High-performance, edge-to-edge native Android 14 wrapper around https://music.apple.com
 *
 * Key Fixes & Capabilities:
 * 1. Virtual Scrolling & Layout Fix: Direct match_parent WebView inside FrameLayout (no ScrollView) + LayoutAlgorithm.NORMAL
 * 2. Full Browser & CDN Capabilities: DOM Storage, Database, WideViewport, MIXED_CONTENT_ALWAYS_ALLOW, Third-Party Cookies, Mobile Chrome UA
 * 3. Opaque Navigation Menu & Full-Bleed Cards: Solid #ffffff (Light) / #1f1f1f (Dark) nav drawer, no broken contain-intrinsic-size gaps, native srcset aspect ratios
 * 4. Widevine DRM Full-Track Playback: Grants PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID in WebChromeClient
 * 5. 120Hz+ Display Refresh Rate, Camera Notch Safe-Area Padding & Screen-Off Background Audio Service
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MusicBetaWrapper"
        private const val APPLE_MUSIC_URL = "https://music.apple.com"
        private const val NOTIFICATION_PERMISSION_CODE = 1001
        private const val DISK_CACHE_MAX_BYTES = 500L * 1024L * 1024L // 500 MB

        // Preferences for Light/Dark Mode & Cache Schema Migration
        private const val PREFS_NAME = "music_wrapper_prefs"
        private const val KEY_THEME_MODE = "pref_theme_mode"
        private const val KEY_CACHE_SCHEMA_VERSION = "pref_cache_schema_version"
        private const val CACHE_SCHEMA_VERSION = 3
        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"

        // Modern Mobile Chrome on Android 14 User-Agent (ensures standard mobile CSS & Widevine DRM EME support)
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

        // Regex matching mzstatic.com artwork dimensions
        private val MZSTATIC_ARTWORK_REGEX = Regex(
            """/(\d{2,5})x(\d{2,5})([a-z]{0,4})\.(jpg|jpeg|png|webp)(\?.*)?$""",
            RegexOption.IGNORE_CASE
        )
    }

    private lateinit var rootContainer: View
    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var errorLayout: FrameLayout
    private lateinit var insetsController: WindowInsetsControllerCompat
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var okHttpClient: OkHttpClient

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

        // 3. Configure WebView with Full Browser Capabilities, 3rd-Party Cookies, Widevine DRM & 500MB Cache
        configureHighPerformanceWebView()

        // 4. Setup Back-press handling to navigate WebView history without reload
        setupBackNavigation()

        // 5. Register Media Command Receiver
        val filter = IntentFilter(ACTION_MEDIA_COMMAND)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(mediaCommandReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(mediaCommandReceiver, filter)
        }

        // 6. Load production Apple Music web player (normalizing any beta.music.apple.com link)
        if (savedInstanceState == null) {
            val incomingUrl = intent?.dataString
            val targetUrl = if (!incomingUrl.isNullOrBlank() && incomingUrl.contains("music.apple.com")) {
                incomingUrl.replace("beta.music.apple.com", "music.apple.com")
            } else {
                APPLE_MUSIC_URL
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

            WindowInsetsCompat.CONSUMED
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

        // 1. Full Browser Capabilities (JS, DOM Storage, Database, Viewport & Normal Layout)
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        @Suppress("DEPRECATION")
        settings.databaseEnabled = true
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL

        // 2. Mixed Content & Third-Party Cookies for Apple CDN (mzstatic.com) & MusicKit Auth
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

        // 3. 500 MB Dedicated OkHttp Disk Cache & Legacy v1.0.0 Cache Purge
        val customCacheDir = File(applicationContext.cacheDir, "music_beta_webview_cache")
        if (!customCacheDir.exists()) {
            customCacheDir.mkdirs()
        }

        val okHttpCacheDir = File(applicationContext.cacheDir, "music_okhttp_cache_v3")
        if (!okHttpCacheDir.exists()) {
            okHttpCacheDir.mkdirs()
        }

        // Purge any v1.0.0/v1.0.1 cached downscaled 300x300 images so cards & banners load full-bleed
        val savedSchema = sharedPreferences.getInt(KEY_CACHE_SCHEMA_VERSION, 1)
        if (savedSchema < CACHE_SCHEMA_VERSION) {
            try {
                File(applicationContext.cacheDir, "music_beta_okhttp_cache").deleteRecursively()
                File(applicationContext.cacheDir, "music_beta_okhttp_cache_v2").deleteRecursively()
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

        // 4. Modern Mobile Chrome User-Agent for standard mobile CSS & Widevine DRM
        settings.userAgentString = MOBILE_CHROME_USER_AGENT

        // 5. Light/Dark Mode configuration on WebView
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
                applyWebViewTheme(currentThemeMode)
                injectSafeAreaInsetsScript()
                injectUiAndMenuFixes()
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

                // Only intercept GET requests for static asset caching
                if (!req.method.equals("GET", ignoreCase = true)) {
                    return super.shouldInterceptRequest(view, request)
                }

                // Safe artwork URL handling: preserves native aspect ratios, .png cutouts & srcset density
                val targetUrl = rewriteAppleArtworkUrl(rawUrl)

                // Cache static assets (.js, .css, .woff2, and mzstatic.com artwork) to disk
                if (isCacheableStaticAsset(targetUrl)) {
                    fetchFromOkHttpDiskCache(targetUrl, req)?.let { cachedResponse ->
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

                // Redirect any beta.music.apple.com navigation to stable music.apple.com
                if (lower.contains("beta.music.apple.com")) {
                    view?.loadUrl(url.replace("beta.music.apple.com", "music.apple.com", ignoreCase = true))
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
             * Requirement 4: Enable Widevine DRM Audio Playback.
             * Automatically grants RESOURCE_PROTECTED_MEDIA_ID so MusicKit JS plays full songs
             * with Widevine DRM instead of falling back to 30-second previews.
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
                            Log.i(TAG, "Granted WebChromeClient DRM permission: ${granted.joinToString()}")
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

    /**
     * Safe Artwork URL handler:
     * Never alters non-square banners (e.g. 580x132, 1080x540), transparent .png artist cutouts,
     * or standard responsive srcset sizes (<= 600px) so genre cards and hero banners never squash.
     */
    private fun rewriteAppleArtworkUrl(url: String): String {
        if (!url.contains("mzstatic.com", ignoreCase = true)) return url
        val match = MZSTATIC_ARTWORK_REGEX.find(url) ?: return url
        val width = match.groupValues[1].toIntOrNull() ?: return url
        val height = match.groupValues[2].toIntOrNull() ?: return url
        val mode = match.groupValues[3].ifEmpty { "bb" }.lowercase()
        val ext = match.groupValues[4].lowercase()
        val query = match.groupValues[5]

        // Preserve all PNG cutouts, non-square aspect ratios, and special crop modes
        if (ext == "png" || width != height || mode != "bb") {
            return url
        }

        // Only cap ultra-large > 600x600 square album covers to 600x600bb.webp
        if (width > 600) {
            return url.replace(MZSTATIC_ARTWORK_REGEX, "/600x600bb.webp$query")
        }
        return url
    }

    private fun isCacheableStaticAsset(url: String): Boolean {
        val cleanPath = url.substringBefore('?').lowercase()
        if (cleanPath.contains("mzstatic.com") &&
            (cleanPath.endsWith(".webp") || cleanPath.endsWith(".jpg") || cleanPath.endsWith(".jpeg") || cleanPath.endsWith(".png"))
        ) {
            return true
        }
        return cleanPath.endsWith(".js") ||
            cleanPath.endsWith(".mjs") ||
            cleanPath.endsWith(".css") ||
            cleanPath.endsWith(".woff2") ||
            cleanPath.endsWith(".woff") ||
            cleanPath.endsWith(".ttf") ||
            cleanPath.endsWith(".svg")
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
            clean.endsWith(".webp") -> "image/webp"
            clean.endsWith(".png") -> "image/png"
            clean.endsWith(".jpg") || clean.endsWith(".jpeg") -> "image/jpeg"
            clean.endsWith(".svg") -> "image/svg+xml"
            else -> "application/octet-stream"
        }
    }

    /**
     * Requirement 3: Targeted CSS & DOM Fix for Opaque Navigation Menu & Clutter Removal.
     * - Forces a solid opaque background (#ffffff in light mode / #1f1f1f in dark mode) on the navigation drawer/header
     *   so opening the side menu never overlaps or shows home screen tiles underneath.
     * - Removes any broken contain-intrinsic-size / content-visibility overrides so virtual scrolling has zero blank gaps.
     * - Hides "Open in Music", "Exit Beta", and "Beta" badges via CSS + DOM MutationObserver.
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
                    '/* 1. Solid Opaque Navigation Drawer & Header */',
                    '.navigation, .web-navigation, #navigation, [data-testid="navigation"], [class*="web-navigation"], .navigation__header, .navigation__content, .web-navigation__content, .web-navigation__list, .header-bar, header[role="banner"], nav[role="navigation"], nav[aria-label] {',
                    '  background-color: ' + navBg + ' !important;',
                    '  background: ' + navBg + ' !important;',
                    '  color: ' + textColor + ' !important;',
                    '  opacity: 1 !important;',
                    '}',
                    '.navigation, .web-navigation, #navigation, [data-testid="navigation"] {',
                    '  z-index: 99999 !important;',
                    '  padding-bottom: 112px !important;',
                    '}',
                    '/* 2. Hide Open in Music, Exit Beta, Beta badges & upsell banners */',
                    '.upsell-banner, [data-testid="upsell-banner"], [data-testid*="exit-beta"], [class*="upsell"], [class*="smart-banner"], [class*="beta-badge"], .native-upsell, a[href*="itunes.apple.com"], a[href*="music://"], a[href*="itms://"], a[href*="beta.music.apple.com"], button[aria-label*="Open in Music"], button[aria-label*="Exit Beta"] {',
                    '  display: none !important;',
                    '  height: 0 !important;',
                    '  max-height: 0 !important;',
                    '  visibility: hidden !important;',
                    '  pointer-events: none !important;',
                    '  overflow: hidden !important;',
                    '}'
                ].join('\n');

                function enforceOpaqueMenuAndCleanLinks() {
                    try {
                        // Hide "Open in Music", "Exit Beta", and "Beta" badge elements by text content
                        var nodes = document.querySelectorAll('a, button, li, span');
                        for (var i = 0; i < nodes.length; i++) {
                            var el = nodes[i];
                            var txt = (el.textContent || '').trim().toLowerCase();
                            if (txt === 'open in music' || txt === 'exit beta' || txt === 'open in music ↗' || txt === 'exit beta ↗' || (el.tagName === 'SPAN' && txt === 'beta')) {
                                var row = el.closest('li') || el;
                                row.style.setProperty('display', 'none', 'important');
                            }
                        }

                        // Ensure any open navigation drawer container has a 100% solid opaque background
                        var navContainers = document.querySelectorAll('.navigation, .web-navigation, #navigation, [data-testid="navigation"], nav, [role="navigation"], [class*="navigation"]');
                        for (var j = 0; j < navContainers.length; j++) {
                            var navEl = navContainers[j];
                            navEl.style.setProperty('background-color', navBg, 'important');
                            navEl.style.setProperty('background', navBg, 'important');
                            navEl.style.setProperty('opacity', '1', 'important');
                        }

                        // Also walk up from the "Recently Added" / "All Playlists" menu items to guarantee the drawer wrapper is opaque
                        for (var k = 0; k < nodes.length; k++) {
                            var item = nodes[k];
                            var itemTxt = (item.textContent || '').trim();
                            if (itemTxt === 'Recently Added' || itemTxt === 'All Playlists') {
                                var curr = item.parentElement;
                                while (curr && curr !== document.body && curr !== document.documentElement) {
                                    var tag = (curr.tagName || '').toUpperCase();
                                    if (tag === 'MAIN') break;
                                    if (curr.clientHeight > 220 && curr.clientWidth > 200) {
                                        curr.style.setProperty('background-color', navBg, 'important');
                                        curr.style.setProperty('background', navBg, 'important');
                                        curr.style.setProperty('opacity', '1', 'important');
                                    }
                                    curr = curr.parentElement;
                                }
                            }
                        }
                    } catch (err) {}
                }

                enforceOpaqueMenuAndCleanLinks();
                if (!window.__musicUiObserverInstalled) {
                    window.__musicUiObserverInstalled = true;
                    var obs = new MutationObserver(function() {
                        enforceOpaqueMenuAndCleanLinks();
                    });
                    obs.observe(document.documentElement || document.body, { childList: true, subtree: true });
                }
            })();
        """.trimIndent()
        executeJavaScript(fixScript)
    }

    private fun injectAudioBridgeScript() {
        val script = """
            (function() {
                if (window.__musicBetaBridgeInjected) return;
                window.__musicBetaBridgeInjected = true;

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
                    let title = "Apple Music";
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
                            const art = (meta.artwork && meta.artwork.length > 0) ? meta.artwork[0].src : '';
                            if (window.AndroidBridge) {
                                window.AndroidBridge.onMetadataUpdated(meta.title || '', meta.artist || '', art, isPlaying);
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