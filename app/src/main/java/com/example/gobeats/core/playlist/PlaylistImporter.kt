package com.example.gobeats.core.playlist

import com.example.gobeats.core.model.Track
import java.io.InputStream
import java.net.URLDecoder

data class ImportedTrackEntry(
    val title: String,
    val artist: String,
    val album: String? = null,
    val rawLine: String = "$artist - $title",
    val streamUri: String? = null
)

data class PlaylistImportResult(
    val playlistName: String,
    val matchedTracks: List<Track>,
    val unmatchedEntries: List<ImportedTrackEntry>,
    val totalCount: Int,
    val streamTracks: List<Track> = emptyList()
) {
    val totalPlayableTracks: List<Track>
        get() = matchedTracks + streamTracks

    val matchRatePercent: Int
        get() = if (totalCount > 0) (((matchedTracks.size + streamTracks.size).toFloat() / totalCount) * 100).toInt().coerceAtMost(100) else 0
}

object PlaylistImporter {

    private val STRIP_REGEX = Regex("""(?i)\s*[\(\[\{](?:official\s*(?:video|audio|music\s*video)|lyrics?|remaster(?:ed)?(?:\s*\d{4})?|live|deluxe|bonus\s*track|feat\.?[^\)\]\}]*)[\)\]\}]""")
    private val PUNCT_REGEX = Regex("""[^\p{L}\p{Nd}\s]""")
    private val WEB_PAGE_HOSTS = setOf("open.spotify.com", "spotify.link", "music.apple.com", "itunes.apple.com")

    /**
     * Parses playlist content from an InputStream given its file extension / MIME hint.
     */
    fun parseStream(input: InputStream, filename: String = ""): Pair<String, List<ImportedTrackEntry>> {
        val text = input.bufferedReader().use { it.readText() }
        val defaultName = filename.substringBeforeLast('.').ifBlank { "Imported Playlist" }
        return parseText(text, defaultName)
    }

    /**
     * Parses raw text formatted as M3U, Spotify/Exportify CSV, Apple Music TSV, or plain text tracklists.
     */
    fun parseText(content: String, fallbackName: String = "Imported Playlist"): Pair<String, List<ImportedTrackEntry>> {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return Pair(fallbackName, emptyList())

        // 1. Detect M3U / M3U8
        if (trimmed.startsWith("#EXTM3U", ignoreCase = true) || trimmed.contains("#EXTINF:")) {
            return parseM3u(trimmed, fallbackName)
        }

        // 2. Detect CSV / TSV with headers
        val firstLine = trimmed.lines().firstOrNull() ?: ""
        if (firstLine.contains("Track Name", ignoreCase = true) ||
            firstLine.contains("Song Name", ignoreCase = true) ||
            (firstLine.contains(",") && firstLine.contains("Artist", ignoreCase = true))) {
            return parseCsv(trimmed, fallbackName)
        }

        if (firstLine.contains("\t") && firstLine.contains("Artist", ignoreCase = true)) {
            return parseTsv(trimmed, fallbackName)
        }

        // 3. Fallback: Plain text line-by-line (also supports direct http/https stream URLs)
        return parsePlainText(trimmed, fallbackName)
    }

    internal fun parseM3u(content: String, defaultName: String): Pair<String, List<ImportedTrackEntry>> {
        val entries = mutableListOf<ImportedTrackEntry>()
        val lines = content.lines()
        var currentTitle = ""
        var currentArtist = ""
        var resolvedPlaylistName = defaultName

        for (line in lines) {
            val l = line.trim()
            if (l.startsWith("#PLAYLIST:", ignoreCase = true)) {
                val plName = l.substringAfter(":").trim()
                if (plName.isNotBlank()) resolvedPlaylistName = plName
            } else if (l.startsWith("#EXTINF:", ignoreCase = true)) {
                // Example: #EXTINF:230,The Weeknd - Starboy
                val commaIdx = l.indexOf(',')
                if (commaIdx != -1 && commaIdx + 1 < l.length) {
                    val info = l.substring(commaIdx + 1).trim()
                    if (info.contains(" - ")) {
                        val parts = info.split(" - ", limit = 2)
                        currentArtist = parts[0].trim()
                        currentTitle = parts[1].trim()
                    } else {
                        currentTitle = info
                        currentArtist = ""
                    }
                }
            } else if (!l.startsWith("#") && l.isNotBlank()) {
                val isHttp = isStreamUrl(l)
                val derivedTitle = if (currentTitle.isNotBlank()) {
                    currentTitle
                } else {
                    extractTitleFromPathOrUrl(l)
                }
                val artist = if (currentArtist.isNotBlank()) currentArtist else if (isHttp) "Internet Stream" else ""
                entries.add(
                    ImportedTrackEntry(
                        title = derivedTitle,
                        artist = artist,
                        rawLine = l,
                        streamUri = if (isHttp) l else null
                    )
                )
                currentTitle = ""
                currentArtist = ""
            }
        }

        return Pair(resolvedPlaylistName, entries)
    }

