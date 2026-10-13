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
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
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
import org.lumina.player.LuminaApp
import org.lumina.player.MainActivity
import org.lumina.player.R
import org.lumina.player.core.dsp.AudioDspController
import org.lumina.player.core.model.Song

class LuminaAudioService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    lateinit var player: ExoPlayer
        private set

    val dspController = AudioDspController()

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): LuminaAudioService = this@LuminaAudioService
    }

    override fun onCreate() {
        super.onCreate()

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true) // true = handle audio focus automatically
            .setHandleAudioBecomingNoisy(true) // auto-pause on headphone unplug
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setSeekParameters(androidx.media3.exoplayer.SeekParameters.EXACT)
            .build()

        // 绑定系统音频会话以支持均衡器 (仅当 AudioSessionId 发生变化时附加音效，杜绝切歌或 seek 频繁重建音效产生爆音/破音)
        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                if (audioSessionId != C.AUDIO_SESSION_ID_UNSET && audioSessionId != 0) {
                    dspController.attachAudioSession(audioSessionId)
                }
            }
        })

        val initialSessionId = player.audioSessionId
        if (initialSessionId != C.AUDIO_SESSION_ID_UNSET && initialSessionId != 0) {
            dspController.attachAudioSession(initialSessionId)
        }

        // 创建前台服务通知渠道 (Android 8.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)
            // 清理旧低优先级通道（低优先级通道在 HyperOS 上无法显示锁屏及灵动岛）
            runCatching { notificationManager?.deleteNotificationChannel("lumina_player_playback_channel") }

            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.app_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Lumina Player playback controls"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                enableVibration(false)
                enableLights(false)
                setSound(null, null)
            }
            notificationManager?.createNotificationChannel(channel)
        }

        // 配置默认 MediaNotificationProvider 并包装适配小米 HyperOS 灵动岛 / 锁屏通知
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

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivityPendingIntent)
            .setCallback(MediaSessionCallback())
            .build()

        // 关键：必须调用 addSession 才能激活 Media3 内部 MediaNotificationManager 维持前台播控通知
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
            // 确保锁屏完全可见 (Public visibility on lockscreen)
            notification.visibility = Notification.VISIBILITY_PUBLIC
            notification.priority = Notification.PRIORITY_MAX

            // 适配小米 HyperOS 灵动岛 / 超级岛 / 焦点通知 (HyperIsland / Focus Notification)
            val extras = notification.extras ?: Bundle()
            extras.putBoolean("miui.focus.enable", true)
            extras.putBoolean("miui.focus.media", true)
            extras.putInt("miui.focus.type", 1) // 持续媒体播放服务
            extras.putBoolean("miui.focus.ongoing", true)
            val title = mediaSession.player.mediaMetadata.title?.toString() ?: ""
            val artist = mediaSession.player.mediaMetadata.artist?.toString() ?: ""
            extras.putString("miui.focus.title", title)
            extras.putString("miui.focus.subtitle", artist)
            extras.putString("miui.focus.ticker", if (artist.isNotBlank()) "$title - $artist" else title)
            extras.putBoolean("miui.showActionInCompactView", true)
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

    fun prepareSong(song: Song, artworkBytes: ByteArray? = null, playWhenReady: Boolean = true) {
        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.displayArtist)
            .setAlbumTitle(song.displayAlbum)

        if (artworkBytes != null && artworkBytes.isNotEmpty()) {
            metadataBuilder.setArtworkData(artworkBytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
        }

        val metadata = metadataBuilder.build()

        // 对于 CUE 分轨，底层播放器必须加载真实的物理音频文件路径，而非带 #cue 标记的虚拟 URI
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
            onUpdateNotification(session, isPlaybackOngoing)
        }
    }

    companion object {
        const val CHANNEL_ID = "lumina_player_playback_channel_v2"
        const val NOTIFICATION_ID = 1001
    }

    override fun onDestroy() {
        mediaSession?.run {
            removeSession(this)
            player.release()
            release()
            mediaSession = null
        }
        dspController.release()
        super.onDestroy()
    }
}
