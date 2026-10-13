package org.lumina.player.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import android.media.AudioManager
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.lumina.player.LuminaApp
import org.lumina.player.MainActivity
import org.lumina.player.R
import org.lumina.player.core.dsp.AudioDspController
import org.lumina.player.core.model.Song

/**
 * Foreground audio playback service hosting ExoPlayer and MediaSession.
 * Provides lock screen controls, system media resumption panel, and Xiaomi HyperOS focus notifications.
 */
class LuminaAudioService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    lateinit var player: ExoPlayer
        private set

    val dspController = AudioDspController()
    val visualizerProcessor = org.lumina.player.core.dsp.VisualizerAudioProcessor(barCount = 24)
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var volumeRampJob: Job? = null

    private var wakeLock: PowerManager.WakeLock? = null
    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): LuminaAudioService = this@LuminaAudioService
    }

    override fun onCreate() {
        super.onCreate()

        // Restore DSP audio effect preferences
        val audioPrefs = (application as? LuminaApp)?.audioPreferences
        if (audioPrefs != null) {
            val s = audioPrefs.settings.value
            dspController.initSettings(
                enabled = s.eqEnabled,
                bassBoost = s.bassBoost.toShort(),
                virtualizer = s.virtualizer.toShort()
            )
            dspController.onPreferencesChanged = { enabled, bass, virt ->
                audioPrefs.setEqEnabled(enabled)
                audioPrefs.setBassBoost(bass.toInt())
                audioPrefs.setVirtualizer(virt.toInt())
            }
        }

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

        val renderersFactory = org.lumina.player.core.dsp.VisualizerAudioProcessor.createRenderersFactory(
            this,
            visualizerProcessor
        )

        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val explicitSessionId = audioManager.generateAudioSessionId()

        player = ExoPlayer.Builder(this, renderersFactory)
            .setAudioAttributes(audioAttributes, true) // Automatically handle audio focus
            .setHandleAudioBecomingNoisy(true) // Auto-pause on headphone disconnection
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setSeekParameters(androidx.media3.exoplayer.SeekParameters.EXACT)
            .build()

        player.setAudioSessionId(explicitSessionId)
        dspController.attachAudioSession(explicitSessionId)

        // Maintain CPU wake lock during active playback and observe session changes
        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                if (audioSessionId != C.AUDIO_SESSION_ID_UNSET && audioSessionId != 0 && audioSessionId != explicitSessionId) {
                    dspController.attachAudioSession(audioSessionId)
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    runCatching { wakeLock?.acquire(24 * 60 * 60 * 1000L) }
                } else {
                    runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
                    visualizerProcessor.resetSpectrum()
                }
            }
        })

        // Create low-importance, silent notification channel for non-intrusive lockscreen controls
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.app_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Lumina Player lock screen playback controls"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null)
                enableVibration(false)
            }
            notificationManager?.createNotificationChannel(channel)
        }

        val defaultNotificationProvider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelId(CHANNEL_ID)
            .setChannelName(R.string.app_name)
            .setNotificationId(NOTIFICATION_ID)
            .build()
        defaultNotificationProvider.setSmallIcon(R.drawable.ic_notification)

        setMediaNotificationProvider(LuminaMediaNotificationProvider(this, defaultNotificationProvider))

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // ForwardingPlayer guarantees Next and Previous commands are always advertised to SystemUI and lockscreen
        val forwardingPlayer = object : ForwardingPlayer(player) {
            override fun getAvailableCommands(): Player.Commands {
                return super.getAvailableCommands().buildUpon()
                    .add(Player.COMMAND_PLAY_PAUSE)
                    .add(Player.COMMAND_SEEK_TO_NEXT)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                    .build()
            }

            override fun isCommandAvailable(command: Int): Boolean {
                return when (command) {
                    Player.COMMAND_PLAY_PAUSE,
                    Player.COMMAND_SEEK_TO_NEXT,
                    Player.COMMAND_SEEK_TO_PREVIOUS,
                    Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                    Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                    Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM -> true
                    else -> super.isCommandAvailable(command)
                }
            }

            override fun seekToNext() {
                LuminaApp.instance.playerController.skipNext()
            }

            override fun seekToNextMediaItem() {
                LuminaApp.instance.playerController.skipNext()
            }

            override fun seekToPrevious() {
                LuminaApp.instance.playerController.skipPrevious()
            }

            override fun seekToPreviousMediaItem() {
                LuminaApp.instance.playerController.skipPrevious()
            }
        }

        mediaSession = MediaSession.Builder(this, forwardingPlayer)
            .setSessionActivity(sessionActivityPendingIntent)
            .setCallback(MediaSessionCallback())
            .build()

        addSession(mediaSession!!)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        mediaSession?.let { session ->
            onUpdateNotification(session, true)
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private class LuminaMediaNotificationProvider(
        private val context: Context,
        private val defaultProvider: DefaultMediaNotificationProvider
    ) : MediaNotification.Provider {

        override fun createNotification(
            mediaSession: MediaSession,
            customLayout: ImmutableList<CommandButton>,
            actionFactory: MediaNotification.ActionFactory,
            onNotificationChangedCallback: MediaNotification.Provider.Callback
        ): MediaNotification {
            val mediaNotification = defaultProvider.createNotification(
                mediaSession,
                customLayout,
                actionFactory,
                onNotificationChangedCallback
            )

            val notification = mediaNotification.notification
            // Ensure full public visibility on lock screen
            notification.visibility = Notification.VISIBILITY_PUBLIC

            // Adapt for Xiaomi HyperOS Focus Notification & Dynamic Island (miui.focus)
            val extras = notification.extras ?: Bundle()
            extras.putBoolean("miui.focus.enable", true)
            extras.putBoolean("miui.focus.media", true)
            extras.putInt("miui.focus.type", 1) // Ongoing media playback service
            extras.putBoolean("miui.focus.ongoing", true)
            extras.putBoolean("miui.showActionInCompactView", true)
            val title = mediaSession.player.mediaMetadata.title?.toString() ?: ""
            val artist = mediaSession.player.mediaMetadata.artist?.toString() ?: ""
            extras.putString("miui.focus.title", title)
            extras.putString("miui.focus.subtitle", artist)
            extras.putString("miui.focus.ticker", if (artist.isNotBlank()) "$title - $artist" else title)
            notification.extras = extras

            return MediaNotification(mediaNotification.notificationId, notification)
        }

        override fun handleCustomCommand(
            mediaSession: MediaSession,
            action: String,
            extras: Bundle
        ): Boolean {
            return defaultProvider.handleCustomCommand(mediaSession, action, extras)
        }
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
                Player.COMMAND_PLAY_PAUSE -> {
                    if (player.isPlaying) {
                        pauseWithRamp()
                    } else {
                        playWithRamp()
                    }
                    return SessionResult.RESULT_SUCCESS
                }
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

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onBind(intent: Intent?): IBinder? {
        val superBinder = super.onBind(intent)
        return if (intent?.action == "androidx.media3.session.MediaSessionService") {
            superBinder
        } else {
            binder
        }
    }

    /**
     * Resumes or starts playback with a raised-cosine micro volume ramp (~42ms)
     * eliminating DAC capacitor and buffer step popping.
     */
    fun playWithRamp(targetVolume: Float = 1.0f) {
        volumeRampJob?.cancel()
        visualizerProcessor.triggerSoftRamp()
        player.volume = 0f
        player.play()
        volumeRampJob = serviceScope.launch(Dispatchers.Main) {
            val steps = 6
            val stepDelayMs = 7L
            for (i in 1..steps) {
                if (!isActive) return@launch
                val fraction = i.toFloat() / steps.toFloat()
                val gain = (0.5f - 0.5f * kotlin.math.cos(Math.PI * fraction).toFloat()) * targetVolume
                player.volume = gain.coerceIn(0f, 1f)
                delay(stepDelayMs)
            }
            player.volume = targetVolume
        }
    }

    /**
     * Pauses playback with a micro volume fade-out (~20ms) to eliminate audio-track
     * cutoff DC offset click.
     */
    fun pauseWithRamp(onComplete: (() -> Unit)? = null) {
        if (!player.isPlaying) {
            player.pause()
            onComplete?.invoke()
            return
        }
        volumeRampJob?.cancel()
        volumeRampJob = serviceScope.launch(Dispatchers.Main) {
            val currentVol = player.volume
            val steps = 4
            val stepDelayMs = 5L
            for (i in 1..steps) {
                if (!isActive) return@launch
                val fraction = 1f - (i.toFloat() / steps.toFloat())
                val gain = (0.5f - 0.5f * kotlin.math.cos(Math.PI * fraction).toFloat()) * currentVol
                player.volume = gain.coerceIn(0f, 1f)
                delay(stepDelayMs)
            }
            player.pause()
            player.volume = 0f
            onComplete?.invoke()
        }
    }

    /**
     * Smoothly dips volume during seeking to eliminate PCM decode jump popping.
     */
    fun seekWithRamp(positionMs: Long) {
        volumeRampJob?.cancel()
        val wasPlaying = player.isPlaying
        volumeRampJob = serviceScope.launch(Dispatchers.Main) {
            if (wasPlaying) {
                player.volume = 0f
            }
            player.seekTo(positionMs)
            visualizerProcessor.triggerSoftRamp()
            if (wasPlaying) {
                val steps = 5
                val stepDelayMs = 7L
                for (i in 1..steps) {
                    if (!isActive) return@launch
                    val fraction = i.toFloat() / steps.toFloat()
                    val gain = (0.5f - 0.5f * kotlin.math.cos(Math.PI * fraction).toFloat()) * 1.0f
                    player.volume = gain.coerceIn(0f, 1f)
                    delay(stepDelayMs)
                }
                player.volume = 1.0f
            } else {
                player.volume = 0f
            }
        }
    }

    fun prepareSong(song: Song, artworkBytes: ByteArray? = null, playWhenReady: Boolean = true, startPositionMs: Long = 0L) {
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
        if (startPositionMs > 0) {
            player.seekTo(startPositionMs)
        }
        player.prepare()
        if (playWhenReady) {
            playWithRamp()
        } else {
            player.playWhenReady = false
            player.volume = 0f
        }

        mediaSession?.let { session ->
            onUpdateNotification(session, true)
        }
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

        mediaSession?.let { session ->
            onUpdateNotification(session, player.isPlaying)
        }
    }

    companion object {
        const val CHANNEL_ID = "lumina_player_playback_channel_v3"
        const val NOTIFICATION_ID = 1001
    }

    override fun onDestroy() {
        runCatching {
            mediaSession?.let {
                removeSession(it)
                it.release()
            }
            mediaSession = null
        }
        runCatching {
            if (::player.isInitialized) {
                player.release()
            }
        }
        volumeRampJob?.cancel()
        dspController.release()
        visualizerProcessor.resetSpectrum()
        serviceScope.cancel()
        runCatching {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        }
        wakeLock = null
        super.onDestroy()
    }
}