    internal fun parseCsv(content: String, defaultName: String): Pair<String, List<ImportedTrackEntry>> {
        val entries = mutableListOf<ImportedTrackEntry>()
        val lines = content.lines()
        if (lines.isEmpty()) return Pair(defaultName, entries)

        val headerCols = parseCsvLine(lines.first()).map { it.trim().lowercase() }
        val titleIdx = headerCols.indexOfFirst { it == "track name" || it == "song name" || it == "title" || it == "name" }
        val artistIdx = headerCols.indexOfFirst { it.contains("artist") }
        val albumIdx = headerCols.indexOfFirst { it.contains("album") }
        val urlIdx = headerCols.indexOfFirst { it.contains("stream") || it == "url" || it == "file_uri" || it == "uri" }

        for (i in 1 until lines.size) {
            val line = lines[i].trim()
            if (line.isBlank()) continue
            val cols = parseCsvLine(line)
            val title = if (titleIdx in cols.indices) cols[titleIdx].trim() else ""
            val artist = if (artistIdx in cols.indices) cols[artistIdx].trim() else ""
            val album = if (albumIdx in cols.indices) cols[albumIdx].trim() else null
            val rawUrl = if (urlIdx in cols.indices) cols[urlIdx].trim() else null
            val streamUri = rawUrl?.takeIf { isStreamUrl(it) }

            if (title.isNotBlank() || streamUri != null) {
                entries.add(
                    ImportedTrackEntry(
                        title = title.ifBlank { extractTitleFromPathOrUrl(streamUri ?: "") },
                        artist = artist.ifBlank { if (streamUri != null) "Internet Stream" else "" },
                        album = album,
                        rawLine = line,
                        streamUri = streamUri
                    )
                )
            }
        }

        return Pair(defaultName, entries)
    }

    internal fun parseTsv(content: String, defaultName: String): Pair<String, List<ImportedTrackEntry>> {
        val entries = mutableListOf<ImportedTrackEntry>()
        val lines = content.lines()
        if (lines.isEmpty()) return Pair(defaultName, entries)

        val headerCols = lines.first().split('\t').map { it.trim().lowercase() }
        val titleIdx = headerCols.indexOfFirst { it == "name" || it == "title" || it == "track name" }
        val artistIdx = headerCols.indexOfFirst { it.contains("artist") }
        val albumIdx = headerCols.indexOfFirst { it.contains("album") }

        for (i in 1 until lines.size) {
            val line = lines[i].trim()
            if (line.isBlank()) continue
            val cols = line.split('\t')
            val title = if (titleIdx in cols.indices) cols[titleIdx].trim() else ""
            val artist = if (artistIdx in cols.indices) cols[artistIdx].trim() else ""
            val album = if (albumIdx in cols.indices) cols[albumIdx].trim() else null

            if (title.isNotBlank()) {
                entries.add(ImportedTrackEntry(title = title, artist = artist, album = album, rawLine = line))
            }
        }

        return Pair(defaultName, entries)
    }

