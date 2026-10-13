package org.lumina.player.data.preferences

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.lumina.player.core.model.PlayMode

data class AudioSettings(
    val eqEnabled: Boolean = true,
    val eqPreset: String = "flat",
    val bassBoost: Int = 0,
    val virtualizer: Int = 0,
    val crossfadeSeconds: Int = 0,
    val replayGainEnabled: Boolean = false,
    val filterShortAudio: Boolean = true,
    val playMode: PlayMode = PlayMode.REPEAT_ALL
)

class AudioPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AudioSettings> = _settings.asStateFlow()

    private fun loadSettings(): AudioSettings {
        val eqEnabled = prefs.getBoolean(KEY_EQ_ENABLED, true)
        val eqPreset = prefs.getString(KEY_EQ_PRESET, "flat") ?: "flat"
        val bassBoost = prefs.getInt(KEY_BASS_BOOST, 0)
        val virtualizer = prefs.getInt(KEY_VIRTUALIZER, 0)
        val crossfade = prefs.getInt(KEY_CROSSFADE, 0)
        val replayGain = prefs.getBoolean(KEY_REPLAY_GAIN, false)
        val filterShort = prefs.getBoolean(KEY_FILTER_SHORT, true)
        val playModeStr = prefs.getString(KEY_PLAY_MODE, PlayMode.REPEAT_ALL.name) ?: PlayMode.REPEAT_ALL.name
        val playMode = runCatching { PlayMode.valueOf(playModeStr) }.getOrDefault(PlayMode.REPEAT_ALL)

        return AudioSettings(
            eqEnabled = eqEnabled,
            eqPreset = eqPreset,
            bassBoost = bassBoost,
            virtualizer = virtualizer,
            crossfadeSeconds = crossfade,
            replayGainEnabled = replayGain,
            filterShortAudio = filterShort,
            playMode = playMode
        )
    }

    fun setEqEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_EQ_ENABLED, enabled).apply()
        _settings.value = _settings.value.copy(eqEnabled = enabled)
    }

    fun setEqPreset(preset: String) {
        prefs.edit().putString(KEY_EQ_PRESET, preset).apply()
        _settings.value = _settings.value.copy(eqPreset = preset)
    }

    fun setBassBoost(strength: Int) {
        prefs.edit().putInt(KEY_BASS_BOOST, strength).apply()
        _settings.value = _settings.value.copy(bassBoost = strength)
    }

    fun setVirtualizer(strength: Int) {
        prefs.edit().putInt(KEY_VIRTUALIZER, strength).apply()
        _settings.value = _settings.value.copy(virtualizer = strength)
    }

    fun setCrossfade(seconds: Int) {
        prefs.edit().putInt(KEY_CROSSFADE, seconds).apply()
        _settings.value = _settings.value.copy(crossfadeSeconds = seconds)
    }

    fun setReplayGain(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_REPLAY_GAIN, enabled).apply()
        _settings.value = _settings.value.copy(replayGainEnabled = enabled)
    }

    fun setFilterShortAudio(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FILTER_SHORT, enabled).apply()
        _settings.value = _settings.value.copy(filterShortAudio = enabled)
    }

    fun setPlayMode(mode: PlayMode) {
        prefs.edit().putString(KEY_PLAY_MODE, mode.name).apply()
        _settings.value = _settings.value.copy(playMode = mode)
    }

    fun getLastSongUri(): String? = prefs.getString(KEY_LAST_SONG_URI, null)

    fun setLastSongUri(uri: String?) {
        prefs.edit().putString(KEY_LAST_SONG_URI, uri).apply()
    }

    fun getLastPositionMs(): Long = prefs.getLong(KEY_LAST_POSITION_MS, 0L)

    fun setLastPositionMs(pos: Long) {
        prefs.edit().putLong(KEY_LAST_POSITION_MS, pos).apply()
    }

    fun getLastQueueUris(): List<String> {
        val raw = prefs.getString(KEY_LAST_QUEUE_URIS, null) ?: return emptyList()
        return raw.split(",").filter { it.isNotBlank() }
    }

    fun setLastQueueUris(uris: List<String>) {
        val raw = uris.joinToString(",")
        prefs.edit().putString(KEY_LAST_QUEUE_URIS, raw).apply()
    }

    companion object {
        private const val PREFS_NAME = "lumina_audio_prefs"
        private const val KEY_EQ_ENABLED = "key_eq_enabled"
        private const val KEY_EQ_PRESET = "key_eq_preset"
        private const val KEY_BASS_BOOST = "key_bass_boost"
        private const val KEY_VIRTUALIZER = "key_virtualizer"
        private const val KEY_CROSSFADE = "key_crossfade"
        private const val KEY_REPLAY_GAIN = "key_replay_gain"
        private const val KEY_FILTER_SHORT = "key_filter_short"
        private const val KEY_PLAY_MODE = "key_play_mode"
        private const val KEY_LAST_SONG_URI = "key_last_song_uri"
        private const val KEY_LAST_POSITION_MS = "key_last_position_ms"
        private const val KEY_LAST_QUEUE_URIS = "key_last_queue_uris"
    }
}
