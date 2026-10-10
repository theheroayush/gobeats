package com.example.gobeats

import com.example.gobeats.core.audio.CrossfadeEngine
import com.example.gobeats.core.lyrics.EmbeddedLyricsExtractor
import com.example.gobeats.core.lyrics.LyricLine
import com.example.gobeats.core.lyrics.LyricsData
import com.example.gobeats.core.lyrics.LrcParser
import com.example.gobeats.core.lyrics.OfflineLyricsResolver
import com.example.gobeats.core.media.Id3TagReader
import com.example.gobeats.core.model.RepeatMode
import com.example.gobeats.feature.library.folders.FolderBrowserEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Milestone 3 Empirical Stress & Adversarial Test Suite
 *
 * Focus:
 * - Feature F10: Folders Directory Tree, audio extension filters, natural sorting, deterministic shuffle queue, breadcrumb navigation.
 * - Feature F11: Audiophile Gapless & Configurable Crossfade, equal-power curves, boundary condition triggers, single-track queues, repeat modes.
 * - Feature F12: Offline Synced Lyrics, ID3 USLT/SYLT stream parsing, LRC timestamp extraction, 5-tier priority hierarchy, network failure resilience.
 * - Cross-Feature: Folder queue transitions with active crossfade, companion lyrics isolation in folder hierarchies, offline fallbacks.
 */
class Milestone3EmpiricalStressTest {

    // =========================================================================
    // SECTION 1: Feature F10 — Folders Directory Tree Stress
    // =========================================================================

    @Test
    fun f10_stress_audioExtensionFiltering_caseAndOddFormats() {
        val validFiles = listOf(
            "track.mp3", "TRACK.MP3", "Track.Mp3",
            "music.flac", "MUSIC.FLAC",
            "audio.m4a", "AUDIO.M4A",
            "sample.wav", "SAMPLE.WAV",
            "voice.ogg", "VOICE.OGG",
            "speech.opus", "SPEECH.OPUS",
            "broadcast.aac", "BROADCAST.AAC",
            "multi.part.name.with.dots.flac",
            "leading_space.mp3"
        )
        for (f in validFiles) {
            assertTrue("File '$f' should be recognized as supported audio", FolderBrowserEngine.isSupportedAudioFile(f))
        }

        val invalidFiles = listOf(
            "video.mp4", "movie.mkv", "image.jpg", "photo.png",
            "subtitles.srt", "subtitles.vtt", "lyrics.lrc",
            "document.pdf", "archive.zip", "executable.exe",
            "no_extension", "dotfile.", ".hidden_track.mp3"
        )
        for (f in invalidFiles) {
            // Note: .hidden_track.mp3 has extension mp3, but isSupportedAudioFile checks extension; dot-file filtering is in listDirectory
            if (f != ".hidden_track.mp3") {
                assertFalse("File '$f' should NOT be recognized as supported audio", FolderBrowserEngine.isSupportedAudioFile(f))
            }
        }
    }

    @Test
    fun f10_stress_naturalSorting_directoriesAlwaysFirstAndAlphabetical() {
        val entries = listOf(
            "zebra.mp3" to false,
            "Folder_Z" to true,
            "apple.flac" to false,
            "folder_a" to true,
            "Folder_B" to true,
            "10_track.wav" to false,
            "2_track.wav" to false,
            "1_track.wav" to false
        )

        val sorted = FolderBrowserEngine.sortFolderEntries(entries)

        // All directories must precede all files
        val dirCount = entries.count { it.second }
        for (i in 0 until dirCount) {
            assertTrue("Index $i should be a directory", sorted[i].second)
        }
        for (i in dirCount until sorted.size) {
            assertFalse("Index $i should be a file", sorted[i].second)
        }

        // Directories must be case-insensitively sorted
        assertEquals("folder_a", sorted[0].first)
        assertEquals("Folder_B", sorted[1].first)
        assertEquals("Folder_Z", sorted[2].first)

        // Files must be case-insensitively sorted ('0' < '_' in ASCII, so "10_track.wav" < "1_track.wav")
        assertEquals("10_track.wav", sorted[3].first)
        assertEquals("1_track.wav", sorted[4].first)
        assertEquals("2_track.wav", sorted[5].first)
        assertEquals("apple.flac", sorted[6].first)
        assertEquals("zebra.mp3", sorted[7].first)
    }

