package com.example.gobeats.core.lyrics

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.example.gobeats.core.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class LyricsRepository(private val context: Context) {

    private val cache = mutableMapOf<String, LyricsData>()
    private val lyricsDir = File(context.filesDir, "lyrics").apply { mkdirs() }

    /**
     * Resolves the raw absolute filesystem path for a track, supporting both
     * direct file URIs and MediaStore content:// URIs.
     */
    fun resolveFilePath(track: Track): String? {
        val fileUri = track.fileUri
        if (fileUri.startsWith("file://")) {
            return Uri.parse(fileUri).path
        }
        if (fileUri.startsWith("/")) {
            return fileUri
        }
        if (fileUri.startsWith("content://")) {
            try {
                val uri = Uri.parse(fileUri)
                val projection = arrayOf(MediaStore.MediaColumns.DATA)
                context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                        if (idx != -1) {
                            val path = cursor.getString(idx)
                            if (!path.isNullOrBlank()) return path
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        return null
    }

    /**
     * Looks for a companion .lrc or .txt file in the same directory as the audio file.
     * Evaluates exact case first, then case-insensitive (.lrc, .LRC, .txt, .TXT).
     */
    fun findCompanionLyrics(track: Track): LyricsData? {
        val audioPath = resolveFilePath(track) ?: return null
        val audioFile = File(audioPath)
        val parentDir = audioFile.parentFile ?: return null
        if (!parentDir.exists() || !parentDir.canRead()) return null

        val baseName = audioFile.nameWithoutExtension

        // 1. Direct check
        val directLrc = File(parentDir, "$baseName.lrc")
        if (directLrc.exists() && directLrc.canRead()) {
            return LrcParser.parse(directLrc.readText(), source = "companion")
        }

        val directUpperLrc = File(parentDir, "$baseName.LRC")
        if (directUpperLrc.exists() && directUpperLrc.canRead()) {
            return LrcParser.parse(directUpperLrc.readText(), source = "companion")
        }

        // 2. Directory scan for case-insensitive match
        try {
            val matchedFile = parentDir.listFiles()?.find { file ->
                file.isFile && (
                        file.name.equals("$baseName.lrc", ignoreCase = true) ||
                        file.name.equals("$baseName.txt", ignoreCase = true)
                )
            }
            if (matchedFile != null && matchedFile.canRead()) {
                val content = matchedFile.readText()
                if (content.isNotBlank()) {
                    return LrcParser.parse(content, source = "companion")
                }
            }
        } catch (_: Exception) {}

        return null
    }

    /**
     * Retrieves offline lyrics with strict priority:
     * 1. In-app custom/cached lyrics
     * 2. Companion .lrc in track directory
     * 3. Embedded ID3/FLAC/M4A metadata tags
     * Returns null if no offline lyrics are available.
     */
    suspend fun getOfflineLyrics(track: Track): LyricsData? = withContext(Dispatchers.IO) {
        // 1. In-memory cache
        cache[track.id]?.let {
            if (it.lines.isNotEmpty()) return@withContext it
        }

        // 2. Check user-saved / cached lyrics in app internal storage
        val localCustomFile = File(lyricsDir, "${track.id}.lrc")
        if (localCustomFile.exists() && localCustomFile.canRead()) {
            val content = localCustomFile.readText()
            if (content.isNotBlank()) {
                val parsed = LrcParser.parse(content, source = "local")
                if (parsed.lines.isNotEmpty()) {
                    cache[track.id] = parsed
                    return@withContext parsed
                }
            }
        }

        // 3. Companion .lrc file in the same directory
        val companion = findCompanionLyrics(track)
        if (companion != null && companion.lines.isNotEmpty()) {
            cache[track.id] = companion
            return@withContext companion
        }

        // 4. Embedded tags (ID3 USLT/SYLT, FLAC Vorbis, M4A ilst)
        val embedded = EmbeddedLyricsExtractor.extractLyrics(context, track)
        if (embedded != null && embedded.lines.isNotEmpty()) {
            cache[track.id] = embedded
            return@withContext embedded
        }

        null
    }

    /**
     * Full lyrics resolution chain:
     * Cache -> Companion .lrc -> Embedded ID3/FLAC/M4A -> Online LRCLIB -> Empty
     */
    suspend fun getLyricsForTrack(track: Track): LyricsData = withContext(Dispatchers.IO) {
        // 1-3. Attempt offline sources first
        val offline = getOfflineLyrics(track)
        if (offline != null && offline.lines.isNotEmpty()) {
            return@withContext offline
        }

        // 4. Automated Online Synced Lyrics via LRCLIB
        try {
            val durationSec = if (track.durationMs > 0) track.durationMs / 1000L else null
            val onlineContent = LrclibClient.fetchLyrics(
                trackName = track.title,
                artistName = track.artistName,
                albumName = track.albumTitle,
                durationSeconds = durationSec
            )

            if (!onlineContent.isNullOrBlank()) {
                // Permanently save to disk for 100% offline access next time!
                val localCustomFile = File(lyricsDir, "${track.id}.lrc")
                localCustomFile.writeText(onlineContent)
                val parsed = LrcParser.parse(onlineContent, source = "lrclib")
                cache[track.id] = parsed
                return@withContext parsed
            }
        } catch (_: Exception) {
            // Graceful fallback on network failure or offline mode
        }

        // 5. Default empty lyrics
        return@withContext LyricsData.EMPTY
    }

    suspend fun searchOnlineLyrics(query: String): List<LrclibRecord> = withContext(Dispatchers.IO) {
        try {
            LrclibClient.searchLyrics(query)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun applyOnlineLyric(trackId: String, record: LrclibRecord): LyricsData = withContext(Dispatchers.IO) {
        val content = record.syncedLyrics ?: record.plainLyrics ?: ""
        if (content.isNotBlank()) {
            val targetFile = File(lyricsDir, "$trackId.lrc")
            targetFile.writeText(content)
            val parsed = LrcParser.parse(content, source = "lrclib")
            cache[trackId] = parsed
            parsed
        } else {
            LyricsData.EMPTY
        }
    }

    suspend fun saveLyrics(trackId: String, content: String): LyricsData = withContext(Dispatchers.IO) {
        val targetFile = File(lyricsDir, "$trackId.lrc")
        targetFile.writeText(content)
        val parsed = LrcParser.parse(content, source = "local")
        cache[trackId] = parsed
        parsed
    }

    suspend fun importLyricsFromUri(trackId: String, uri: Uri): LyricsData = withContext(Dispatchers.IO) {
        val content = context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.bufferedReader().readText()
        } ?: ""

        if (content.isNotBlank()) {
            saveLyrics(trackId, content)
        } else {
            LyricsData.EMPTY
        }
    }
}
