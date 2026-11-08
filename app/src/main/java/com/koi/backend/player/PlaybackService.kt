package com.koi.backend.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CacheBitmapLoader
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionResult
import androidx.media3.session.SimpleBitmapLoader
import com.koi.KoiApplication
import com.koi.backend.innertube.InnerTubeClient
import com.koi.ui.MainActivity
import com.koi.ui.R

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    companion object {
        const val CHANNEL_ID = "koi_playback_channel"
        const val NOTIFICATION_ID = 1001
        var isRunning = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createNotificationChannel()

        setListener(object : Listener {
            override fun onForegroundServiceStartNotAllowedException() {
                android.util.Log.w("PlaybackService", "Foreground service start not allowed in background by system")
            }
        })

        val app = application as? KoiApplication
        val player = app?.playerEngine?.exoPlayer
        if (player != null) {
            val sessionActivityPendingIntent = PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val forwardingPlayer = KoiForwardingPlayer(player) {
                (application as? KoiApplication)?.playerEngine
            }

            val callback = object : MediaSession.Callback {
                @Deprecated("Deprecated in Java")
                @Suppress("DEPRECATION")
                override fun onPlayerCommandRequest(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    playerCommand: Int
                ): Int {
                    when (playerCommand) {
                        Player.COMMAND_PLAY_PAUSE,
                        Player.COMMAND_SEEK_TO_NEXT,
                        Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                        Player.COMMAND_SEEK_TO_PREVIOUS,
                        Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> {
                            return SessionResult.RESULT_SUCCESS
                        }
                    }
                    return super.onPlayerCommandRequest(session, controller, playerCommand)
                }
            }

            val session = MediaSession.Builder(this, forwardingPlayer)
                .setSessionActivity(sessionActivityPendingIntent)
                .setCallback(callback)
                .setBitmapLoader(KoiNotificationBitmapLoader(this))
                .build()
            mediaSession = session
            addSession(session)

            val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(CHANNEL_ID)
                .setChannelName(R.string.playback_channel_name)
                .setNotificationId(NOTIFICATION_ID)
                .build().apply {
                    setSmallIcon(R.drawable.ic_koi_logo)
                }
            setMediaNotificationProvider(notificationProvider)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.playback_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Koi Music Playback"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val app = application as? KoiApplication
        val engine = app?.playerEngine
        val player = mediaSession?.player
        val isBuffering = engine?.isBuffering?.value == true
        val isPlaying = player?.isPlaying == true || player?.playWhenReady == true
        // Keep playing in background even if app task is swiped away from recent apps
        if (!isPlaying && !isBuffering) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        isRunning = false
        mediaSession?.run {
            removeSession(this)
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}

@OptIn(UnstableApi::class)
private class KoiForwardingPlayer(
    private val player: Player,
    private val engineProvider: () -> PlayerEngine?
) : ForwardingPlayer(player) {

    override fun getAvailableCommands(): Player.Commands {
        return super.getAvailableCommands().buildUpon()
            .add(Player.COMMAND_PLAY_PAUSE)
            .add(Player.COMMAND_SEEK_TO_NEXT)
            .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .build()
    }

    override fun isCommandAvailable(command: Int): Boolean {
        return command == Player.COMMAND_PLAY_PAUSE ||
                command == Player.COMMAND_SEEK_TO_NEXT ||
                command == Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM ||
                command == Player.COMMAND_SEEK_TO_PREVIOUS ||
                command == Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM ||
                super.isCommandAvailable(command)
    }

    override fun hasNextMediaItem(): Boolean = true

    override fun hasPreviousMediaItem(): Boolean = true

    @Deprecated("Deprecated in Java", ReplaceWith("hasNextMediaItem()"))
    override fun hasNext(): Boolean = true

    @Deprecated("Deprecated in Java", ReplaceWith("hasNextMediaItem()"))
    override fun hasNextWindow(): Boolean = true

    @Deprecated("Deprecated in Java", ReplaceWith("hasPreviousMediaItem()"))
    override fun hasPrevious(): Boolean = true

    @Deprecated("Deprecated in Java", ReplaceWith("hasPreviousMediaItem()"))
    override fun hasPreviousWindow(): Boolean = true

    override fun seekToNext() {
        engineProvider()?.skipNext()
    }

    override fun seekToNextMediaItem() {
        engineProvider()?.skipNext()
    }

    @Deprecated("Deprecated in Java", ReplaceWith("seekToNextMediaItem()"))
    override fun seekToNextWindow() {
        engineProvider()?.skipNext()
    }

    @Deprecated("Deprecated in Java", ReplaceWith("seekToNextMediaItem()"))
    override fun next() {
        engineProvider()?.skipNext()
    }

    override fun seekToPrevious() {
        engineProvider()?.skipPrevious()
    }

    override fun seekToPreviousMediaItem() {
        engineProvider()?.skipPrevious()
    }

    @Deprecated("Deprecated in Java", ReplaceWith("seekToPreviousMediaItem()"))
    override fun seekToPreviousWindow() {
        engineProvider()?.skipPrevious()
    }

    @Deprecated("Deprecated in Java", ReplaceWith("seekToPreviousMediaItem()"))
    override fun previous() {
        engineProvider()?.skipPrevious()
    }

    override fun getCurrentMediaItem(): MediaItem? {
        val current = engineProvider()?.currentTrack?.value
        val superItem = super.getCurrentMediaItem()
        if (current != null) {
            val highResThumb = InnerTubeClient.getHighResThumbnailUrl(current.thumbnailUrl)
            val metadata = MediaMetadata.Builder()
                .setTitle(current.title)
                .setArtist(current.artists)
                .setAlbumTitle(current.album ?: "")
                .setArtworkUri(highResThumb?.let { Uri.parse(it) } ?: current.thumbnailUrl?.let { Uri.parse(it) })
                .build()

            return (superItem?.buildUpon() ?: MediaItem.Builder().setMediaId(current.videoId))
                .setMediaId(current.videoId)
                .setMediaMetadata(metadata)
                .build()
        }
        return superItem
    }

    override fun getMediaMetadata(): MediaMetadata {
        val current = engineProvider()?.currentTrack?.value
        if (current != null) {
            val highResThumb = InnerTubeClient.getHighResThumbnailUrl(current.thumbnailUrl)
            return MediaMetadata.Builder()
                .setTitle(current.title)
                .setArtist(current.artists)
                .setAlbumTitle(current.album ?: "")
                .setArtworkUri(highResThumb?.let { Uri.parse(it) } ?: current.thumbnailUrl?.let { Uri.parse(it) })
                .build()
        }
        return super.getMediaMetadata()
    }

    override fun getPlaybackState(): Int {
        val engine = engineProvider()
        if (engine?.isBuffering?.value == true) {
            return Player.STATE_BUFFERING
        }
        return super.getPlaybackState()
    }

    override fun getPlayWhenReady(): Boolean {
        val engine = engineProvider()
        if (engine?.isBuffering?.value == true) {
            return true
        }
        return super.getPlayWhenReady()
    }

    override fun isPlaying(): Boolean {
        val engine = engineProvider()
        if (engine?.isBuffering?.value == true) {
            return false
        }
        return super.isPlaying()
    }

    override fun play() {
        val engine = engineProvider()
        val p = player
        if (p.playbackState == Player.STATE_IDLE || p.mediaItemCount == 0 || p.playbackState == Player.STATE_ENDED) {
            engine?.togglePlayPause()
        } else {
            super.play()
        }
    }

    override fun pause() {
        val engine = engineProvider()
        if (engine?.isBuffering?.value == true) {
            engine.togglePlayPause()
        } else {
            super.pause()
        }
    }
}
