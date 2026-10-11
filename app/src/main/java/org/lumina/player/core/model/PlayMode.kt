package org.lumina.player.core.model

/**
 * 播放模式
 */
enum class PlayMode {
    /** 顺序播放（列表播放到底停止） */
    SEQUENTIAL,
    /** 列表循环 */
    REPEAT_ALL,
    /** 单曲循环 */
    REPEAT_ONE,
    /** 随机播放 */
    SHUFFLE;

    fun next(): PlayMode = when (this) {
        SEQUENTIAL -> REPEAT_ALL
        REPEAT_ALL -> REPEAT_ONE
        REPEAT_ONE -> SHUFFLE
        SHUFFLE -> SEQUENTIAL
    }
}
