package org.lumina.player.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionResult
import org.lumina.player.LuminaApp
import org.lumina.player.MainActivity
import org.lumina.player.core.dsp.AudioDspController
import org.lumina.player.core.model.Song

/**
 * Background audio playback service hosting ExoPlayer and MediaSession.
 * Operates without foreground notifications per user preference for distraction-free listening.
 */
class LuminaAudioService : Service() {

    private var mediaSession: MediaSession? = null
    lateinit var player: ExoPlayer
        private set

    val dspController = AudioDspController()

    private var wakeLock: PowerManager.WakeLock? = null
    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): LuminaAudioService = this@LuminaAudioService
    }

    override fun onCreate() {
        super.onCreate()

        // Acquire CPU partial wake lock to prevent background audio interruption when screen is turned off
        val powerManager = getSystemService(PowerManager::class.java)
        wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "LuminaPlayer:PlaybackWakeLock"
        )?.apply {
            setReferenceCounted(false)
        }

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true) // Automatically handle audio focus
            .setHandleAudioBecomingNoisy(true) // Auto-pause on headphone disconnection
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setSeekParameters(androidx.media3.exoplayer.SeekParameters.EXACT)
            .build()

        // Attach system audio session for equalizer & maintain CPU wake lock during active playback
        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                if (audioSessionId != C.AUDIO_SESSION_ID_UNSET && audioSessionId != 0) {
                    dspController.attachAudioSession(audioSessionId)
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    runCatching { wakeLock?.acquire(24 * 60 * 60 * 1000L) }
                } else {
                    runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
                }
            }
        })

        val initialSessionId = player.audioSessionId
        if (initialSessionId != C.AUDIO_SESSION_ID_UNSET && initialSessionId != 0) {
            dspController.attachAudioSession(initialSessionId)
        }

        // Clean up any lingering notification channels and cancel past notifications
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager?.cancelAll()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching {
                notificationManager?.deleteNotificationChannel("lumina_player_playback_channel")
                notificationManager?.deleteNotificationChannel("lumina_player_playback_channel_v2")
            }
        }

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Initialize MediaSession for headset and external media button controls
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivityPendingIntent)
            .setCallback(MediaSessionCallback())
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private inner class MediaSessionCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val connectionResult = super.onConnect(session, controller)
            val availableSessionCommands = connectionResult.availableSessionCommands
            val availablePlayerCommands = connectionResult.availablePlayerCommands.buildUpon()
                .add(Player.COMMAND_PLAY_PAUSE)
                .add(Player.COMMAND_SEEK_TO_NEXT)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                .add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                .build()
            return MediaSession.ConnectionResult.accept(availableSessionCommands, availablePlayerCommands)
        }

        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun onPlayerCommandRequest(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            playerCommand: Int
        ): Int {
            when (playerCommand) {
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> {
                    LuminaApp.instance.playerController.skipNext()
                    return SessionResult.RESULT_SUCCESS
                }
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> {
                    LuminaApp.instance.playerController.skipPrevious()
                    return SessionResult.RESULT_SUCCESS
                }
            }
            return super.onPlayerCommandRequest(session, controller, playerCommand)
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    fun prepareSong(song: Song, artworkBytes: ByteArray? = null, playWhenReady: Boolean = true) {
        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.displayArtist)
            .setAlbumTitle(song.displayAlbum)

        if (artworkBytes != null && artworkBytes.isNotEmpty()) {
            metadataBuilder.setArtworkData(artworkBytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
        }

        val metadata = metadataBuilder.build()

        // For CUE tracks, the underlying player loads the physical audio file with start/end clipping
        val playableUri = if (song.isVirtualCueTrack && song.filePath.isNotBlank()) {
            android.net.Uri.fromFile(java.io.File(song.filePath))
        } else {
            android.net.Uri.parse(song.uri)
        }

        val mediaItem = MediaItem.Builder()
            .setUri(playableUri)
            .setMediaId(song.uri)
            .setMediaMetadata(metadata)
            .apply {
                if (song.isVirtualCueTrack && song.cueEndMs > song.cueStartMs) {
                    setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(song.cueStartMs)
                            .setEndPositionMs(song.cueEndMs)
                            .build()
                    )
                }
            }
            .build()

        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = playWhenReady
    }

    fun updateArtwork(artworkBytes: ByteArray?) {
        val currentItem = player.currentMediaItem ?: return
        val newMetadata = currentItem.mediaMetadata.buildUpon()
            .setArtworkData(artworkBytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
            .build()
        val newItem = currentItem.buildUpon()
            .setMediaMetadata(newMetadata)
            .build()
        player.replaceMediaItem(player.currentMediaItemIndex, newItem)
    }

    override fun onDestroy() {
        runCatching {
            mediaSession?.release()
            mediaSession = null
        }
        runCatching {
            if (::player.isInitialized) {
                player.release()
            }
        }
        dspController.release()
        runCatching {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        }
        wakeLock = null
        super.onDestroy()
    }
}
