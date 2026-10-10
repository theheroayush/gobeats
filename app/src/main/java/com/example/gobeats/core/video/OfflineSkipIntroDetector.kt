package com.example.gobeats.core.video

import android.content.ContentResolver
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gobeats.core.model.SkipIntroSegment
import com.example.gobeats.core.model.Track
import com.example.gobeats.theme.GobeatsAmber
import java.io.File
import java.util.Locale
import java.util.regex.Pattern

/**
 * 100% Offline detector of intros/openings in video files.
 *
 * Inspects track duration and playback position, searches for companion chapter markers
 * with labels ('Intro', 'Opening', 'OP', 'Theme', 'Prologue'), and provides an intelligent
 * default smart intro window (0s to 85s / 90s for long media > 5 minutes).
 */
object OfflineSkipIntroDetector {

    /** Default duration for anime/series opening theme in milliseconds (85 seconds). */
    const val DEFAULT_SMART_INTRO_END_MS = 85_000L

    /** Minimum video duration (5 minutes) required to activate the default smart intro window. */
    const val MIN_MEDIA_DURATION_FOR_SMART_INTRO_MS = 300_000L

    private val PRIMARY_INTRO_PATTERN = Pattern.compile(
        "(?i)\\b(intro|opening|op|theme\\s*song|theme|title\\s*sequence|main\\s*title)\\b"
    )
    private val SECONDARY_INTRO_PATTERN = Pattern.compile(
        "(?i)\\b(prologue|recap|credits)\\b"
    )

    data class ParsedChapter(
        val name: String,
        val startMs: Long,
        val endMs: Long
    )

    /**
     * Detects the intro/opening segment for a given [Track].
     *
     * 1. Checks companion chapter files on local disk (e.g. .chapters.txt, .chp, .cue, .vtt).
     * 2. If a chapter matches common intro/opening labels, returns the chapter boundary.
     * 3. Falls back to smart intro window (0s to 85s) if media duration > 5 minutes.
     * 4. For shorter media without chapter markers, returns a non-active segment (0s to 0s).
     */
    fun detectIntroSegment(track: Track): SkipIntroSegment {
        // Step 1: Attempt to detect chapters from companion files on disk
        val companionChapters = findAndParseCompanionChapters(track.fileUri)
        val matchedChapter = findMatchingIntroChapter(companionChapters)
        if (matchedChapter != null) {
            val safeEnd = if (matchedChapter.endMs > matchedChapter.startMs) {
                matchedChapter.endMs
            } else {
                (matchedChapter.startMs + DEFAULT_SMART_INTRO_END_MS).coerceAtMost(track.durationMs)
            }
            val formattedLabel = if (matchedChapter.name.isNotBlank()) {
                "Skip ${matchedChapter.name.trim()}"
            } else {
                "Skip Intro"
            }
            return SkipIntroSegment(
                startMs = matchedChapter.startMs.coerceAtLeast(0L),
                endMs = safeEnd,
                label = formattedLabel
            )
        }

        // Step 2: Intelligent default smart intro window for long media (> 5 minutes)
        if (track.durationMs >= MIN_MEDIA_DURATION_FOR_SMART_INTRO_MS) {
            val smartEnd = DEFAULT_SMART_INTRO_END_MS.coerceAtMost(track.durationMs)
            return SkipIntroSegment(
                startMs = 0L,
                endMs = smartEnd,
                label = "Skip Intro"
            )
        }

        // Short media (< 5 min): no intro segment
        return SkipIntroSegment(
            startMs = 0L,
            endMs = 0L,
            label = ""
        )
    }

    /**
     * Determines whether the Skip Intro button should be active and visible at the given [positionMs].
     */
    fun isSkipIntroActive(positionMs: Long, segment: SkipIntroSegment): Boolean {
        if (segment.endMs <= segment.startMs) return false
        val activeEndThreshold = (segment.endMs - 800L).coerceAtLeast(segment.startMs)
        return positionMs >= segment.startMs && positionMs < activeEndThreshold
    }

