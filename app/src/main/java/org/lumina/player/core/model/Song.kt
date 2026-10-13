package org.lumina.player.core.model

import android.net.Uri

/**
 * Core audio track domain model representing local songs and virtual CUE sheet tracks.
 */
data class Song(
    val id: Long,
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long = 0L,
    val durationMs: Long = 0L,
    val filePath: String = "",
    val fileSize: Long = 0L,
    val mimeType: String = "audio/*",
    val trackNumber: Int = 0,
    val year: Int = 0,
    val genre: String = "",
    val bitrateKbps: Int = 0,
    val sampleRate: Int = 44100,
    val bitDepth: Int = 16,
    val channels: Int = 2,
    val audioFormat: AudioFormatType = AudioFormatType.UNKNOWN,
    val isFavorite: Boolean = false,
    val playCount: Int = 0,
    val lastPlayedTime: Long = 0L,
    val dateAdded: Long = 0L,
    // CUE sheet virtual split track attributes
    val isVirtualCueTrack: Boolean = false,
    val cueStartMs: Long = 0L,
    val cueEndMs: Long = 0L
) {
    val durationFormatted: String
        get() {
            val totalSeconds = (if (isVirtualCueTrack && cueEndMs > cueStartMs) (cueEndMs - cueStartMs) else durationMs) / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return String.format("%02d:%02d", minutes, seconds)
        }

    val displayArtist: String
        get() = artist.ifBlank { "Unknown Artist" }

    val displayAlbum: String
        get() = album.ifBlank { "Unknown Album" }

    val isHiRes: Boolean
        get() = audioFormat.isHiRes || bitDepth >= 24 || sampleRate >= 48000 || audioFormat == AudioFormatType.DSD

    val hiResBadgeText: String?
        get() = when {
            audioFormat == AudioFormatType.DSD -> "DSD"
            bitDepth >= 24 && sampleRate >= 96000 -> "${bitDepth}B/${sampleRate / 1000}k"
            bitDepth >= 24 -> "${bitDepth}BIT"
            audioFormat.isLossless -> audioFormat.displayName
            else -> null
        }
}
