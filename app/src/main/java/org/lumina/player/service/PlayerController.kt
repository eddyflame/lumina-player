package org.lumina.player.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
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

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private var positionTrackerJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var stopAtEndOfTrack = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as? LuminaAudioService.LocalBinder
            service = localBinder?.getService()
            service?.player?.addListener(playerListener)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service?.player?.removeListener(playerListener)
            service = null
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) {
            _isPlaying.value = playing
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
    }

    private fun bindAudioService() {
        val intent = Intent(context, LuminaAudioService::class.java)
        context.startService(intent)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        isBound = true
    }

    fun playSong(song: Song, newQueue: List<Song> = emptyList()) {
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

        // 加载歌词与技术参数
        scope.launch(Dispatchers.IO) {
            val meta = AudioTagReader.extractMetadata(context, song.uri, song.filePath)
            _currentTechInfo.value = meta.technicalInfo

            val lyricsText = meta.embeddedLyrics ?: AudioTagReader.findExternalLrc(song.filePath)
            val parsed = if (!lyricsText.isNullOrBlank()) LrcParser.parse(lyricsText) else emptyList()
            _currentLyrics.value = parsed

            // 记录曲目播放计数
            repository.recordPlay(song.uri)
        }

        service?.prepareSong(song, playWhenReady = true)
    }

    fun togglePlayPause() {
        val player = service?.player ?: return
        if (player.isPlaying) {
            player.pause()
        } else {
            if (_currentSong.value != null) {
                player.play()
            } else if (_queue.value.isNotEmpty()) {
                val first = _queue.value.first()
                playSong(first)
            }
        }
    }

    fun play() {
        service?.player?.play()
    }

    fun pause() {
        service?.player?.pause()
    }

    fun seekTo(positionMs: Long) {
        _currentPositionMs.value = positionMs
        service?.player?.seekTo(positionMs)
    }

    fun skipNext() {
        val q = _queue.value
        if (q.isEmpty()) return

        val nextIndex = when (_playMode.value) {
            PlayMode.SHUFFLE -> Random.nextInt(q.size)
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

        // 如果播放时间已经超过 3 秒，先跳回本首歌开头
        if (_currentPositionMs.value > 3000L) {
            seekTo(0L)
            return
        }

        val prevIndex = when (_playMode.value) {
            PlayMode.SHUFFLE -> Random.nextInt(q.size)
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
        }
    }

    fun clearQueue() {
        service?.player?.stop()
        _queue.value = emptyList()
        _queueIndex.value = -1
        _currentSong.value = null
        _isPlaying.value = false
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

                // 剩余 10 秒开启温和音量淡出
                if (remaining <= 10) {
                    val volume = remaining / 10f
                    service?.player?.volume = volume.coerceIn(0f, 1f)
                }
            }
            // 倒计时结束
            pause()
            service?.player?.volume = 1f
            _sleepTimerRemainingSeconds.value = null
        }
    }

    fun setSleepTimerEndOfTrack() {
        sleepTimerJob?.cancel()
        stopAtEndOfTrack = true
        _sleepTimerRemainingSeconds.value = -1L // 表示播完当前曲目
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        stopAtEndOfTrack = false
        service?.player?.volume = 1f
        _sleepTimerRemainingSeconds.value = null
    }

    fun getDspController() = service?.dspController

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
            while (isActive) {
                val pos = service?.player?.currentPosition ?: 0L
                _currentPositionMs.value = pos
                val dur = service?.player?.duration ?: 0L
                if (dur > 0 && dur != _durationMs.value) {
                    _durationMs.value = dur
                }
                delay(200L)
            }
        }
    }

    private fun stopPositionTracker() {
        positionTrackerJob?.cancel()
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