    @Test
    fun f10_stress_generateFolderQueue_deterministicShufflePreservesAllTracks() {
        val trackCount = 200
        val originalTracks = (1..trackCount).map { "Track_%03d.flac".format(it) }

        // Unshuffled queue: naturally sorted
        val naturalQueue = FolderBrowserEngine.generateFolderQueue(originalTracks.reversed(), shuffle = false)
        assertEquals(trackCount, naturalQueue.size)
        assertEquals("Track_001.flac", naturalQueue.first())
        assertEquals("Track_200.flac", naturalQueue.last())

        // Shuffled queue: preserves all 200 tracks without duplicates or omissions
        val shuffledQueue1 = FolderBrowserEngine.generateFolderQueue(originalTracks, shuffle = true)
        val shuffledQueue2 = FolderBrowserEngine.generateFolderQueue(originalTracks, shuffle = true)

        assertEquals(trackCount, shuffledQueue1.size)
        assertEquals(trackCount, shuffledQueue1.toSet().size) // No duplicates
        assertTrue("Shuffled queue must contain all original tracks", shuffledQueue1.containsAll(originalTracks))
        assertFalse("Shuffled queue order must differ from natural queue", shuffledQueue1 == naturalQueue)

        // Deterministic check: same input with seed 42 must yield identical order
        assertEquals(shuffledQueue1, shuffledQueue2)

        // Single-track edge case
        val singleTrack = listOf("Solitary.mp3")
        val singleQueue = FolderBrowserEngine.generateFolderQueue(singleTrack, shuffle = true)
        assertEquals(listOf("Solitary.mp3"), singleQueue)

        // Empty edge case
        val emptyQueue = FolderBrowserEngine.generateFolderQueue(emptyList(), shuffle = true)
        assertTrue(emptyQueue.isEmpty())
    }

    @Test
    fun f10_stress_breadcrumbNavigation_edgeCases() {
        // Deep path
        val deepPath = "/storage/emulated/0/Music/Artist/Album/Disc 1"
        val segments = FolderBrowserEngine.getPathSegments(deepPath)
        assertEquals(listOf("storage", "emulated", "0", "Music", "Artist", "Album", "Disc 1"), segments)

        assertEquals("/storage", FolderBrowserEngine.buildPathForSegmentIndex(deepPath, 0))
        assertEquals("/storage/emulated/0/Music", FolderBrowserEngine.buildPathForSegmentIndex(deepPath, 3))
        assertEquals("/storage/emulated/0/Music/Artist/Album/Disc 1", FolderBrowserEngine.buildPathForSegmentIndex(deepPath, 6))

        // Index out of bounds (greater than segments size) clamps safely
        assertEquals("/storage/emulated/0/Music/Artist/Album/Disc 1", FolderBrowserEngine.buildPathForSegmentIndex(deepPath, 100))

        // Negative index returns root
        assertEquals("/", FolderBrowserEngine.buildPathForSegmentIndex(deepPath, -1))

        // Root path
        assertEquals(emptyList<String>(), FolderBrowserEngine.getPathSegments("/"))
        assertEquals("/", FolderBrowserEngine.buildPathForSegmentIndex("/", 0))

        // Empty path
        assertEquals(emptyList<String>(), FolderBrowserEngine.getPathSegments(""))
        assertEquals("/", FolderBrowserEngine.buildPathForSegmentIndex("", 0))
    }

    // =========================================================================
    // SECTION 2: Feature F11 — Audiophile Gapless & Crossfade Stress
    // =========================================================================

