package com.example.gobeats.core.backup

import com.example.gobeats.core.database.GobeatsDatabaseHelper
import com.example.gobeats.core.model.Track
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream

data class BackupTrackRecord(
    val uri: String,
    val title: String,
    val artist: String = ""
)

data class BackupPlaylistRecord(
    val name: String,
    val description: String = "",
    val tracks: List<BackupTrackRecord> = emptyList()
)

data class BackupParsedPayload(
    val version: Int,
    val app: String,
    val exportedAt: Long,
    val playlists: List<BackupPlaylistRecord>,
    val favorites: List<BackupTrackRecord>
)

data class BackupRestoreResult(
    val success: Boolean,
    val playlistsRestored: Int,
    val tracksLinked: Int,
    val favoritesRestored: Int,
    val message: String
)

object BackupRestoreEngine {

    const val CURRENT_SCHEMA_VERSION = 1

    /**
     * Pure function: Serializes structured playlist and favorite records to a valid JSON string.
     */
    fun serializeBackup(
        playlists: List<BackupPlaylistRecord>,
        favorites: List<BackupTrackRecord>,
        exportedAt: Long = System.currentTimeMillis()
    ): String {
        val root = JSONObject()
        root.put("version", CURRENT_SCHEMA_VERSION)
        root.put("app", "GoBeats")
        root.put("exportedAt", exportedAt)

        val playlistsArray = JSONArray()
        for (pl in playlists) {
            val plJson = JSONObject()
            plJson.put("name", pl.name)
            plJson.put("description", pl.description)

            val tracksArray = JSONArray()
            for (tr in pl.tracks) {
                val tJson = JSONObject()
                tJson.put("uri", tr.uri)
                tJson.put("title", tr.title)
                tJson.put("artist", tr.artist)
                tracksArray.put(tJson)
            }
            plJson.put("tracks", tracksArray)
            playlistsArray.put(plJson)
        }
        root.put("playlists", playlistsArray)

        val favoritesArray = JSONArray()
        for (fav in favorites) {
            val fJson = JSONObject()
            fJson.put("uri", fav.uri)
            fJson.put("title", fav.title)
            fJson.put("artist", fav.artist)
            favoritesArray.put(fJson)
        }
        root.put("favorites", favoritesArray)

        return root.toString(2)
    }

    /**
     * Pure function: Parses and validates a JSON string into a BackupParsedPayload.
     */
    fun parseBackup(jsonString: String): BackupParsedPayload {
        val root = JSONObject(jsonString)
        val app = root.optString("app", "")
        if (app != "GoBeats" && !root.has("playlists")) {
            throw IllegalArgumentException("Invalid backup format: Not a recognized GoBeats backup file.")
        }

        val version = root.optInt("version", 1)
        val exportedAt = root.optLong("exportedAt", 0L)

        val playlists = mutableListOf<BackupPlaylistRecord>()
        val playlistsArray = root.optJSONArray("playlists") ?: JSONArray()
        for (i in 0 until playlistsArray.length()) {
            val plObj = playlistsArray.getJSONObject(i)
            val name = plObj.optString("name", "Restored Playlist").trim()
            val desc = plObj.optString("description", "")
            val tracks = mutableListOf<BackupTrackRecord>()
            val tracksArray = plObj.optJSONArray("tracks") ?: JSONArray()
            for (j in 0 until tracksArray.length()) {
                val tObj = tracksArray.getJSONObject(j)
                tracks.add(
                    BackupTrackRecord(
                        uri = tObj.optString("uri", ""),
                        title = tObj.optString("title", ""),
                        artist = tObj.optString("artist", "")
                    )
                )
            }
            playlists.add(BackupPlaylistRecord(name, desc, tracks))
        }

        val favorites = mutableListOf<BackupTrackRecord>()
        val favoritesArray = root.optJSONArray("favorites") ?: JSONArray()
        for (i in 0 until favoritesArray.length()) {
            val fObj = favoritesArray.getJSONObject(i)
            favorites.add(
                BackupTrackRecord(
                    uri = fObj.optString("uri", ""),
                    title = fObj.optString("title", ""),
                    artist = fObj.optString("artist", "")
                )
            )
        }

        return BackupParsedPayload(version, app, exportedAt, playlists, favorites)
    }

