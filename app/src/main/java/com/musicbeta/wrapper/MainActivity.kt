package com.musicbeta.wrapper

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.MutableContextWrapper
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
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.view.Display
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.HttpAuthHandler
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
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
import java.io.ByteArrayInputStream
import java.io.File

/**
 * MainActivity for MusicBetaWrapper (v1.1.0).
 * High-performance, zero-reload, edge-to-edge native Android 14 wrapper around https://beta.music.apple.com
 *
 * Integrated Audio, Auth Cookie Persistence, DRM & Crash-Safety Systems:
 * 1. Cookie Persistence & Cross-Domain Auth (idmsa.apple.com -> music.apple.com / beta.music.apple.com):
 *    - Enables global cookies and third-party cookies (CookieManager.setAcceptThirdPartyCookies(webView, true))
 *      on both the primary WebView and any OAuth sign-in popup WebViews.
 *    - Flushes cookies to persistent SQLite disk storage (CookieManager.getInstance().flush()) on page start,
 *      cross-domain history updates (doUpdateVisitedHistory), page finish, and lifecycle pause.
 * 2. Crash-Safe WebView Singleton Re-Attachment:
 *    - Retains a single WebView instance across UI recompositions/navigation using MutableContextWrapper(context.applicationContext).
 *    - Always executes if (webView.parent != null) { (webView.parent as? ViewGroup)?.removeView(webView) } before attaching.
 *    - Only calls webView.loadUrl(...) if webView.url == null so an already loaded session never reloads.
 * 3. Widevine DRM, OAuth Popup Windows & WebView Audio Optimization:
 *    - Grants PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID in WebChromeClient with a clean Widevine-compatible Chrome User-Agent.
 *    - Handles multi-window Apple ID OAuth popups (onCreateWindow / onCloseWindow) so sign-in tokens post back seamlessly.
 * 4. SSL, HTTP Auth & Redirect Loop Prevention:
 *    - Preserves Set-Cookie headers across 302 redirects by never calling view.loadUrl() mid-redirect on https:// URLs.
 *    - Guards against infinite redirect loops with a sliding-window timestamp detector and preserves credentials in onReceivedHttpAuthRequest.
 * 5. Audiophile & Power-User Suite:
 *    - Native 5-Band EQ Sound Profiles (Apple Studio, Bass Boost, Vocal Clarity, Late Night, Flat Bypass) + Preamp Gain on Session 0.
 *    - Web Audio / MusicKit Crossfade Engine (0s-12s), High-Res 600x600 Lock-Screen Art & Interactive Seekbar Scrubbing.
 *    - Mini-Player Swipe Gestures with Native Haptic Feedback, Lyrics Keep-Awake (FLAG_KEEP_SCREEN_ON), Smart Sleep Timer with 10s Fade-Out,
 *      Cellular Data Saver vs. Wi-Fi Auto-Bitrate Switching, and Universal Song Link Share Sheet Resolver.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MusicBetaWrapper"
        private const val APPLE_MUSIC_BETA_URL = "https://beta.music.apple.com"
        private const val NOTIFICATION_PERMISSION_CODE = 1001
        private const val DISK_CACHE_MAX_BYTES = 500L * 1024L * 1024L // 500 MB

        // Redirect Loop Protection Constants
        private const val REDIRECT_LOOP_WINDOW_MS = 8_000L
        private const val MAX_RAPID_REDIRECTS = 4

        // Preferences for Light/Dark Mode, EQ Sound Profile, Crossfade & Data Saver
        private const val PREFS_NAME = "music_wrapper_prefs"
        private const val KEY_THEME_MODE = "pref_theme_mode"
        private const val KEY_SOUND_PROFILE = "pref_sound_profile"
        private const val KEY_CROSSFADE_SECONDS = "pref_crossfade_seconds"
        private const val KEY_CELLULAR_DATA_SAVER = "pref_cellular_data_saver"
        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"

        // Native 5-Band EQ Sound Profile IDs
        const val PROFILE_APPLE_STUDIO = "apple_studio"
        const val PROFILE_BASS_BOOST = "bass_boost"
        const val PROFILE_VOCAL_CLARITY = "vocal_clarity"
        const val PROFILE_LATE_NIGHT = "late_night"
        const val PROFILE_FLAT_BYPASS = "flat_bypass"

        // Clean Widevine-compatible Mobile Chrome User-Agent (strips Android "; wv" token so Apple ID & DRM never reject the WebView)
        const val MOBILE_CHROME_USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

        // Broadcast actions between Activity and MediaPlaybackService
        const val ACTION_MEDIA_COMMAND = "com.musicbeta.wrapper.MEDIA_COMMAND"
        const val EXTRA_COMMAND = "extra_command"
        const val EXTRA_SEEK_POSITION_MS = "extra_seek_position_ms"
        const val COMMAND_PLAY = "command_play"
        const val COMMAND_PAUSE = "command_pause"
        const val COMMAND_NEXT = "command_next"
        const val COMMAND_PREV = "command_prev"
        const val COMMAND_SEEK_TO = "command_seek_to"

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

        // System #1: Crash-Safe Singleton WebView with MutableContextWrapper
        @Volatile
        private var singletonContextWrapper: MutableContextWrapper? = null

        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var singletonWebView: WebView? = null

        @Volatile
        private var isWebViewConfigured: Boolean = false

        // System #4: Guard Against Duplicate Audio DSP Initialization
        @Volatile
        var isAudioEngineInitialized: Boolean = false
            private set

        @Volatile
        private var singletonAudioEngine: AppleSignatureAudioEngine? = null

        /**
         * Crash-safe singleton WebView factory/accessor using MutableContextWrapper(context.applicationContext).
         * Always detaches the WebView from any existing parent ViewGroup BEFORE returning so attaching to a
         * FrameLayout or Compose AndroidView factory NEVER throws IllegalStateException: The specified child already has a parent.
         */
        @Synchronized
        fun obtainDetachedSingletonWebView(context: Context): WebView {
            val wrapper = singletonContextWrapper ?: MutableContextWrapper(context.applicationContext).also {
                singletonContextWrapper = it
            }
            wrapper.baseContext = context

            val webView = singletonWebView ?: WebView(wrapper).also { created ->
                created.id = R.id.webView
                created.layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                singletonWebView = created
            }

            // CRITICAL CRASH GUARD: Always remove from previous parent before returning or re-attaching
            if (webView.parent != null) {
                (webView.parent as? ViewGroup)?.removeView(webView)
            }

            return webView
        }
    }

    private lateinit var rootContainer: FrameLayout
    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var errorLayout: FrameLayout
    private lateinit var insetsController: WindowInsetsControllerCompat
    private lateinit var sharedPreferences: SharedPreferences
    private var appleSignatureAudioEngine: AppleSignatureAudioEngine? = null
    private var oauthPopupWebView: WebView? = null

    // Tracks recent redirect timestamps per normalized URL to prevent infinite redirect loops
    private val redirectHistoryMap = HashMap<String, MutableList<Long>>()

    private var currentRefreshRate: Float = 60.0f
    private var isPlayingAudio: Boolean = false
    private var currentThemeMode: String = THEME_SYSTEM
    private var currentSoundProfile: String = PROFILE_APPLE_STUDIO
    private var crossfadeSeconds: Int = 4
    private var isCellularDataSaverEnabled: Boolean = false
    private var lastTopInsetDp: Int = 0
    private var lastBottomInsetDp: Int = 0
    private var lastLeftInsetDp: Int = 0
    private var lastRightInsetDp: Int = 0

    // Smart Sleep Timer with 10-second gentle volume fade-out
    private val sleepTimerHandler = Handler(Looper.getMainLooper())
    private var sleepTimerRunnable: Runnable? = null
    private var sleepTimerRemainingSeconds: Int = 0

    // Adaptive Network Bitrate Callback (Wi-Fi 256kbps vs Cellular 64kbps Data Saver)
    private var connectivityManager: ConnectivityManager? = null
    private var networkBitrateCallback: ConnectivityManager.NetworkCallback? = null

    // Broadcast receiver for hardware media keys & lock-screen seekbar scrubbing forwarded by MediaPlaybackService
    private val mediaCommandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_MEDIA_COMMAND) {
                when (intent.getStringExtra(EXTRA_COMMAND)) {
                    COMMAND_PLAY -> executeJavaScript("dispatchMediaAction('play')")
                    COMMAND_PAUSE -> executeJavaScript("dispatchMediaAction('pause')")
                    COMMAND_NEXT -> executeJavaScript("dispatchMediaAction('next')")
                    COMMAND_PREV -> executeJavaScript("dispatchMediaAction('previous')")
                    COMMAND_SEEK_TO -> {
                        val posMs = intent.getLongExtra(EXTRA_SEEK_POSITION_MS, 0L)
                        seekToPositionMs(posMs)
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        currentThemeMode = sharedPreferences.getString(KEY_THEME_MODE, THEME_SYSTEM) ?: THEME_SYSTEM
        currentSoundProfile = sharedPreferences.getString(KEY_SOUND_PROFILE, PROFILE_APPLE_STUDIO) ?: PROFILE_APPLE_STUDIO
        crossfadeSeconds = sharedPreferences.getInt(KEY_CROSSFADE_SECONDS, 4)
        isCellularDataSaverEnabled = sharedPreferences.getBoolean(KEY_CELLULAR_DATA_SAVER, false)

        // Apply saved theme before view inflation
        applyAppCompatNightMode(currentThemeMode)

        super.onCreate(savedInstanceState)

        // 1. Force High Refresh Rate (120 Hz+)
        enforceMaxDisplayRefreshRate()

        // 2. Setup True Edge-to-Edge display with dynamic status & nav bar tinting
        setupEdgeToEdge()

        setContentView(R.layout.activity_main)

        rootContainer = findViewById(R.id.rootContainer)
        progressBar = findViewById(R.id.progressBar)
        errorLayout = findViewById(R.id.errorLayout)

        // 3. Attach Crash-Safe Singleton WebView (Prevent IllegalStateException)
        attachOrRecoverWebView()

        // Setup Safe Area Inset Handling to resolve Status Bar & Camera Notch Overlap
        setupWindowInsetsHandling()

        // Request POST_NOTIFICATIONS on Android 13+ (API 33+) for foreground media notification
        checkNotificationPermission()

        // 4. Initialize Native "Apple Signature Sound" DynamicsProcessing DSP (Session 0) & Android 14+ Bit-Perfect USB DAC Engine
        // Guarded by isAudioEngineInitialized and full try/catch (Throwable) blocks so UI re-attachment never duplicates effects
        initSingletonAudioEngineSafely()
        appleSignatureAudioEngine?.applySoundProfile(currentSoundProfile)

        // 5. Register Adaptive Network Bitrate Monitor (256kbps Wi-Fi vs 64kbps Cellular Data Saver)
        registerAdaptiveNetworkBitrateCallback()

        // 6. Setup Back-press handling to navigate WebView history without reload
        setupBackNavigation()

        // 7. Register Media Command Receiver
        val filter = IntentFilter(ACTION_MEDIA_COMMAND)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(mediaCommandReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(mediaCommandReceiver, filter)
        }

        // 8. Zero-Reload Persistence & Share Sheet Universal Song Link Resolution:
        // Only call webView.loadUrl(...) if webView.url == null or if an explicit deep-link/share intent arrived
        val targetUrl = resolveIncomingOrSharedUrl(intent)
        if (webView.url == null) {
            webView.loadUrl(targetUrl)
        } else if (targetUrl != APPLE_MUSIC_BETA_URL && targetUrl != webView.url) {
            webView.loadUrl(targetUrl)
        } else {
            progressBar.visibility = View.GONE
            injectSafeAreaInsetsScript()
            injectUiAndMenuFixes()
        }
    }

    /**
     * Attaches the singleton WebView to rootContainer safely, removing any XML placeholder WebView
     * and ensuring webView.parent is cleared before addView so IllegalStateException can never occur.
     */
    private fun attachOrRecoverWebView() {
        val xmlPlaceholder = rootContainer.findViewById<View>(R.id.webView)
        if (xmlPlaceholder != null && xmlPlaceholder !== singletonWebView) {
            (xmlPlaceholder.parent as? ViewGroup)?.removeView(xmlPlaceholder)
        }

        webView = obtainDetachedSingletonWebView(this)

        // CRITICAL CRASH GUARD: Ensure webView has no parent before adding to rootContainer
        if (webView.parent != null) {
            (webView.parent as? ViewGroup)?.removeView(webView)
        }

        rootContainer.addView(
            webView,
            0,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        if (!isWebViewConfigured) {
            configureHighPerformanceWebView()
            isWebViewConfigured = true
        } else {
            // Re-bind callbacks, third-party cookies, and theme to current Activity instance without reloading the page
            configurePersistentCookies(webView)
            setupWebViewClients()
            applyWebViewTheme(currentThemeMode)
            applyRendererPriorityPolicySafely()
        }
    }

    /**
     * System #4: Guard Against Duplicate Audio DSP Initialization.
     * Uses isAudioEngineInitialized boolean flag and full try/catch (Throwable) guards
     * so re-attaching UI never creates duplicate DynamicsProcessing instances or crashes on Session 0.
     */
    private fun initSingletonAudioEngineSafely() {
        try {
            if (!isAudioEngineInitialized || singletonAudioEngine == null) {
                val engine = singletonAudioEngine ?: AppleSignatureAudioEngine(applicationContext).also {
                    singletonAudioEngine = it
                }
                engine.start()
                isAudioEngineInitialized = true
            }
            appleSignatureAudioEngine = singletonAudioEngine
        } catch (t: Throwable) {
            Log.w(TAG, "Audio DSP initialization guarded against failure on Session 0", t)
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
                if (!root) return;
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

    /**
     * Requirement 1: Cookie Persistence & Cross-Domain Auth Configuration.
     * Enables first-party and third-party cookies so authentication tokens (media-user-token, myacinfo, itspod)
     * issued by idmsa.apple.com / appleid.apple.com / buy.itunes.apple.com persist across redirects to
     * music.apple.com and beta.music.apple.com without dropping the session.
     */
    private fun configurePersistentCookies(targetWebView: WebView) {
        try {
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(targetWebView, true)
            CookieManager.setAcceptFileSchemeCookies(false)
            cookieManager.flush()
        } catch (t: Throwable) {
            Log.w(TAG, "Error configuring CookieManager for cross-domain auth", t)
        }
    }

    /**
     * Flushes in-memory Chromium cookies to persistent SQLite disk storage immediately.
     */
    private fun flushCookiesSafely() {
        try {
            CookieManager.getInstance().flush()
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to flush CookieManager to disk", t)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureHighPerformanceWebView() {
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        webView.isVerticalScrollBarEnabled = false
        webView.isHorizontalScrollBarEnabled = false

        val settings = webView.settings

        // 1. Cookie Persistence & Cross-Domain Auth (idmsa.apple.com <-> beta.music.apple.com)
        configurePersistentCookies(webView)

        // 2. WebSettings Configuration: JS, DOM Storage, Database, & Viewport Zoom Controls
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        @Suppress("DEPRECATION")
        settings.databaseEnabled = true

        // Enable responsive viewport & pinch-to-zoom controls while hiding legacy on-screen zoom buttons
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL

        // 3. Media & DRM Playback + OAuth Multi-Window Support
        // Allow autoplay without user gesture and allow Apple ID OAuth sign-in popups
        settings.mediaPlaybackRequiresUserGesture = false
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.setSupportMultipleWindows(true)
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        settings.loadsImagesAutomatically = true
        settings.blockNetworkImage = false
        settings.allowFileAccess = false
        settings.allowContentAccess = false

        // 4. Built-In Chromium Cache (No external OkHttp interceptors that could strip Cookie headers or break CORS)
        settings.cacheMode = WebSettings.LOAD_DEFAULT

        val customCacheDir = File(applicationContext.cacheDir, "music_beta_webview_cache")
        if (!customCacheDir.exists()) {
            customCacheDir.mkdirs()
        }
        manageCacheQuota(customCacheDir, DISK_CACHE_MAX_BYTES)

        @Suppress("DEPRECATION")
        settings.setRenderPriority(WebSettings.RenderPriority.HIGH)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            settings.offscreenPreRaster = true
        }

        // Background Renderer Priority Policy (API 26+) wrapped in try/catch
        applyRendererPriorityPolicySafely()

        // 5. Clean Widevine-compatible Chrome User-Agent (removes "; wv" token so Apple ID & MusicKit DRM never block the client)
        val defaultUa = WebSettings.getDefaultUserAgent(applicationContext)
            .replace("; wv", "")
            .replace(Regex("Version/\\d+\\.\\d+\\s*"), "")
        settings.userAgentString = if (defaultUa.contains("Chrome/")) defaultUa else MOBILE_CHROME_USER_AGENT

        // 6. Light/Dark Mode configuration on WebView
        applyWebViewTheme(currentThemeMode)

        // 7. JavaScript Interface for Media Session & Background Sync
        try {
            webView.removeJavascriptInterface("AndroidBridge")
        } catch (_: Throwable) {}
        webView.addJavascriptInterface(MusicBridge(), "AndroidBridge")

        // 8. Custom WebViewClient & WebChromeClient (Auth Redirects, Widevine DRM & Renderer Recovery)
        setupWebViewClients()
    }

    /**
     * System #3: Keep Chromium renderer alive in the background on Android O+ (API 26+)
     * wrapped inside a try/catch block for guaranteed crash safety.
     */
    private fun applyRendererPriorityPolicySafely() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                webView.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false)
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to set WebView renderer priority policy", t)
            }
        }
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
                if (document.documentElement) {
                    document.documentElement.style.colorScheme = scheme;
                }
                if (document.head) {
                    let meta = document.querySelector('meta[name="color-scheme"]');
                    if (!meta) {
                        meta = document.createElement('meta');
                        meta.name = 'color-scheme';
                        document.head.appendChild(meta);
                    }
                    meta.content = scheme;
                }
            })();
        """.trimIndent()
        webView.evaluateJavascript(themeScript, null)
    }

    /**
     * Detects rapid identical redirects within REDIRECT_LOOP_WINDOW_MS so the WebView never gets
     * stuck in an infinite auth redirect loop.
     */
    private fun isRedirectLoopDetected(url: String): Boolean {
        val now = System.currentTimeMillis()
        val normalizedKey = url.substringBefore("#")
        val timestamps = redirectHistoryMap.getOrPut(normalizedKey) { mutableListOf() }
        timestamps.removeAll { now - it > REDIRECT_LOOP_WINDOW_MS }
        timestamps.add(now)
        return timestamps.size > MAX_RAPID_REDIRECTS
    }

    private fun setupWebViewClients() {
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                progressBar.visibility = View.VISIBLE
                errorLayout.visibility = View.GONE
                flushCookiesSafely()
            }

            /**
             * Flush cookies on every SPA state push and cross-domain redirect (e.g. idmsa.apple.com -> beta.music.apple.com)
             * so auth tokens are written to disk before the next route reads them.
             */
            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                super.doUpdateVisitedHistory(view, url, isReload)
                flushCookiesSafely()
            }

            override fun onPageCommitVisible(view: WebView?, url: String?) {
                super.onPageCommitVisible(view, url)
                flushCookiesSafely()
                injectSafeAreaInsetsScript()
                injectUiAndMenuFixes()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.visibility = View.GONE

                // Inject <link rel="preconnect"> tags for low-latency Apple Music API, streaming audio & artwork CDN
                val preconnectScript = """
                    (function() {
                        try {
                            var head = document.head || document.getElementsByTagName('head')[0];
                            if (!head) return;
                            var origins = [
                                'https://amp-api.music.apple.com',
                                'https://aod-ssl.itunes.apple.com',
                                'https://is1-ssl.mzstatic.com'
                            ];
                            for (var i = 0; i < origins.length; i++) {
                                var href = origins[i];
                                if (!head.querySelector('link[rel="preconnect"][href="' + href + '"]')) {
                                    var link = document.createElement('link');
                                    link.rel = 'preconnect';
                                    link.href = href;
                                    link.crossOrigin = 'anonymous';
                                    head.appendChild(link);
                                }
                            }
                        } catch (e) {}
                    })();
                """.trimIndent()
                view?.evaluateJavascript(preconnectScript, null)

                // Requirement 1: Flush session & MusicKit auth cookies to persistent storage upon page finish
                flushCookiesSafely()

                injectSafeAreaInsetsScript()
                injectUiAndMenuFixes()
                injectAppleSignatureWebAudioScript()
                injectAudioBridgeScript()
            }

            /**
             * Requirement 4: Handle HTTP authentication requests without stripping existing session cookies.
             */
            override fun onReceivedHttpAuthRequest(
                view: WebView?,
                handler: HttpAuthHandler?,
                host: String?,
                realm: String?
            ) {
                flushCookiesSafely()
                if (view != null && handler != null && handler.useHttpAuthUsernamePassword() && host != null && realm != null) {
                    @Suppress("DEPRECATION")
                    val credentials = view.getHttpAuthUsernamePassword(host, realm)
                    if (credentials != null && credentials.size == 2) {
                        handler.proceed(credentials[0], credentials[1])
                        return
                    }
                }
                super.onReceivedHttpAuthRequest(view, handler, host, realm)
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

                // Let Chromium's native HTTP/2 disk cache (LOAD_DEFAULT) handle all network requests natively
                return super.shouldInterceptRequest(view, request)
            }

            override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                super.onReceivedError(view, errorCode, description, failingUrl)
                if (errorCode == ERROR_HOST_LOOKUP || errorCode == ERROR_CONNECT) {
                    errorLayout.visibility = View.VISIBLE
                }
            }

            /**
             * Requirement 4: Cookie-Preserving Redirect Handling & Loop Prevention.
             * - Blocks custom native app deep-link schemes (music://, itms://, itms-apps://, intent://) so the OS
             *   never hijacks authentication into an external app.
             * - Allows all standard HTTPS authentication and media domains (idmsa.apple.com, appleid.apple.com,
             *   buy.itunes.apple.com, itunes.apple.com, music.apple.com, beta.music.apple.com) to load natively
             *   by returning false. Never calls view.loadUrl() mid-redirect, which would abort the in-flight HTTP 302
             *   response and strip Set-Cookie headers.
             */
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return false
                val url = uri.toString()
                val scheme = uri.scheme?.lowercase() ?: ""

                // 1. Block non-web native app deep links ("Open in Music" / App Store intents)
                if (scheme == "music" ||
                    scheme == "itms" ||
                    scheme == "itms-apps" ||
                    scheme == "itms-appss" ||
                    scheme == "intent" ||
                    scheme == "market"
                ) {
                    Log.i(TAG, "Blocked external deep-link scheme to preserve WebView session: $url")
                    return true
                }

                // Only allow http/https navigation inside the WebView
                if (scheme != "https" && scheme != "http") {
                    return true
                }

                // 2. Prevent infinite redirect loops while preserving cookies
                if (request.isRedirect && isRedirectLoopDetected(url)) {
                    Log.w(TAG, "Redirect loop detected and halted for URL: $url")
                    flushCookiesSafely()
                    view?.stopLoading()
                    return true
                }

                // 3. Flush cookies across domain transitions (e.g., idmsa.apple.com -> beta.music.apple.com)
                flushCookiesSafely()

                // Return false so WebView follows redirects natively with all Set-Cookie & Authorization headers intact
                return false
            }

            /**
             * System #2: Safely recover if the Chromium renderer process is killed by low memory
             * instead of letting the application crash.
             */
            override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                Log.w(TAG, "WebView onRenderProcessGone triggered; recovering WebView safely without app crash")
                try {
                    if (view != null) {
                        if (view.parent != null) {
                            (view.parent as? ViewGroup)?.removeView(view)
                        }
                        view.destroy()
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "Error destroying terminated WebView instance", t)
                }

                singletonWebView = null
                isWebViewConfigured = false

                runOnUiThread {
                    try {
                        attachOrRecoverWebView()
                        if (webView.url == null) {
                            webView.loadUrl(APPLE_MUSIC_BETA_URL)
                        }
                    } catch (t: Throwable) {
                        Log.e(TAG, "Failed to re-attach recovered WebView", t)
                    }
                }
                return true
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
             * Requirement 3: Enable Widevine DRM Audio Playback.
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

            /**
             * Requirement 1 & 3: Support OAuth Sign-In Popup Windows (idmsa.apple.com / appleid.apple.com).
             * Apple Music Web Player opens its Apple ID sign-in flow in a secondary window via window.open().
             * Hosting this child WebView in-app with third-party cookies enabled ensures the 2FA token and
             * media-user-token are shared with the main WebView and posted back via window.opener.
             */
            @SuppressLint("SetJavaScriptEnabled")
            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?
            ): Boolean {
                val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                val context = view?.context ?: this@MainActivity

                // Clean up any existing OAuth popup before creating a new one
                closeOAuthPopupWebView()

                val popupWebView = WebView(context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    @Suppress("DEPRECATION")
                    settings.databaseEnabled = true
                    settings.javaScriptCanOpenWindowsAutomatically = true
                    settings.setSupportMultipleWindows(true)
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    settings.setSupportZoom(true)
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    settings.userAgentString = webView.settings.userAgentString
                }

                // Share cookies & third-party cookies between OAuth popup and primary WebView
                configurePersistentCookies(popupWebView)

                popupWebView.webViewClient = object : WebViewClient() {
                    override fun onPageStarted(v: WebView?, url: String?, favicon: Bitmap?) {
                        super.onPageStarted(v, url, favicon)
                        flushCookiesSafely()
                    }

                    override fun doUpdateVisitedHistory(v: WebView?, url: String?, isReload: Boolean) {
                        super.doUpdateVisitedHistory(v, url, isReload)
                        flushCookiesSafely()
                    }

                    override fun onPageFinished(v: WebView?, url: String?) {
                        super.onPageFinished(v, url)
                        flushCookiesSafely()
                    }

                    override fun shouldOverrideUrlLoading(v: WebView?, req: WebResourceRequest?): Boolean {
                        val scheme = req?.url?.scheme?.lowercase() ?: ""
                        if (scheme != "https" && scheme != "http") {
                            return true
                        }
                        flushCookiesSafely()
                        return false
                    }
                }

                popupWebView.webChromeClient = object : WebChromeClient() {
                    override fun onCloseWindow(window: WebView?) {
                        super.onCloseWindow(window)
                        flushCookiesSafely()
                        closeOAuthPopupWebView()
                    }
                }

                oauthPopupWebView = popupWebView
                rootContainer.addView(popupWebView)

                transport.webView = popupWebView
                resultMsg.sendToTarget()
                return true
            }

            override fun onCloseWindow(window: WebView?) {
                super.onCloseWindow(window)
                flushCookiesSafely()
                closeOAuthPopupWebView()
            }

            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                Log.d(TAG, "WebView Console: ${consoleMessage?.message()} (line ${consoleMessage?.lineNumber()})")
                return true
            }
        }
    }

    private fun closeOAuthPopupWebView() {
        runOnUiThread {
            try {
                oauthPopupWebView?.let { popup ->
                    flushCookiesSafely()
                    (popup.parent as? ViewGroup)?.removeView(popup)
                    popup.stopLoading()
                    popup.destroy()
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Error closing OAuth popup WebView", t)
            } finally {
                oauthPopupWebView = null
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
                    '.upsell-banner, [data-testid="upsell-banner"], [data-testid*="exit-beta"], [class*="upsell"], [class*="smart-banner"], .native-upsell, a[href^="music://"], a[href^="itms://"], a[href^="itms-apps://"], a[href*="exit-beta"], button[aria-label*="Open in Music"], button[aria-label*="Exit Beta"] {',
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
     * System #2: MusicKit JS High-Bitrate (256kbps AAC), Crossfade Engine & Web Audio 32-Bit Float Mastering Injection.
     * - Sets localStorage['music.ampwebplay.bitrate'] = '256' and polls window.MusicKit to enforce
     *   music.bitrate = MusicKit.PlaybackBitrate.HIGH (256kbps AAC) or adaptive 64kbps when Cellular Data Saver is active.
     * - Injects configurable gapless/crossfade ramp engine (0s-12s) and Sleep Timer fade multiplier hook.
     * - Safely attempts to attach a 32-bit float Web Audio API mastering chain (65Hz low-shelf +1.8dB warmth,
     *   13.5kHz high-shelf +2.0dB air, soft-knee DynamicsCompressor) to <audio> elements inside a try/catch
     *   so if Widevine EME blocks createMediaElementSource CORS, playback continues uninterrupted via the
     *   native Android DynamicsProcessing engine on Session 0.
     */
    private fun injectAppleSignatureWebAudioScript() {
        val initialCrossfade = crossfadeSeconds
        val script = """
            (function() {
                window.__musicBetaCrossfadeSeconds = $initialCrossfade;
                window.__musicBetaSleepFadeMultiplier = 1.0;

                if (window.__appleSignatureAudioInjected) return;
                window.__appleSignatureAudioInjected = true;

                // 2a. Force 256kbps High-Efficiency AAC in localStorage & MusicKit JS
                try {
                    localStorage.setItem('music.ampwebplay.bitrate', '256');
                } catch (storageErr) {}

                window.__setMusicKitAdaptiveBitrate = function(targetKbps) {
                    try {
                        localStorage.setItem('music.ampwebplay.bitrate', String(targetKbps));
                        if (window.MusicKit && typeof window.MusicKit.getInstance === 'function') {
                            var music = window.MusicKit.getInstance();
                            if (music) {
                                if (targetKbps >= 256) {
                                    music.bitrate = (window.MusicKit.PlaybackBitrate && window.MusicKit.PlaybackBitrate.HIGH !== undefined)
                                        ? window.MusicKit.PlaybackBitrate.HIGH : 256;
                                } else {
                                    music.bitrate = (window.MusicKit.PlaybackBitrate && window.MusicKit.PlaybackBitrate.STANDARD !== undefined)
                                        ? window.MusicKit.PlaybackBitrate.STANDARD : 64;
                                }
                            }
                        }
                    } catch (_) {}
                };

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
                                        try {
                                            if (localStorage.getItem('music.ampwebplay.bitrate') !== '64') {
                                                music.bitrate = highValue;
                                            }
                                        } catch (_) {}
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

                // 2b. Smooth Crossfade & Sleep Timer Volume Envelope Controller
                window.__applySleepTimerFade = function(multiplier) {
                    window.__musicBetaSleepFadeMultiplier = Math.max(0, Math.min(1, multiplier));
                    var audios = document.querySelectorAll('audio');
                    for (var i = 0; i < audios.length; i++) {
                        try {
                            audios[i].volume = window.__musicBetaSleepFadeMultiplier;
                        } catch (_) {}
                    }
                };

                // 2c. Resilient 32-Bit Float Web Audio Mastering Graph (with EME/CORS fallback to Native Session 0 DSP)
                var audioCtx = null;
                function attachWebAudioMastering(audioEl) {
                    if (!audioEl || audioEl.__appleSignatureAttached) return;
                    audioEl.__appleSignatureAttached = true;

                    // Attach smooth crossfade envelope listener on timeupdate
                    audioEl.addEventListener('timeupdate', function() {
                        try {
                            var cfSec = Number(window.__musicBetaCrossfadeSeconds || 0);
                            var sleepMult = Number(window.__musicBetaSleepFadeMultiplier !== undefined ? window.__musicBetaSleepFadeMultiplier : 1.0);
                            if (cfSec > 0 && audioEl.duration > cfSec * 2) {
                                var remaining = audioEl.duration - audioEl.currentTime;
                                var cfMult = 1.0;
                                if (remaining > 0 && remaining <= cfSec) {
                                    cfMult = Math.max(0.15, remaining / cfSec);
                                } else if (audioEl.currentTime <= cfSec * 0.5) {
                                    cfMult = Math.min(1.0, 0.35 + (audioEl.currentTime / (cfSec * 0.5)) * 0.65);
                                }
                                audioEl.volume = Math.max(0, Math.min(1, cfMult * sleepMult));
                            } else {
                                audioEl.volume = Math.max(0, Math.min(1, sleepMult));
                            }
                        } catch (_) {}
                    });

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
                var lastLyricsVisible = false;

                window.dispatchMediaAction = function(action, valueMs) {
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
                        } else if (action === 'seekTo' && typeof valueMs === 'number') {
                            var targetSec = Math.max(0, valueMs / 1000);
                            if (window.MusicKit && typeof window.MusicKit.getInstance === 'function') {
                                var mk = window.MusicKit.getInstance();
                                if (mk && typeof mk.seekToTime === 'function') {
                                    mk.seekToTime(targetSec);
                                    return;
                                }
                            }
                            var audioEl = document.querySelector('audio');
                            if (audioEl) {
                                audioEl.currentTime = targetSec;
                            }
                        }
                    } catch(e) {
                        console.error('Error dispatching media action:', e);
                    }
                };

                // Mini-Player Horizontal Swipe Left/Right Gestures with Native Android Haptic Feedback
                var touchStartX = 0;
                var touchStartY = 0;
                document.addEventListener('touchstart', function(e) {
                    if (!e.touches || e.touches.length === 0) return;
                    var target = e.target;
                    var inMiniPlayer = target && target.closest && target.closest(
                        '.web-chrome-playback-controls, [data-testid="web-chrome-playback-controls"], .mini-player, .chrome-player'
                    );
                    if (inMiniPlayer) {
                        touchStartX = e.touches[0].clientX;
                        touchStartY = e.touches[0].clientY;
                    } else {
                        touchStartX = 0;
                        touchStartY = 0;
                    }
                }, { passive: true });

                document.addEventListener('touchend', function(e) {
                    if (!touchStartX || !e.changedTouches || e.changedTouches.length === 0) return;
                    var dx = e.changedTouches[0].clientX - touchStartX;
                    var dy = e.changedTouches[0].clientY - touchStartY;
                    touchStartX = 0;
                    touchStartY = 0;
                    if (Math.abs(dx) > 64 && Math.abs(dy) < 42) {
                        if (window.AndroidBridge && typeof window.AndroidBridge.triggerHapticFeedback === 'function') {
                            window.AndroidBridge.triggerHapticFeedback();
                        }
                        if (dx < 0) {
                            window.dispatchMediaAction('next');
                        } else {
                            window.dispatchMediaAction('previous');
                        }
                    }
                }, { passive: true });

                const getPlaybackTiming = function() {
                    var posMs = 0;
                    var durMs = 0;
                    try {
                        var audioEl = document.querySelector('audio');
                        if (audioEl) {
                            posMs = Math.round((audioEl.currentTime || 0) * 1000);
                            durMs = Math.round((audioEl.duration || 0) * 1000);
                        }
                    } catch (_) {}
                    return { posMs: isFinite(posMs) ? posMs : 0, durMs: isFinite(durMs) ? durMs : 0 };
                };

                const notifyStateChange = function(playing) {
                    let title = "Apple Music Beta";
                    let artist = "Now Playing";
                    let artwork = "";

                    if (navigator.mediaSession && navigator.mediaSession.metadata) {
                        title = navigator.mediaSession.metadata.title || title;
                        artist = navigator.mediaSession.metadata.artist || artist;
                        if (navigator.mediaSession.metadata.artwork && navigator.mediaSession.metadata.artwork.length > 0) {
                            artwork = navigator.mediaSession.metadata.artwork[navigator.mediaSession.metadata.artwork.length - 1].src || "";
                        }
                    }

                    const timing = getPlaybackTiming();
                    if (window.AndroidBridge) {
                        window.AndroidBridge.onPlaybackStateChanged(playing, title, artist, artwork);
                        if (typeof window.AndroidBridge.onPlaybackProgressUpdated === 'function') {
                            window.AndroidBridge.onPlaybackProgressUpdated(playing, title, artist, artwork, timing.posMs, timing.durMs);
                        }
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
                            const art = (meta.artwork && meta.artwork.length > 0)
                                ? meta.artwork[meta.artwork.length - 1].src
                                : '';
                            const timing = getPlaybackTiming();
                            const signature = title + '|' + artist + '|' + art + '|' + isPlaying;
                            if (signature !== lastMetaSignature) {
                                lastMetaSignature = signature;
                                if (window.AndroidBridge) {
                                    window.AndroidBridge.onMetadataUpdated(title, artist, art, isPlaying);
                                }
                            }
                            if (isPlaying && window.AndroidBridge && typeof window.AndroidBridge.onPlaybackProgressUpdated === 'function') {
                                window.AndroidBridge.onPlaybackProgressUpdated(isPlaying, title, artist, art, timing.posMs, timing.durMs);
                            }
                        }

                        // Detect Time-Synced Lyrics Panel & keep screen awake while reading lyrics
                        try {
                            var lyricsNode = document.querySelector('[data-testid*="lyrics"], .lyrics__container, .web-chrome-lyrics');
                            var isLyricsOpen = !!(lyricsNode && lyricsNode.offsetParent !== null);
                            if (isLyricsOpen !== lastLyricsVisible) {
                                lastLyricsVisible = isLyricsOpen;
                                if (window.AndroidBridge && typeof window.AndroidBridge.onLyricsVisibilityChanged === 'function') {
                                    window.AndroidBridge.onLyricsVisibilityChanged(isLyricsOpen);
                                }
                            }
                        } catch (_) {}
                    }, 2000);
                }
            })();
        """.trimIndent()

        webView.evaluateJavascript(script, null)
    }

    private fun executeJavaScript(code: String) {
        runOnUiThread {
            try {
                webView.evaluateJavascript(code, null)
            } catch (_: Throwable) {}
        }
    }

    /**
     * Interactive Native 5-Band EQ Sound Profile Switcher.
     * Applies one of the 5 studio curves (apple_studio, bass_boost, vocal_clarity, late_night, flat_bypass)
     * directly to Session 0 DynamicsProcessing without interrupting playback.
     */
    fun applySoundProfilePreset(profileId: String) {
        currentSoundProfile = profileId
        sharedPreferences.edit().putString(KEY_SOUND_PROFILE, profileId).apply()
        appleSignatureAudioEngine?.applySoundProfile(profileId)
    }

    /**
     * Custom 5-Band Equalizer & Preamp Gain adjustment on Session 0 DynamicsProcessing.
     */
    fun setCustomEqBands(bandGainsDb: FloatArray, preampDb: Float = 0.0f) {
        appleSignatureAudioEngine?.setCustom5BandEq(bandGainsDb, preampDb)
    }

    /**
     * Updates the Web Audio / MusicKit crossfade duration (0s-12s) and persists it.
     */
    fun setCrossfadeDuration(seconds: Int) {
        val clamped = seconds.coerceIn(0, 12)
        crossfadeSeconds = clamped
        sharedPreferences.edit().putInt(KEY_CROSSFADE_SECONDS, clamped).apply()
        executeJavaScript("window.__musicBetaCrossfadeSeconds = $clamped;")
    }

    /**
     * Toggles Automatic Cellular Data Saver (64kbps HE-AAC on Cellular, 256kbps AAC on Wi-Fi).
     */
    fun setCellularDataSaverEnabled(enabled: Boolean) {
        isCellularDataSaverEnabled = enabled
        sharedPreferences.edit().putBoolean(KEY_CELLULAR_DATA_SAVER, enabled).apply()
        evaluateAndApplyNetworkBitrate()
    }

    private fun registerAdaptiveNetworkBitrateCallback() {
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
            connectivityManager = cm
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    super.onCapabilitiesChanged(network, networkCapabilities)
                    evaluateAndApplyNetworkBitrate(networkCapabilities)
                }
            }
            networkBitrateCallback = callback
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                cm.registerDefaultNetworkCallback(callback)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Adaptive network bitrate callback registration guarded", t)
        }
    }

    private fun evaluateAndApplyNetworkBitrate(caps: NetworkCapabilities? = null) {
        try {
            val cm = connectivityManager ?: (getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager)
            val capabilities = caps ?: cm?.activeNetwork?.let { cm.getNetworkCapabilities(it) }
            val isCellular = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true &&
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == false
            val targetKbps = if (isCellular && isCellularDataSaverEnabled) 64 else 256
            executeJavaScript("if (window.__setMusicKitAdaptiveBitrate) window.__setMusicKitAdaptiveBitrate($targetKbps);")
        } catch (_: Throwable) {}
    }

    /**
     * Smart Sleep Timer with 10-Second Gentle Volume Fade-Out.
     * Counts down from durationMinutes and smoothly attenuates playback volume over the final 10 seconds
     * before pausing playback and restoring full volume for the next session.
     */
    fun startSleepTimer(durationMinutes: Int) {
        cancelSleepTimer()
        if (durationMinutes <= 0) return

        sleepTimerRemainingSeconds = durationMinutes * 60
        val runnable = object : Runnable {
            override fun run() {
                if (sleepTimerRemainingSeconds <= 0) {
                    executeJavaScript("if (window.__applySleepTimerFade) window.__applySleepTimerFade(1.0); dispatchMediaAction('pause');")
                    sleepTimerRunnable = null
                    return
                }
                if (sleepTimerRemainingSeconds <= 10) {
                    val fadeMultiplier = sleepTimerRemainingSeconds.toFloat() / 10.0f
                    executeJavaScript("if (window.__applySleepTimerFade) window.__applySleepTimerFade($fadeMultiplier);")
                }
                sleepTimerRemainingSeconds--
                sleepTimerHandler.postDelayed(this, 1_000L)
            }
        }
        sleepTimerRunnable = runnable
        sleepTimerHandler.post(runnable)
    }

    fun cancelSleepTimer() {
        sleepTimerRunnable?.let { sleepTimerHandler.removeCallbacks(it) }
        sleepTimerRunnable = null
        sleepTimerRemainingSeconds = 0
        executeJavaScript("if (window.__applySleepTimerFade) window.__applySleepTimerFade(1.0);")
    }

    /**
     * Scrubs the active Apple Music track to positionMs from the Android Lock-Screen / Notification Seekbar.
     */
    fun seekToPositionMs(positionMs: Long) {
        executeJavaScript("if (window.dispatchMediaAction) window.dispatchMediaAction('seekTo', $positionMs);")
    }

    /**
     * Queries the active hardware output sink (USB-C DAC Bit-Perfect, Bluetooth A2DP, Wired Headset, or Speaker).
     */
    fun getActiveAudioOutputStatus(): String {
        return appleSignatureAudioEngine?.getActiveAudioOutputSinkLabel() ?: "Speaker • Session 0 DSP"
    }

    /**
     * Universal Song Link Resolver for Deep Links & Android Share Sheet (Intent.ACTION_SEND).
     */
    private fun resolveIncomingOrSharedUrl(incomingIntent: Intent?): String {
        if (incomingIntent == null) return APPLE_MUSIC_BETA_URL
        val rawCandidate = when (incomingIntent.action) {
            Intent.ACTION_SEND -> incomingIntent.getStringExtra(Intent.EXTRA_TEXT)
            else -> incomingIntent.dataString
        } ?: return APPLE_MUSIC_BETA_URL

        val urlMatch = Regex("https?://(beta\\.|www\\.)?music\\.apple\\.com[^\\s\"]*", RegexOption.IGNORE_CASE)
            .find(rawCandidate)?.value

        if (!urlMatch.isNullOrBlank()) {
            return urlMatch.replace(
                Regex("^https?://(www\\.)?music\\.apple\\.com", RegexOption.IGNORE_CASE),
                APPLE_MUSIC_BETA_URL
            )
        }
        return APPLE_MUSIC_BETA_URL
    }

    /**
     * Launches Android's native Share Sheet with a clean universal Apple Music link.
     */
    fun shareCurrentTrack(title: String, artist: String, rawUrl: String?) {
        try {
            val cleanUrl = (rawUrl ?: webView.url ?: APPLE_MUSIC_BETA_URL)
                .replace("https://beta.music.apple.com", "https://music.apple.com")
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "$title — $artist")
                putExtra(Intent.EXTRA_TEXT, "Listen to $title by $artist on Apple Music: $cleanUrl")
            }
            startActivity(Intent.createChooser(shareIntent, "Share Song"))
        } catch (t: Throwable) {
            Log.w(TAG, "Error launching native share sheet", t)
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

        @JavascriptInterface
        fun onPlaybackProgressUpdated(
            isPlaying: Boolean,
            title: String,
            artist: String,
            artworkUrl: String,
            positionMs: Long,
            durationMs: Long
        ) {
            runOnUiThread {
                if (isPlaying || this@MainActivity.isPlayingAudio) {
                    val serviceIntent = Intent(this@MainActivity, MediaPlaybackService::class.java).apply {
                        action = MediaPlaybackService.ACTION_UPDATE_METADATA
                        putExtra(MediaPlaybackService.EXTRA_TITLE, title)
                        putExtra(MediaPlaybackService.EXTRA_ARTIST, artist)
                        putExtra(MediaPlaybackService.EXTRA_ARTWORK_URL, artworkUrl)
                        putExtra(MediaPlaybackService.EXTRA_POSITION_MS, positionMs)
                        putExtra(MediaPlaybackService.EXTRA_DURATION_MS, durationMs)
                    }
                    ContextCompat.startForegroundService(this@MainActivity, serviceIntent)
                }
            }
        }

        @JavascriptInterface
        fun triggerHapticFeedback() {
            runOnUiThread {
                try {
                    window.decorView.performHapticFeedback(
                        HapticFeedbackConstants.CONFIRM,
                        HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                    )
                } catch (_: Throwable) {}
            }
        }

        @JavascriptInterface
        fun onLyricsVisibilityChanged(isVisible: Boolean) {
            runOnUiThread {
                try {
                    if (isVisible) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                } catch (_: Throwable) {}
            }
        }

        @JavascriptInterface
        fun shareTrack(title: String, artist: String, url: String) {
            runOnUiThread {
                shareCurrentTrack(title, artist, url)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val targetUrl = resolveIncomingOrSharedUrl(intent)
        if (targetUrl != APPLE_MUSIC_BETA_URL && targetUrl != webView.url) {
            webView.loadUrl(targetUrl)
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
        try {
            CookieManager.getInstance().flush()
        } catch (_: Throwable) {}
        Log.d(TAG, "onPause called: Keeping audio pipeline & WebView renderer alive for background playback")
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "onStop called: WebView audio preserved for screen-off playback")
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        try {
            webView.saveState(outState)
        } catch (_: Throwable) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(mediaCommandReceiver)
        } catch (_: Throwable) {}

        try {
            networkBitrateCallback?.let { cb ->
                connectivityManager?.unregisterNetworkCallback(cb)
            }
        } catch (_: Throwable) {}
        networkBitrateCallback = null

        // Detach Singleton WebView from Activity container and rebind MutableContextWrapper to applicationContext
        // so the WebView survives UI recompositions/recreations without leaking the Activity or throwing IllegalStateException
        try {
            if (::webView.isInitialized && webView.parent != null) {
                (webView.parent as? ViewGroup)?.removeView(webView)
            }
            singletonContextWrapper?.baseContext = applicationContext
        } catch (t: Throwable) {
            Log.w(TAG, "Error detaching singleton WebView in onDestroy", t)
        }

        // Only release global audio DSP if the user is truly finishing the app (not recomposing or rotating)
        if (isFinishing && !isChangingConfigurations && !isPlayingAudio) {
            cancelSleepTimer()
            try {
                singletonAudioEngine?.release()
                singletonAudioEngine = null
                appleSignatureAudioEngine = null
                isAudioEngineInitialized = false
            } catch (_: Throwable) {}
        }
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
        if (webView.url == null) {
            webView.loadUrl(APPLE_MUSIC_BETA_URL)
        } else {
            webView.reload()
        }
    }
}

/**
 * System #3 & #4: AppleSignatureAudioEngine
 * - Native Android DynamicsProcessing Mastering Engine (API 28+) attached to global audio session 0
 *   so hardware-decoded Widevine DRM WebView streams receive full 5-band Pre-EQ/Post-EQ and Anti-Clip Limiter.
 * - Supports interactive 5-Band EQ Sound Profile Presets (Apple Studio, Bass Boost, Vocal Clarity, Late Night, Flat Bypass)
 *   and custom user EQ bands + Preamp Gain.
 * - Android 14+ (API 34+) Bit-Perfect USB DAC / Headset Bypass via AudioManager.registerAudioDeviceCallback
 *   and AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT (44.1kHz PCM Float).
 * - Guarded with isAudioEngineInitialized and full try/catch (Throwable) blocks to prevent duplicate initialization or crashes.
 */
class AppleSignatureAudioEngine(context: Context) {

    companion object {
        private const val TAG = "AppleSignatureAudio"
        private const val GLOBAL_AUDIO_SESSION_ID = 0
        private const val CHANNEL_COUNT = 2
        private const val EQ_BAND_COUNT = 5
        private val BAND_CUTOFFS_HZ = floatArrayOf(80.0f, 250.0f, 1000.0f, 3500.0f, 20000.0f)
    }

    private val appContext: Context = context.applicationContext
    private val audioManager: AudioManager? =
        appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var isAudioEngineInitialized: Boolean = false
    private var dynamicsProcessing: DynamicsProcessing? = null
    private var audioDeviceCallback: AudioDeviceCallback? = null

    private val mediaAudioAttributes: AudioAttributes by lazy {
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
    }

    @Synchronized
    fun start() {
        if (isAudioEngineInitialized) {
            Log.d(TAG, "AppleSignatureAudioEngine already initialized; skipping duplicate Session 0 attachment")
            return
        }
        try {
            initDynamicsProcessingMasteringEngine()
            registerUsbBitPerfectCallback()
            isAudioEngineInitialized = true
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to start AppleSignatureAudioEngine safely", t)
        }
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
        if (dynamicsProcessing != null) return

        try {
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
        } catch (t: Throwable) {
            Log.w(TAG, "DynamicsProcessing Session 0 unavailable on this OEM build: ${t.message}")
        }
    }

    /**
     * Dynamically applies a named 5-Band Sound Profile Preset to the active Session 0 DynamicsProcessing instance.
     */
    fun applySoundProfile(profileId: String) {
        when (profileId) {
            MainActivity.PROFILE_BASS_BOOST ->
                setCustom5BandEq(floatArrayOf(4.5f, 1.0f, 0.0f, 0.8f, 1.8f), preampDb = -1.0f)
            MainActivity.PROFILE_VOCAL_CLARITY ->
                setCustom5BandEq(floatArrayOf(0.5f, -1.2f, 1.2f, 2.6f, 2.0f), preampDb = 0.0f)
            MainActivity.PROFILE_LATE_NIGHT ->
                setCustom5BandEq(floatArrayOf(3.2f, 0.0f, 0.5f, 1.8f, 3.0f), preampDb = 1.5f)
            MainActivity.PROFILE_FLAT_BYPASS ->
                setCustom5BandEq(floatArrayOf(0.0f, 0.0f, 0.0f, 0.0f, 0.0f), preampDb = 0.0f)
            else ->
                setCustom5BandEq(floatArrayOf(2.0f, -0.5f, 0.0f, 1.0f, 2.2f), preampDb = 0.0f)
        }
    }

    /**
     * Applies custom 5-band gains ([80Hz, 250Hz, 1kHz, 3.5kHz, 12kHz+]) and Preamp postGain to Session 0.
     */
    fun setCustom5BandEq(gainsDb: FloatArray, preampDb: Float = 0.0f) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        val dp = dynamicsProcessing ?: return
        if (gainsDb.size < EQ_BAND_COUNT) return

        try {
            for (i in 0 until EQ_BAND_COUNT) {
                val cutoff = BAND_CUTOFFS_HZ[i]
                val preGain = if (i <= 1) gainsDb[i] else 0.0f
                val postGain = if (i >= 2) gainsDb[i] else 0.0f
                dp.setPreEqBandAllChannelsTo(i, DynamicsProcessing.EqBand(true, cutoff, preGain))
                dp.setPostEqBandAllChannelsTo(i, DynamicsProcessing.EqBand(true, cutoff, postGain))
            }
            val limiter = DynamicsProcessing.Limiter(
                true,
                true,
                0,
                2.0f,
                60.0f,
                4.0f,
                -1.0f,
                preampDb.coerceIn(-6.0f, 6.0f)
            )
            dp.setLimiterAllChannelsTo(limiter)
        } catch (t: Throwable) {
            Log.w(TAG, "Error updating custom 5-band EQ on Session 0: ${t.message}")
        }
    }

    /**
     * Returns a human-readable HUD string describing the currently active audio output sink.
     */
    fun getActiveAudioOutputSinkLabel(): String {
        try {
            val devices = audioManager?.getDevices(AudioManager.GET_DEVICES_OUTPUTS) ?: emptyArray()
            for (d in devices) {
                if (isUsbAudioSink(d)) {
                    return "USB-C DAC (${d.productName}) • Bit-Perfect 44.1kHz Float"
                }
                if (d.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || d.type == AudioDeviceInfo.TYPE_BLE_HEADSET) {
                    return "Bluetooth (${d.productName}) • 256kbps AAC + Session 0 DSP"
                }
                if (d.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || d.type == AudioDeviceInfo.TYPE_WIRED_HEADSET) {
                    return "Wired Headphones • 32-Bit Float + Session 0 DSP"
                }
            }
        } catch (_: Throwable) {}
        return "Speaker • Apple Signature Session 0 DSP"
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
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to register AudioDeviceCallback: ${t.message}")
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
        } catch (t: Throwable) {
            Log.w(TAG, "Bit-Perfect USB mixer configuration not supported by DAC: ${t.message}")
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun clearBitPerfectUsbOutput(manager: AudioManager, usbDevice: AudioDeviceInfo) {
        try {
            manager.clearPreferredMixerAttributes(mediaAudioAttributes, usbDevice)
            Log.i(TAG, "Cleared Bit-Perfect mixer attributes for disconnected USB DAC: ${usbDevice.productName}")
        } catch (_: Throwable) {}
    }

    @Synchronized
    fun release() {
        try {
            audioDeviceCallback?.let { cb ->
                audioManager?.unregisterAudioDeviceCallback(cb)
            }
        } catch (_: Throwable) {}
        audioDeviceCallback = null

        try {
            dynamicsProcessing?.setEnabled(false)
            dynamicsProcessing?.release()
        } catch (_: Throwable) {}
        dynamicsProcessing = null
        isAudioEngineInitialized = false
    }
}