    /**
     * Returns the target timestamp in milliseconds to seek to when user triggers Skip Intro.
     */
    fun getSkipTargetMs(segment: SkipIntroSegment): Long {
        return segment.endMs.coerceAtLeast(0L)
    }

    /**
     * Inspects local storage for companion chapter files (.chapters.txt, .chp, .cue, chapters.vtt).
     */
    internal fun findAndParseCompanionChapters(fileUriString: String): List<ParsedChapter> {
        val path = when {
            fileUriString.startsWith("file://", ignoreCase = true) -> fileUriString.substring(7)
            !fileUriString.contains("://") -> fileUriString
            else -> try {
                val parsedUri = Uri.parse(fileUriString)
                if (parsedUri.scheme == ContentResolver.SCHEME_FILE) parsedUri.path else null
            } catch (_: Throwable) {
                null
            }
        } ?: return emptyList()

        val videoFile = File(path)
        val parentDir = videoFile.parentFile ?: return emptyList()
        if (!parentDir.exists() || !parentDir.isDirectory) return emptyList()

        val baseName = videoFile.nameWithoutExtension
        val chapterFiles = parentDir.listFiles { _, name ->
            val lower = name.lowercase(Locale.ROOT)
            (lower.startsWith(baseName.lowercase(Locale.ROOT)) || lower.contains("chapter")) &&
                    (lower.endsWith(".chapters.txt") || lower.endsWith(".chp") ||
                            lower.endsWith(".cue") || lower.endsWith(".vtt") || lower.endsWith(".txt"))
        } ?: return emptyList()

        for (chpFile in chapterFiles) {
            val chapters = parseChapterFile(chpFile)
            if (chapters.isNotEmpty()) {
                return chapters
            }
        }

        return emptyList()
    }

    /**
     * Parses standard chapter file formats:
     * - OGM Chapter format (CHAPTER01=00:00:00.000, CHAPTER01NAME=Opening)
     * - WebVTT Chapter format (00:00:00.000 --> 00:01:25.000 \n Intro)
     * - CUE Sheet format (TRACK 01 AUDIO \n TITLE "Intro" \n INDEX 01 00:00:00)
     */
    internal fun parseChapterFile(file: File): List<ParsedChapter> {
        if (!file.exists() || !file.canRead()) return emptyList()
        val lines = try {
            file.readLines()
        } catch (_: Exception) {
            return emptyList()
        }

        val results = mutableListOf<ParsedChapter>()

        // 1. Try OGM chapter format:
        // CHAPTER01=00:00:00.000
        // CHAPTER01NAME=Intro
        val ogmTimes = mutableMapOf<String, Long>()
        val ogmNames = mutableMapOf<String, String>()
        val ogmKeyPattern = Pattern.compile("(?i)^CHAPTER(\\d+)=(.*)$")
        val ogmNamePattern = Pattern.compile("(?i)^CHAPTER(\\d+)NAME=(.*)$")

        for (line in lines) {
            val trimmed = line.trim()
            val timeMatcher = ogmKeyPattern.matcher(trimmed)
            if (timeMatcher.matches()) {
                val index = timeMatcher.group(1) ?: continue
                val timeStr = timeMatcher.group(2)?.trim() ?: continue
                ogmTimes[index] = parseTimestampToMs(timeStr)
                continue
            }

            val nameMatcher = ogmNamePattern.matcher(trimmed)
            if (nameMatcher.matches()) {
                val index = nameMatcher.group(1) ?: continue
                val nameStr = nameMatcher.group(2)?.trim() ?: continue
                ogmNames[index] = nameStr
            }
        }

        if (ogmTimes.isNotEmpty()) {
            val sortedKeys = ogmTimes.keys.sortedBy { it.toIntOrNull() ?: 0 }
            for (i in sortedKeys.indices) {
                val key = sortedKeys[i]
                val start = ogmTimes[key] ?: 0L
                val nextKey = sortedKeys.getOrNull(i + 1)
                val end = if (nextKey != null) ogmTimes[nextKey] ?: (start + DEFAULT_SMART_INTRO_END_MS) else (start + DEFAULT_SMART_INTRO_END_MS)
                val name = ogmNames[key] ?: "Chapter $key"
                results.add(ParsedChapter(name = name, startMs = start, endMs = end))
            }
            if (results.isNotEmpty()) return results
        }

        // 2. Try VTT chapter format:
        // 00:00:00.000 --> 00:01:25.000
        // Intro
        var currentStart = -1L
        var currentEnd = -1L
        val vttTimePattern = Pattern.compile("(\\d{1,2}:\\d{2}:\\d{2}[.,]\\d{3})\\s*-->\\s*(\\d{1,2}:\\d{2}:\\d{2}[.,]\\d{3})")

        for (line in lines) {
            val trimmed = line.trim()
            val matcher = vttTimePattern.matcher(trimmed)
            if (matcher.find()) {
                currentStart = parseTimestampToMs(matcher.group(1) ?: "")
                currentEnd = parseTimestampToMs(matcher.group(2) ?: "")
            } else if (currentStart >= 0 && trimmed.isNotBlank() && !trimmed.startsWith("NOTE") && !trimmed.startsWith("WEBVTT")) {
                results.add(ParsedChapter(name = trimmed, startMs = currentStart, endMs = currentEnd))
                currentStart = -1L
                currentEnd = -1L
            }
        }

        return results
    }