    internal fun parsePlainText(content: String, defaultName: String): Pair<String, List<ImportedTrackEntry>> {
        val entries = mutableListOf<ImportedTrackEntry>()
        val lines = content.lines()

        for (raw in lines) {
            val l = raw.trim()
            if (l.isBlank() || l.startsWith("//") || l.startsWith("#")) continue

            // Direct HTTP/HTTPS stream URL on its own line
            if (isStreamUrl(l)) {
                entries.add(
                    ImportedTrackEntry(
                        title = extractTitleFromPathOrUrl(l),
                        artist = "Internet Stream",
                        rawLine = l,
                        streamUri = l
                    )
                )
                continue
            }

            // Formats:
            // 1) "Artist - Title"
            // 2) "1. Artist - Title" or "01 - Artist - Title"
            // 3) "Title by Artist"
            val strippedNumber = l.replace(Regex("""^\s*\d+[\.\-\)]\s*"""), "")
            if (strippedNumber.contains(" - ")) {
                val parts = strippedNumber.split(" - ", limit = 2)
                val first = parts[0].trim()
                val second = parts[1].trim()
                if (isStreamUrl(second)) {
                    entries.add(
                        ImportedTrackEntry(
                            title = first,
                            artist = "Internet Stream",
                            rawLine = l,
                            streamUri = second
                        )
                    )
                } else {
                    entries.add(ImportedTrackEntry(title = second, artist = first, rawLine = l))
                }
            } else if (strippedNumber.contains(" by ", ignoreCase = true)) {
                val parts = strippedNumber.split(Regex("""(?i)\s+by\s+"""), limit = 2)
                entries.add(ImportedTrackEntry(title = parts[0].trim(), artist = parts[1].trim(), rawLine = l))
            } else {
                entries.add(ImportedTrackEntry(title = strippedNumber, artist = "", rawLine = l))
            }
        }

        return Pair(defaultName, entries)
    }

    private fun isStreamUrl(candidate: String): Boolean {
        val lower = candidate.trim().lowercase()
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return false
        val host = lower.substringAfter("://").substringBefore('/')
        return host !in WEB_PAGE_HOSTS
    }

    private fun extractTitleFromPathOrUrl(pathOrUrl: String): String {
        val withoutQuery = pathOrUrl.substringBefore('?').substringBefore('#')
        val rawSegment = withoutQuery.substringAfterLast('/').substringAfterLast('\\')
        val decoded = try {
            URLDecoder.decode(rawSegment, "UTF-8")
        } catch (_: Exception) {
            rawSegment
        }
        return decoded
            .substringBeforeLast('.')
            .replace('_', ' ')
            .replace('-', ' ')
            .replace(Regex("""\s+"""), " ")
            .trim()
            .ifBlank { "Internet Stream" }
    }

    private fun detectStreamFormat(url: String): String {
        val clean = url.substringBefore('?').lowercase()
        return when {
            clean.endsWith(".flac") -> "FLAC"
            clean.endsWith(".wav") -> "WAV"
            clean.endsWith(".m3u8") -> "HLS"
            clean.endsWith(".mp4") -> "MP4"
            clean.endsWith(".mkv") -> "MKV"
            clean.endsWith(".webm") -> "WEBM"
            clean.endsWith(".m4a") || clean.endsWith(".aac") -> "M4A"
            clean.endsWith(".opus") -> "OPUS"
            clean.endsWith(".ogg") -> "OGG"
            else -> "STREAM"
        }
    }

    private fun isVideoStreamUrl(url: String): Boolean {
        val clean = url.substringBefore('?').lowercase()
        return clean.endsWith(".mp4") || clean.endsWith(".mkv") || clean.endsWith(".webm") ||
            clean.endsWith(".mov") || clean.endsWith(".m3u8")
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val sb = java.lang.StringBuilder()
        var inQuotes = false

        for (ch in line) {
            when {
                ch == '\"' -> inQuotes = !inQuotes
                ch == ',' && !inQuotes -> {
                    result.add(sb.toString())
                    sb.setLength(0)
                }
                else -> sb.append(ch)
            }
        }
        result.add(sb.toString())
        return result
    }