    /**
     * Pure function: Matches a backup track record against locally available tracks.
     * Uses exact URI first, falling back to case-insensitive Title and Artist match.
     */
    fun matchBackupTrack(record: BackupTrackRecord, availableTracks: List<Track>): Track? {
        val exactUri = availableTracks.firstOrNull { it.fileUri == record.uri }
        if (exactUri != null) return exactUri

        val titleMatches = availableTracks.filter { it.title.trim().equals(record.title.trim(), ignoreCase = true) }
        if (titleMatches.isEmpty()) return null
        if (titleMatches.size == 1) return titleMatches.first()

        return titleMatches.firstOrNull { it.artistName.trim().equals(record.artist.trim(), ignoreCase = true) }
            ?: titleMatches.first()
    }

    /**
     * Serializes user playlists, track associations, and favorites from the SQLite database.
     */
    suspend fun createBackupJson(databaseHelper: GobeatsDatabaseHelper): String {
        val dbPlaylists = databaseHelper.getAllPlaylists().filter { !it.isSmart && it.id != "pl_liked" && it.id != "pl_favorites" }
        val playlistRecords = dbPlaylists.map { pl ->
            val tracks = databaseHelper.getPlaylistTracks(pl.id)
            BackupPlaylistRecord(
                name = pl.name,
                description = pl.description ?: "",
                tracks = tracks.map { BackupTrackRecord(it.fileUri, it.title, it.artistName) }
            )
        }

        val dbFavorites = databaseHelper.getFavoriteTracks()
        val favoriteRecords = dbFavorites.map { BackupTrackRecord(it.fileUri, it.title, it.artistName) }

        return serializeBackup(playlistRecords, favoriteRecords)
    }

    /**
     * Exports backup directly to an OutputStream.
     */
    suspend fun exportToStream(databaseHelper: GobeatsDatabaseHelper, outputStream: OutputStream) {
        val json = createBackupJson(databaseHelper)
        outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write(json)
            writer.flush()
        }
    }

    /**
     * Restores playlists and favorites from a JSON string into the SQLite database.
     */
    suspend fun restoreFromJson(databaseHelper: GobeatsDatabaseHelper, jsonString: String): BackupRestoreResult {
        return try {
            val parsed = parseBackup(jsonString)
            val allTracks = databaseHelper.getAllTracks()

            var playlistsRestored = 0
            var tracksLinked = 0
            var favoritesRestored = 0

            val existingPlaylists = databaseHelper.getAllPlaylists().associateBy { it.name.trim().lowercase() }

            for (pl in parsed.playlists) {
                val playlist = existingPlaylists[pl.name.lowercase()]
                    ?: databaseHelper.createPlaylist(pl.name, pl.description)

                playlistsRestored++

                for (tr in pl.tracks) {
                    val matched = matchBackupTrack(tr, allTracks)
                    if (matched != null) {
                        databaseHelper.addTrackToPlaylist(playlist.id, matched.id)
                        tracksLinked++
                    }
                }
            }

            for (fav in parsed.favorites) {
                val matched = matchBackupTrack(fav, allTracks)
                if (matched != null) {
                    databaseHelper.toggleFavorite(matched.id)
                    favoritesRestored++
                }
            }

            BackupRestoreResult(
                success = true,
                playlistsRestored = playlistsRestored,
                tracksLinked = tracksLinked,
                favoritesRestored = favoritesRestored,
                message = "Restored $playlistsRestored playlists ($tracksLinked tracks linked) and $favoritesRestored favorites."
            )
        } catch (e: Exception) {
            BackupRestoreResult(
                success = false,
                playlistsRestored = 0,
                tracksLinked = 0,
                favoritesRestored = 0,
                message = "Failed to restore backup: ${e.message}"
            )
        }
    }

    /**
     * Restores backup directly from an InputStream.
     */
    suspend fun restoreFromStream(databaseHelper: GobeatsDatabaseHelper, inputStream: InputStream): BackupRestoreResult {
        val jsonString = inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        return restoreFromJson(databaseHelper, jsonString)
    }
}
