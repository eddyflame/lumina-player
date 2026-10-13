package org.lumina.player.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.lumina.player.core.dsp.AudioDspController
import org.lumina.player.core.lyrics.LyricLine
import org.lumina.player.core.lyrics.LrcParser
import org.lumina.player.core.model.AudioTechnicalInfo
import org.lumina.player.core.model.PlayMode
import org.lumina.player.core.model.Song
import org.lumina.player.core.tag.AudioTagReader
import org.lumina.player.data.repository.MusicRepository
import kotlin.random.Random

class PlayerController(
    private val context: Context,
    private val repository: MusicRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var service: LuminaAudioService? = null
    private var isBound = false

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    val queue: StateFlow<List<Song>> = _queue.asStateFlow()

    private val _queueIndex = MutableStateFlow(-1)
    val queueIndex: StateFlow<Int> = _queueIndex.asStateFlow()

    private val _playMode = MutableStateFlow(PlayMode.REPEAT_ALL)
    val playMode: StateFlow<PlayMode> = _playMode.asStateFlow()

    private val _currentLyrics = MutableStateFlow<List<LyricLine>>(emptyList())
    val currentLyrics: StateFlow<List<LyricLine>> = _currentLyrics.asStateFlow()

    private val _lyricsOffsetMs = MutableStateFlow(0L)
    val lyricsOffsetMs: StateFlow<Long> = _lyricsOffsetMs.asStateFlow()

    private val _currentTechInfo = MutableStateFlow(AudioTechnicalInfo())
    val currentTechInfo: StateFlow<AudioTechnicalInfo> = _currentTechInfo.asStateFlow()

    private val _sleepTimerRemainingSeconds = MutableStateFlow<Long?>(null)
    val sleepTimerRemainingSeconds: StateFlow<Long?> = _sleepTimerRemainingSeconds.asStateFlow()

    private val _currentArtwork = MutableStateFlow<android.graphics.Bitmap?>(null)
    val currentArtwork: StateFlow<android.graphics.Bitmap?> = _currentArtwork.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private var positionTrackerJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var metadataJob: Job? = null
    private var stopAtEndOfTrack = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as? LuminaAudioService.LocalBinder
            service = localBinder?.getService()
            service?.player?.addListener(playerListener)
            _currentSong.value?.let { pendingSong ->
                if (service?.player?.currentMediaItem == null) {
                    val shouldPlay = _isPlaying.value
                    service?.prepareSong(pendingSong, playWhenReady = shouldPlay)
                    val pos = _currentPositionMs.value
                    if (pos > 0) {
                        service?.player?.seekTo(pos)
                    }
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service?.player?.removeListener(playerListener)
            service = null
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) {
            _isPlaying.value = playing
            notifyWidgetUpdate()
            if (playing) {
                startPositionTracker()
            } else {
                stopPositionTracker()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> {
                    val dur = service?.player?.duration ?: 0L
                    if (dur > 0) _durationMs.value = dur
                }
                Player.STATE_ENDED -> {
                    if (stopAtEndOfTrack) {
                        stopAtEndOfTrack = false
                        cancelSleepTimer()
                        pause()
                    } else {
                        onTrackEnded()
                    }
                }
            }
        }
    }

    init {
        bindAudioService()
        scope.launch {
            repository.songsFlow.collect { songs ->
                if (_currentSong.value == null && songs.isNotEmpty()) {
                    restoreLastPlaybackState(songs)
                }
            }
        }
    }

    private fun bindAudioService() {
        val intent = Intent(context, LuminaAudioService::class.java)
        runCatching {
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            isBound = true
        }
    }

    fun playSong(song: Song, newQueue: List<Song> = emptyList()) {
        val intent = Intent(context, LuminaAudioService::class.java)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        if (newQueue.isNotEmpty()) {
            _queue.value = newQueue
            _queueIndex.value = newQueue.indexOfFirst { it.uri == song.uri }.coerceAtLeast(0)
        } else {
            val currentList = _queue.value
            val idx = currentList.indexOfFirst { it.uri == song.uri }
            if (idx >= 0) {
                _queueIndex.value = idx
            } else {
                val updated = currentList + song
                _queue.value = updated
                _queueIndex.value = updated.size - 1
            }
        }

        _currentSong.value = song
        _currentPositionMs.value = 0L
        _durationMs.value = song.durationMs
        _lyricsOffsetMs.value = 0L
        _currentArtwork.value = null
        notifyWidgetUpdate()

        val audioPrefs = (context.applicationContext as? org.lumina.player.LuminaApp)?.audioPreferences
        audioPrefs?.setLastSongUri(song.uri)
        audioPrefs?.setLastPositionMs(0L)
        audioPrefs?.setLastQueueUris(_queue.value.map { it.uri })

        // Load artwork, lyrics, and technical audio parameters asynchronously
        metadataJob?.cancel()
        metadataJob = scope.launch(Dispatchers.IO) {
            val art = AudioTagReader.extractArtwork(context, song.uri, song.filePath)
            if (_currentSong.value?.uri != song.uri) return@launch
            val artBytes = art?.let { bitmap ->
                val stream = java.io.ByteArrayOutputStream()
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, stream)
                stream.toByteArray()
            }
            if (_currentSong.value?.uri != song.uri) return@launch
            withContext(Dispatchers.Main) {
                if (_currentSong.value?.uri == song.uri) {
                    _currentArtwork.value = art
                    notifyWidgetUpdate()
                    if (artBytes != null) {
                        service?.updateArtwork(artBytes)
                    }
                }
            }

            val meta = AudioTagReader.extractMetadata(context, song.uri, song.filePath)
            if (_currentSong.value?.uri != song.uri) return@launch
            _currentTechInfo.value = meta.technicalInfo

            val lyricsText = meta.embeddedLyrics ?: AudioTagReader.findExternalLrc(song.filePath)
            val parsed = if (!lyricsText.isNullOrBlank()) LrcParser.parse(lyricsText) else emptyList()
            if (_currentSong.value?.uri != song.uri) return@launch
            _currentLyrics.value = parsed

            // Record song play count
            repository.recordPlay(song.uri)
        }

        service?.prepareSong(song, playWhenReady = true)
    }

    fun togglePlayPause() {
        val player = service?.player
        if (player == null || player.currentMediaItem == null) {
            _currentSong.value?.let { restoredSong ->
                val startPos = _currentPositionMs.value
                playSong(restoredSong, _queue.value)
                if (startPos > 0) {
                    seekTo(startPos)
                }
                return
            }
        }
        if (player?.isPlaying == true) {
            player.pause()
        } else {
            if (_currentSong.value != null) {
                player?.play()
            } else if (_queue.value.isNotEmpty()) {
                val first = _queue.value.first()
                playSong(first)
            }
        }
    }

    fun play() {
        val player = service?.player
        if (player == null || player.currentMediaItem == null) {
            _currentSong.value?.let { restoredSong ->
                val startPos = _currentPositionMs.value
                playSong(restoredSong, _queue.value)
                if (startPos > 0) {
                    seekTo(startPos)
                }
                return
            }
        }
        player?.play()
    }

    fun pause() {
        service?.player?.pause()
    }

    fun seekTo(positionMs: Long) {
        val player = service?.player ?: return
        val duration = _durationMs.value
        val safePosition = if (duration > 0) positionMs.coerceIn(0L, duration) else positionMs.coerceAtLeast(0L)
        _currentPositionMs.value = safePosition
        player.seekTo(safePosition)
    }

    fun skipNext() {
        val q = _queue.value
        if (q.isEmpty()) return

        val nextIndex = when (_playMode.value) {
            PlayMode.SHUFFLE -> {
                if (q.size > 1) {
                    var r = Random.nextInt(q.size)
                    while (r == _queueIndex.value) {
                        r = Random.nextInt(q.size)
                    }
                    r
                } else {
                    0
                }
            }
            PlayMode.REPEAT_ONE -> _queueIndex.value
            PlayMode.REPEAT_ALL, PlayMode.SEQUENTIAL -> (_queueIndex.value + 1) % q.size
        }

        if (nextIndex in q.indices) {
            playSong(q[nextIndex])
        }
    }

    fun skipPrevious() {
        val q = _queue.value
        if (q.isEmpty()) return

        // If playback position exceeds 3 seconds, seek back to beginning of current track
        if (_currentPositionMs.value > 3000L) {
            seekTo(0L)
            return
        }

        val prevIndex = when (_playMode.value) {
            PlayMode.SHUFFLE -> {
                if (q.size > 1) {
                    var r = Random.nextInt(q.size)
                    while (r == _queueIndex.value) {
                        r = Random.nextInt(q.size)
                    }
                    r
                } else {
                    0
                }
            }
            PlayMode.REPEAT_ONE -> _queueIndex.value
            PlayMode.REPEAT_ALL, PlayMode.SEQUENTIAL -> {
                if (_queueIndex.value - 1 < 0) q.size - 1 else _queueIndex.value - 1
            }
        }

        if (prevIndex in q.indices) {
            playSong(q[prevIndex])
        }
    }

    fun setPlayMode(mode: PlayMode) {
        _playMode.value = mode
    }

    fun adjustLyricsOffset(deltaMs: Long) {
        _lyricsOffsetMs.value += deltaMs
    }

    fun resetLyricsOffset() {
        _lyricsOffsetMs.value = 0L
    }

    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
        service?.player?.playbackParameters = PlaybackParameters(speed)
    }

    fun removeFromQueue(index: Int) {
        val q = _queue.value.toMutableList()
        if (index in q.indices) {
            q.removeAt(index)
            _queue.value = q
            if (index == _queueIndex.value) {
                if (q.isNotEmpty()) {
                    val newIdx = index.coerceAtMost(q.size - 1)
                    playSong(q[newIdx])
                } else {
                    service?.player?.stop()
                    _currentSong.value = null
                    _queueIndex.value = -1
                }
            } else if (index < _queueIndex.value) {
                _queueIndex.value -= 1
            }
            notifyWidgetUpdate()
        }
    }

    fun clearQueue() {
        service?.player?.stop()
        metadataJob?.cancel()
        cancelSleepTimer()
        _queue.value = emptyList()
        _queueIndex.value = -1
        _currentSong.value = null
        _currentArtwork.value = null
        _isPlaying.value = false
        notifyWidgetUpdate()
    }

    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        stopAtEndOfTrack = false
        if (minutes <= 0) {
            _sleepTimerRemainingSeconds.value = null
            return
        }

        val totalSeconds = minutes * 60L
        _sleepTimerRemainingSeconds.value = totalSeconds

        sleepTimerJob = scope.launch {
            var remaining = totalSeconds
            while (isActive && remaining > 0) {
                delay(1000L)
                remaining--
                _sleepTimerRemainingSeconds.value = remaining

                // Gentle volume fade-out during last 10 seconds
                if (remaining <= 10) {
                    val volume = remaining / 10f
                    service?.player?.volume = volume.coerceIn(0f, 1f)
                }
            }
            // Sleep timer countdown finished
            pause()
            service?.player?.volume = 1f
            _sleepTimerRemainingSeconds.value = null
        }
    }

    fun setSleepTimerEndOfTrack() {
        sleepTimerJob?.cancel()
        stopAtEndOfTrack = true
        _sleepTimerRemainingSeconds.value = -1L // Indicates stop at the end of current track
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        stopAtEndOfTrack = false
        service?.player?.volume = 1f
        _sleepTimerRemainingSeconds.value = null
    }

    private val fallbackDspController by lazy { AudioDspController() }

    fun getDspController(): AudioDspController = service?.dspController ?: fallbackDspController

    private fun onTrackEnded() {
        when (_playMode.value) {
            PlayMode.REPEAT_ONE -> {
                seekTo(0L)
                play()
            }
            PlayMode.SEQUENTIAL -> {
                val q = _queue.value
                if (_queueIndex.value + 1 < q.size) {
                    skipNext()
                } else {
                    pause()
                }
            }
            PlayMode.REPEAT_ALL, PlayMode.SHUFFLE -> {
                skipNext()
            }
        }
    }

    private fun startPositionTracker() {
        positionTrackerJob?.cancel()
        positionTrackerJob = scope.launch {
            var saveCounter = 0
            while (isActive) {
                val pos = service?.player?.currentPosition ?: 0L
                _currentPositionMs.value = pos
                val dur = service?.player?.duration ?: 0L
                if (dur > 0 && dur != _durationMs.value) {
                    _durationMs.value = dur
                }
                saveCounter++
                if (saveCounter >= 5) { // Persist playback position once per second
                    saveCounter = 0
                    val audioPrefs = (context.applicationContext as? org.lumina.player.LuminaApp)?.audioPreferences
                    audioPrefs?.setLastPositionMs(pos)
                }
                delay(200L)
            }
        }
    }

    private fun stopPositionTracker() {
        positionTrackerJob?.cancel()
    }

    /**
     * Restores previous playback song, queue, and position from interrupted/closed session.
     * Prevents MiniPlayer loss and allows resuming seamless playback.
     */
    fun restoreLastPlaybackState(songs: List<Song>) {
        if (_currentSong.value != null || songs.isEmpty()) return
        val audioPrefs = (context.applicationContext as? org.lumina.player.LuminaApp)?.audioPreferences ?: return
        val lastUri = audioPrefs.getLastSongUri() ?: return
        val targetSong = songs.firstOrNull { it.uri == lastUri } ?: return
        val lastPos = audioPrefs.getLastPositionMs()
        val queueUris = audioPrefs.getLastQueueUris()
        val restoredQueue = if (queueUris.isNotEmpty()) {
            val map = songs.associateBy { it.uri }
            queueUris.mapNotNull { map[it] }.ifEmpty { listOf(targetSong) }
        } else {
            listOf(targetSong)
        }

        _queue.value = restoredQueue
        _queueIndex.value = restoredQueue.indexOfFirst { it.uri == targetSong.uri }.coerceAtLeast(0)
        _currentSong.value = targetSong
        _currentPositionMs.value = lastPos
        _durationMs.value = targetSong.durationMs
        notifyWidgetUpdate()

        // Silently preload artwork, lyrics, and metadata in the background
        metadataJob?.cancel()
        metadataJob = scope.launch(Dispatchers.IO) {
            val art = AudioTagReader.extractArtwork(context, targetSong.uri, targetSong.filePath)
            if (_currentSong.value?.uri != targetSong.uri) return@launch
            val meta = AudioTagReader.extractMetadata(context, targetSong.uri, targetSong.filePath)
            if (_currentSong.value?.uri != targetSong.uri) return@launch
            val lyricsText = meta.embeddedLyrics ?: AudioTagReader.findExternalLrc(targetSong.filePath)
            val parsed = if (!lyricsText.isNullOrBlank()) LrcParser.parse(lyricsText) else emptyList()
            if (_currentSong.value?.uri != targetSong.uri) return@launch
            withContext(Dispatchers.Main) {
                if (_currentSong.value?.uri == targetSong.uri) {
                    _currentArtwork.value = art
                    _currentTechInfo.value = meta.technicalInfo
                    _currentLyrics.value = parsed
                    notifyWidgetUpdate()
                }
            }
        }
    }

    private fun notifyWidgetUpdate() {
        org.lumina.player.ui.widget.LuminaPlayerWidgetProvider.updateAll(
            context,
            _currentSong.value,
            _isPlaying.value,
            _currentArtwork.value
        )
    }

    fun release() {
        stopPositionTracker()
        sleepTimerJob?.cancel()
        if (isBound) {
            context.unbindService(serviceConnection)
            isBound = false
        }
    }
}
