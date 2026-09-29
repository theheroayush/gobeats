package com.example.gobeats.core.database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.gobeats.core.model.Album
import com.example.gobeats.core.model.Artist
import com.example.gobeats.core.model.MusicSource
import com.example.gobeats.core.model.Playlist
import com.example.gobeats.core.model.SourceType
import com.example.gobeats.core.model.Track
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
        const val DATABASE_VERSION = 2

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

        // Seed default local music source
        val defaultSource = ContentValues().apply {
            put("id", "source_local")
            put("type", SourceType.LOCAL_STORAGE.name)
            put("name", "This Phone")
            put("created_at", System.currentTimeMillis())
        }
        db.insert("music_sources", null, defaultSource)
    }

    // ==========================================
    // TRACK OPERATIONS
    // ==========================================

    suspend fun insertOrUpdateTrack(track: Track) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        db.beginTransaction()
        try {
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
                put("source_id", track.sourceId)
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

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
            notifyDataChanged()
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
            )
            ORDER BY t.title COLLATE NOCASE ASC
            LIMIT 50
        """.trimIndent()
        val arg = "%$query%"

        db.rawQuery(sqlQuery, arrayOf(arg, arg, arg)).use { cursor ->
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
        val values = ContentValues().apply {
            put("id", id)
            put("name", name)
            put("description", description)
            put("created_at", now)
            put("updated_at", now)
        }
        db.insert("playlists", null, values)
        notifyDataChanged()
        Playlist(id = id, name = name, description = description, createdAt = now, updatedAt = now)
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
        val db = writableDatabase
        db.beginTransaction()
        try {
            // Find max position
            val maxPosCursor = db.rawQuery("SELECT COALESCE(MAX(position), -1) FROM playlist_tracks WHERE playlist_id = ?", arrayOf(playlistId))
            val nextPos = maxPosCursor.use { if (it.moveToFirst()) it.getInt(0) + 1 else 0 }

            val values = ContentValues().apply {
                put("playlist_id", playlistId)
                put("track_id", trackId)
                put("position", nextPos)
                put("added_at", System.currentTimeMillis())
            }
            db.insert("playlist_tracks", null, values)

            val updateValues = ContentValues().apply {
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
        val values = ContentValues().apply {
            put("track_id", trackId)
            put("started_at", System.currentTimeMillis())
        }
        db.insert("playback_history", null, values)
        notifyDataChanged()
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
}
