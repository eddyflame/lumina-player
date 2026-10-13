package org.lumina.player.data.scanner

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.lumina.player.core.cue.CueSheetParser
import org.lumina.player.core.model.AudioFormatType
import org.lumina.player.core.model.Song
import org.lumina.player.core.tag.AudioTagReader
import java.io.File

/**
 * Asynchronous scanner for Android MediaStore and local filesystem including CUE sheet companion track detection.
 */
class MediaStoreScanner(private val context: Context) {

    /**
     * Scans system MediaStore for audio files.
     */
    suspend fun scanMediaStore(filterShortAudio: Boolean = true): List<Song> = withContext(Dispatchers.IO) {
        val songs = mutableListOf<Song>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DATE_ADDED
        )

        val minDuration = if (filterShortAudio) 30_000L else 5_000L
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= ?"
        val selectionArgs = arrayOf(minDuration.toString())
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        try {
            val cursor = context.contentResolver.query(
                collection,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )

            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durationCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val sizeCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val mimeCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val trackCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                val yearCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
                val dateAddedCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)

                while (it.moveToNext()) {
                    val id = it.getLong(idCol)
                    val title = it.getString(titleCol) ?: "Unknown Title"
                    val artist = it.getString(artistCol) ?: "Unknown Artist"
                    val album = it.getString(albumCol) ?: "Unknown Album"
                    val albumId = it.getLong(albumIdCol)
                    val duration = it.getLong(durationCol)
                    val filePath = it.getString(dataCol) ?: ""
                    val size = it.getLong(sizeCol)
                    val mime = it.getString(mimeCol) ?: "audio/*"
                    val track = it.getInt(trackCol)
                    val year = it.getInt(yearCol)
                    val dateAdded = it.getLong(dateAddedCol)

                    val contentUri = ContentUris.withAppendedId(collection, id).toString()
                    val format = AudioFormatType.fromExtension(filePath.ifBlank { title })

                    // Check for companion .cue file in the same directory
                    val cueFile = findAccompanyingCue(filePath)
                    if (cueFile != null && File(filePath).exists()) {
                        val cueText = runCatching { cueFile.readText(Charsets.UTF_8) }.getOrNull()
                            ?: runCatching { cueFile.readText(charset("GBK")) }.getOrNull()
                        if (cueText != null) {
                            val cueSheet = CueSheetParser.parse(cueText)
                            if (cueSheet.tracks.isNotEmpty()) {
                                val virtualTracks = CueSheetParser.createVirtualSongs(cueSheet, File(filePath), duration)
                                songs.addAll(virtualTracks)
                                continue // Already split into virtual tracks, skip full image track
                            }
                        }
                    }

                    // Supplement technical parameters and tags
                    val meta = if (filePath.isNotBlank()) {
                        AudioTagReader.extractMetadata(context, contentUri, filePath)
                    } else null

                    val song = Song(
                        id = id,
                        uri = contentUri,
                        title = meta?.title?.ifBlank { title } ?: title,
                        artist = meta?.artist?.ifBlank { artist } ?: artist,
                        album = meta?.album?.ifBlank { album } ?: album,
                        albumId = albumId,
                        durationMs = if (duration > 0) duration else meta?.durationMs ?: 0L,
                        filePath = filePath,
                        fileSize = size,
                        mimeType = mime,
                        trackNumber = track,
                        year = if (year > 0) year else meta?.year ?: 0,
                        genre = meta?.genre ?: "",
                        bitrateKbps = meta?.technicalInfo?.bitrateKbps ?: 0,
                        sampleRate = meta?.technicalInfo?.sampleRate ?: 44100,
                        bitDepth = meta?.technicalInfo?.bitDepth ?: 16,
                        channels = meta?.technicalInfo?.channels ?: 2,
                        audioFormat = format,
                        dateAdded = dateAdded
                    )
                    songs.add(song)
                }
            }
        } catch (_: Exception) {
            // Fail-safe fault tolerance
        }

        songs
    }

    /**
     * Traverses physical directories for deep offline folder scanning (suitable for external storage & Hi-Fi folders).
     */
    suspend fun scanDirectory(dir: File): List<Song> = withContext(Dispatchers.IO) {
        val songs = mutableListOf<Song>()
        if (!dir.exists() || !dir.isDirectory) return@withContext songs

        dir.walkTopDown()
            .filter { it.isFile }
            .filter { file ->
                val ext = file.extension.lowercase()
                ext in listOf("mp3", "flac", "wav", "ape", "wv", "dsf", "dff", "m4a", "aac", "ogg", "opus", "aiff")
            }
            .forEach { file ->
                val meta = AudioTagReader.extractMetadata(context, "file://${file.absolutePath}", file.absolutePath)
                val format = AudioFormatType.fromExtension(file.name)

                val song = Song(
                    id = file.absolutePath.hashCode().toLong(),
                    uri = "file://${file.absolutePath}",
                    title = meta.title?.ifBlank { file.nameWithoutExtension } ?: file.nameWithoutExtension,
                    artist = meta.artist?.ifBlank { "Unknown Artist" } ?: "Unknown Artist",
                    album = meta.album?.ifBlank { dir.name } ?: dir.name,
                    durationMs = meta.durationMs,
                    filePath = file.absolutePath,
                    fileSize = file.length(),
                    trackNumber = 0,
                    year = meta.year,
                    genre = meta.genre ?: "",
                    bitrateKbps = meta.technicalInfo.bitrateKbps,
                    sampleRate = meta.technicalInfo.sampleRate,
                    bitDepth = meta.technicalInfo.bitDepth,
                    channels = meta.technicalInfo.channels,
                    audioFormat = format,
                    dateAdded = file.lastModified() / 1000L
                )
                songs.add(song)
            }

        songs
    }

    private fun findAccompanyingCue(filePath: String): File? {
        if (filePath.isBlank()) return null
        val audioFile = File(filePath)
        if (!audioFile.exists()) return null
        val parent = audioFile.parentFile ?: return null

        // Strategy 1: audioFileName.cue
        val directCue = File(parent, "${audioFile.nameWithoutExtension}.cue")
        if (directCue.exists()) return directCue

        // Strategy 2: unique .cue file in directory
        val cueFiles = parent.listFiles { f -> f.extension.equals("cue", ignoreCase = true) }
        return if (cueFiles != null && cueFiles.size == 1) cueFiles[0] else null
    }
}