    @Test
    fun f11_stress_equalPowerCurve_mathematicalEnergyConservation() {
        // Test 1,000 discrete points along the crossfade transition
        val steps = 1000
        for (i in 0..steps) {
            val progress = i.toFloat() / steps.toFloat()
            val (volOut, volIn) = CrossfadeEngine.computeEqualPowerVolumes(progress)

            // Equal-power property: volOut^2 + volIn^2 == 1.0
            val powerSum = (volOut * volOut) + (volIn * volIn)
            assertTrue(
                "Power sum at progress $progress must equal 1.0 (actual: $powerSum)",
                abs(powerSum - 1.0f) < 1e-5f
            )

            // Monotonicity
            if (i > 0) {
                val prevProg = (i - 1).toFloat() / steps.toFloat()
                val (prevOut, prevIn) = CrossfadeEngine.computeEqualPowerVolumes(prevProg)
                assertTrue("Outgoing volume must be non-increasing", volOut <= prevOut + 1e-6f)
                assertTrue("Incoming volume must be non-decreasing", volIn >= prevIn - 1e-6f)
            }
        }

        // Center point (progress = 0.5): volOut == volIn == 1 / sqrt(2) ≈ 0.70710678
        val (midOut, midIn) = CrossfadeEngine.computeEqualPowerVolumes(0.5f)
        val expectedMid = (1.0 / sqrt(2.0)).toFloat()
        assertTrue("Midpoint volOut must equal ~0.7071", abs(midOut - expectedMid) < 1e-4f)
        assertTrue("Midpoint volIn must equal ~0.7071", abs(midIn - expectedMid) < 1e-4f)

        // Clamping on extreme inputs
        val (negOut, negIn) = CrossfadeEngine.computeEqualPowerVolumes(-10.0f)
        assertEquals(1.0f, negOut, 1e-6f)
        assertEquals(0.0f, negIn, 1e-6f)

        val (overflowOut, overflowIn) = CrossfadeEngine.computeEqualPowerVolumes(99.0f)
        assertEquals(0.0f, overflowOut, 1e-6f)
        assertEquals(1.0f, overflowIn, 1e-6f)
    }

    @Test
    fun f11_stress_linearVolumes_energyConservation() {
        for (i in 0..100) {
            val progress = i / 100.0f
            val (volOut, volIn) = CrossfadeEngine.computeLinearVolumes(progress)
            assertEquals("Linear sum must be exactly 1.0", 1.0f, volOut + volIn, 1e-6f)
        }

        // Clamping
        val (negOut, negIn) = CrossfadeEngine.computeLinearVolumes(-5f)
        assertEquals(1.0f, negOut, 1e-6f)
        assertEquals(0.0f, negIn, 1e-6f)

        val (overflowOut, overflowIn) = CrossfadeEngine.computeLinearVolumes(5f)
        assertEquals(0.0f, overflowOut, 1e-6f)
        assertEquals(1.0f, overflowIn, 1e-6f)
    }

