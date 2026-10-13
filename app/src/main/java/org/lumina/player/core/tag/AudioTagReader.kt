package org.lumina.player.core.tag

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import org.lumina.player.core.model.AudioFormatType
import org.lumina.player.core.model.AudioTechnicalInfo
import java.io.File

/**
 * Pure Kotlin audio metadata, artwork, and lyrics extractor using MediaMetadataRetriever.
 */
object AudioTagReader {

    data class ExtractedAudioMeta(
        val title: String? = null,
        val artist: String? = null,
        val album: String? = null,
        val year: Int = 0,
        val genre: String? = null,
        val durationMs: Long = 0L,
        val embeddedLyrics: String? = null,
        val technicalInfo: AudioTechnicalInfo = AudioTechnicalInfo()
    )

    /**
     * Extracts audio metadata, ID3 tags, and technical parameters.
     */
    fun extractMetadata(context: Context, uriString: String, filePath: String): ExtractedAudioMeta {
        val retriever = MediaMetadataRetriever()
        return try {
            if (filePath.isNotBlank() && File(filePath).exists()) {
                retriever.setDataSource(filePath)
            } else {
                retriever.setDataSource(context, Uri.parse(uriString))
            }

            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            val yearStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
            val genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val bitrateStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
            val mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: ""

            val durationMs = durationStr?.toLongOrNull() ?: 0L
            val bitrate = (bitrateStr?.toIntOrNull() ?: 0) / 1000
            val year = yearStr?.take(4)?.toIntOrNull() ?: 0

            val format = if (filePath.isNotBlank()) {
                AudioFormatType.fromExtension(filePath)
            } else {
                AudioFormatType.fromExtension(uriString)
            }

            // Read sample rate if supported by platform version (Android 12+)
            val sampleRateStr = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)
            } else null
            val sampleRate = sampleRateStr?.toIntOrNull() ?: if (format.isLossless) 44100 else 44100

            val bitDepth = if (format == AudioFormatType.FLAC || format == AudioFormatType.WAV || format == AudioFormatType.APE) {
                if (sampleRate >= 96000 || bitrate > 2000) 24 else 16
            } else 16

            val file = if (filePath.isNotBlank()) File(filePath) else null
            val fileSize = file?.length() ?: 0L

            val techInfo = AudioTechnicalInfo(
                format = format,
                codecName = mimeType.ifBlank { format.displayName },
                bitDepth = bitDepth,
                sampleRate = sampleRate,
                bitrateKbps = bitrate,
                channels = 2,
                containerFormat = format.name,
                filePath = filePath,
                fileSizeBytes = fileSize,
                isHiRes = format.isHiRes || bitDepth >= 24 || sampleRate >= 48000
            )

            // Search for external companion .lrc file
            val externalLrc = findExternalLrc(filePath)

            ExtractedAudioMeta(
                title = title,
                artist = artist,
                album = album,
                year = year,
                genre = genre,
                durationMs = durationMs,
                embeddedLyrics = externalLrc,
                technicalInfo = techInfo
            )
        } catch (_: Exception) {
            ExtractedAudioMeta()
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }
    }

    /**
     * Extracts embedded album cover artwork Bitmap.
     */
    fun extractArtwork(context: Context, uriString: String, filePath: String): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            if (filePath.isNotBlank() && File(filePath).exists()) {
                retriever.setDataSource(filePath)
            } else {
                retriever.setDataSource(context, Uri.parse(uriString))
            }
            val pictureBytes = retriever.embeddedPicture
            if (pictureBytes != null && pictureBytes.isNotEmpty()) {
                // Decode bounds first to calculate safe inSampleSize against excessive RAM usage
                val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(pictureBytes, 0, pictureBytes.size, boundsOptions)
                
                var sampleSize = 1
                val maxDimension = 1024
                while (boundsOptions.outWidth / (sampleSize * 2) >= maxDimension && 
                       boundsOptions.outHeight / (sampleSize * 2) >= maxDimension) {
                    sampleSize *= 2
                }

                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                BitmapFactory.decodeByteArray(pictureBytes, 0, pictureBytes.size, decodeOptions)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }
    }

    /**
     * Searches for companion .lrc file in the same directory.
     */
    fun findExternalLrc(audioFilePath: String): String? {
        if (audioFilePath.isBlank()) return null
        return try {
            val audioFile = File(audioFilePath)
            if (!audioFile.exists()) return null
            val baseName = audioFile.nameWithoutExtension
            val lrcFile = File(audioFile.parentFile, "$baseName.lrc")
            if (lrcFile.exists() && lrcFile.isFile && lrcFile.canRead()) {
                lrcFile.readText(Charsets.UTF_8)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}