    internal fun findMatchingIntroChapter(chapters: List<ParsedChapter>): ParsedChapter? {
        for (chapter in chapters) {
            if (PRIMARY_INTRO_PATTERN.matcher(chapter.name).find()) {
                return chapter
            }
        }
        for (chapter in chapters) {
            if (SECONDARY_INTRO_PATTERN.matcher(chapter.name).find()) {
                return chapter
            }
        }
        return null
    }

    /**
     * Parses timestamps like "00:01:25.000" or "01:25,000" or "01:25" into milliseconds.
     */
    internal fun parseTimestampToMs(raw: String): Long {
        try {
            val clean = raw.trim().replace(',', '.')
            val parts = clean.split(':')
            return when (parts.size) {
                3 -> {
                    val hours = parts[0].toLong()
                    val minutes = parts[1].toLong()
                    val secondsParts = parts[2].split('.')
                    val seconds = secondsParts[0].toLong()
                    val millis = if (secondsParts.size > 1) {
                        secondsParts[1].padEnd(3, '0').take(3).toLong()
                    } else 0L
                    (hours * 3600_000L) + (minutes * 60_000L) + (seconds * 1000L) + millis
                }
                2 -> {
                    val minutes = parts[0].toLong()
                    val secondsParts = parts[1].split('.')
                    val seconds = secondsParts[0].toLong()
                    val millis = if (secondsParts.size > 1) {
                        secondsParts[1].padEnd(3, '0').take(3).toLong()
                    } else 0L
                    (minutes * 60_000L) + (seconds * 1000L) + millis
                }
                else -> 0L
            }
        } catch (_: Exception) {
            return 0L
        }
    }
}

/**
 * Jetpack Compose Skip Intro pill button with fluid entry/exit animations.
 */
@Composable
fun SkipIntroPill(
    visible: Boolean,
    segment: SkipIntroSegment,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInHorizontally { it / 2 },
        exit = fadeOut() + slideOutHorizontally { it / 2 },
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xE618181B),
            border = BorderStroke(1.dp, GobeatsAmber),
            shadowElevation = 6.dp,
            modifier = Modifier.clickable { onSkip() }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.FastForward,
                    contentDescription = null,
                    tint = GobeatsAmber,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = segment.label.ifBlank { "Skip Intro" },
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.3.sp
                )
            }
        }
    }
}
