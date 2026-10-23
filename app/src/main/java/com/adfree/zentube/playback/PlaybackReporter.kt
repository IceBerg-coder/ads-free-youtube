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

            function report() {
                try {
                    if (!window.ZenTubePlayback) { return; }
                    var v = document.querySelector('video');
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
