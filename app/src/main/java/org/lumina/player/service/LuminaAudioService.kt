package org.lumina.player.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
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
import org.lumina.player.data.preferences.LockscreenControlStyle
import org.lumina.player.ui.widget.LuminaPlayerWidgetProvider

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
                presetId = s.eqPreset,
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
                mediaSession?.let { session ->
                    runCatching { onUpdateNotification(session, isPlaying) }
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                mediaSession?.let { session ->
                    runCatching { onUpdateNotification(session, player.isPlaying) }
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

        // Reactively update lockscreen notification when user switches control styles in settings
        serviceScope.launch {
            (application as? LuminaApp)?.themePreferences?.themeSettings?.collect { _ ->
                mediaSession?.let { session ->
                    runCatching { onUpdateNotification(session, player.isPlaying) }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        runCatching {
            when (intent?.action) {
                LuminaPlayerWidgetProvider.ACTION_PLAY_PAUSE -> {
                    (application as? LuminaApp)?.playerController?.togglePlayPause()
                }
                LuminaPlayerWidgetProvider.ACTION_PREV -> {
                    (application as? LuminaApp)?.playerController?.skipPrevious()
                }
                LuminaPlayerWidgetProvider.ACTION_NEXT -> {
                    (application as? LuminaApp)?.playerController?.skipNext()
                }
            }
            mediaSession?.let { session ->
                if (::player.isInitialized && player.isPlaying) {
                    onUpdateNotification(session, true)
                }
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    fun updateNotification() {
        mediaSession?.let { session ->
            runCatching { onUpdateNotification(session, player.isPlaying) }
        }
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
            val app = context.applicationContext as? LuminaApp
            val lockscreenStyle = app?.themePreferences?.themeSettings?.value?.lockscreenControlStyle
                ?: LockscreenControlStyle.LUMINA_COMPACT

            return if (lockscreenStyle == LockscreenControlStyle.SYSTEM_MEDIA) {
                createSystemMediaNotification(
                    mediaSession,
                    customLayout,
                    actionFactory,
                    onNotificationChangedCallback
                )
            } else {
                createLuminaCompactNotification(mediaSession)
            }
        }

        private fun createSystemMediaNotification(
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
            notification.color = 0xFF0284C7.toInt()

            applyHyperOsFocusExtras(notification, mediaSession)

            return MediaNotification(mediaNotification.notificationId, notification)
        }

        private fun createLuminaCompactNotification(mediaSession: MediaSession): MediaNotification {
            val app = context.applicationContext as? LuminaApp
            val song = app?.playerController?.currentSong?.value
            val isPlaying = mediaSession.player.isPlaying

            val title = song?.title
                ?: mediaSession.player.mediaMetadata.title?.toString()
                ?: context.getString(R.string.app_name)
            val artist = if (song != null) {
                "${song.displayArtist} · ${song.displayAlbum}"
            } else {
                mediaSession.player.mediaMetadata.artist?.toString() ?: context.getString(R.string.app_subtitle)
            }

            // Launch app intent
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openPendingIntent = PendingIntent.getActivity(
                context,
                0,
                openAppIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            // Playback controls intents targeting LuminaPlayerWidgetProvider
            val prevPendingIntent = PendingIntent.getBroadcast(
                context,
                11,
                Intent(context, LuminaPlayerWidgetProvider::class.java).apply {
                    action = LuminaPlayerWidgetProvider.ACTION_PREV
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val playPausePendingIntent = PendingIntent.getBroadcast(
                context,
                12,
                Intent(context, LuminaPlayerWidgetProvider::class.java).apply {
                    action = LuminaPlayerWidgetProvider.ACTION_PLAY_PAUSE
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val nextPendingIntent = PendingIntent.getBroadcast(
                context,
                13,
                Intent(context, LuminaPlayerWidgetProvider::class.java).apply {
                    action = LuminaPlayerWidgetProvider.ACTION_NEXT
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val compactViews = RemoteViews(context.packageName, R.layout.notification_media_compact)
            val expandedViews = RemoteViews(context.packageName, R.layout.notification_media_expanded)

            // Setup compact view
            compactViews.setOnClickPendingIntent(R.id.notif_container, openPendingIntent)
            compactViews.setTextViewText(R.id.notif_app_tag, context.getString(R.string.app_name_upper))
            compactViews.setTextViewText(R.id.notif_title, title)
            compactViews.setTextViewText(R.id.notif_artist, artist)
            compactViews.setOnClickPendingIntent(R.id.notif_btn_prev, prevPendingIntent)
            compactViews.setOnClickPendingIntent(R.id.notif_btn_play_pause, playPausePendingIntent)
            compactViews.setOnClickPendingIntent(R.id.notif_btn_next, nextPendingIntent)
            compactViews.setImageViewResource(R.id.notif_btn_prev, R.drawable.ic_widget_prev)
            compactViews.setImageViewResource(
                R.id.notif_btn_play_pause,
                if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
            )
            compactViews.setImageViewResource(R.id.notif_btn_next, R.drawable.ic_widget_next)

            // Setup expanded view
            expandedViews.setOnClickPendingIntent(R.id.notif_exp_container, openPendingIntent)
            expandedViews.setTextViewText(R.id.notif_exp_app_tag, context.getString(R.string.app_name_upper))
            expandedViews.setTextViewText(R.id.notif_exp_title, title)
            expandedViews.setTextViewText(R.id.notif_exp_artist, artist)
            expandedViews.setOnClickPendingIntent(R.id.notif_exp_btn_prev, prevPendingIntent)
            expandedViews.setOnClickPendingIntent(R.id.notif_exp_btn_play_pause, playPausePendingIntent)
            expandedViews.setOnClickPendingIntent(R.id.notif_exp_btn_next, nextPendingIntent)
            expandedViews.setImageViewResource(R.id.notif_exp_btn_prev, R.drawable.ic_widget_prev)
            expandedViews.setImageViewResource(
                R.id.notif_exp_btn_play_pause,
                if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
            )
            expandedViews.setImageViewResource(R.id.notif_exp_btn_next, R.drawable.ic_widget_next)

            // Dynamic audio spec badge in expanded view
            val techInfo = app?.playerController?.currentTechInfo?.value
            val specText = when {
                techInfo != null && techInfo.isHiRes -> {
                    val sampleKhz = (techInfo.sampleRate / 1000.0).let {
                        if (it % 1.0 == 0.0) "${it.toInt()}kHz" else "${it}kHz"
                    }
                    "Hi-Res ${sampleKhz}/${techInfo.bitDepth}bit · DSP"
                }
                techInfo != null && techInfo.format != org.lumina.player.core.model.AudioFormatType.UNKNOWN -> {
                    "${techInfo.format.displayName} · DSP Audio"
                }
                song != null && song.isHiRes -> {
                    "Hi-Res Lossless · DSP"
                }
                else -> {
                    "Hi-Res · DSP Audio"
                }
            }
            expandedViews.setTextViewText(R.id.notif_exp_spec, specText)

            // Artwork processing
            val currentArtwork = app?.playerController?.currentArtwork?.value
            val rawArtwork: Bitmap? = if (currentArtwork != null && !currentArtwork.isRecycled) {
                currentArtwork
            } else {
                val artData = mediaSession.player.mediaMetadata.artworkData
                if (artData != null && artData.isNotEmpty()) {
                    runCatching { BitmapFactory.decodeByteArray(artData, 0, artData.size) }.getOrNull()
                } else {
                    null
                }
            }

            if (rawArtwork != null && !rawArtwork.isRecycled) {
                val roundedArt = getRoundedCornerBitmap(rawArtwork, cornerRadiusPx = 28f)
                compactViews.setImageViewBitmap(R.id.notif_art, roundedArt)
                expandedViews.setImageViewBitmap(R.id.notif_exp_art, roundedArt)
            } else {
                compactViews.setImageViewResource(R.id.notif_art, R.drawable.ic_widget_default_art)
                expandedViews.setImageViewResource(R.id.notif_exp_art, R.drawable.ic_widget_default_art)
            }

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(artist)
                .setContentIntent(openPendingIntent)
                .setOngoing(isPlaying)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setColor(0xFF0284C7.toInt())
                .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                .setCustomContentView(compactViews)
                .setCustomBigContentView(expandedViews)

            val notification = builder.build()
            notification.visibility = Notification.VISIBILITY_PUBLIC
            applyHyperOsFocusExtras(notification, mediaSession)

            return MediaNotification(NOTIFICATION_ID, notification)
        }

        private fun applyHyperOsFocusExtras(notification: Notification, mediaSession: MediaSession) {
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
        }

        private fun getRoundedCornerBitmap(src: Bitmap, cornerRadiusPx: Float): Bitmap {
            return runCatching {
                val size = 160
                val scaled = if (src.width != size || src.height != size) {
                    Bitmap.createScaledBitmap(src, size, size, true)
                } else {
                    src
                }
                val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(output)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    isAntiAlias = true
                    isFilterBitmap = true
                }
                val rect = RectF(0f, 0f, size.toFloat(), size.toFloat())
                canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, paint)
                paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
                canvas.drawBitmap(scaled, 0f, 0f, paint)
                if (scaled !== src) {
                    scaled.recycle()
                }
                output
            }.getOrDefault(src)
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
     * Resumes or starts playback with a smooth Hann envelope anti-pop ramp directly
     * applied to PCM audio buffers via VisualizerAudioProcessor.
     * Keeps player.volume at 1.0f to avoid hardware AudioFlinger mixer step quantization clicks.
     */
    fun playWithRamp() {
        if (!player.isPlaying && player.currentMediaItem != null) {
            player.seekTo(player.currentPosition)
        }
        visualizerProcessor.triggerSoftRamp()
        player.volume = 1.0f
        player.play()
    }

    /**
     * Pauses playback cleanly while preserving normal hardware volume level.
     */
    fun pauseWithRamp(onComplete: (() -> Unit)? = null) {
        player.pause()
        player.volume = 1.0f
        onComplete?.invoke()
    }

    /**
     * Smoothly seeks to target position with an anti-pop soft ramp on subsequent decoded frames.
     */
    fun seekWithRamp(positionMs: Long) {
        visualizerProcessor.triggerSoftRamp()
        player.volume = 1.0f
        player.seekTo(positionMs)
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

        player.volume = 1.0f
        player.setMediaItem(mediaItem)
        if (startPositionMs > 0) {
            player.seekTo(startPositionMs)
        }
        player.prepare()
        if (playWhenReady) {
            visualizerProcessor.triggerSoftRamp()
            player.play()
        } else {
            player.playWhenReady = false
        }

        mediaSession?.let { session ->
            runCatching { onUpdateNotification(session, true) }
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
            runCatching { onUpdateNotification(session, player.isPlaying) }
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
