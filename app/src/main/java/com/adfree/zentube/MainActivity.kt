package com.adfree.zentube

import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.adfree.zentube.adblock.AdBlockEngine
import com.adfree.zentube.adblock.ZenTubeBridge
import com.adfree.zentube.ui.theme.*

class MainActivity : ComponentActivity() {

    private var webView: WebView? = null
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var isPipModeState = mutableStateOf(false)
    private var blockedAdsCount = mutableIntStateOf(0)
    private var isAdBlockEnabled = mutableStateOf(true)
    private var isAmoledDark = mutableStateOf(true)
    private var isDesktopMode = mutableStateOf(false)

    private val mobileUserAgent: String by lazy {
        WebSettings.getDefaultUserAgent(this)
    }
    private val desktopUserAgent =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val targetUrl = intent?.data?.toString() ?: "https://m.youtube.com"

        setContent {
            ZenTubeTheme {
                MainScreen(initialUrl = targetUrl)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.let { uri ->
            webView?.loadUrl(uri.toString())
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val currentUrl = webView?.url ?: ""
        if (currentUrl.contains("watch") || customView != null) {
            triggerPipMode()
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isPipModeState.value = isInPictureInPictureMode
    }

    private fun triggerPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
                enterPictureInPictureMode(params)
            } catch (e: Exception) {
                // Device may not support aspect ratio
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MainScreen(initialUrl: String) {
        val isInPip by remember { isPipModeState }
        var currentNavRoute by remember { mutableStateOf("home") }
        var searchVisible by remember { mutableStateOf(false) }
        var searchInput by remember { mutableStateOf("") }
        var showSettingsSheet by remember { mutableStateOf(false) }
        var pageProgress by remember { mutableFloatStateOf(0f) }
        var isPageLoading by remember { mutableStateOf(false) }
        var canGoBack by remember { mutableStateOf(false) }
        val focusManager = LocalFocusManager.current

        BackHandler(enabled = !isInPip) {
            if (customView != null) {
                hideCustomView()
            } else if (searchVisible) {
                searchVisible = false
            } else if (webView?.canGoBack() == true) {
                webView?.goBack()
            } else {
                finish()
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (isAmoledDark.value) BlackAmoled else DarkSurface)
        ) {
            Scaffold(
                containerColor = if (isAmoledDark.value) BlackAmoled else DarkSurface,
                topBar = {
                    if (!isInPip && customView == null) {
                        Column {
                            TopAppBar(
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = if (isAmoledDark.value) BlackAmoled else DarkSurface,
                                    titleContentColor = Color.White
                                ),
                                title = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.clickable {
                                            webView?.loadUrl("https://m.youtube.com")
                                            currentNavRoute = "home"
                                        }
                                    ) {
                                        Text(
                                            text = "Zen",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 20.sp,
                                            color = Color.White
                                        )
                                        Surface(
                                            color = RedPrimary,
                                            shape = RoundedCornerShape(4.dp),
                                            modifier = Modifier.padding(start = 2.dp)
                                        ) {
                                            Text(
                                                text = "Tube",
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 14.sp,
                                                color = Color.White,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                },
                                actions = {
                                    // Shield Counter Pill
                                    Surface(
                                        color = ShieldGreenGlow,
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier
                                            .border(1.dp, ShieldGreen, RoundedCornerShape(16.dp))
                                            .clickable { showSettingsSheet = true }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(7.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isAdBlockEnabled.value) ShieldGreen else Color.Gray)
                                            )
                                            Text(
                                                text = "${blockedAdsCount.intValue} ads",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = ShieldGreen
                                            )
                                        }
                                    }

                                    IconButton(onClick = { searchVisible = !searchVisible }) {
                                        Icon(
                                            imageVector = if (searchVisible) Icons.Filled.Close else Icons.Filled.Search,
                                            contentDescription = "Search",
                                            tint = Color.White
                                        )
                                    }

                                    IconButton(onClick = { triggerPipMode() }) {
                                        Icon(
                                            imageVector = Icons.Outlined.PictureInPictureAlt,
                                            contentDescription = "PiP",
                                            tint = Color.White
                                        )
                                    }

                                    IconButton(onClick = { showSettingsSheet = true }) {
                                        Icon(
                                            imageVector = Icons.Filled.MoreVert,
                                            contentDescription = "More",
                                            tint = Color.White
                                        )
                                    }
                                }
                            )

                            // Search bar expand
                            AnimatedVisibility(visible = searchVisible) {
                                Surface(
                                    color = DarkCard,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    shape = RoundedCornerShape(24.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = null,
                                            tint = TextSecondary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        TextField(
                                            value = searchInput,
                                            onValueChange = { searchInput = it },
                                            placeholder = {
                                                Text(
                                                    "Search YouTube or paste URL...",
                                                    color = TextSecondary,
                                                    fontSize = 14.sp
                                                )
                                            },
                                            singleLine = true,
                                            colors = TextFieldDefaults.colors(
                                                focusedContainerColor = Color.Transparent,
                                                unfocusedContainerColor = Color.Transparent,
                                                disabledContainerColor = Color.Transparent,
                                                cursorColor = RedPrimary,
                                                focusedIndicatorColor = Color.Transparent,
                                                unfocusedIndicatorColor = Color.Transparent,
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White
                                            ),
                                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                            keyboardActions = KeyboardActions(onSearch = {
                                                focusManager.clearFocus()
                                                searchVisible = false
                                                val query = searchInput.trim()
                                                if (query.isNotEmpty()) {
                                                    val url = when {
                                                        query.startsWith("http://") || query.startsWith("https://") -> query
                                                        query.contains("youtube.com") || query.contains("youtu.be") -> "https://$query"
                                                        else -> "https://m.youtube.com/results?search_query=" + Uri.encode(query)
                                                    }
                                                    webView?.loadUrl(url)
                                                    searchInput = ""
                                                }
                                            }),
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (searchInput.isNotEmpty()) {
                                            IconButton(
                                                onClick = { searchInput = "" },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Clear,
                                                    contentDescription = "Clear",
                                                    tint = TextSecondary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Loading Bar
                            if (isPageLoading) {
                                LinearProgressIndicator(
                                    progress = { pageProgress },
                                    modifier = Modifier.fillMaxWidth().height(2.dp),
                                    color = RedPrimary,
                                    trackColor = Color.Transparent
                                )
                            }
                        }
                    }
                },
                bottomBar = {
                    if (!isInPip && customView == null) {
                        NavigationBar(
                            containerColor = if (isAmoledDark.value) BlackAmoled else DarkSurface,
                            contentColor = Color.White,
                            tonalElevation = 0.dp
                        ) {
                            NavigationBarItem(
                                selected = currentNavRoute == "home",
                                onClick = {
                                    currentNavRoute = "home"
                                    webView?.loadUrl("https://m.youtube.com")
                                },
                                icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                                label = { Text("Home", fontSize = 11.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = RedPrimary,
                                    selectedTextColor = RedPrimary,
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary,
                                    indicatorColor = Color.Transparent
                                )
                            )
                            NavigationBarItem(
                                selected = currentNavRoute == "trending",
                                onClick = {
                                    currentNavRoute = "trending"
                                    webView?.loadUrl("https://m.youtube.com/feed/trending")
                                },
                                icon = { Icon(Icons.Default.Whatshot, contentDescription = "Trending") },
                                label = { Text("Trending", fontSize = 11.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = RedPrimary,
                                    selectedTextColor = RedPrimary,
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary,
                                    indicatorColor = Color.Transparent
                                )
                            )
                            NavigationBarItem(
                                selected = currentNavRoute == "subs",
                                onClick = {
                                    currentNavRoute = "subs"
                                    webView?.loadUrl("https://m.youtube.com/feed/subscriptions")
                                },
                                icon = { Icon(Icons.Default.Subscriptions, contentDescription = "Subscriptions") },
                                label = { Text("Subs", fontSize = 11.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = RedPrimary,
                                    selectedTextColor = RedPrimary,
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary,
                                    indicatorColor = Color.Transparent
                                )
                            )
                            NavigationBarItem(
                                selected = currentNavRoute == "library",
                                onClick = {
                                    currentNavRoute = "library"
                                    webView?.loadUrl("https://m.youtube.com/feed/library")
                                },
                                icon = { Icon(Icons.Default.VideoLibrary, contentDescription = "Library") },
                                label = { Text("Library", fontSize = 11.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = RedPrimary,
                                    selectedTextColor = RedPrimary,
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary,
                                    indicatorColor = Color.Transparent
                                )
                            )
                            NavigationBarItem(
                                selected = false,
                                onClick = { showSettingsSheet = true },
                                icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                                label = { Text("Settings", fontSize = 11.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = RedPrimary,
                                    selectedTextColor = RedPrimary,
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary,
                                    indicatorColor = Color.Transparent
                                )
                            )
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(if (isInPip || customView != null) PaddingValues(0.dp) else innerPadding)
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                webView = this
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                setupWebViewSettings(this)
                                addJavascriptInterface(
                                    ZenTubeBridge {
                                        runOnUiThread {
                                            blockedAdsCount.intValue++
                                        }
                                    },
                                    "ZenTubeBridge"
                                )
                                webViewClient = object : WebViewClient() {
                                    override fun shouldInterceptRequest(
                                        view: WebView?,
                                        request: WebResourceRequest?
                                    ): WebResourceResponse? {
                                        val url = request?.url ?: return null
                                        if (isAdBlockEnabled.value && AdBlockEngine.isAdRequest(url)) {
                                            runOnUiThread {
                                                blockedAdsCount.intValue++
                                            }
                                            return AdBlockEngine.createEmptyResponse()
                                        }
                                        return super.shouldInterceptRequest(view, request)
                                    }

                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        super.onPageStarted(view, url, favicon)
                                        isPageLoading = true
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        isPageLoading = false
                                        canGoBack = view?.canGoBack() == true
                                        if (isAdBlockEnabled.value) {
                                            view?.evaluateJavascript(AdBlockEngine.ADBLOCK_INJECTION_SCRIPT, null)
                                        }
                                    }
                                }

                                webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                        pageProgress = newProgress / 100f
                                        if (newProgress >= 100) {
                                            isPageLoading = false
                                        }
                                    }

                                    override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                                        showCustomView(view, callback)
                                    }

                                    override fun onHideCustomView() {
                                        hideCustomView()
                                    }
                                }

                                loadUrl(initialUrl)
                            }
                        }
                    )
                }
            }

            // Modern Settings Bottom Sheet
            if (showSettingsSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showSettingsSheet = false },
                    containerColor = DarkCard,
                    contentColor = Color.White
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "ZenTube Control Center",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        // AdBlock Switch
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Ad & Tracker Shield", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Blocks video ads, promotional shelves & trackers",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }
                            Switch(
                                checked = isAdBlockEnabled.value,
                                onCheckedChange = {
                                    isAdBlockEnabled.value = it
                                    webView?.reload()
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = ShieldGreen
                                )
                            )
                        }

                        // AMOLED Mode
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("AMOLED Pure Black", fontWeight = FontWeight.SemiBold)
                                Text("Ultra dark background to save battery", fontSize = 12.sp, color = TextSecondary)
                            }
                            Switch(
                                checked = isAmoledDark.value,
                                onCheckedChange = {
                                    isAmoledDark.value = it
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = RedPrimary
                                )
                            )
                        }

                        // Desktop Mode
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Desktop Mode", fontWeight = FontWeight.SemiBold)
                                Text("Request desktop YouTube layout", fontSize = 12.sp, color = TextSecondary)
                            }
                            Switch(
                                checked = isDesktopMode.value,
                                onCheckedChange = {
                                    isDesktopMode.value = it
                                    webView?.settings?.userAgentString =
                                        if (it) desktopUserAgent else mobileUserAgent
                                    webView?.reload()
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = RedPrimary
                                )
                            )
                        }

                        HorizontalDivider(color = DarkCardElevated)

                        // Action Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    webView?.reload()
                                    showSettingsSheet = false
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Reload")
                            }

                            Button(
                                onClick = {
                                    CookieManager.getInstance().removeAllCookies(null)
                                    webView?.clearCache(true)
                                    webView?.clearHistory()
                                    blockedAdsCount.intValue = 0
                                    webView?.reload()
                                    showSettingsSheet = false
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = DarkCardElevated)
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Clear Data")
                            }
                        }

                        Text(
                            text = "ZenTube v1.0.0 • Clean & Modern Ad-Free YouTube",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp)
                        )
                    }
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebViewSettings(wv: WebView) {
        val s = wv.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.databaseEnabled = true
        s.mediaPlaybackRequiresUserGesture = false
        s.allowFileAccess = false
        s.setSupportZoom(true)
        s.builtInZoomControls = false
        s.displayZoomControls = false
        s.useWideViewPort = true
        s.loadWithOverviewMode = true
        s.cacheMode = WebSettings.LOAD_DEFAULT
        s.userAgentString = mobileUserAgent

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)
    }

    private fun showCustomView(view: View?, callback: WebChromeClient.CustomViewCallback?) {
        if (customView != null) {
            callback?.onCustomViewHidden()
            return
        }
        customView = view
        customViewCallback = callback
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        val decor = window.decorView as FrameLayout
        decor.addView(
            view,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
    }

    private fun hideCustomView() {
        if (customView == null) return
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE

        val decor = window.decorView as FrameLayout
        decor.removeView(customView)
        customView = null
        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
    }
}
