package com.example.gobeats.core.lyrics

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class LrclibRecord(
    val id: Long,
    val trackName: String,
    val artistName: String,
    val albumName: String?,
    val durationSeconds: Long,
    val instrumental: Boolean,
    val plainLyrics: String?,
    val syncedLyrics: String?
)

object LrclibClient {

    private const val BASE_URL = "https://lrclib.net/api"
    private const val USER_AGENT = "gobeats-android/1.0 (https://github.com/example/gobeats)"
    private const val CONNECT_TIMEOUT_MS = 6000
    private const val READ_TIMEOUT_MS = 9000

    /**
     * Cleans titles by removing parenthetical/bracketed noise (e.g. "[320kbps]", "(Official Video)").
     */
    fun cleanTitleForSearch(rawTitle: String): String {
        var clean = rawTitle.replace(Regex("""(\[.*?\]|\(.*?(video|audio|lyrics|kbps|official|download|remix|hd|feat|ft).*?\})""", RegexOption.IGNORE_CASE), "")
        clean = clean.replace(Regex("""^\d+[\s\.\-_]+"""), "") // Remove leading track numbers e.g. "01 - "
        clean = clean.replace(Regex("""[\(\[].*?[\)\]]"""), "") // Remove any remaining brackets
        return clean.trim().ifEmpty { rawTitle.trim() }
    }

    fun cleanArtistForSearch(rawArtist: String): String {
        val lower = rawArtist.lowercase().trim()
        if (lower == "local audio" || lower == "unknown artist" || lower == "unknown" ||
            lower == "music video" || lower == "<unknown>") {
            return ""
        }
        return rawArtist.trim()
    }

    /**
     * Attempts to fetch lyrics via exact match, falling back to search if needed.
     */
    fun fetchLyrics(
        trackName: String,
        artistName: String,
        albumName: String? = null,
        durationSeconds: Long? = null
    ): String? {
        val cleanTrack = cleanTitleForSearch(trackName)
        val cleanArtist = cleanArtistForSearch(artistName)

        // 1. Try exact match if duration and artist are provided
        if (durationSeconds != null && durationSeconds > 0 && cleanArtist.isNotBlank()) {
            try {
                val exact = getExact(cleanTrack, cleanArtist, albumName, durationSeconds)
                if (exact != null) {
                    if (!exact.syncedLyrics.isNullOrBlank()) return exact.syncedLyrics
                    if (!exact.plainLyrics.isNullOrBlank()) return exact.plainLyrics
                }
            } catch (_: Exception) {}
        }

        // 2. Fallback to targeted search by track name and artist
        try {
            val results = search(cleanTrack, cleanArtist)
            val bestSynced = results.firstOrNull { !it.syncedLyrics.isNullOrBlank() }
            if (bestSynced?.syncedLyrics?.isNotBlank() == true) {
                return bestSynced.syncedLyrics
            }

            val bestPlain = results.firstOrNull { !it.plainLyrics.isNullOrBlank() }
            if (bestPlain?.plainLyrics?.isNotBlank() == true) {
                return bestPlain.plainLyrics
            }
        } catch (_: Exception) {}

        // 3. Fallback to broad query search if artist was missing
        if (cleanArtist.isBlank()) {
            try {
                val results = searchByQuery(cleanTrack)
                val bestSynced = results.firstOrNull { !it.syncedLyrics.isNullOrBlank() }
                if (bestSynced?.syncedLyrics?.isNotBlank() == true) {
                    return bestSynced.syncedLyrics
                }
            } catch (_: Exception) {}
        }

        return null
    }

    /**
     * Calls GET https://lrclib.net/api/get
     */
    fun getExact(
        trackName: String,
        artistName: String,
        albumName: String?,
        durationSeconds: Long
    ): LrclibRecord? {
        val sb = StringBuilder("$BASE_URL/get?")
        sb.append("track_name=").append(URLEncoder.encode(trackName, "UTF-8"))
        sb.append("&artist_name=").append(URLEncoder.encode(artistName, "UTF-8"))
        if (!albumName.isNullOrBlank()) {
            sb.append("&album_name=").append(URLEncoder.encode(albumName, "UTF-8"))
        }
        sb.append("&duration=").append(durationSeconds)

        val jsonString = executeHttpGet(sb.toString()) ?: return null
        return parseRecord(JSONObject(jsonString))
    }

    /**
     * Calls GET https://lrclib.net/api/search?track_name=...&artist_name=...
     */
    fun search(trackName: String, artistName: String): List<LrclibRecord> {
        val sb = StringBuilder("$BASE_URL/search?")
        sb.append("track_name=").append(URLEncoder.encode(trackName, "UTF-8"))
        if (artistName.isNotBlank()) {
            sb.append("&artist_name=").append(URLEncoder.encode(artistName, "UTF-8"))
        }

        val jsonString = executeHttpGet(sb.toString()) ?: return emptyList()
        return parseRecordList(JSONArray(jsonString))
    }

    /**
     * Calls GET https://lrclib.net/api/search?q=...
     */
    fun searchByQuery(query: String): List<LrclibRecord> {
        val url = "$BASE_URL/search?q=" + URLEncoder.encode(query, "UTF-8")
        val jsonString = executeHttpGet(url) ?: return emptyList()
        return parseRecordList(JSONArray(jsonString))
    }

    fun searchLyrics(query: String): List<LrclibRecord> = searchByQuery(query)

    fun parseRecord(obj: JSONObject): LrclibRecord {
        return LrclibRecord(
            id = obj.optLong("id", 0L),
            trackName = obj.optString("trackName", ""),
            artistName = obj.optString("artistName", ""),
            albumName = obj.optString("albumName").ifBlank { null },
            durationSeconds = obj.optLong("duration", 0L),
            instrumental = obj.optBoolean("instrumental", false),
            plainLyrics = obj.optString("plainLyrics").ifBlank { null },
            syncedLyrics = obj.optString("syncedLyrics").ifBlank { null }
        )
    }

    fun parseRecordList(arr: JSONArray): List<LrclibRecord> {
        val list = mutableListOf<LrclibRecord>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            list.add(parseRecord(obj))
        }
        return list
    }

    private fun executeHttpGet(urlString: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(urlString)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept", "application/json")
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS

            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).use { reader ->
                    reader.readText()
                }
            } else {
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }
}
