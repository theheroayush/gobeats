package com.example.gobeats.core.database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.gobeats.core.model.Album
import com.example.gobeats.core.model.Artist
import com.example.gobeats.core.model.DeviceAudioProfile
import com.example.gobeats.core.model.DeviceAudioProfileType
import com.example.gobeats.core.model.MusicSource
import com.example.gobeats.core.model.Playlist
import com.example.gobeats.core.model.SavedPlaybackState
import com.example.gobeats.core.model.SourceType
import com.example.gobeats.core.model.Track
import com.example.gobeats.core.playback.ProcessDeathPersistenceEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.util.UUID

class GobeatsDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "gobeats.db"
        const val DATABASE_VERSION = 5

        @Volatile
        private var INSTANCE: GobeatsDatabaseHelper? = null

        fun getInstance(context: Context): GobeatsDatabaseHelper {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: GobeatsDatabaseHelper(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val _dbChangeTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val dbChangeTrigger: Flow<Unit> = _dbChangeTrigger.asSharedFlow()

    private fun notifyDataChanged() {
        _dbChangeTrigger.tryEmit(Unit)
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            try {
                db.execSQL("ALTER TABLE tracks ADD COLUMN is_video INTEGER NOT NULL DEFAULT 0;")
                db.execSQL("ALTER TABLE tracks ADD COLUMN video_width INTEGER NOT NULL DEFAULT 0;")
                db.execSQL("ALTER TABLE tracks ADD COLUMN video_height INTEGER NOT NULL DEFAULT 0;")
            } catch (_: Exception) {}
        }
        if (oldVersion < 3) {
            try {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS video_playback_positions (
                        video_id TEXT PRIMARY KEY NOT NULL,
                        position_ms INTEGER NOT NULL,
                        duration_ms INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL,
                        FOREIGN KEY (video_id) REFERENCES tracks(id) ON DELETE CASCADE
                    );
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_video_positions_updated ON video_playback_positions(updated_at DESC);")
            } catch (_: Exception) {}
        }
        if (oldVersion < 4) {
            try {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS track_playback_stats (
                        track_id TEXT PRIMARY KEY NOT NULL,
                        play_count INTEGER NOT NULL DEFAULT 0,
                        skip_count INTEGER NOT NULL DEFAULT 0,
                        completed_count INTEGER NOT NULL DEFAULT 0,
                        last_played_at INTEGER NOT NULL DEFAULT 0,
                        total_listen_ms INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY (track_id) REFERENCES tracks(id) ON DELETE CASCADE
                    );
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_stats_play_count ON track_playback_stats(play_count DESC);")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_stats_last_played ON track_playback_stats(last_played_at DESC);")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS lyrics_cache (
                        track_id TEXT PRIMARY KEY NOT NULL,
                        plain_lyrics TEXT,
                        synced_lyrics TEXT,
                        source TEXT NOT NULL DEFAULT 'LRCLIB',
                        updated_at INTEGER NOT NULL,
                        FOREIGN KEY (track_id) REFERENCES tracks(id) ON DELETE CASCADE
                    );
                    """.trimIndent()
                )
            } catch (_: Exception) {}
        }
        if (oldVersion < 5) {
            try {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS device_audio_profiles (
                        device_id TEXT PRIMARY KEY NOT NULL,
                        device_name TEXT NOT NULL,
                        device_type TEXT NOT NULL,
                        preferred_eq_preset TEXT NOT NULL DEFAULT 'Studio Reference',
                        av_delay_ms INTEGER NOT NULL DEFAULT 0,
                        updated_at INTEGER NOT NULL
                    );
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS offline_generated_subtitles (
                        track_id TEXT NOT NULL,
                        language_code TEXT NOT NULL,
                        subtitle_content TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        PRIMARY KEY (track_id, language_code),
                        FOREIGN KEY (track_id) REFERENCES tracks(id) ON DELETE CASCADE
                    );
                    """.trimIndent()
                )
            } catch (_: Exception) {}
        }
        try {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS playback_state (
                    id INTEGER PRIMARY KEY CHECK (id = 1),
                    current_track_id TEXT,
                    position_ms INTEGER NOT NULL DEFAULT 0,
                    is_playing INTEGER NOT NULL DEFAULT 0,
                    queue_json TEXT NOT NULL DEFAULT '[]',
                    updated_at INTEGER NOT NULL
                );
                """.trimIndent()
            )
        } catch (_: Exception) {}
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE music_sources (
                id TEXT PRIMARY KEY NOT NULL,
                type TEXT NOT NULL,
                name TEXT NOT NULL,
                root_uri TEXT,
                is_enabled INTEGER NOT NULL DEFAULT 1,
                created_at INTEGER NOT NULL
            );
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE artists (
                id TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL,
                sort_name TEXT,
                artwork_uri TEXT,
                track_count INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL
            );
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE albums (
                id TEXT PRIMARY KEY NOT NULL,
                title TEXT NOT NULL,
                artist_id TEXT,
                artist_name TEXT NOT NULL,
                year INTEGER,
                artwork_uri TEXT,
                track_count INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL,
                FOREIGN KEY (artist_id) REFERENCES artists(id) ON DELETE SET NULL
            );
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_albums_artist ON albums(artist_id);")

        db.execSQL(
            """
            CREATE TABLE tracks (
                id TEXT PRIMARY KEY NOT NULL,
                source_id TEXT NOT NULL,
                file_uri TEXT NOT NULL UNIQUE,
                title TEXT NOT NULL,
                artist_name TEXT NOT NULL,
                album_title TEXT NOT NULL,
                duration_ms INTEGER NOT NULL DEFAULT 0,
                artist_id TEXT,
                album_id TEXT,
                track_number INTEGER DEFAULT 1,
                disc_number INTEGER DEFAULT 1,
                genre TEXT,
                year INTEGER,
                format TEXT DEFAULT 'MP3',
                bitrate INTEGER DEFAULT 0,
                sample_rate INTEGER DEFAULT 0,
                file_size INTEGER NOT NULL DEFAULT 0,
                artwork_uri TEXT,
                is_available INTEGER NOT NULL DEFAULT 1,
                date_added INTEGER NOT NULL,
                is_video INTEGER NOT NULL DEFAULT 0,
                video_width INTEGER NOT NULL DEFAULT 0,
                video_height INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY (source_id) REFERENCES music_sources(id) ON DELETE CASCADE,
                FOREIGN KEY (artist_id) REFERENCES artists(id) ON DELETE SET NULL,
                FOREIGN KEY (album_id) REFERENCES albums(id) ON DELETE SET NULL
            );
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_tracks_album ON tracks(album_id);")
        db.execSQL("CREATE INDEX idx_tracks_artist ON tracks(artist_id);")
        db.execSQL("CREATE INDEX idx_tracks_title ON tracks(title);")
        db.execSQL("CREATE INDEX idx_tracks_date_added ON tracks(date_added DESC);")
        db.execSQL("CREATE INDEX idx_tracks_is_video ON tracks(is_video);")

        db.execSQL(
            """
            CREATE TABLE playlists (
                id TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL,
                description TEXT,
                artwork_uri TEXT,
                is_smart INTEGER NOT NULL DEFAULT 0,
                track_count INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            );
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE playlist_tracks (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                playlist_id TEXT NOT NULL,
                track_id TEXT NOT NULL,
                position INTEGER NOT NULL,
                added_at INTEGER NOT NULL,
                FOREIGN KEY (playlist_id) REFERENCES playlists(id) ON DELETE CASCADE,
                FOREIGN KEY (track_id) REFERENCES tracks(id) ON DELETE CASCADE
            );
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_playlist_tracks ON playlist_tracks(playlist_id, position);")

        db.execSQL(
            """
            CREATE TABLE favorites (
                track_id TEXT PRIMARY KEY NOT NULL,
                created_at INTEGER NOT NULL,
                FOREIGN KEY (track_id) REFERENCES tracks(id) ON DELETE CASCADE
            );
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE playback_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                track_id TEXT NOT NULL,
                started_at INTEGER NOT NULL,
                completed_at INTEGER,
                completion_percent REAL NOT NULL DEFAULT 0.0,
                FOREIGN KEY (track_id) REFERENCES tracks(id) ON DELETE CASCADE
            );
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_history_started ON playback_history(started_at DESC);")

        db.execSQL(
            """
            CREATE TABLE playback_state (
                id INTEGER PRIMARY KEY CHECK (id = 1),
                current_track_id TEXT,
                position_ms INTEGER NOT NULL DEFAULT 0,
                is_playing INTEGER NOT NULL DEFAULT 0,
                queue_json TEXT NOT NULL DEFAULT '[]',
                updated_at INTEGER NOT NULL
            );
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE track_metadata_overrides (
                track_id TEXT PRIMARY KEY NOT NULL,
                title TEXT,
                artist TEXT,
                album TEXT,
                genre TEXT,
                year INTEGER,
                artwork_uri TEXT,
                updated_at INTEGER NOT NULL,
                FOREIGN KEY (track_id) REFERENCES tracks(id) ON DELETE CASCADE
            );
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS video_playback_positions (
                video_id TEXT PRIMARY KEY NOT NULL,
                position_ms INTEGER NOT NULL,
                duration_ms INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                FOREIGN KEY (video_id) REFERENCES tracks(id) ON DELETE CASCADE
            );
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_video_positions_updated ON video_playback_positions(updated_at DESC);")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS track_playback_stats (
                track_id TEXT PRIMARY KEY NOT NULL,
                play_count INTEGER NOT NULL DEFAULT 0,
                skip_count INTEGER NOT NULL DEFAULT 0,
                completed_count INTEGER NOT NULL DEFAULT 0,
                last_played_at INTEGER NOT NULL DEFAULT 0,
                total_listen_ms INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY (track_id) REFERENCES tracks(id) ON DELETE CASCADE
            );
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_stats_play_count ON track_playback_stats(play_count DESC);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_stats_last_played ON track_playback_stats(last_played_at DESC);")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS lyrics_cache (
                track_id TEXT PRIMARY KEY NOT NULL,
                plain_lyrics TEXT,
                synced_lyrics TEXT,
                source TEXT NOT NULL DEFAULT 'LRCLIB',
                updated_at INTEGER NOT NULL,
                FOREIGN KEY (track_id) REFERENCES tracks(id) ON DELETE CASCADE
            );
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS device_audio_profiles (
                device_id TEXT PRIMARY KEY NOT NULL,
                device_name TEXT NOT NULL,
                device_type TEXT NOT NULL,
                preferred_eq_preset TEXT NOT NULL DEFAULT 'Studio Reference',
                av_delay_ms INTEGER NOT NULL DEFAULT 0,
                updated_at INTEGER NOT NULL
            );
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS offline_generated_subtitles (
                track_id TEXT NOT NULL,
                language_code TEXT NOT NULL,
                subtitle_content TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                PRIMARY KEY (track_id, language_code),
                FOREIGN KEY (track_id) REFERENCES tracks(id) ON DELETE CASCADE
            );
            """.trimIndent()
        )

        // Seed default local, external, and internet music sources
        val now = System.currentTimeMillis()
        listOf(
            Triple("source_local", SourceType.LOCAL_STORAGE.name, "This Phone"),
            Triple("source_external", SourceType.LOCAL_STORAGE.name, "External Files"),
            Triple("source_internet", SourceType.STREAMING_SERVICE.name, "Internet Stream")
        ).forEach { (id, type, name) ->
            val sourceValues = ContentValues().apply {
                put("id", id)
                put("type", type)
                put("name", name)
                put("created_at", now)
            }
            db.insertWithOnConflict("music_sources", null, sourceValues, SQLiteDatabase.CONFLICT_IGNORE)
        }
    }

    // ==========================================
    // TRACK OPERATIONS
    // ==========================================

    suspend fun insertOrUpdateTrack(track: Track) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            insertTrackInternal(db, track)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
            notifyDataChanged()
        }
    }

    private fun insertTrackInternal(db: SQLiteDatabase, track: Track) {
        val effectiveSourceId = track.sourceId.ifBlank { "source_local" }
        val sourceValues = ContentValues().apply {
            put("id", effectiveSourceId)
            put("type", if (effectiveSourceId == "source_internet") SourceType.STREAMING_SERVICE.name else SourceType.LOCAL_STORAGE.name)
            put("name", when (effectiveSourceId) {
                "source_external" -> "External Files"
                "source_internet" -> "Internet Stream"
                else -> "This Phone"
            })
            put("created_at", System.currentTimeMillis())
        }
        db.insertWithOnConflict("music_sources", null, sourceValues, SQLiteDatabase.CONFLICT_IGNORE)

        // Check for persistent user overrides
        var finalTitle = track.title
        var finalArtist = track.artistName
        var finalAlbum = track.albumTitle
        var finalGenre = track.genre
        var finalYear = track.year
        var finalArtworkUri = track.artworkUri

        try {
            db.rawQuery("SELECT title, artist, album, genre, year, artwork_uri FROM track_metadata_overrides WHERE track_id = ?", arrayOf(track.id)).use { c ->
                if (c.moveToFirst()) {
                    val oTitle = c.getString(0)
                    val oArtist = c.getString(1)
                    val oAlbum = c.getString(2)
                    val oGenre = c.getString(3)
                    val oYear = if (c.isNull(4)) null else c.getInt(4)
                    val oArt = c.getString(5)
                    if (!oTitle.isNullOrBlank()) finalTitle = oTitle
                    if (!oArtist.isNullOrBlank()) finalArtist = oArtist
                    if (!oAlbum.isNullOrBlank()) finalAlbum = oAlbum
                    if (!oGenre.isNullOrBlank()) finalGenre = oGenre
                    if (oYear != null) finalYear = oYear
                    if (!oArt.isNullOrBlank()) finalArtworkUri = oArt
                }
            }
        } catch (_: Exception) {}

        // Ensure artist exists
        val artistId = track.artistId ?: ("art_" + finalArtist.hashCode().toString())
        val artistValues = ContentValues().apply {
            put("id", artistId)
            put("name", finalArtist)
            put("created_at", System.currentTimeMillis())
        }
        db.insertWithOnConflict("artists", null, artistValues, SQLiteDatabase.CONFLICT_IGNORE)

        // Ensure album exists
        val albumId = track.albumId ?: ("alb_" + (finalAlbum + finalArtist).hashCode().toString())
        val albumValues = ContentValues().apply {
            put("id", albumId)
            put("title", finalAlbum)
            put("artist_id", artistId)
            put("artist_name", finalArtist)
            put("artwork_uri", finalArtworkUri)
            put("created_at", System.currentTimeMillis())
        }
        db.insertWithOnConflict("albums", null, albumValues, SQLiteDatabase.CONFLICT_IGNORE)

        val trackValues = ContentValues().apply {
            put("id", track.id)
            put("source_id", effectiveSourceId)
            put("file_uri", track.fileUri)
            put("title", finalTitle)
            put("artist_name", finalArtist)
            put("album_title", finalAlbum)
            put("duration_ms", track.durationMs)
            put("artist_id", artistId)
            put("album_id", albumId)
            put("track_number", track.trackNumber)
            put("disc_number", track.discNumber)
            put("genre", finalGenre)
            put("year", finalYear)
            put("format", track.format)
            put("bitrate", track.bitrate)
            put("sample_rate", track.sampleRate)
            put("file_size", track.fileSize)
            put("artwork_uri", finalArtworkUri)
            put("is_available", if (track.isAvailable) 1 else 0)
            put("date_added", track.dateAdded)
            put("is_video", if (track.isVideo) 1 else 0)
            put("video_width", track.videoWidth)
            put("video_height", track.videoHeight)
        }
        db.insertWithOnConflict("tracks", null, trackValues, SQLiteDatabase.CONFLICT_REPLACE)
    }

    suspend fun insertTracksBatch(tracks: List<Track>, notify: Boolean = true) = withContext(Dispatchers.IO) {
        if (tracks.isEmpty()) return@withContext
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (track in tracks) {
                insertTrackInternal(db, track)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
            if (notify) notifyDataChanged()
        }
    }

    suspend fun getAllTracks(): List<Track> = withContext(Dispatchers.IO) {
        val list = mutableListOf<Track>()
        val db = readableDatabase
        val query = """
            SELECT t.*, CASE WHEN f.track_id IS NOT NULL THEN 1 ELSE 0 END AS is_fav
            FROM tracks t
            LEFT JOIN favorites f ON t.id = f.track_id
            WHERE t.is_available = 1
            ORDER BY t.title COLLATE NOCASE ASC
        """.trimIndent()

        db.rawQuery(query, null).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(cursorToTrack(cursor))
            }
        }
        list
    }

    suspend fun getRecentlyAddedTracks(limit: Int = 20): List<Track> = withContext(Dispatchers.IO) {
        val list = mutableListOf<Track>()
        val db = readableDatabase
        val query = """
            SELECT t.*, CASE WHEN f.track_id IS NOT NULL THEN 1 ELSE 0 END AS is_fav
            FROM tracks t
            LEFT JOIN favorites f ON t.id = f.track_id
            WHERE t.is_available = 1
            ORDER BY t.date_added DESC
            LIMIT ?
        """.trimIndent()

        db.rawQuery(query, arrayOf(limit.toString())).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(cursorToTrack(cursor))
            }
        }
        list
    }

    suspend fun getFavoriteTracks(): List<Track> = withContext(Dispatchers.IO) {
        val list = mutableListOf<Track>()
        val db = readableDatabase
        val query = """
            SELECT t.*, 1 AS is_fav
            FROM tracks t
            INNER JOIN favorites f ON t.id = f.track_id
            WHERE t.is_available = 1
            ORDER BY f.created_at DESC
        """.trimIndent()

        db.rawQuery(query, null).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(cursorToTrack(cursor))
            }
        }
        list
    }

    suspend fun getTracksByAlbum(albumId: String): List<Track> = withContext(Dispatchers.IO) {
        val list = mutableListOf<Track>()
        val db = readableDatabase
        val query = """
            SELECT t.*, CASE WHEN f.track_id IS NOT NULL THEN 1 ELSE 0 END AS is_fav
            FROM tracks t
            LEFT JOIN favorites f ON t.id = f.track_id
            WHERE t.album_id = ? AND t.is_available = 1
            ORDER BY t.disc_number ASC, t.track_number ASC
        """.trimIndent()

        db.rawQuery(query, arrayOf(albumId)).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(cursorToTrack(cursor))
            }
        }
        list
    }

    suspend fun getTracksByArtist(artistId: String): List<Track> = withContext(Dispatchers.IO) {
        val list = mutableListOf<Track>()
        val db = readableDatabase
        val query = """
            SELECT t.*, CASE WHEN f.track_id IS NOT NULL THEN 1 ELSE 0 END AS is_fav
            FROM tracks t
            LEFT JOIN favorites f ON t.id = f.track_id
            WHERE t.artist_id = ? AND t.is_available = 1
            ORDER BY t.year DESC, t.title COLLATE NOCASE ASC
        """.trimIndent()

        db.rawQuery(query, arrayOf(artistId)).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(cursorToTrack(cursor))
            }
        }
        list
    }

    suspend fun searchTracks(query: String): List<Track> = withContext(Dispatchers.IO) {
        val list = mutableListOf<Track>()
        val db = readableDatabase
        val sqlQuery = """
            SELECT t.*, CASE WHEN f.track_id IS NOT NULL THEN 1 ELSE 0 END AS is_fav
            FROM tracks t
            LEFT JOIN favorites f ON t.id = f.track_id
            WHERE t.is_available = 1 AND (
                t.title LIKE ? OR t.artist_name LIKE ? OR t.album_title LIKE ?
                OR COALESCE(t.genre, '') LIKE ? OR COALESCE(t.format, '') LIKE ?
                OR t.file_uri LIKE ?
            )
            ORDER BY t.title COLLATE NOCASE ASC
            LIMIT 100
        """.trimIndent()
        val arg = "%$query%"

        db.rawQuery(sqlQuery, arrayOf(arg, arg, arg, arg, arg, arg)).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(cursorToTrack(cursor))
            }
        }
        list
    }

    suspend fun toggleFavorite(trackId: String): Boolean = withContext(Dispatchers.IO) {
        val db = writableDatabase
        var isNowFav = false
        db.beginTransaction()
        try {
            val checkCursor = db.rawQuery("SELECT 1 FROM favorites WHERE track_id = ?", arrayOf(trackId))
            val exists = checkCursor.use { it.moveToFirst() }
            if (exists) {
                db.delete("favorites", "track_id = ?", arrayOf(trackId))
                isNowFav = false
            } else {
                val values = ContentValues().apply {
                    put("track_id", trackId)
                    put("created_at", System.currentTimeMillis())
                }
                db.insert("favorites", null, values)
                isNowFav = true
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
            notifyDataChanged()
        }
        isNowFav
    }

    // ==========================================
    // ALBUM & ARTIST OPERATIONS
    // ==========================================

    suspend fun getAllAlbums(): List<Album> = withContext(Dispatchers.IO) {
        val list = mutableListOf<Album>()
        val db = readableDatabase
        val query = """
            SELECT a.id, a.title, a.artist_id, a.artist_name, a.year, a.artwork_uri,
                   COUNT(t.id) as track_count
            FROM albums a
            LEFT JOIN tracks t ON a.id = t.album_id AND t.is_available = 1
            GROUP BY a.id
            HAVING track_count > 0
            ORDER BY a.title COLLATE NOCASE ASC
        """.trimIndent()

        db.rawQuery(query, null).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(
                    Album(
                        id = cursor.getString(0),
                        title = cursor.getString(1),
                        artistId = cursor.getString(2),
                        artistName = cursor.getString(3),
                        year = if (cursor.isNull(4)) null else cursor.getInt(4),
                        artworkUri = cursor.getString(5),
                        trackCount = cursor.getInt(6)
                    )
                )
            }
        }
        list
    }

    suspend fun getAllArtists(): List<Artist> = withContext(Dispatchers.IO) {
        val list = mutableListOf<Artist>()
        val db = readableDatabase
        val query = """
            SELECT ar.id, ar.name, ar.sort_name, ar.artwork_uri,
                   COUNT(t.id) as track_count
            FROM artists ar
            LEFT JOIN tracks t ON ar.id = t.artist_id AND t.is_available = 1
            GROUP BY ar.id
            HAVING track_count > 0
            ORDER BY ar.name COLLATE NOCASE ASC
        """.trimIndent()

        db.rawQuery(query, null).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(
                    Artist(
                        id = cursor.getString(0),
                        name = cursor.getString(1),
                        sortName = cursor.getString(2),
                        artworkUri = cursor.getString(3),
                        trackCount = cursor.getInt(4)
                    )
                )
            }
        }
        list
    }

    // ==========================================
    // PLAYLIST OPERATIONS
    // ==========================================

    suspend fun createPlaylist(name: String, description: String? = null): Playlist = withContext(Dispatchers.IO) {
        val db = writableDatabase
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val cleanName = name.trim().ifBlank { "My Playlist" }
        val values = ContentValues().apply {
            put("id", id)
            put("name", cleanName)
            put("description", description)
            put("created_at", now)
            put("updated_at", now)
        }
        db.insert("playlists", null, values)
        notifyDataChanged()
        Playlist(id = id, name = cleanName, description = description, createdAt = now, updatedAt = now)
    }

    suspend fun getAllPlaylists(): List<Playlist> = withContext(Dispatchers.IO) {
        val list = mutableListOf<Playlist>()
        val db = readableDatabase
        val query = """
            SELECT p.id, p.name, p.description, p.artwork_uri, p.is_smart,
                   COUNT(pt.id) as track_count, p.created_at, p.updated_at
            FROM playlists p
            LEFT JOIN playlist_tracks pt ON p.id = pt.playlist_id
            GROUP BY p.id
            ORDER BY p.updated_at DESC
        """.trimIndent()

        db.rawQuery(query, null).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(
                    Playlist(
                        id = cursor.getString(0),
                        name = cursor.getString(1),
                        description = cursor.getString(2),
                        artworkUri = cursor.getString(3),
                        isSmart = cursor.getInt(4) == 1,
                        trackCount = cursor.getInt(5),
                        createdAt = cursor.getLong(6),
                        updatedAt = cursor.getLong(7)
                    )
                )
            }
        }
        list
    }

    suspend fun addTrackToPlaylist(playlistId: String, trackId: String) = withContext(Dispatchers.IO) {
        if (playlistId == "pl_liked" || playlistId == "pl_favorites") {
            val db = writableDatabase
            val values = ContentValues().apply {
                put("track_id", trackId)
                put("created_at", System.currentTimeMillis())
            }
            db.insertWithOnConflict("favorites", null, values, SQLiteDatabase.CONFLICT_IGNORE)
            notifyDataChanged()
            return@withContext
        }

        val db = writableDatabase
        db.beginTransaction()
        try {
            val existsCursor = db.rawQuery(
                "SELECT 1 FROM playlist_tracks WHERE playlist_id = ? AND track_id = ?",
                arrayOf(playlistId, trackId)
            )
            val alreadyExists = existsCursor.use { it.moveToFirst() }
            if (!alreadyExists) {
                val maxPosCursor = db.rawQuery(
                    "SELECT COALESCE(MAX(position), -1) FROM playlist_tracks WHERE playlist_id = ?",
                    arrayOf(playlistId)
                )
                val nextPos = maxPosCursor.use { if (it.moveToFirst()) it.getInt(0) + 1 else 0 }

                val values = ContentValues().apply {
                    put("playlist_id", playlistId)
                    put("track_id", trackId)
                    put("position", nextPos)
                    put("added_at", System.currentTimeMillis())
                }
                db.insert("playlist_tracks", null, values)
            }

            val countCursor = db.rawQuery(
                "SELECT COUNT(*) FROM playlist_tracks WHERE playlist_id = ?",
                arrayOf(playlistId)
            )
            val count = countCursor.use { if (it.moveToFirst()) it.getInt(0) else 0 }

            val updateValues = ContentValues().apply {
                put("track_count", count)
                put("updated_at", System.currentTimeMillis())
            }
            db.update("playlists", updateValues, "id = ?", arrayOf(playlistId))

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
            notifyDataChanged()
        }
    }

    suspend fun getPlaylistTracks(playlistId: String): List<Track> = withContext(Dispatchers.IO) {
        if (playlistId == "pl_favorites" || playlistId == "pl_liked") {
            return@withContext getFavoriteTracks()
        }
        if (playlistId == "pl_most_played") {
            return@withContext getMostPlayedTracks(50)
        }
        if (playlistId == "pl_recently_played") {
            return@withContext getRecentlyPlayedTracks(50)
        }
        if (playlistId == "pl_heavy_rotation") {
            return@withContext getHeavyRotationTracks(50)
        }
        if (playlistId == "pl_forgotten_favorites") {
            return@withContext getForgottenFavorites(50)
        }
        val list = mutableListOf<Track>()
        val db = readableDatabase
        val query = """
            SELECT t.*, CASE WHEN f.track_id IS NOT NULL THEN 1 ELSE 0 END AS is_fav
            FROM playlist_tracks pt
            INNER JOIN tracks t ON pt.track_id = t.id
            LEFT JOIN favorites f ON t.id = f.track_id
            WHERE pt.playlist_id = ? AND t.is_available = 1
            ORDER BY pt.position ASC
        """.trimIndent()

        db.rawQuery(query, arrayOf(playlistId)).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(cursorToTrack(cursor))
            }
        }
        list
    }

    suspend fun deletePlaylist(playlistId: String) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        db.delete("playlist_tracks", "playlist_id = ?", arrayOf(playlistId))
        db.delete("playlists", "id = ?", arrayOf(playlistId))
        notifyDataChanged()
    }

    suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        db.delete("playlist_tracks", "playlist_id = ? AND track_id = ?", arrayOf(playlistId, trackId))
        val countCursor = db.rawQuery("SELECT COUNT(*) FROM playlist_tracks WHERE playlist_id = ?", arrayOf(playlistId))
        val count = if (countCursor.moveToFirst()) countCursor.getInt(0) else 0
        countCursor.close()
        val cv = ContentValues().apply {
            put("track_count", count)
            put("updated_at", System.currentTimeMillis())
        }
        db.update("playlists", cv, "id = ?", arrayOf(playlistId))
        notifyDataChanged()
    }

    suspend fun seedDefaultPlaylistsIfEmpty() = withContext(Dispatchers.IO) {
        // Clean out any legacy mock playlists from previous iterations so user library is 100% clean
        val db = writableDatabase
        db.delete("playlists", "id IN ('pl_chill', 'pl_workout', 'pl_latenight', 'pl_focus', 'pl_travel', 'pl_rainy', 'pl_feelgood', 'pl_favorites')", null)
        db.delete("playlist_tracks", "playlist_id IN ('pl_chill', 'pl_workout', 'pl_latenight', 'pl_focus', 'pl_travel', 'pl_rainy', 'pl_feelgood', 'pl_favorites')", null)
        notifyDataChanged()
    }

    // ==========================================
    // HISTORY OPERATIONS
    // ==========================================

    suspend fun recordTrackPlayed(trackId: String) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("track_id", trackId)
            put("started_at", now)
        }
        db.insert("playback_history", null, values)
        try {
            val sql = """
                INSERT INTO track_playback_stats (track_id, play_count, skip_count, completed_count, last_played_at, total_listen_ms)
                VALUES (?, 1, 0, 0, ?, 0)
                ON CONFLICT(track_id) DO UPDATE SET
                    play_count = play_count + 1,
                    last_played_at = ?;
            """.trimIndent()
            db.execSQL(sql, arrayOf<Any>(trackId, now, now))
        } catch (_: Exception) {}
        notifyDataChanged()
    }

    suspend fun recordPlaybackTelemetry(
        trackId: String,
        listenDurationMs: Long,
        isCompleted: Boolean,
        isSkipped: Boolean
    ): Unit = withContext(Dispatchers.IO) {
        val db = writableDatabase
        val now = System.currentTimeMillis()
        val playIncrement = if (listenDurationMs >= 30_000L || isCompleted) 1 else 0
        val skipIncrement = if (isSkipped) 1 else 0
        val completedIncrement = if (isCompleted) 1 else 0

        val sql = """
            INSERT INTO track_playback_stats (track_id, play_count, skip_count, completed_count, last_played_at, total_listen_ms)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT(track_id) DO UPDATE SET
                play_count = play_count + ?,
                skip_count = skip_count + ?,
                completed_count = completed_count + ?,
                last_played_at = ?,
                total_listen_ms = total_listen_ms + ?;
        """.trimIndent()

        try {
            db.execSQL(
                sql,
                arrayOf<Any>(
                    trackId,
                    playIncrement,
                    skipIncrement,
                    completedIncrement,
                    now,
                    listenDurationMs,
                    playIncrement,
                    skipIncrement,
                    completedIncrement,
                    now,
                    listenDurationMs
                )
            )
        } catch (_: Exception) {}
        notifyDataChanged()
    }

    suspend fun getMostPlayedTracks(limit: Int = 30): List<Track> = withContext(Dispatchers.IO) {
        val list = mutableListOf<Track>()
        val db = readableDatabase
        val query = """
            SELECT t.*, CASE WHEN f.track_id IS NOT NULL THEN 1 ELSE 0 END AS is_fav
            FROM track_playback_stats s
            INNER JOIN tracks t ON s.track_id = t.id
            LEFT JOIN favorites f ON t.id = f.track_id
            WHERE t.is_available = 1 AND s.play_count > 0
            ORDER BY s.play_count DESC, s.last_played_at DESC
            LIMIT ?
        """.trimIndent()
        db.rawQuery(query, arrayOf(limit.toString())).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(cursorToTrack(cursor))
            }
        }
        list
    }

    suspend fun getHeavyRotationTracks(limit: Int = 30): List<Track> = withContext(Dispatchers.IO) {
        val list = mutableListOf<Track>()
        val db = readableDatabase
        val fourteenDaysAgo = System.currentTimeMillis() - (14L * 24 * 60 * 60 * 1000)
        val query = """
            SELECT t.*, CASE WHEN f.track_id IS NOT NULL THEN 1 ELSE 0 END AS is_fav
            FROM track_playback_stats s
            INNER JOIN tracks t ON s.track_id = t.id
            LEFT JOIN favorites f ON t.id = f.track_id
            WHERE t.is_available = 1 AND s.play_count > 0 AND s.last_played_at >= ?
            ORDER BY s.play_count DESC, s.last_played_at DESC
            LIMIT ?
        """.trimIndent()
        db.rawQuery(query, arrayOf(fourteenDaysAgo.toString(), limit.toString())).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(cursorToTrack(cursor))
            }
        }
        if (list.size < 5) {
            val most = getMostPlayedTracks(limit)
            val existingIds = list.map { it.id }.toSet()
            list.addAll(most.filter { it.id !in existingIds })
        }
        list.take(limit)
    }

    suspend fun getForgottenFavorites(limit: Int = 30): List<Track> = withContext(Dispatchers.IO) {
        val list = mutableListOf<Track>()
        val db = readableDatabase
        val twentyOneDaysAgo = System.currentTimeMillis() - (21L * 24 * 60 * 60 * 1000)
        val query = """
            SELECT t.*, CASE WHEN f.track_id IS NOT NULL THEN 1 ELSE 0 END AS is_fav
            FROM tracks t
            LEFT JOIN favorites f ON t.id = f.track_id
            LEFT JOIN track_playback_stats s ON t.id = s.track_id
            WHERE t.is_available = 1
              AND (f.track_id IS NOT NULL OR (s.play_count IS NOT NULL AND s.play_count >= 2))
              AND (s.last_played_at IS NULL OR s.last_played_at < ?)
            ORDER BY COALESCE(s.play_count, 1) DESC, t.date_added DESC
            LIMIT ?
        """.trimIndent()
        db.rawQuery(query, arrayOf(twentyOneDaysAgo.toString(), limit.toString())).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(cursorToTrack(cursor))
            }
        }
        list
    }

    suspend fun saveCachedLyrics(
        trackId: String,
        plainLyrics: String?,
        syncedLyrics: String?,
        source: String = "LRCLIB"
    ): Unit = withContext(Dispatchers.IO) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("track_id", trackId)
            put("plain_lyrics", plainLyrics)
            put("synced_lyrics", syncedLyrics)
            put("source", source)
            put("updated_at", System.currentTimeMillis())
        }
        db.insertWithOnConflict("lyrics_cache", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    suspend fun getCachedLyrics(trackId: String): CachedLyrics? = withContext(Dispatchers.IO) {
        val db = readableDatabase
        val query = "SELECT track_id, plain_lyrics, synced_lyrics, source, updated_at FROM lyrics_cache WHERE track_id = ? LIMIT 1"
        db.rawQuery(query, arrayOf(trackId)).use { cursor ->
            if (cursor.moveToFirst()) {
                CachedLyrics(
                    trackId = cursor.getString(0),
                    plainLyrics = if (cursor.isNull(1)) null else cursor.getString(1),
                    syncedLyrics = if (cursor.isNull(2)) null else cursor.getString(2),
                    source = cursor.getString(3) ?: "LRCLIB",
                    updatedAt = cursor.getLong(4)
                )
            } else null
        }
    }

    suspend fun getSmartPlaylists(): List<Playlist> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val mostPlayed = getMostPlayedTracks(50)
        val recentlyPlayed = getRecentlyPlayedTracks(50)
        val heavyRotation = getHeavyRotationTracks(50)
        val forgotten = getForgottenFavorites(50)
        val favs = getFavoriteTracks()

        listOf(
            Playlist(
                id = "pl_liked",
                name = "Liked Songs",
                description = "Your favorite tracks",
                isSmart = true,
                trackCount = favs.size,
                createdAt = now,
                updatedAt = now
            ),
            Playlist(
                id = "pl_most_played",
                name = "Most Played",
                description = "Your all-time on-repeat anthems",
                isSmart = true,
                trackCount = mostPlayed.size,
                createdAt = now,
                updatedAt = now
            ),
            Playlist(
                id = "pl_heavy_rotation",
                name = "Heavy Rotation",
                description = "Tracks you cannot stop playing lately",
                isSmart = true,
                trackCount = heavyRotation.size,
                createdAt = now,
                updatedAt = now
            ),
            Playlist(
                id = "pl_recently_played",
                name = "Recently Played",
                description = "Your chronological playback stream",
                isSmart = true,
                trackCount = recentlyPlayed.size,
                createdAt = now,
                updatedAt = now
            ),
            Playlist(
                id = "pl_forgotten_favorites",
                name = "Forgotten Favorites",
                description = "Tracks you loved that deserve a replay",
                isSmart = true,
                trackCount = forgotten.size,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    suspend fun getRecentlyPlayedTracks(limit: Int = 10): List<Track> = withContext(Dispatchers.IO) {
        val list = mutableListOf<Track>()
        val db = readableDatabase
        val query = """
            SELECT t.*, CASE WHEN f.track_id IS NOT NULL THEN 1 ELSE 0 END AS is_fav
            FROM playback_history h
            INNER JOIN tracks t ON h.track_id = t.id
            LEFT JOIN favorites f ON t.id = f.track_id
            WHERE t.is_available = 1
            ORDER BY h.started_at DESC
            LIMIT ?
        """.trimIndent()

        db.rawQuery(query, arrayOf(limit.toString())).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(cursorToTrack(cursor))
            }
        }
        list
    }

    suspend fun getVideoTracks(): List<Track> = withContext(Dispatchers.IO) {
        val list = mutableListOf<Track>()
        val db = readableDatabase
        val query = """
            SELECT t.*, CASE WHEN f.track_id IS NOT NULL THEN 1 ELSE 0 END AS is_fav
            FROM tracks t
            LEFT JOIN favorites f ON t.id = f.track_id
            WHERE t.is_available = 1 AND t.is_video = 1
            ORDER BY t.title COLLATE NOCASE ASC
        """.trimIndent()

        db.rawQuery(query, null).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(cursorToTrack(cursor))
            }
        }
        list
    }

    // Cursor Converter
    private fun cursorToTrack(c: Cursor): Track {
        val isFavCol = c.getColumnIndex("is_fav")
        val isFavorite = if (isFavCol >= 0) c.getInt(isFavCol) == 1 else false

        val isVideoCol = c.getColumnIndex("is_video")
        val isVideo = if (isVideoCol >= 0) c.getInt(isVideoCol) == 1 else false

        val vWidthCol = c.getColumnIndex("video_width")
        val videoWidth = if (vWidthCol >= 0) c.getInt(vWidthCol) else 0

        val vHeightCol = c.getColumnIndex("video_height")
        val videoHeight = if (vHeightCol >= 0) c.getInt(vHeightCol) else 0

        return Track(
            id = c.getString(c.getColumnIndexOrThrow("id")),
            sourceId = c.getString(c.getColumnIndexOrThrow("source_id")),
            fileUri = c.getString(c.getColumnIndexOrThrow("file_uri")),
            title = c.getString(c.getColumnIndexOrThrow("title")),
            artistName = c.getString(c.getColumnIndexOrThrow("artist_name")),
            albumTitle = c.getString(c.getColumnIndexOrThrow("album_title")),
            durationMs = c.getLong(c.getColumnIndexOrThrow("duration_ms")),
            artistId = c.getString(c.getColumnIndexOrThrow("artist_id")),
            albumId = c.getString(c.getColumnIndexOrThrow("album_id")),
            trackNumber = c.getInt(c.getColumnIndexOrThrow("track_number")),
            discNumber = c.getInt(c.getColumnIndexOrThrow("disc_number")),
            genre = c.getString(c.getColumnIndexOrThrow("genre")),
            year = if (c.isNull(c.getColumnIndexOrThrow("year"))) null else c.getInt(c.getColumnIndexOrThrow("year")),
            format = c.getString(c.getColumnIndexOrThrow("format")),
            bitrate = c.getInt(c.getColumnIndexOrThrow("bitrate")),
            sampleRate = c.getInt(c.getColumnIndexOrThrow("sample_rate")),
            fileSize = c.getLong(c.getColumnIndexOrThrow("file_size")),
            artworkUri = c.getString(c.getColumnIndexOrThrow("artwork_uri")),
            isAvailable = c.getInt(c.getColumnIndexOrThrow("is_available")) == 1,
            isFavorite = isFavorite,
            dateAdded = c.getLong(c.getColumnIndexOrThrow("date_added")),
            isVideo = isVideo,
            videoWidth = videoWidth,
            videoHeight = videoHeight
        )
    }

    suspend fun updateTrackMetadata(payload: com.example.gobeats.core.media.TagEditPayload): Track? = withContext(Dispatchers.IO) {
        val db = writableDatabase
        db.beginTransaction()
        var updatedTrack: Track? = null
        try {
            val trackId = payload.trackId
            val artistId = "art_" + payload.artist.hashCode().toString()
            val albumId = "alb_" + (payload.album + payload.artist).hashCode().toString()

            // Ensure artist exists
            val artistValues = ContentValues().apply {
                put("id", artistId)
                put("name", payload.artist)
                put("created_at", System.currentTimeMillis())
            }
            db.insertWithOnConflict("artists", null, artistValues, SQLiteDatabase.CONFLICT_IGNORE)

            // Ensure album exists
            val albumValues = ContentValues().apply {
                put("id", albumId)
                put("title", payload.album)
                put("artist_id", artistId)
                put("artist_name", payload.artist)
                if (payload.artworkUri != null) {
                    put("artwork_uri", payload.artworkUri)
                }
                put("created_at", System.currentTimeMillis())
            }
            db.insertWithOnConflict("albums", null, albumValues, SQLiteDatabase.CONFLICT_IGNORE)

            // Update tracks table
            val trackValues = ContentValues().apply {
                put("title", payload.title)
                put("artist_name", payload.artist)
                put("album_title", payload.album)
                put("artist_id", artistId)
                put("album_id", albumId)
                if (payload.year != null) put("year", payload.year)
                if (payload.genre != null) put("genre", payload.genre)
                if (payload.trackNumber != null) put("track_number", payload.trackNumber)
                if (payload.artworkUri != null) put("artwork_uri", payload.artworkUri)
            }
            db.update("tracks", trackValues, "id = ?", arrayOf(trackId))

            // Record override in track_metadata_overrides table so rescans preserve it
            val overrideValues = ContentValues().apply {
                put("track_id", trackId)
                put("title", payload.title)
                put("artist", payload.artist)
                put("album", payload.album)
                put("genre", payload.genre)
                put("year", payload.year)
                put("artwork_uri", payload.artworkUri)
                put("updated_at", System.currentTimeMillis())
            }
            db.insertWithOnConflict("track_metadata_overrides", null, overrideValues, SQLiteDatabase.CONFLICT_REPLACE)

            // Fetch the updated track
            val query = """
                SELECT t.*, CASE WHEN f.track_id IS NOT NULL THEN 1 ELSE 0 END AS is_fav
                FROM tracks t
                LEFT JOIN favorites f ON t.id = f.track_id
                WHERE t.id = ?
                LIMIT 1
            """.trimIndent()
            db.rawQuery(query, arrayOf(trackId)).use { cursor ->
                if (cursor.moveToFirst()) {
                    updatedTrack = cursorToTrack(cursor)
                }
            }

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
            notifyDataChanged()
        }
        updatedTrack
    }

    suspend fun getTrackById(trackId: String): Track? = withContext(Dispatchers.IO) {
        val db = readableDatabase
        val query = """
            SELECT t.*, CASE WHEN f.track_id IS NOT NULL THEN 1 ELSE 0 END AS is_fav
            FROM tracks t
            LEFT JOIN favorites f ON t.id = f.track_id
            WHERE t.id = ?
            LIMIT 1
        """.trimIndent()
        db.rawQuery(query, arrayOf(trackId)).use { cursor ->
            if (cursor.moveToFirst()) cursorToTrack(cursor) else null
        }
    }

    suspend fun saveVideoPosition(videoId: String, positionMs: Long, durationMs: Long): Unit = withContext(Dispatchers.IO) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("video_id", videoId)
            put("position_ms", positionMs)
            put("duration_ms", durationMs)
            put("updated_at", System.currentTimeMillis())
        }
        db.insertWithOnConflict("video_playback_positions", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    suspend fun getVideoPosition(videoId: String): VideoPlaybackPosition? = withContext(Dispatchers.IO) {
        val db = readableDatabase
        val query = "SELECT video_id, position_ms, duration_ms, updated_at FROM video_playback_positions WHERE video_id = ? LIMIT 1"
        db.rawQuery(query, arrayOf(videoId)).use { cursor ->
            if (cursor.moveToFirst()) {
                VideoPlaybackPosition(
                    videoId = cursor.getString(0),
                    positionMs = cursor.getLong(1),
                    durationMs = cursor.getLong(2),
                    updatedAt = cursor.getLong(3)
                )
            } else null
        }
    }

    suspend fun clearVideoPosition(videoId: String): Unit = withContext(Dispatchers.IO) {
        val db = writableDatabase
        db.delete("video_playback_positions", "video_id = ?", arrayOf(videoId))
    }

    suspend fun savePlaybackState(
        trackId: String?,
        positionMs: Long,
        queueIds: List<String>,
        isPlaying: Boolean
    ): Unit = withContext(Dispatchers.IO) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("id", 1)
            put("current_track_id", trackId)
            put("position_ms", positionMs.coerceAtLeast(0L))
            put("is_playing", if (isPlaying) 1 else 0)
            put("queue_json", ProcessDeathPersistenceEngine.serializeQueue(queueIds))
            put("updated_at", System.currentTimeMillis())
        }
        db.insertWithOnConflict("playback_state", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    suspend fun getPlaybackState(): SavedPlaybackState? = withContext(Dispatchers.IO) {
        val db = readableDatabase
        val query = "SELECT current_track_id, position_ms, is_playing, queue_json, updated_at FROM playback_state WHERE id = 1 LIMIT 1"
        db.rawQuery(query, null).use { cursor ->
            if (cursor.moveToFirst()) {
                val trackId = if (cursor.isNull(0)) null else cursor.getString(0)
                val pos = cursor.getLong(1).coerceAtLeast(0L)
                val isPlaying = cursor.getInt(2) == 1
                val queueJson = cursor.getString(3) ?: "[]"
                val updatedAt = cursor.getLong(4)
                val queueIds = try {
                    ProcessDeathPersistenceEngine.deserializeQueue(queueJson)
                } catch (_: Exception) {
                    emptyList()
                }
                SavedPlaybackState(
                    currentTrackId = trackId,
                    positionMs = pos,
                    isPlaying = isPlaying,
                    queueTrackIds = queueIds,
                    updatedAt = updatedAt
                )
            } else null
        }
    }

    suspend fun clearPlaybackState(): Unit = withContext(Dispatchers.IO) {
        val db = writableDatabase
        db.delete("playback_state", "id = 1", null)
    }

    suspend fun getTracksByIds(trackIds: List<String>): List<Track> = withContext(Dispatchers.IO) {
        if (trackIds.isEmpty()) return@withContext emptyList()
        val list = mutableListOf<Track>()
        val db = readableDatabase
        trackIds.chunked(900).forEach { chunk ->
            val placeholders = chunk.joinToString(",") { "?" }
            val query = """
                SELECT t.*, CASE WHEN f.track_id IS NOT NULL THEN 1 ELSE 0 END AS is_fav
                FROM tracks t
                LEFT JOIN favorites f ON t.id = f.track_id
                WHERE t.id IN ($placeholders)
            """.trimIndent()
            db.rawQuery(query, chunk.toTypedArray()).use { cursor ->
                while (cursor.moveToNext()) {
                    list.add(cursorToTrack(cursor))
                }
            }
        }
        list
    }

    // ==========================================
    // DEVICE AUDIO PROFILES & SUBTITLES (v5)
    // ==========================================

    suspend fun saveDeviceAudioProfile(profile: DeviceAudioProfile) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("device_id", profile.deviceId)
            put("device_name", profile.name)
            put("device_type", profile.type.name)
            put("preferred_eq_preset", profile.preferredEqPreset)
            put("av_delay_ms", profile.avDelayMs)
            put("updated_at", profile.updatedAt)
        }
        db.insertWithOnConflict("device_audio_profiles", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    suspend fun getDeviceAudioProfile(deviceId: String): DeviceAudioProfile? = withContext(Dispatchers.IO) {
        val db = readableDatabase
        val cursor = db.query(
            "device_audio_profiles",
            null,
            "device_id = ?",
            arrayOf(deviceId),
            null,
            null,
            null
        )
        cursor.use { c ->
            if (c.moveToFirst()) {
                val dId = c.getString(c.getColumnIndexOrThrow("device_id"))
                val name = c.getString(c.getColumnIndexOrThrow("device_name"))
                val typeStr = c.getString(c.getColumnIndexOrThrow("device_type"))
                val type = try {
                    DeviceAudioProfileType.valueOf(typeStr)
                } catch (_: Exception) {
                    DeviceAudioProfileType.SPEAKER
                }
                val preset = c.getString(c.getColumnIndexOrThrow("preferred_eq_preset"))
                val avDelay = c.getLong(c.getColumnIndexOrThrow("av_delay_ms"))
                val updated = c.getLong(c.getColumnIndexOrThrow("updated_at"))
                DeviceAudioProfile(
                    deviceId = dId,
                    name = name,
                    type = type,
                    preferredEqPreset = preset,
                    avDelayMs = avDelay,
                    updatedAt = updated
                )
            } else null
        }
    }

    suspend fun getAllDeviceAudioProfiles(): List<DeviceAudioProfile> = withContext(Dispatchers.IO) {
        val db = readableDatabase
        val list = mutableListOf<DeviceAudioProfile>()
        val cursor = db.query(
            "device_audio_profiles",
            null,
            null,
            null,
            null,
            null,
            "updated_at DESC"
        )
        cursor.use { c ->
            while (c.moveToNext()) {
                val dId = c.getString(c.getColumnIndexOrThrow("device_id"))
                val name = c.getString(c.getColumnIndexOrThrow("device_name"))
                val typeStr = c.getString(c.getColumnIndexOrThrow("device_type"))
                val type = try {
                    DeviceAudioProfileType.valueOf(typeStr)
                } catch (_: Exception) {
                    DeviceAudioProfileType.SPEAKER
                }
                val preset = c.getString(c.getColumnIndexOrThrow("preferred_eq_preset"))
                val avDelay = c.getLong(c.getColumnIndexOrThrow("av_delay_ms"))
                val updated = c.getLong(c.getColumnIndexOrThrow("updated_at"))
                list.add(
                    DeviceAudioProfile(
                        deviceId = dId,
                        name = name,
                        type = type,
                        preferredEqPreset = preset,
                        avDelayMs = avDelay,
                        updatedAt = updated
                    )
                )
            }
        }
        list
    }

    suspend fun saveGeneratedSubtitle(trackId: String, lang: String, srtContent: String) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("track_id", trackId)
            put("language_code", lang)
            put("subtitle_content", srtContent)
            put("created_at", System.currentTimeMillis())
        }
        db.insertWithOnConflict("offline_generated_subtitles", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    suspend fun getGeneratedSubtitle(trackId: String, lang: String): String? = withContext(Dispatchers.IO) {
        val db = readableDatabase
        val cursor = db.query(
            "offline_generated_subtitles",
            arrayOf("subtitle_content"),
            "track_id = ? AND language_code = ?",
            arrayOf(trackId, lang),
            null,
            null,
            null
        )
        cursor.use { c ->
            if (c.moveToFirst()) {
                c.getString(c.getColumnIndexOrThrow("subtitle_content"))
            } else null
        }
    }
}

data class VideoPlaybackPosition(
    val videoId: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long
)

data class CachedLyrics(
    val trackId: String,
    val plainLyrics: String?,
    val syncedLyrics: String?,
    val source: String,
    val updatedAt: Long
)


