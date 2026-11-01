package com.adfree.zentube.playback

import android.webkit.JavascriptInterface

/**
 * JavaScript that observes the page's HTML5 video element and reports
 * playback state (playing, position, duration, speed, title) back to the
 * app through the ZenTubePlayback bridge. Idempotent: safe to inject on
 * every page lifecycle event.
 */
object PlaybackReporter {

    val REPORT_SCRIPT = """
        (function() {
            if (window.__zentube_playback_installed) { return; }
            window.__zentube_playback_installed = true;

            // Background playback: the player pauses itself when the page reports
            // that it is hidden (app in background, screen off). Present the page
            // as always visible and swallow visibility events. This runs at
            // document start, before the page's own scripts register listeners.
            try {
                var visible = function() { return false; };
                Object.defineProperty(Document.prototype, 'hidden',
                    { configurable: true, get: visible });
                Object.defineProperty(Document.prototype, 'webkitHidden',
                    { configurable: true, get: visible });
                Object.defineProperty(Document.prototype, 'visibilityState',
                    { configurable: true, get: function() { return 'visible'; } });
                Object.defineProperty(Document.prototype, 'webkitVisibilityState',
                    { configurable: true, get: function() { return 'visible'; } });
                ['visibilitychange', 'webkitvisibilitychange'].forEach(function(type) {
                    window.addEventListener(type, function(e) {
                        e.stopImmediatePropagation();
                    }, true);
                });
            } catch (e) {}

            function cleanTitle(t) {
                return (t || '')
                    .replace(/^\(\d+\)\s*/, '')
                    .replace(/\s*-\s*YouTube\s*${'$'}/, '')
                    .trim();
            }

            // Full-viewport video layout used while the app is in Picture-in-Picture,
            // so the PiP window shows only the player instead of the whole page.
            // Called from report() as well: at document start there may be no
            // <head>/<html> yet, so the first attempt can be a no-op.
            function ensurePipStyle() {
                try {
                    if (document.getElementById('zentube-pip-style')) { return; }
                    var parent = document.head || document.documentElement;
                    if (!parent) { return; }
                    var style = document.createElement('style');
                    style.id = 'zentube-pip-style';
                    style.textContent =
                        'html.zt-pip, html.zt-pip body { overflow: hidden !important; background: #000 !important; }' +
                        'html.zt-pip .html5-video-player { position: fixed !important; top: 0 !important;' +
                        ' left: 0 !important; width: 100vw !important; height: 100vh !important;' +
                        ' z-index: 2147483647 !important; background: #000 !important; }' +
                        'html.zt-pip .html5-video-container, html.zt-pip video { top: 0 !important;' +
                        ' left: 0 !important; width: 100% !important; height: 100% !important; }';
                    parent.appendChild(style);
                } catch (e) {}
            }

            // Feed previews and other pages also contain <video> elements; only
            // treat watch / shorts / live pages as real media.
            function isMediaPage() {
                return /^\/(watch|shorts|live)(\/|${'$'})/.test(location.pathname);
            }

            function report() {
                try {
                    ensurePipStyle();
                    if (!window.ZenTubePlayback) { return; }
                    var v = document.querySelector('video');
                    if (!isMediaPage() || !v || v.readyState < 1 || !(v.duration > 0)) {
                        ZenTubePlayback.onPlaybackState(false, false, 0, 0, 1.0, '');
                        return;
                    }
                    var dur = isFinite(v.duration) ? v.duration : 0;
                    // The ad skipper temporarily raises the rate; report the user's rate.
                    var rate = window.__zentube_adState ? window.__zentube_adState.rate : v.playbackRate;
                    ZenTubePlayback.onPlaybackState(
                        true,
                        !v.paused && !v.ended,
                        v.currentTime || 0,
                        dur,
                        rate || 1.0,
                        cleanTitle(document.title)
                    );
                } catch (e) {}
            }

            setInterval(report, 500);
            document.addEventListener('play', report, true);
            document.addEventListener('pause', report, true);
            document.addEventListener('ended', report, true);
            document.addEventListener('loadedmetadata', report, true);
            report();
        })();
    """.trimIndent()

    fun playScript(): String =
        "(function(){var v=document.querySelector('video');if(v){v.play();}})();"

    fun pauseScript(): String =
        "(function(){var v=document.querySelector('video');if(v){v.pause();}})();"

    fun seekByScript(deltaSeconds: Double): String =
        "(function(){var v=document.querySelector('video');" +
            "if(v){try{v.currentTime=Math.max(0,(v.currentTime||0)+($deltaSeconds));}catch(e){}}})();"

    fun seekToScript(positionSeconds: Double): String =
        "(function(){var v=document.querySelector('video');" +
            "if(v){try{v.currentTime=Math.max(0,$positionSeconds);}catch(e){}}})();"

    fun setSpeedScript(rate: Float): String =
        "(function(){var v=document.querySelector('video');if(v){v.playbackRate=$rate;}})();"

    /** Switch the page to (or back from) the video-only layout used in PiP. */
    fun pipScript(enabled: Boolean): String =
        "(function(){document.documentElement.classList.toggle('zt-pip'," +
            "${if (enabled) "true" else "false"});})();"

    fun requestFullscreenScript(): String =
        "(function(){var p=document.querySelector('.html5-video-player');" +
            "if(p&&p.requestFullscreen){p.requestFullscreen();}})();"
}

/**
 * JS -> Kotlin bridge mirroring the ZenTubeBridge pattern. Callbacks arrive
 * on a background thread; MainActivity hops to the UI thread.
 */
class ZenTubePlaybackBridge(
    private val onState: (
        hasMedia: Boolean,
        isPlaying: Boolean,
        positionSeconds: Double,
        durationSeconds: Double,
        speed: Float,
        title: String
    ) -> Unit
) {
    @JavascriptInterface
    fun onPlaybackState(
        hasMedia: Boolean,
        isPlaying: Boolean,
        positionSeconds: Double,
        durationSeconds: Double,
        speed: Float,
        title: String
    ) {
        onState(hasMedia, isPlaying, positionSeconds, durationSeconds, speed, title)
    }
}