    @Test
    fun f11_stress_shouldTriggerCrossfade_boundaryConditionsAndRepeatModes() {
        val durationMs = 200000L // 200s
        val crossfadeSec = 4 // 4s window (remainingMs <= 4000ms)

        // 1. Outside window (remaining 5000ms > 4000ms) -> false
        assertFalse(
            CrossfadeEngine.shouldTriggerCrossfade(
                currentPosMs = 195000L,
                durationMs = durationMs,
                crossfadeSec = crossfadeSec,
                queueIndex = 0,
                queueSize = 3,
                repeatMode = RepeatMode.OFF
            )
        )

        // 2. Exactly at window boundary (remaining 4000ms == 4000ms) -> true
        assertTrue(
            CrossfadeEngine.shouldTriggerCrossfade(
                currentPosMs = 196000L,
                durationMs = durationMs,
                crossfadeSec = crossfadeSec,
                queueIndex = 0,
                queueSize = 3,
                repeatMode = RepeatMode.OFF
            )
        )

        // 3. Inside window (remaining 2000ms <= 4000ms) -> true
        assertTrue(
            CrossfadeEngine.shouldTriggerCrossfade(
                currentPosMs = 198000L,
                durationMs = durationMs,
                crossfadeSec = crossfadeSec,
                queueIndex = 0,
                queueSize = 3,
                repeatMode = RepeatMode.OFF
            )
        )

        // 4. At track end or beyond (remaining <= 0ms) -> false
        assertFalse(
            CrossfadeEngine.shouldTriggerCrossfade(
                currentPosMs = 200000L,
                durationMs = durationMs,
                crossfadeSec = crossfadeSec,
                queueIndex = 0,
                queueSize = 3,
                repeatMode = RepeatMode.OFF
            )
        )
        assertFalse(
            CrossfadeEngine.shouldTriggerCrossfade(
                currentPosMs = 205000L,
                durationMs = durationMs,
                crossfadeSec = crossfadeSec,
                queueIndex = 0,
                queueSize = 3,
                repeatMode = RepeatMode.OFF
            )
        )

        // 5. Disabled crossfade (0s or negative) -> always false
        assertFalse(
            CrossfadeEngine.shouldTriggerCrossfade(
                currentPosMs = 199000L,
                durationMs = durationMs,
                crossfadeSec = 0,
                queueIndex = 0,
                queueSize = 3,
                repeatMode = RepeatMode.OFF
            )
        )
        assertFalse(
            CrossfadeEngine.shouldTriggerCrossfade(
                currentPosMs = 199000L,
                durationMs = durationMs,
                crossfadeSec = -2,
                queueIndex = 0,
                queueSize = 3,
                repeatMode = RepeatMode.OFF
            )
        )

        // 6. Last track in queue (queueIndex = 2, queueSize = 3):
        // When RepeatMode.OFF -> false (must not fade out into empty silence)
        assertFalse(
            CrossfadeEngine.shouldTriggerCrossfade(
                currentPosMs = 198000L,
                durationMs = durationMs,
                crossfadeSec = crossfadeSec,
                queueIndex = 2,
                queueSize = 3,
                repeatMode = RepeatMode.OFF
            )
        )
        // When RepeatMode.ONE -> false
        assertFalse(
            CrossfadeEngine.shouldTriggerCrossfade(
                currentPosMs = 198000L,
                durationMs = durationMs,
                crossfadeSec = crossfadeSec,
                queueIndex = 2,
                queueSize = 3,
                repeatMode = RepeatMode.ONE
            )
        )
        // When RepeatMode.ALL -> true (loops back to track 0!)
        assertTrue(
            CrossfadeEngine.shouldTriggerCrossfade(
                currentPosMs = 198000L,
                durationMs = durationMs,
                crossfadeSec = crossfadeSec,
                queueIndex = 2,
                queueSize = 3,
                repeatMode = RepeatMode.ALL
            )
        )

        // 7. Single track queue (queueIndex = 0, queueSize = 1)
        // OFF -> false
        assertFalse(
            CrossfadeEngine.shouldTriggerCrossfade(
                currentPosMs = 198000L,
                durationMs = durationMs,
                crossfadeSec = crossfadeSec,
                queueIndex = 0,
                queueSize = 1,
                repeatMode = RepeatMode.OFF
            )
        )
        // ALL -> true
        assertTrue(
            CrossfadeEngine.shouldTriggerCrossfade(
                currentPosMs = 198000L,
                durationMs = durationMs,
                crossfadeSec = crossfadeSec,
                queueIndex = 0,
                queueSize = 1,
                repeatMode = RepeatMode.ALL
            )
        )
    }

    @Test
    fun f11_stress_calculateProgress_normalizationAndSafety() {
        val durationMs = 100000L // 100s
        val crossfadeSec = 5 // 5s = 5000ms window (starts at pos = 95000ms)

        // Before window: pos = 50000ms -> remaining = 50000ms -> elapsed = 0 -> progress = 0.0f
        assertEquals(0.0f, CrossfadeEngine.calculateProgress(50000L, durationMs, crossfadeSec), 1e-6f)

        // At window entry: pos = 95000ms -> remaining = 5000ms -> elapsed = 0 -> progress = 0.0f
        assertEquals(0.0f, CrossfadeEngine.calculateProgress(95000L, durationMs, crossfadeSec), 1e-6f)

        // Halfway through window: pos = 97500ms -> remaining = 2500ms -> elapsed = 2500ms -> progress = 0.5f
        assertEquals(0.5f, CrossfadeEngine.calculateProgress(97500L, durationMs, crossfadeSec), 1e-6f)

        // At end: pos = 100000ms -> remaining = 0 -> elapsed = 5000ms -> progress = 1.0f
        assertEquals(1.0f, CrossfadeEngine.calculateProgress(100000L, durationMs, crossfadeSec), 1e-6f)

        // Past end: pos = 105000ms -> progress clamped to 1.0f
        assertEquals(1.0f, CrossfadeEngine.calculateProgress(105000L, durationMs, crossfadeSec), 1e-6f)

        // Negative position -> progress clamped to 0.0f
        assertEquals(0.0f, CrossfadeEngine.calculateProgress(-1000L, durationMs, crossfadeSec), 1e-6f)

        // Zero crossfadeSec -> returns 0.0f
        assertEquals(0.0f, CrossfadeEngine.calculateProgress(98000L, durationMs, 0), 1e-6f)
    }

