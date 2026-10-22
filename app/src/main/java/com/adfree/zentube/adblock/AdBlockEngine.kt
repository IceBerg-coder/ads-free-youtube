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
        "api-adservices.apple.com",
        "ad.youtube.com",
        "s.youtube.com",
        "video-stats.l.google.com"
    )

    private val BLOCKED_PATH_KEYWORDS = listOf(
        "/api/stats/ads",
        "/pagead/",
        "/ptracking",
        "/youtubei/v1/log_event",
        "/youtubei/v1/player/ad_break",
        "/youtubei/v1/ads",
        "/pcs/view",
        "/pcs/click",
        "googleads",
        "doubleclick",
        "pagead"
    )

    fun isAdRequest(uri: Uri): Boolean {
        val host = uri.host?.lowercase() ?: return false
        for (blocked in BLOCKED_HOSTS) {
            if (host == blocked || host.endsWith(".$blocked")) {
                return true
            }
        }
        val fullUrl = uri.toString().lowercase()
        for (kw in BLOCKED_PATH_KEYWORDS) {
            if (fullUrl.contains(kw)) {
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
        /* All ad renderers, sponsored cards, banners and promos */
        ytm-promoted-sparkles-web-renderer,
        ytm-promoted-sparkles-text-search-renderer,
        ytm-companion-ad-renderer,
        ytm-promoted-video-renderer,
        ytm-ad-slot-renderer,
        ytm-in-feed-ad-layout-renderer,
        ytm-compact-promoted-item-renderer,
        ytd-promoted-sparkles-web-renderer,
        ytd-in-feed-ad-layout-renderer,
        ytd-ad-slot-renderer,
        ytd-banner-promo-renderer,
        ytd-rich-item-renderer:has(ytd-ad-slot-renderer),
        ytm-item-section-renderer:has(ytm-promoted-sparkles-web-renderer),
        ytm-item-section-renderer:has(ytm-promoted-sparkles-text-search-renderer),
        ytm-item-section-renderer:has(ytm-ad-slot-renderer),
        ytm-item-section-renderer:has(.badge-shape-wiz__text),
        ytm-item-section-renderer:has([aria-label*="Sponsored" i]),
        ytm-video-with-context-renderer:has(.badge-shape-wiz__text),
        ytm-compact-video-renderer:has(.badge-shape-wiz__text),
        ytm-video-with-context-renderer:has([aria-label*="Sponsored" i]),
        ytm-compact-video-renderer:has([aria-label*="Sponsored" i]),
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
        ytm-merch-shelf-renderer,
        ytm-paid-content-overlay-renderer,
        ytm-shopping-companion-card-renderer,
        ytd-shopping-drawer-renderer,
        .ad-showing,
        .ad-interrupting {
            display: none !important;
        }
    """.trimIndent()

    val ADBLOCK_INJECTION_SCRIPT = """
        (function() {
            // Prevent multiple hook registrations
            if (!window.__zentube_hooks_installed) {
                window.__zentube_hooks_installed = true;

                // Hook window.fetch to strip sponsored & ad JSON responses from YouTube API
                if (window.fetch) {
                    const originalFetch = window.fetch;
                    window.fetch = async function(...args) {
                        const url = typeof args[0] === 'string' ? args[0] : (args[0] && args[0].url) || '';
                        
                        // Block ad beacon / telemetry requests outright
                        if (url.includes('/api/stats/ads') || url.includes('/pagead/') || url.includes('/ptracking') || url.includes('doubleclick')) {
                            return new Response('{}', { status: 200, headers: { 'Content-Type': 'application/json' } });
                        }

                        const response = await originalFetch.apply(this, args);

                        // Clean YouTube JSON API endpoints (player, browse, search, next)
                        if (url.includes('/youtubei/v1/player') || url.includes('/youtubei/v1/browse') || 
                            url.includes('/youtubei/v1/search') || url.includes('/youtubei/v1/next')) {
                            try {
                                const clone = response.clone();
                                const json = await clone.json();

                                if (json.playerAds) delete json.playerAds;
                                if (json.adPlacements) delete json.adPlacements;
                                if (json.adBreakHeartbeatParams) delete json.adBreakHeartbeatParams;
                                if (json.adSlots) delete json.adSlots;

                                // Recursively prune ad objects from contents
                                function purgeAds(obj) {
                                    if (!obj || typeof obj !== 'object') return;
                                    if (Array.isArray(obj)) {
                                        for (let i = obj.length - 1; i >= 0; i--) {
                                            const item = obj[i];
                                            if (item && typeof item === 'object') {
                                                const keys = Object.keys(item);
                                                const isAd = keys.some(k => {
                                                    const lk = k.toLowerCase();
                                                    return lk.includes('adslot') || 
                                                           lk.includes('promoted') || 
                                                           lk.includes('infeedad') ||
                                                           lk.includes('companionad');
                                                });
                                                if (isAd) {
                                                    obj.splice(i, 1);
                                                    if (window.ZenTubeBridge && typeof window.ZenTubeBridge.onAdBlocked === 'function') {
                                                        window.ZenTubeBridge.onAdBlocked();
                                                    }
                                                    continue;
                                                }
                                                purgeAds(item);
                                            }
                                        }
                                    } else {
                                        for (const key of Object.keys(obj)) {
                                            const lk = key.toLowerCase();
                                            if (lk.includes('adslot') || lk.includes('promotedsparkles') || 
                                                lk.includes('infeedad') || lk.includes('playerads') || 
                                                lk.includes('adplacements')) {
                                                delete obj[key];
                                            } else {
                                                purgeAds(obj[key]);
                                            }
                                        }
                                    }
                                }
                                purgeAds(json);

                                return new Response(JSON.stringify(json), {
                                    status: response.status,
                                    statusText: response.statusText,
                                    headers: response.headers
                                });
                            } catch(e) {
                                return response;
                            }
                        }
                        return response;
                    };
                }
            }

            // Always ensure CSS is injected
            function injectStyle() {
                if (document.getElementById('zentube-adblock-style')) return;
                const style = document.createElement('style');
                style.id = 'zentube-adblock-style';
                style.type = 'text/css';
                style.innerHTML = `$ADBLOCK_CSS`;
                (document.head || document.documentElement).appendChild(style);
            }
            injectStyle();

            // Aggressive Scanner for Sponsored Feed and Search Cards
            function purgeSponsoredCards() {
                try {
                    // Check badge elements indicating a sponsored video or post
                    const badgeElements = document.querySelectorAll(
                        '.badge-shape-wiz__text, ytm-badge-and-byline-renderer, .yt-core-attributed-string, span[role="text"], [aria-label*="Sponsored" i]'
                    );
                    badgeElements.forEach(badge => {
                        const txt = (badge.innerText || badge.textContent || badge.getAttribute('aria-label') || '').trim();
                        if (/^(sponsored|advertisement|ad)$/i.test(txt) || txt.toLowerCase().includes('sponsored')) {
                            const card = badge.closest(
                                'ytm-item-section-renderer, ytm-video-with-context-renderer, ytm-compact-video-renderer, ytm-compact-ad-renderer, ytd-rich-item-renderer, ytd-video-renderer, ytm-rich-item-renderer, ytd-ad-slot-renderer, div.compact-media-item'
                            );
                            if (card && card.style.display !== 'none') {
                                card.style.setProperty('display', 'none', 'important');
                                card.remove();
                                if (window.ZenTubeBridge && typeof window.ZenTubeBridge.onAdBlocked === 'function') {
                                    window.ZenTubeBridge.onAdBlocked();
                                }
                            }
                        }
                    });

                    // Remove explicit ad containers
                    const explicitAds = document.querySelectorAll(
                        'ytm-promoted-sparkles-web-renderer, ytm-promoted-sparkles-text-search-renderer, ytm-companion-ad-renderer, ytm-ad-slot-renderer, ytm-in-feed-ad-layout-renderer, ytd-ad-slot-renderer, ytm-compact-promoted-item-renderer'
                    );
                    explicitAds.forEach(el => {
                        el.style.setProperty('display', 'none', 'important');
                        el.remove();
                    });
                } catch(e) {}
            }

            // Video Ad Fast-Forward & Instant Skip
            function skipVideoAds() {
                try {
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

            function tick() {
                injectStyle();
                purgeSponsoredCards();
                skipVideoAds();
            }

            if (!window.__zentube_interval_set) {
                window.__zentube_interval_set = true;
                setInterval(tick, 200);

                const observer = new MutationObserver(tick);
                observer.observe(document.documentElement || document.body, {
                    childList: true,
                    subtree: true
                });
            }

            tick();
        })();
    """.trimIndent()
}

class ZenTubeBridge(private val onAdBlockedCallback: () -> Unit) {
    @JavascriptInterface
    fun onAdBlocked() {
        onAdBlockedCallback()
    }
}
