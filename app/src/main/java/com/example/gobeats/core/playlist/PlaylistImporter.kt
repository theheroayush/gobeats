package com.example.gobeats.core.playlist

import com.example.gobeats.core.model.Track
import java.io.InputStream

data class ImportedTrackEntry(
    val title: String,
    val artist: String,
    val album: String? = null,
    val rawLine: String = "$artist - $title"
)

data class PlaylistImportResult(
    val playlistName: String,
    val matchedTracks: List<Track>,
    val unmatchedEntries: List<ImportedTrackEntry>,
    val totalCount: Int
) {
    val matchRatePercent: Int
        get() = if (totalCount > 0) ((matchedTracks.size.toFloat() / totalCount) * 100).toInt() else 0
}

object PlaylistImporter {

    private val STRIP_REGEX = Regex("""(?i)\s*[\(\[\{](?:official\s*(?:video|audio|music\s*video)|lyrics?|remaster(?:ed)?(?:\s*\d{4})?|live|deluxe|bonus\s*track|feat\.?[^\)\]\}]*)[\)\]\}]""")
    private val PUNCT_REGEX = Regex("""[^\p{L}\p{Nd}\s]""")

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

        // 3. Fallback: Plain text line-by-line
        return parsePlainText(trimmed, fallbackName)
    }

    internal fun parseM3u(content: String, defaultName: String): Pair<String, List<ImportedTrackEntry>> {
        val entries = mutableListOf<ImportedTrackEntry>()
        val lines = content.lines()
        var currentTitle = ""
        var currentArtist = ""

        for (line in lines) {
            val l = line.trim()
            if (l.startsWith("#EXTINF:", ignoreCase = true)) {
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
                val title = if (currentTitle.isNotBlank()) currentTitle else l.substringAfterLast('/').substringAfterLast('\\').substringBeforeLast('.')
                val artist = currentArtist
                entries.add(ImportedTrackEntry(title = title, artist = artist, rawLine = l))
                currentTitle = ""
                currentArtist = ""
            }
        }

        return Pair(defaultName, entries)
    }

    internal fun parseCsv(content: String, defaultName: String): Pair<String, List<ImportedTrackEntry>> {
        val entries = mutableListOf<ImportedTrackEntry>()
        val lines = content.lines()
        if (lines.isEmpty()) return Pair(defaultName, entries)

        val headerCols = parseCsvLine(lines.first()).map { it.trim().lowercase() }
        val titleIdx = headerCols.indexOfFirst { it == "track name" || it == "song name" || it == "title" || it == "name" }
        val artistIdx = headerCols.indexOfFirst { it.contains("artist") }
        val albumIdx = headerCols.indexOfFirst { it.contains("album") }

        for (i in 1 until lines.size) {
            val line = lines[i].trim()
            if (line.isBlank()) continue
            val cols = parseCsvLine(line)
            val title = if (titleIdx in cols.indices) cols[titleIdx].trim() else ""
            val artist = if (artistIdx in cols.indices) cols[artistIdx].trim() else ""
            val album = if (albumIdx in cols.indices) cols[albumIdx].trim() else null

            if (title.isNotBlank()) {
                entries.add(ImportedTrackEntry(title = title, artist = artist, album = album, rawLine = line))
            }
        }

        return Pair(defaultName, entries)
    }

    internal fun parseTsv(content: String, defaultName: String): Pair<String, List<ImportedTrackEntry>> {
        val entries = mutableListOf<ImportedTrackEntry>()
        val lines = content.lines()
        if (lines.isEmpty()) return Pair(defaultName, entries)

        val headerCols = lines.first().split('\t').map { it.trim().lowercase() }
        val titleIdx = headerCols.indexOfFirst { it == "name" || it == "title" }
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

            // Formats:
            // 1) "Artist - Title"
            // 2) "1. Artist - Title" or "01 - Artist - Title"
            // 3) "Title by Artist"
            val strippedNumber = l.replace(Regex("""^\s*\d+[\.\-\)]\s*"""), "")
            if (strippedNumber.contains(" - ")) {
                val parts = strippedNumber.split(" - ", limit = 2)
                entries.add(ImportedTrackEntry(title = parts[1].trim(), artist = parts[0].trim(), rawLine = l))
            } else if (strippedNumber.contains(" by ", ignoreCase = true)) {
                val parts = strippedNumber.split(Regex("""(?i)\s+by\s+"""), limit = 2)
                entries.add(ImportedTrackEntry(title = parts[0].trim(), artist = parts[1].trim(), rawLine = l))
            } else {
                entries.add(ImportedTrackEntry(title = strippedNumber, artist = "", rawLine = l))
            }
        }

        return Pair(defaultName, entries)
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
     * Matches imported entries against the offline library using clean token fuzzy matching.
     */
    fun matchTracks(imported: List<ImportedTrackEntry>, library: List<Track>, playlistName: String): PlaylistImportResult {
        val matchedTracks = mutableListOf<Track>()
        val matchedTrackIds = mutableSetOf<String>()
        val unmatched = mutableListOf<ImportedTrackEntry>()

        for (entry in imported) {
            val cleanEntryTitle = cleanForMatching(entry.title)
            val cleanEntryArtist = cleanForMatching(entry.artist)

            // 1. Exact or near-exact match
            var candidate = library.find { track ->
                val trackTitle = cleanForMatching(track.title)
                val trackArtist = cleanForMatching(track.artistName)

                val titleMatches = trackTitle == cleanEntryTitle ||
                    trackTitle.contains(cleanEntryTitle) ||
                    cleanEntryTitle.contains(trackTitle)

                val artistMatches = cleanEntryArtist.isBlank() ||
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
            } else {
                unmatched.add(entry)
            }
        }

        return PlaylistImportResult(
            playlistName = playlistName,
            matchedTracks = matchedTracks,
            unmatchedEntries = unmatched,
            totalCount = imported.size
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
