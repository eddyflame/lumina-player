package org.lumina.player.core.model

/**
 * Playback queue repetition and shuffling modes.
 */
enum class PlayMode {
    /** Sequential playback (stops at the end of playlist) */
    SEQUENTIAL,
    /** Repeat all songs in playlist */
    REPEAT_ALL,
    /** Repeat single track */
    REPEAT_ONE,
    /** Shuffle playlist order */
    SHUFFLE;

    fun next(): PlayMode = when (this) {
        SEQUENTIAL -> REPEAT_ALL
        REPEAT_ALL -> REPEAT_ONE
        REPEAT_ONE -> SHUFFLE
        SHUFFLE -> SEQUENTIAL
    }
}