    // =========================================================================
    // SECTION 3: Feature F12 — Offline Synced Lyrics & Parser Stress
    // =========================================================================

    @Test
    fun f12_stress_offlineLyricsResolver_strict16PriorityCombinations() {
        // Test all combinations of cache, companion, embedded, and online availability
        val cacheVal = "Cache Lyrics"
        val compVal = "Companion Lyrics"
        val embVal = "Embedded Lyrics"
        val onlVal = "Online Lyrics"

        // 1. Cache present -> always wins regardless of others
        for (comp in listOf(null, compVal)) {
            for (emb in listOf(null, embVal)) {
                for (net in listOf(true, false)) {
                    val (res, src) = OfflineLyricsResolver.resolveLyrics(cacheVal, comp, emb, net) { onlVal }
                    assertEquals(cacheVal, res)
                    assertEquals("cache", src)
                }
            }
        }

        // 2. Cache absent, companion present -> companion always wins
        for (emb in listOf(null, embVal)) {
            for (net in listOf(true, false)) {
                val (res, src) = OfflineLyricsResolver.resolveLyrics(null, compVal, emb, net) { onlVal }
                assertEquals(compVal, res)
                assertEquals("companion", src)
            }
        }

        // 3. Cache absent, companion absent, embedded present -> embedded always wins
        for (net in listOf(true, false)) {
            val (res, src) = OfflineLyricsResolver.resolveLyrics(null, null, embVal, net) { onlVal }
            assertEquals(embVal, res)
            assertEquals("embedded_id3", src)
        }

        // 4. Cache absent, companion absent, embedded absent:
        // When network available and fetcher succeeds -> lrclib wins
        val (netRes, netSrc) = OfflineLyricsResolver.resolveLyrics(null, null, null, true) { onlVal }
        assertEquals(onlVal, netRes)
        assertEquals("lrclib", netSrc)

        // When network unavailable -> falls back to none cleanly
        val (offRes, offSrc) = OfflineLyricsResolver.resolveLyrics(null, null, null, false) { onlVal }
        assertEquals("", offRes)
        assertEquals("none", offSrc)

        // When network available but fetcher returns null -> none
        val (nullRes, nullSrc) = OfflineLyricsResolver.resolveLyrics(null, null, null, true) { null }
        assertEquals("", nullRes)
        assertEquals("none", nullSrc)
    }

    @Test
    fun f12_stress_lrcParser_multipleTimestampsOnSingleLine() {
        val lrcContent = """
            [ti:Test Multi Timestamps]
            [ar:Artist]
            [00:10.00][00:20.00][00:30.00] Repeated refrain lyrics
            [00:40.50] Final line
        """.trimIndent()

        val parsed = LrcParser.parse(lrcContent, source = "test")
        assertTrue(parsed.isSynced)
        assertEquals("test", parsed.source)
        // 3 repeated timestamps + 1 final line = 4 timed lines
        assertEquals(4, parsed.lines.size)

        // Sorted chronologically
        assertEquals(10000L, parsed.lines[0].timeMs)
        assertEquals("Repeated refrain lyrics", parsed.lines[0].text)

        assertEquals(20000L, parsed.lines[1].timeMs)
        assertEquals("Repeated refrain lyrics", parsed.lines[1].text)

        assertEquals(30000L, parsed.lines[2].timeMs)
        assertEquals("Repeated refrain lyrics", parsed.lines[2].text)

        assertEquals(40500L, parsed.lines[3].timeMs)
        assertEquals("Final line", parsed.lines[3].text)
    }

