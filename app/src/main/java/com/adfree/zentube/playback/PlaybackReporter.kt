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

            function cleanTitle(t) {
                return (t || '')
                    .replace(/^\(\d+\)\s*/, '')
                    .replace(/\s*-\s*YouTube\s*${'$'}/, '')
                    .trim();
            }

            // The page can hold several <video> elements at once (feed previews,
            // the mini-player, ads). The first one in DOM order is often a muted
            // feed preview that appears and disappears while scrolling, which made
            // the app flap between "media" and "no media" and flicker the
            // mini-player bar. Always prefer the real player element, then any
            // video that is actually playing, and only then the first video.
            function findVideo() {
                var v = document.querySelector('.html5-video-player video');
                if (v) return v;
                var videos = document.querySelectorAll('video');
                for (var i = 0; i < videos.length; i++) {
                    var c = videos[i];
                    if (!c.paused && !c.ended && (c.currentTime || 0) > 0) return c;
                }
                return videos.length ? videos[0] : null;
            }
            window.__zentube_findVideo = findVideo;

            function report() {
                try {
                    if (!window.ZenTubePlayback) { return; }
                    var v = findVideo();
                    if (!v || v.readyState < 1 || !(v.duration > 0)) {
                        ZenTubePlayback.onPlaybackState(false, false, 0, 0, 1.0, '');
                        return;
                    }
                    var dur = isFinite(v.duration) ? v.duration : 0;
                    ZenTubePlayback.onPlaybackState(
                        true,
                        !v.paused && !v.ended,
                        v.currentTime || 0,
                        dur,
                        v.playbackRate || 1.0,
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

    // Control scripts target the same video the reporter observes, so
    // play/pause/seek never hit a stray feed-preview element.
    private fun targetVideoExpr(): String =
        "var f=window.__zentube_findVideo;var v=f?f():document.querySelector('video');"

    fun playScript(): String =
        "(function(){" + targetVideoExpr() + "if(v){v.play();}})();"

    fun pauseScript(): String =
        "(function(){" + targetVideoExpr() + "if(v){v.pause();}})();"

    fun seekByScript(deltaSeconds: Double): String =
        "(function(){" + targetVideoExpr() +
            "if(v){try{v.currentTime=Math.max(0,(v.currentTime||0)+($deltaSeconds));}catch(e){}}})();"

    fun seekToScript(positionSeconds: Double): String =
        "(function(){" + targetVideoExpr() +
            "if(v){try{v.currentTime=Math.max(0,$positionSeconds);}catch(e){}}})();"

    fun setSpeedScript(rate: Float): String =
        "(function(){" + targetVideoExpr() + "if(v){v.playbackRate=$rate;}})();"

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
