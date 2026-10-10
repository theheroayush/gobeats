package com.example.gobeats.feature.library.folders

import java.io.File
import java.util.Random

/**
 * Core engine for directory tree parsing, audio extension filtering,
 * natural sorting, and queue generation for GoBeats folder browsing.
 */
object FolderBrowserEngine {

    val SUPPORTED_AUDIO_EXTENSIONS = setOf("mp3", "flac", "m4a", "wav", "ogg", "opus", "aac")

    /**
     * Determines whether a given file name corresponds to a supported audio format.
     * Case-insensitive evaluation.
     */
    fun isSupportedAudioFile(fileName: String): Boolean {
        val ext = fileName.substringAfterLast(".", "").lowercase()
        return SUPPORTED_AUDIO_EXTENSIONS.contains(ext)
    }

    /**
     * Sorts directory entries: directories first, then files, both in case-insensitive alphabetical order.
     * Entry format: Pair<name, isDirectory>.
     */
    fun sortFolderEntries(entries: List<Pair<String, Boolean>>): List<Pair<String, Boolean>> {
        return entries.sortedWith(
            compareByDescending<Pair<String, Boolean>> { it.second }
                .thenBy { it.first.lowercase() }
        )
    }

    /**
     * Generates a playable queue of audio file paths.
     * If shuffle is false, orders naturally (case-insensitively alphabetical).
     * If shuffle is true, applies deterministic/pseudo-random shuffling while preserving all items.
     */
    fun generateFolderQueue(audioFiles: List<String>, shuffle: Boolean): List<String> {
        val sorted = audioFiles.sortedBy { it.lowercase() }
        return if (shuffle) sorted.shuffled(Random(42)) else sorted
    }

    /**
     * Splits a path into non-empty segments for breadcrumb navigation.
     */
    fun getPathSegments(path: String): List<String> {
        return path.split("/").filter { it.isNotEmpty() }
    }

    /**
     * Builds the absolute path corresponding to a zero-indexed breadcrumb segment.
     */
    fun buildPathForSegmentIndex(path: String, index: Int): String {
        val segments = getPathSegments(path)
        if (segments.isEmpty() || index < 0) return "/"
        val targetSegments = segments.take((index + 1).coerceAtMost(segments.size))
        return "/" + targetSegments.joinToString("/")
    }
}