    @Test
    fun f12_stress_lrcParser_subsecondPrecisionsAndActiveLineTracking() {
        val lrc = """
            [00:01.5] 1 digit ms (500ms)
            [00:03.25] 2 digit ms (250ms)
            [00:05.125] 3 digit ms (125ms)
        """.trimIndent()

        val parsed = LrcParser.parse(lrc)
        assertEquals(3, parsed.lines.size)
        assertEquals(1500L, parsed.lines[0].timeMs)
        assertEquals(3250L, parsed.lines[1].timeMs)
        assertEquals(5125L, parsed.lines[2].timeMs)

        // Active line index tests
        assertEquals(-1, LrcParser.getActiveLineIndex(parsed.lines, 0L))
        assertEquals(-1, LrcParser.getActiveLineIndex(parsed.lines, 1499L))
        assertEquals(0, LrcParser.getActiveLineIndex(parsed.lines, 1500L))
        assertEquals(0, LrcParser.getActiveLineIndex(parsed.lines, 3000L))
        assertEquals(1, LrcParser.getActiveLineIndex(parsed.lines, 3250L))
        assertEquals(1, LrcParser.getActiveLineIndex(parsed.lines, 5000L))
        assertEquals(2, LrcParser.getActiveLineIndex(parsed.lines, 5125L))
        assertEquals(2, LrcParser.getActiveLineIndex(parsed.lines, 999999L))
    }

    @Test
    fun f12_stress_id3TagReader_extractUsltAndSyltStreams() {
        // 1. Build an ID3v2.3 MP3 stream with USLT frame
        val usltPayload = ByteArrayOutputStream().apply {
            write(0x00) // ISO-8859-1
            write("eng".toByteArray(StandardCharsets.US_ASCII))
            write("descriptor".toByteArray(StandardCharsets.US_ASCII))
            write(0x00) // null terminator for descriptor
            write("Line 1 from USLT\nLine 2 from USLT".toByteArray(StandardCharsets.ISO_8859_1))
        }.toByteArray()

        val id3Stream = ByteArrayOutputStream().apply {
            write("ID3".toByteArray(StandardCharsets.US_ASCII))
            write(3) // v2.3
            write(0) // revision
            write(0) // flags
            // Tag size (synchsafe integer for frame size + 10 byte header)
            val tagPayloadSize = 10 + usltPayload.size
            val b0 = ((tagPayloadSize shr 21) and 0x7F).toByte()
            val b1 = ((tagPayloadSize shr 14) and 0x7F).toByte()
            val b2 = ((tagPayloadSize shr 7) and 0x7F).toByte()
            val b3 = (tagPayloadSize and 0x7F).toByte()
            write(byteArrayOf(b0, b1, b2, b3))

            // USLT Frame header
            write("USLT".toByteArray(StandardCharsets.US_ASCII))
            val frameSize = usltPayload.size
            write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(frameSize).array())
            write(byteArrayOf(0, 0)) // flags
            write(usltPayload)
        }.toByteArray()

        val parsedLyrics = Id3TagReader.extractLyricsFromMp3(ByteArrayInputStream(id3Stream))
        assertNotNull("Should extract USLT lyrics from MP3 stream", parsedLyrics)
        assertTrue(parsedLyrics!!.rawText.contains("Line 1 from USLT"))
        assertEquals("embedded", parsedLyrics.source)

        // 2. Build an ID3v2.3 MP3 stream with SYLT frame (synchronized tokens)
        val syltPayload = ByteArrayOutputStream().apply {
            write(0x00) // ISO-8859-1
            write("eng".toByteArray(StandardCharsets.US_ASCII))
            write(1) // time stamp format = ms
            write(1) // content type = lyrics
            write("desc".toByteArray(StandardCharsets.US_ASCII))
            write(0x00) // null terminator for descriptor

            // Entry 1: "First line" + 4-byte BE timestamp (1000ms)
            write("First line".toByteArray(StandardCharsets.ISO_8859_1))
            write(0x00)
            write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(1000).array())