    /**
     * Matches imported entries against the offline library using clean token fuzzy matching,
     * file path matching, and direct internet stream creation for online URLs.
     */
    fun matchTracks(imported: List<ImportedTrackEntry>, library: List<Track>, playlistName: String): PlaylistImportResult {
        val matchedTracks = mutableListOf<Track>()
        val matchedTrackIds = mutableSetOf<String>()
        val streamTracks = mutableListOf<Track>()
        val unmatched = mutableListOf<ImportedTrackEntry>()

        for (entry in imported) {
            val cleanEntryTitle = cleanForMatching(entry.title)
            val cleanEntryArtist = cleanForMatching(entry.artist)
            val rawFileSegment = entry.rawLine.substringBefore('?').substringAfterLast('/').substringAfterLast('\\').substringBeforeLast('.').lowercase()

            // 1. Exact or near-exact match by title/artist or filename
            var candidate = library.find { track ->
                if (entry.rawLine.isNotBlank() && track.fileUri.equals(entry.rawLine, ignoreCase = true)) {
                    return@find true
                }
                val trackFileName = track.fileUri.substringAfterLast('/').substringAfterLast('\\').substringBeforeLast('.').lowercase()
                if (rawFileSegment.length >= 3 && trackFileName == rawFileSegment) {
                    return@find true
                }

                val trackTitle = cleanForMatching(track.title)
                val trackArtist = cleanForMatching(track.artistName)

                val titleMatches = cleanEntryTitle.isNotBlank() && (
                    trackTitle == cleanEntryTitle ||
                        trackTitle.contains(cleanEntryTitle) ||
                        cleanEntryTitle.contains(trackTitle)
                    )

                val artistMatches = cleanEntryArtist.isBlank() ||
                    cleanEntryArtist == "internet stream" ||
                    trackArtist == cleanEntryArtist ||
                    trackArtist.contains(cleanEntryArtist) ||
                    cleanEntryArtist.contains(trackArtist)

                titleMatches && artistMatches
            }

            // 2. Fallback: fuzzy token overlap on title if candidate not found yet
            if (candidate == null && cleanEntryTitle.length >= 3) {
                val entryTokens = cleanEntryTitle.split(' ').filter { it.length > 2 }.toSet()
                candidate = library.maxByOrNull { track ->
                    val trackTokens = cleanForMatching(track.title).split(' ').filter { it.length > 2 }.toSet()
                    val overlap = entryTokens.intersect(trackTokens).size
                    if (overlap >= 2 || (entryTokens.isNotEmpty() && overlap == entryTokens.size)) overlap else -1
                }?.takeIf { track ->
                    val trackTokens = cleanForMatching(track.title).split(' ').filter { it.length > 2 }.toSet()
                    entryTokens.intersect(trackTokens).isNotEmpty()
                }
            }

            if (candidate != null && !matchedTrackIds.contains(candidate.id)) {
                matchedTracks.add(candidate)
                matchedTrackIds.add(candidate.id)
            } else if (entry.streamUri != null) {
                val streamId = "stream_${entry.streamUri.hashCode().toUInt().toString(16)}"
                if (!matchedTrackIds.contains(streamId)) {
                    val streamTrack = Track(
                        id = streamId,
                        title = entry.title.ifBlank { extractTitleFromPathOrUrl(entry.streamUri) },
                        artistName = entry.artist.ifBlank { "Internet Stream" },
                        albumTitle = entry.album ?: "Online Streams",
                        durationMs = 0L,
                        format = detectStreamFormat(entry.streamUri),
                        bitrate = 320,
                        sampleRate = 44100,
                        fileUri = entry.streamUri,
                        sourceId = "source_internet",
                        isVideo = isVideoStreamUrl(entry.streamUri)
                    )
                    streamTracks.add(streamTrack)
                    matchedTrackIds.add(streamId)
                }
            } else {
                unmatched.add(entry)
            }
        }

        return PlaylistImportResult(
            playlistName = playlistName,
            matchedTracks = matchedTracks,
            unmatchedEntries = unmatched,
            totalCount = imported.size,
            streamTracks = streamTracks
        )
    }

    fun cleanForMatching(s: String): String {
        return s
            .replace(STRIP_REGEX, " ")
            .replace(PUNCT_REGEX, " ")
            .lowercase()
            .replace(Regex("""\s+"""), " ")
            .trim()
    }
}

