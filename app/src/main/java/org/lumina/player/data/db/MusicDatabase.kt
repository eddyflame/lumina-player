package org.lumina.player.data.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.lumina.player.core.model.AudioFormatType
import org.lumina.player.core.model.Playlist
import org.lumina.player.core.model.Song

/**
 * 纯 Kotlin 高性能本地曲库持久化数据库 (原生 SQLiteOpenHelper + StateFlow)
 */
class MusicDatabase(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _songsFlow = MutableStateFlow<List<Song>>(emptyList())
    val songsFlow: StateFlow<List<Song>> = _songsFlow.asStateFlow()

    private val _playlistsFlow = MutableStateFlow<List<Playlist>>(emptyList())
    val playlistsFlow: StateFlow<List<Playlist>> = _playlistsFlow.asStateFlow()

    init {
        refreshAll()
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_SONGS (
                $COL_URI TEXT PRIMARY KEY,
                $COL_ID INTEGER NOT NULL,
                $COL_TITLE TEXT NOT NULL,
                $COL_ARTIST TEXT NOT NULL,
                $COL_ALBUM TEXT NOT NULL,
                $COL_ALBUM_ID INTEGER NOT NULL,
                $COL_DURATION INTEGER NOT NULL,
                $COL_FILE_PATH TEXT NOT NULL,
                $COL_FILE_SIZE INTEGER NOT NULL,
                $COL_MIME_TYPE TEXT NOT NULL,
                $COL_TRACK_NUMBER INTEGER NOT NULL,
                $COL_YEAR INTEGER NOT NULL,
                $COL_GENRE TEXT NOT NULL,
                $COL_BITRATE INTEGER NOT NULL,
                $COL_SAMPLE_RATE INTEGER NOT NULL,
                $COL_BIT_DEPTH INTEGER NOT NULL,
                $COL_CHANNELS INTEGER NOT NULL,
                $COL_FORMAT TEXT NOT NULL,
                $COL_FAVORITE INTEGER NOT NULL DEFAULT 0,
                $COL_PLAY_COUNT INTEGER NOT NULL DEFAULT 0,
                $COL_LAST_PLAYED INTEGER NOT NULL DEFAULT 0,
                $COL_DATE_ADDED INTEGER NOT NULL,
                $COL_IS_CUE INTEGER NOT NULL DEFAULT 0,
                $COL_CUE_START INTEGER NOT NULL DEFAULT 0,
                $COL_CUE_END INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_PLAYLISTS (
                $COL_PL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_PL_NAME TEXT NOT NULL,
                $COL_PL_CREATED_AT INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_PLAYLIST_ITEMS (
                $COL_PI_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_PI_PLAYLIST_ID INTEGER NOT NULL,
                $COL_PI_SONG_URI TEXT NOT NULL,
                $COL_PI_POSITION INTEGER NOT NULL,
                FOREIGN KEY ($COL_PI_PLAYLIST_ID) REFERENCES $TABLE_PLAYLISTS($COL_PL_ID) ON DELETE CASCADE
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // 平滑升级
    }

    /**
     * 批量插入或增量更新扫描到的歌曲（保留收藏、播放次数等状态）
     */
    fun syncScannedSongs(scannedSongs: List<Song>) {
        scope.launch {
            withContext(Dispatchers.IO) {
                val db = writableDatabase
                db.beginTransaction()
                try {
                    for (song in scannedSongs) {
                        // 查询现有收藏和播放统计
                        var isFav = false
                        var playCount = 0
                        var lastPlayed = 0L

                        val cursor = db.query(
                            TABLE_SONGS,
                            arrayOf(COL_FAVORITE, COL_PLAY_COUNT, COL_LAST_PLAYED),
                            "$COL_URI = ?",
                            arrayOf(song.uri),
                            null, null, null
                        )
                        cursor.use {
                            if (it.moveToFirst()) {
                                isFav = it.getInt(0) == 1
                                playCount = it.getInt(1)
                                lastPlayed = it.getLong(2)
                            }
                        }

                        val cv = ContentValues().apply {
                            put(COL_URI, song.uri)
                            put(COL_ID, song.id)
                            put(COL_TITLE, song.title)
                            put(COL_ARTIST, song.artist)
                            put(COL_ALBUM, song.album)
                            put(COL_ALBUM_ID, song.albumId)
                            put(COL_DURATION, song.durationMs)
                            put(COL_FILE_PATH, song.filePath)
                            put(COL_FILE_SIZE, song.fileSize)
                            put(COL_MIME_TYPE, song.mimeType)
                            put(COL_TRACK_NUMBER, song.trackNumber)
                            put(COL_YEAR, song.year)
                            put(COL_GENRE, song.genre)
                            put(COL_BITRATE, song.bitrateKbps)
                            put(COL_SAMPLE_RATE, song.sampleRate)
                            put(COL_BIT_DEPTH, song.bitDepth)
                            put(COL_CHANNELS, song.channels)
                            put(COL_FORMAT, song.audioFormat.name)
                            put(COL_FAVORITE, if (isFav) 1 else 0)
                            put(COL_PLAY_COUNT, playCount)
                            put(COL_LAST_PLAYED, lastPlayed)
                            put(COL_DATE_ADDED, song.dateAdded)
                            put(COL_IS_CUE, if (song.isVirtualCueTrack) 1 else 0)
                            put(COL_CUE_START, song.cueStartMs)
                            put(COL_CUE_END, song.cueEndMs)
                        }
                        db.insertWithOnConflict(TABLE_SONGS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
                    }
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
            }
            refreshAll()
        }
    }

    /**
     * 切换歌曲的“我喜欢”状态
     */
    fun toggleFavorite(uri: String) {
        scope.launch {
            withContext(Dispatchers.IO) {
                val db = writableDatabase
                db.execSQL(
                    "UPDATE $TABLE_SONGS SET $COL_FAVORITE = (1 - $COL_FAVORITE) WHERE $COL_URI = ?",
                    arrayOf(uri)
                )
            }
            refreshAll()
        }
    }

    /**
     * 记录曲目播放计数与时间戳
     */
    fun recordPlay(uri: String) {
        scope.launch {
            withContext(Dispatchers.IO) {
                val db = writableDatabase
                val now = System.currentTimeMillis()
                db.execSQL(
                    "UPDATE $TABLE_SONGS SET $COL_PLAY_COUNT = $COL_PLAY_COUNT + 1, $COL_LAST_PLAYED = ? WHERE $COL_URI = ?",
                    arrayOf<Any>(now, uri)
                )
            }
            refreshAll()
        }
    }

    /**
     * 编辑更新歌曲元数据标签
     */
    fun updateSongTags(uri: String, title: String, artist: String, album: String, year: Int, genre: String) {
        scope.launch {
            withContext(Dispatchers.IO) {
                val db = writableDatabase
                val cv = ContentValues().apply {
                    put(COL_TITLE, title)
                    put(COL_ARTIST, artist)
                    put(COL_ALBUM, album)
                    put(COL_YEAR, year)
                    put(COL_GENRE, genre)
                }
                db.update(TABLE_SONGS, cv, "$COL_URI = ?", arrayOf(uri))
            }
            refreshAll()
        }
    }

    /**
     * 创建自定义歌单
     */
    fun createPlaylist(name: String) {
        scope.launch {
            withContext(Dispatchers.IO) {
                val db = writableDatabase
                val cv = ContentValues().apply {
                    put(COL_PL_NAME, name)
                    put(COL_PL_CREATED_AT, System.currentTimeMillis())
                }
                db.insert(TABLE_PLAYLISTS, null, cv)
            }
            refreshPlaylists()
        }
    }

    /**
     * 删除歌单
     */
    fun deletePlaylist(playlistId: Long) {
        scope.launch {
            withContext(Dispatchers.IO) {
                val db = writableDatabase
                db.delete(TABLE_PLAYLIST_ITEMS, "$COL_PI_PLAYLIST_ID = ?", arrayOf(playlistId.toString()))
                db.delete(TABLE_PLAYLISTS, "$COL_PL_ID = ?", arrayOf(playlistId.toString()))
            }
            refreshPlaylists()
        }
    }

    /**
     * 添加歌曲到歌单
     */
    fun addSongToPlaylist(playlistId: Long, songUri: String) {
        scope.launch {
            withContext(Dispatchers.IO) {
                val db = writableDatabase
                val countCursor = db.rawQuery(
                    "SELECT COUNT(*) FROM $TABLE_PLAYLIST_ITEMS WHERE $COL_PI_PLAYLIST_ID = ?",
                    arrayOf(playlistId.toString())
                )
                val count = countCursor.use { if (it.moveToFirst()) it.getInt(0) else 0 }

                val cv = ContentValues().apply {
                    put(COL_PI_PLAYLIST_ID, playlistId)
                    put(COL_PI_SONG_URI, songUri)
                    put(COL_PI_POSITION, count)
                }
                db.insert(TABLE_PLAYLIST_ITEMS, null, cv)
            }
            refreshPlaylists()
        }
    }

    /**
     * 从歌单中移除歌曲
     */
    fun removeSongFromPlaylist(playlistId: Long, songUri: String) {
        scope.launch {
            withContext(Dispatchers.IO) {
                val db = writableDatabase
                db.delete(
                    TABLE_PLAYLIST_ITEMS,
                    "$COL_PI_PLAYLIST_ID = ? AND $COL_PI_SONG_URI = ?",
                    arrayOf(playlistId.toString(), songUri)
                )
            }
            refreshPlaylists()
        }
    }

    /**
     * 获取指定歌单的所有歌曲列表
     */
    suspend fun getPlaylistSongs(playlistId: Long): List<Song> = withContext(Dispatchers.IO) {
        val db = readableDatabase
        val list = mutableListOf<Song>()
        val sql = """
            SELECT s.* FROM $TABLE_SONGS s
            INNER JOIN $TABLE_PLAYLIST_ITEMS pi ON s.$COL_URI = pi.$COL_PI_SONG_URI
            WHERE pi.$COL_PI_PLAYLIST_ID = ?
            ORDER BY pi.$COL_PI_POSITION ASC
        """.trimIndent()
        val cursor = db.rawQuery(sql, arrayOf(playlistId.toString()))
        cursor.use {
            while (it.moveToNext()) {
                list.add(parseSongRow(it))
            }
        }
        list
    }

    private fun refreshAll() {
        scope.launch {
            refreshSongs()
            refreshPlaylists()
        }
    }

    private suspend fun refreshSongs() {
        val list = queryAllSongs()
        _songsFlow.value = list
    }

    private suspend fun refreshPlaylists() {
        val list = queryAllPlaylists()
        _playlistsFlow.value = list
    }

    private suspend fun queryAllSongs(): List<Song> = withContext(Dispatchers.IO) {
        val db = readableDatabase
        val list = mutableListOf<Song>()
        val cursor = db.query(TABLE_SONGS, null, null, null, null, null, "$COL_TITLE ASC")
        cursor.use {
            while (it.moveToNext()) {
                list.add(parseSongRow(it))
            }
        }
        list
    }

    private suspend fun queryAllPlaylists(): List<Playlist> = withContext(Dispatchers.IO) {
        val db = readableDatabase
        val list = mutableListOf<Playlist>()
        val sql = """
            SELECT p.$COL_PL_ID, p.$COL_PL_NAME, p.$COL_PL_CREATED_AT, COUNT(pi.$COL_PI_ID) as count
            FROM $TABLE_PLAYLISTS p
            LEFT JOIN $TABLE_PLAYLIST_ITEMS pi ON p.$COL_PL_ID = pi.$COL_PI_PLAYLIST_ID
            GROUP BY p.$COL_PL_ID
            ORDER BY p.$COL_PL_NAME ASC
        """.trimIndent()
        val cursor = db.rawQuery(sql, null)
        cursor.use {
            while (it.moveToNext()) {
                val id = it.getLong(0)
                val name = it.getString(1)
                val createdAt = it.getLong(2)
                val count = it.getInt(3)
                list.add(Playlist(id = id, name = name, songCount = count, createdAt = createdAt))
            }
        }
        list
    }

    private fun parseSongRow(cursor: Cursor): Song {
        val uri = cursor.getString(cursor.getColumnIndexOrThrow(COL_URI))
        val id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID))
        val title = cursor.getString(cursor.getColumnIndexOrThrow(COL_TITLE))
        val artist = cursor.getString(cursor.getColumnIndexOrThrow(COL_ARTIST))
        val album = cursor.getString(cursor.getColumnIndexOrThrow(COL_ALBUM))
        val albumId = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ALBUM_ID))
        val duration = cursor.getLong(cursor.getColumnIndexOrThrow(COL_DURATION))
        val path = cursor.getString(cursor.getColumnIndexOrThrow(COL_FILE_PATH))
        val size = cursor.getLong(cursor.getColumnIndexOrThrow(COL_FILE_SIZE))
        val mime = cursor.getString(cursor.getColumnIndexOrThrow(COL_MIME_TYPE))
        val trackNo = cursor.getInt(cursor.getColumnIndexOrThrow(COL_TRACK_NUMBER))
        val year = cursor.getInt(cursor.getColumnIndexOrThrow(COL_YEAR))
        val genre = cursor.getString(cursor.getColumnIndexOrThrow(COL_GENRE))
        val bitrate = cursor.getInt(cursor.getColumnIndexOrThrow(COL_BITRATE))
        val sampleRate = cursor.getInt(cursor.getColumnIndexOrThrow(COL_SAMPLE_RATE))
        val bitDepth = cursor.getInt(cursor.getColumnIndexOrThrow(COL_BIT_DEPTH))
        val channels = cursor.getInt(cursor.getColumnIndexOrThrow(COL_CHANNELS))
        val formatStr = cursor.getString(cursor.getColumnIndexOrThrow(COL_FORMAT))
        val fav = cursor.getInt(cursor.getColumnIndexOrThrow(COL_FAVORITE)) == 1
        val playCount = cursor.getInt(cursor.getColumnIndexOrThrow(COL_PLAY_COUNT))
        val lastPlayed = cursor.getLong(cursor.getColumnIndexOrThrow(COL_LAST_PLAYED))
        val dateAdded = cursor.getLong(cursor.getColumnIndexOrThrow(COL_DATE_ADDED))
        val isCue = cursor.getInt(cursor.getColumnIndexOrThrow(COL_IS_CUE)) == 1
        val cueStart = cursor.getLong(cursor.getColumnIndexOrThrow(COL_CUE_START))
        val cueEnd = cursor.getLong(cursor.getColumnIndexOrThrow(COL_CUE_END))

        val format = runCatching { AudioFormatType.valueOf(formatStr) }.getOrDefault(AudioFormatType.UNKNOWN)

        return Song(
            id = id,
            uri = uri,
            title = title,
            artist = artist,
            album = album,
            albumId = albumId,
            durationMs = duration,
            filePath = path,
            fileSize = size,
            mimeType = mime,
            trackNumber = trackNo,
            year = year,
            genre = genre,
            bitrateKbps = bitrate,
            sampleRate = sampleRate,
            bitDepth = bitDepth,
            channels = channels,
            audioFormat = format,
            isFavorite = fav,
            playCount = playCount,
            lastPlayedTime = lastPlayed,
            dateAdded = dateAdded,
            isVirtualCueTrack = isCue,
            cueStartMs = cueStart,
            cueEndMs = cueEnd
        )
    }

    companion object {
        private const val DB_NAME = "lumina_player.db"
        private const val DB_VERSION = 1

        private const val TABLE_SONGS = "songs"
        private const val COL_URI = "uri"
        private const val COL_ID = "id"
        private const val COL_TITLE = "title"
        private const val COL_ARTIST = "artist"
        private const val COL_ALBUM = "album"
        private const val COL_ALBUM_ID = "album_id"
        private const val COL_DURATION = "duration_ms"
        private const val COL_FILE_PATH = "file_path"
        private const val COL_FILE_SIZE = "file_size"
        private const val COL_MIME_TYPE = "mime_type"
        private const val COL_TRACK_NUMBER = "track_number"
        private const val COL_YEAR = "year"
        private const val COL_GENRE = "genre"
        private const val COL_BITRATE = "bitrate"
        private const val COL_SAMPLE_RATE = "sample_rate"
        private const val COL_BIT_DEPTH = "bit_depth"
        private const val COL_CHANNELS = "channels"
        private const val COL_FORMAT = "audio_format"
        private const val COL_FAVORITE = "is_favorite"
        private const val COL_PLAY_COUNT = "play_count"
        private const val COL_LAST_PLAYED = "last_played_time"
        private const val COL_DATE_ADDED = "date_added"
        private const val COL_IS_CUE = "is_cue"
        private const val COL_CUE_START = "cue_start_ms"
        private const val COL_CUE_END = "cue_end_ms"

        private const val TABLE_PLAYLISTS = "playlists"
        private const val COL_PL_ID = "id"
        private const val COL_PL_NAME = "name"
        private const val COL_PL_CREATED_AT = "created_at"

        private const val TABLE_PLAYLIST_ITEMS = "playlist_items"
        private const val COL_PI_ID = "id"
        private const val COL_PI_PLAYLIST_ID = "playlist_id"
        private const val COL_PI_SONG_URI = "song_uri"
        private const val COL_PI_POSITION = "position"
    }
}
