package com.adfree.zentube.adblock

import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

/**
 * uBlock Origin-grade Filtering & Scriptlet Engine for YouTube
 * Incorporating Raymond Hill's (gorhill/uBlock) json-prune, set-constant,
 * prevent-fetch, and procedural cosmetic filtering principles.
 */
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
        "/youtubei/v1/att/",
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

    val UBLOCK_COSMETIC_CSS = """
        /* === uBlock Origin Cosmetic & Procedural Rules for YouTube === */
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
        ytd-rich-item-renderer:has(.badge-shape-wiz__text),
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
        ytd-enforcement-message-view-model,
        tp-yt-paper-dialog:has(ytd-enforcement-message-view-model),
        .ad-showing,
        .ad-interrupting {
            display: none !important;
        }
    """.trimIndent()

    val ADBLOCK_INJECTION_SCRIPT = """
        (function() {
            // =========================================================================
            // uBlock Origin Scriptlet Suite for YouTube (gorhill/uBlock)
            // 1. set-constant: neutralizes ytInitialPlayerResponse, ytInitialData, & flags
            // 2. json-prune: removes ad slots, placements, and promoted modules from JSON
            // 3. prevent-fetch / prevent-xhr: blocks telemetry, ad beacons & ad breaks
            // 4. procedural cosmetic cleaner: dynamic badge and card purger
            // 5. auto-skip & fast-forward player engine
            // =========================================================================

            if (!window.__ublock_engine_installed) {
                window.__ublock_engine_installed = true;

                // --- Helper: Deep Ad Pruner (uBlock json-prune logic) ---
                const PRUNE_KEYS = new Set([
                    'playerads',
                    'adplacements',
                    'adslots',
                    'adbreakheartbeatparams',
                    'adslotrenderer',
                    'infeedadlayoutrenderer',
                    'promotedsparkleswebrenderer',
                    'promotedsparklestextsearchrenderer',
                    'companionadrenderer',
                    'promotedvideorenderer',
                    'compactpromoteditemrenderer',
                    'legacyimportant',
                    'adplacementrenderer'
                ]);

                function ublockJsonPrune(obj) {
                    if (!obj || typeof obj !== 'object') return obj;
                    if (Array.isArray(obj)) {
                        for (let i = obj.length - 1; i >= 0; i--) {
                            const item = obj[i];
                            if (item && typeof item === 'object') {
                                const keys = Object.keys(item);
                                const isAd = keys.some(k => PRUNE_KEYS.has(k.toLowerCase()) || k.toLowerCase().includes('adslot') || k.toLowerCase().includes('promoted'));
                                if (isAd) {
                                    obj.splice(i, 1);
                                    if (window.ZenTubeBridge && typeof window.ZenTubeBridge.onAdBlocked === 'function') {
                                        window.ZenTubeBridge.onAdBlocked();
                                    }
                                    continue;
                                }
                                ublockJsonPrune(item);
                            }
                        }
                    } else {
                        for (const key of Object.keys(obj)) {
                            const lk = key.toLowerCase();
                            if (PRUNE_KEYS.has(lk) || lk.includes('adslot') || lk.includes('promotedsparkles') || lk.includes('infeedad')) {
                                delete obj[key];
                                if (window.ZenTubeBridge && typeof window.ZenTubeBridge.onAdBlocked === 'function') {
                                    window.ZenTubeBridge.onAdBlocked();
                                }
                            } else {
                                ublockJsonPrune(obj[key]);
                            }
                        }
                    }
                    return obj;
                }

                // --- uBlock Scriptlet: set-constant ---
                // Override Object.prototype.hasAllowedInstreamAd to true
                try {
                    Object.defineProperty(Object.prototype, 'hasAllowedInstreamAd', {
                        get: () => true,
                        set: () => {},
                        configurable: true
                    });
                } catch(e) {}

                // Trap window.ytInitialPlayerResponse to prevent ads from loading initially
                let trappedPlayerResponse = undefined;
                try {
                    Object.defineProperty(window, 'ytInitialPlayerResponse', {
                        get: () => trappedPlayerResponse,
                        set: (val) => {
                            trappedPlayerResponse = ublockJsonPrune(val);
                        },
                        configurable: true
                    });
                } catch(e) {}

                // Trap window.ytInitialData to prune sponsored feed cards initially
                let trappedInitialData = undefined;
                try {
                    Object.defineProperty(window, 'ytInitialData', {
                        get: () => trappedInitialData,
                        set: (val) => {
                            trappedInitialData = ublockJsonPrune(val);
                        },
                        configurable: true
                    });
                } catch(e) {}

                // --- uBlock Scriptlet: json-prune on JSON.parse ---
                const originalJSONParse = JSON.parse;
                JSON.parse = function(...args) {
                    const result = originalJSONParse.apply(this, args);
                    if (result && typeof result === 'object') {
                        return ublockJsonPrune(result);
                    }
                    return result;
                };

                // --- uBlock Scriptlet: json-prune-fetch-response & prevent-fetch ---
                if (window.fetch) {
                    const originalFetch = window.fetch;
                    window.fetch = async function(...args) {
                        const url = typeof args[0] === 'string' ? args[0] : (args[0] && args[0].url) || '';

                        // uBlock prevent-fetch: Block ad beacons, telemetry, and tracking endpoints
                        if (url.includes('/api/stats/ads') || 
                            url.includes('/pagead/') || 
                            url.includes('/ptracking') || 
                            url.includes('/youtubei/v1/log_event') || 
                            url.includes('/youtubei/v1/att/') || 
                            url.includes('doubleclick') ||
                            url.includes('googleads')) {
                            return new Response('{}', { status: 200, headers: { 'Content-Type': 'application/json' } });
                        }

                        const response = await originalFetch.apply(this, args);

                        // uBlock json-prune-fetch-response: Clean YouTube API responses
                        if (url.includes('/youtubei/v1/player') || 
                            url.includes('/youtubei/v1/browse') || 
                            url.includes('/youtubei/v1/search') || 
                            url.includes('/youtubei/v1/next')) {
                            try {
                                const clone = response.clone();
                                const json = await clone.json();
                                const pruned = ublockJsonPrune(json);

                                return new Response(JSON.stringify(pruned), {
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

                // --- uBlock prevent-xhr: Hook XMLHttpRequest ---
                const originalXHROpen = XMLHttpRequest.prototype.open;
                XMLHttpRequest.prototype.open = function(method, url, ...rest) {
                    const urlStr = String(url);
                    if (urlStr.includes('/api/stats/ads') || 
                        urlStr.includes('/pagead/') || 
                        urlStr.includes('/ptracking') || 
                        urlStr.includes('doubleclick') || 
                        urlStr.includes('googleads')) {
                        // Redirect blocked request to dummy data uri
                        return originalXHROpen.call(this, 'GET', 'data:application/json,{}', ...rest);
                    }
                    return originalXHROpen.call(this, method, url, ...rest);
                };
            }

            // --- uBlock Origin Cosmetic Injection ---
            function ensureCosmeticStyles() {
                if (!document.getElementById('ublock-youtube-cosmetic')) {
                    const style = document.createElement('style');
                    style.id = 'ublock-youtube-cosmetic';
                    style.type = 'text/css';
                    style.innerHTML = `$UBLOCK_COSMETIC_CSS`;
                    (document.head || document.documentElement).appendChild(style);
                }
            }
            ensureCosmeticStyles();

            // --- uBlock Procedural Filtering & Sponsored Badge Hunter ---
            function runProceduralFilter() {
                try {
                    // Hunt all elements carrying "Sponsored", "Ad", or promotional markers
                    const badges = document.querySelectorAll(
                        '.badge-shape-wiz__text, ytm-badge-and-byline-renderer, .yt-core-attributed-string, span[role="text"], [aria-label*="Sponsored" i], [aria-label*="Ad" i]'
                    );
                    badges.forEach(badge => {
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

                    // Remove anti-adblock message dialogs if YouTube attempts to pop them up
                    const antiAdblockModals = document.querySelectorAll(
                        'ytd-enforcement-message-view-model, tp-yt-paper-dialog:has(ytd-enforcement-message-view-model)'
                    );
                    antiAdblockModals.forEach(m => m.remove());
                } catch(e) {}
            }

            // --- Video Ad Fast-Forward & Skip Engine ---
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

                    // Dismiss promo sheets / "Open in App" banners
                    const dismissButtons = document.querySelectorAll(
                        'ytm-mealbar-promo-renderer #dismiss-button, ytm-upsell-dialog-renderer button'
                    );
                    dismissButtons.forEach(b => b.click());
                } catch(e) {}
            }

            function cycle() {
                ensureCosmeticStyles();
                runProceduralFilter();
                skipVideoAds();
            }

            if (!window.__ublock_observer_attached) {
                window.__ublock_observer_attached = true;
                setInterval(cycle, 200);

                const observer = new MutationObserver(cycle);
                observer.observe(document.documentElement || document.body, {
                    childList: true,
                    subtree: true
                });
            }

            cycle();
        })();
    """.trimIndent()
}

class ZenTubeBridge(private val onAdBlockedCallback: () -> Unit) {
    @JavascriptInterface
    fun onAdBlocked() {
        onAdBlockedCallback()
    }
}