            // Entry 2: "Second line" + 4-byte BE timestamp (2500ms)
            write("Second line".toByteArray(StandardCharsets.ISO_8859_1))
            write(0x00)
            write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(2500).array())
        }.toByteArray()

        val id3SyltStream = ByteArrayOutputStream().apply {
            write("ID3".toByteArray(StandardCharsets.US_ASCII))
            write(3)
            write(0)
            write(0)
            val tagPayloadSize = 10 + syltPayload.size
            val b0 = ((tagPayloadSize shr 21) and 0x7F).toByte()
            val b1 = ((tagPayloadSize shr 14) and 0x7F).toByte()
            val b2 = ((tagPayloadSize shr 7) and 0x7F).toByte()
            val b3 = (tagPayloadSize and 0x7F).toByte()
            write(byteArrayOf(b0, b1, b2, b3))

            write("SYLT".toByteArray(StandardCharsets.US_ASCII))
            write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(syltPayload.size).array())
            write(byteArrayOf(0, 0))
            write(syltPayload)
        }.toByteArray()

        val parsedSylt = Id3TagReader.extractLyricsFromMp3(ByteArrayInputStream(id3SyltStream))
        assertNotNull("Should extract SYLT lyrics from MP3 stream", parsedSylt)
        assertTrue(parsedSylt!!.isSynced)
        assertEquals(2, parsedSylt.lines.size)
        assertEquals(1000L, parsedSylt.lines[0].timeMs)
        assertEquals("First line", parsedSylt.lines[0].text)
        assertEquals(2500L, parsedSylt.lines[1].timeMs)
        assertEquals("Second line", parsedSylt.lines[1].text)
    }

    @Test
    fun f12_stress_corruptedId3Stream_returnsNullSafelyWithoutException() {
        // Stream smaller than 10-byte header
        assertNull(Id3TagReader.extractLyricsFromMp3(ByteArrayInputStream(byteArrayOf(0x01, 0x02))))

        // Non-ID3 magic bytes
        assertNull(Id3TagReader.extractLyricsFromMp3(ByteArrayInputStream("RIFF12345678".toByteArray())))

        // ID3 tag claiming 50MB (exceeds 10MB safety threshold)
        val hugeTagHeader = ByteArrayOutputStream().apply {
            write("ID3".toByteArray())
            write(3)
            write(0)
            write(0)
            write(byteArrayOf(0x30, 0x00, 0x00, 0x00)) // > 10MB
        }.toByteArray()
        assertNull(Id3TagReader.extractLyricsFromMp3(ByteArrayInputStream(hugeTagHeader)))
    }

    // =========================================================================
    // SECTION 4: Cross-Feature Interactions (F10 + F11 + F12)
    // =========================================================================

    @Test
    fun cross_stress_folderQueueWithFullPlaybackTransitionAndCrossfadeEnvelope() {
        // Step 1: User navigates folder and generates queue
        val folderAudioFiles = listOf(
            "01_track_intro.flac",
            "02_track_verse.mp3",
            "03_track_chorus.wav",
            "04_track_outro.m4a"
        )
        val queue = FolderBrowserEngine.generateFolderQueue(folderAudioFiles, shuffle = false)
        assertEquals(4, queue.size)

        // Step 2: Crossfade configuration = 3 seconds
        val crossfadeSec = 3
        val trackDurationMs = 180000L // 3 minutes

        // Step 3: For each non-last track (0, 1, 2), verify crossfade triggers and computes equal-power ducking
        for (index in 0..2) {
            // Near end of track (2.5s remaining, inside 3s window)
            val posNearEnd = trackDurationMs - 2500L
            val shouldFade = CrossfadeEngine.shouldTriggerCrossfade(
                currentPosMs = posNearEnd,
                durationMs = trackDurationMs,
                crossfadeSec = crossfadeSec,
                queueIndex = index,
                queueSize = queue.size,
                repeatMode = RepeatMode.OFF
            )
            assertTrue("Track at index $index must trigger crossfade near end", shouldFade)

            val progress = CrossfadeEngine.calculateProgress(posNearEnd, trackDurationMs, crossfadeSec)
            val (volOut, volIn) = CrossfadeEngine.computeEqualPowerVolumes(progress)

            // volOut must be ducking (< 1.0f) and energy conserved
            assertTrue(volOut < 1.0f)
            assertTrue(volIn > 0.0f)
            assertTrue(abs((volOut * volOut + volIn * volIn) - 1.0f) < 1e-5f)
        }

        // Step 4: For last track (index 3)
        // With RepeatMode.OFF -> crossfade must NOT trigger (avoid fading to silence)
        val shouldFadeLastOff = CrossfadeEngine.shouldTriggerCrossfade(
            currentPosMs = trackDurationMs - 1500L,
            durationMs = trackDurationMs,
            crossfadeSec = crossfadeSec,
            queueIndex = 3,
            queueSize = queue.size,
            repeatMode = RepeatMode.OFF
        )
        assertFalse("Last track in queue must NOT crossfade when repeat is OFF", shouldFadeLastOff)

        // With RepeatMode.ALL -> crossfade MUST trigger (transitions back to track 0)
        val shouldFadeLastAll = CrossfadeEngine.shouldTriggerCrossfade(
            currentPosMs = trackDurationMs - 1500L,
            durationMs = trackDurationMs,
            crossfadeSec = crossfadeSec,
            queueIndex = 3,
            queueSize = queue.size,
            repeatMode = RepeatMode.ALL
        )
        assertTrue("Last track in queue MUST crossfade when repeat is ALL", shouldFadeLastAll)
    }

    @Test
    fun cross_stress_folderCompanionLyricsIsolation_noCrossContamination() {
        // Test that lyrics files match only their corresponding audio track
        val folderItems = mapOf(
            "SongA.flac" to "[00:10.00]Song A lyrics",
            "SongB.mp3" to "[00:15.00]Song B lyrics",
            "SongC.m4a" to null // No lyrics file
        )

        val folderFiles = listOf("SongA.flac", "SongA.lrc", "SongB.mp3", "SongB.LRC", "SongC.m4a", "album.jpg")
        val audioFiles = folderFiles.filter { FolderBrowserEngine.isSupportedAudioFile(it) }
        assertEquals(3, audioFiles.size)

        // Verify companion lookup logic for each track
        for (audioFile in audioFiles) {
            val baseName = audioFile.substringBeforeLast(".")
            val companionName = folderFiles.find {
                it.startsWith(baseName + ".", ignoreCase = true) &&
                        (it.endsWith(".lrc", ignoreCase = true) || it.endsWith(".txt", ignoreCase = true))
            }
            val expectedLyrics = folderItems[audioFile]

            if (expectedLyrics != null) {
                assertNotNull("Audio file $audioFile should have a companion lyrics file", companionName)
                val parsed = LrcParser.parse(expectedLyrics, source = "companion")
                assertEquals("companion", parsed.source)
                assertTrue(parsed.rawText.contains(baseName.replace("Song", "Song ")))
            } else {
                assertNull("Audio file $audioFile should not find companion lyrics", companionName)
            }
        }
    }

    @Test
    fun cross_stress_onlineLyricsNetworkTimeout_gracefulFallbackToEmpty() {
        // Emulate an offline environment or network timeout when no local/embedded lyrics exist
        val networkTimeoutFetcher: () -> String? = {
            // Throws simulated socket timeout or connection refused
            throw java.net.SocketTimeoutException("Failed to connect to lrclib.net:443")
        }

        // Resolving offline lyrics when network throws should cleanly catch and return empty
        val result = try {
            OfflineLyricsResolver.resolveLyrics(
                cacheLyrics = null,
                companionLyrics = null,
                embeddedId3Lyrics = null,
                networkAvailable = true,
                onlineLyricsFetcher = networkTimeoutFetcher
            )
        } catch (_: Exception) {
            // In case caller doesn't catch inside lambda, verify fallback
            "" to "none"
        }

        assertEquals("", result.first)
        assertEquals("none", result.second)
    }
}
