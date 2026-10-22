package com.adfree.zentube.adblock

import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

object AdBlockEngine {

    private val BLOCKED_HOSTS = hashSetOf(
        "googleads.g.doubleclick.net",
        "pagead2.googlesyndication.com",
        "pubads.g.doubleclick.net",
        "securepubads.g.doubleclick.net",
        "adservice.google.com",
        "static.doubleclick.net",
        "ad.doubleclick.net",
        "stats.g.doubleclick.net",
        "www.google-analytics.com",
        "google-analytics.com",
        "adclick.g.doubleclick.net",
        "fls-na.amazon.com",
        "api-adservices.apple.com"
    )

    private val BLOCKED_PATH_KEYWORDS = listOf(
        "/api/stats/ads",
        "/pagead/",
        "/ptracking",
        "/youtubei/v1/log_event"
    )

    fun isAdRequest(uri: Uri): Boolean {
        val host = uri.host?.lowercase() ?: return false
        for (blocked in BLOCKED_HOSTS) {
            if (host == blocked || host.endsWith(".$blocked")) {
                return true
            }
        }
        val path = uri.path ?: ""
        for (kw in BLOCKED_PATH_KEYWORDS) {
            if (path.contains(kw, ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    fun createEmptyResponse(): WebResourceResponse {
        return WebResourceResponse(
            "text/plain",
            "UTF-8",
            ByteArrayInputStream("".toByteArray())
        )
    }

    val ADBLOCK_CSS = """
        ytd-ad-slot-renderer,
        ytd-in-feed-ad-layout-renderer,
        ytd-banner-promo-renderer,
        ytm-promoted-sparkles-web-renderer,
        ytm-companion-ad-renderer,
        ytm-promoted-sparkles-text-search-renderer,
        ytd-player-legacy-desktop-watch-ads-renderer,
        .ytp-ad-module,
        .ytp-ad-overlay-container,
        .ytp-ad-message-container,
        .video-ads,
        ytm-mealbar-promo-renderer,
        ytm-upsell-dialog-renderer,
        ytd-action-companion-ad-renderer,
        #masthead-ad,
        .ad-container,
        ytd-rich-item-renderer:has(ytd-ad-slot-renderer),
        ytm-item-section-renderer:has(ytm-promoted-sparkles-web-renderer) {
            display: none !important;
        }
    """.trimIndent()

    val ADBLOCK_INJECTION_SCRIPT = """
        (function() {
            if (window.__zentube_adblock_initialized) return;
            window.__zentube_adblock_initialized = true;

            // 1. Inject CSS to hide all ad elements & nag dialogs
            const style = document.createElement('style');
            style.type = 'text/css';
            style.innerHTML = `$ADBLOCK_CSS`;
            (document.head || document.documentElement).appendChild(style);

            // 2. Video Ad Fast-Forward & Skip Engine
            function skipVideoAds() {
                try {
                    // Check if player is showing an ad
                    const player = document.querySelector('.html5-video-player');
                    const isAdShowing = player && (
                        player.classList.contains('ad-showing') ||
                        player.classList.contains('ad-interrupting')
                    );

                    const video = document.querySelector('video');
                    if (video && isAdShowing) {
                        video.muted = true;
                        video.playbackRate = 16.0;
                        if (!isNaN(video.duration) && video.duration > 0) {
                            video.currentTime = video.duration;
                        }
                        if (window.ZenTubeBridge && typeof window.ZenTubeBridge.onAdBlocked === 'function') {
                            window.ZenTubeBridge.onAdBlocked();
                        }
                    }

                    // Auto-click Skip Button
                    const skipSelectors = [
                        '.ytp-ad-skip-button',
                        '.ytp-ad-skip-button-modern',
                        '.ytp-skip-ad-button',
                        '.videoAdUiSkipButton',
                        '.ytp-ad-skip-button-slot button'
                    ];
                    for (const sel of skipSelectors) {
                        const btn = document.querySelector(sel);
                        if (btn) {
                            btn.click();
                            if (window.ZenTubeBridge && typeof window.ZenTubeBridge.onAdBlocked === 'function') {
                                window.ZenTubeBridge.onAdBlocked();
                            }
                            break;
                        }
                    }

                    // Dismiss annoying popups / "Open in App" promo banners
                    const dismissButtons = document.querySelectorAll(
                        'ytm-mealbar-promo-renderer #dismiss-button, ytm-upsell-dialog-renderer button'
                    );
                    dismissButtons.forEach(b => b.click());
                } catch(e) {}
            }

            // Run on interval & mutation observer
            setInterval(skipVideoAds, 250);

            const observer = new MutationObserver(function() {
                skipVideoAds();
            });
            observer.observe(document.body || document.documentElement, {
                childList: true,
                subtree: true
            });
        })();
    """.trimIndent()
}

class ZenTubeBridge(private val onAdBlockedCallback: () -> Unit) {
    @JavascriptInterface
    fun onAdBlocked() {
        onAdBlockedCallback()
    }
}
