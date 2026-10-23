package com.adfree.zentube.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState as MediaPlaybackState
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.adfree.zentube.MainActivity
import com.adfree.zentube.R

/**
 * Commands the activity exposes so the playback service (and media session)
 * can drive the WebView's HTML5 video element.
 */
interface PlaybackController {
    fun play()
    fun pause()
    fun seekTo(positionSeconds: Double)
    fun seekBy(deltaSeconds: Double)
    fun setSpeed(rate: Float)
}

/**
 * Shared, process-wide view of the WebView's current playback state.
 * Written by MainActivity (via the JS bridge) and read by PlaybackService.
 */
object PlaybackState {
    @Volatile var title: String = ""
    @Volatile var isPlaying: Boolean = false
    @Volatile var hasMedia: Boolean = false
    @Volatile var positionSeconds: Double = 0.0
    @Volatile var durationSeconds: Double = 0.0
    @Volatile var speed: Float = 1.0f
    @Volatile var controller: PlaybackController? = null
    @Volatile var serviceRunning: Boolean = false
}

/**
 * Foreground service that keeps ZenTube's WebView playback alive when the
 * app is backgrounded or the screen is off, and exposes media controls
 * (notification, lock screen, headset/media buttons) through a MediaSession.
 */
class PlaybackService : Service() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        mediaSession = MediaSession(this, "ZenTubePlayback").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    PlaybackState.controller?.play()
                }

                override fun onPause() {
                    PlaybackState.controller?.pause()
                }

                override fun onStop() {
                    PlaybackState.controller?.pause()
                    stopPlayback()
                }

                override fun onSeekTo(pos: Long) {
                    PlaybackState.controller?.seekTo(pos / 1000.0)
                }
            }, Handler(Looper.getMainLooper()))
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> PlaybackState.controller?.play()
            ACTION_PAUSE -> PlaybackState.controller?.pause()
            ACTION_STOP -> {
                PlaybackState.controller?.pause()
                stopPlayback()
                return START_NOT_STICKY
            }
        }
        // ACTION_UPDATE (and any start) falls through: (re)post the notification.
        PlaybackState.serviceRunning = true
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        syncMediaSession()
        return START_STICKY
    }

    override fun onDestroy() {
        PlaybackState.serviceRunning = false
        mediaSession?.isActive = false
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun stopPlayback() {
        PlaybackState.serviceRunning = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Media controls for ZenTube playback"
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun servicePendingIntent(action: String): PendingIntent {
        val intent = Intent(this, PlaybackService::class.java).setAction(action)
        return PendingIntent.getService(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildNotification(): Notification {
        val isPlaying = PlaybackState.isPlaying
        val title = PlaybackState.title.ifBlank { "ZenTube" }

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val toggleAction = if (isPlaying) {
            Notification.Action.Builder(
                android.R.drawable.ic_media_pause,
                "Pause",
                servicePendingIntent(ACTION_PAUSE)
            ).build()
        } else {
            Notification.Action.Builder(
                android.R.drawable.ic_media_play,
                "Play",
                servicePendingIntent(ACTION_PLAY)
            ).build()
        }

        val stopAction = Notification.Action.Builder(
            android.R.drawable.ic_menu_close_clear_cancel,
            "Stop",
            servicePendingIntent(ACTION_STOP)
        ).build()

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_zen_notification)
            .setContentTitle(title)
            .setContentText("ZenTube")
            .setContentIntent(contentIntent)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)
            .setShowWhen(false)
            .addAction(toggleAction)
            .addAction(stopAction)
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(mediaSession?.sessionToken)
                    .setShowActionsInCompactView(0)
            )
            .build()
    }

    private fun syncMediaSession() {
        val session = mediaSession ?: return
        session.setMetadata(
            MediaMetadata.Builder()
                .putString(
                    MediaMetadata.METADATA_KEY_TITLE,
                    PlaybackState.title.ifBlank { "ZenTube" }
                )
                .putLong(
                    MediaMetadata.METADATA_KEY_DURATION,
                    (PlaybackState.durationSeconds * 1000).toLong()
                )
                .build()
        )
        val state = if (PlaybackState.isPlaying) {
            MediaPlaybackState.STATE_PLAYING
        } else {
            MediaPlaybackState.STATE_PAUSED
        }
        session.setPlaybackState(
            MediaPlaybackState.Builder()
                .setActions(
                    MediaPlaybackState.ACTION_PLAY or
                        MediaPlaybackState.ACTION_PAUSE or
                        MediaPlaybackState.ACTION_PLAY_PAUSE or
                        MediaPlaybackState.ACTION_SEEK_TO or
                        MediaPlaybackState.ACTION_STOP
                )
                .setState(
                    state,
                    (PlaybackState.positionSeconds * 1000).toLong(),
                    PlaybackState.speed
                )
                .build()
        )
    }

    companion object {
        private const val CHANNEL_ID = "zentube_playback"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_PLAY = "com.adfree.zentube.playback.PLAY"
        const val ACTION_PAUSE = "com.adfree.zentube.playback.PAUSE"
        const val ACTION_STOP = "com.adfree.zentube.playback.STOP"
        const val ACTION_UPDATE = "com.adfree.zentube.playback.UPDATE"

        /** Start the foreground playback service (or refresh it if running). */
        fun start(context: Context) {
            try {
                val intent = Intent(context, PlaybackService::class.java)
                    .setAction(ACTION_UPDATE)
                context.startForegroundService(intent)
            } catch (e: Exception) {
                // Android 12+ can forbid starting a foreground service while
                // the app is backgrounded; the next state change retries.
            }
        }

        /** Refresh notification + media session from the latest PlaybackState. */
        fun refresh(context: Context) {
            if (!PlaybackState.serviceRunning) return
            val intent = Intent(context, PlaybackService::class.java)
                .setAction(ACTION_UPDATE)
            context.startService(intent)
        }

        /** Stop the foreground playback service entirely. */
        fun stop(context: Context) {
            PlaybackState.serviceRunning = false
            context.stopService(Intent(context, PlaybackService::class.java))
        }
    }
}
