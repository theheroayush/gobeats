package com.example.gobeats

import com.example.gobeats.core.model.SkipIntroSegment
import com.example.gobeats.core.model.Track
import com.example.gobeats.core.video.OfflineSkipIntroDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class OfflineSkipIntroDetectorTest {

    private fun createTestTrack(durationMs: Long, fileUri: String = "file:///dummy.mp4"): Track {
        return Track(
            id = "test_video_1",
            sourceId = "local",
            fileUri = fileUri,
            title = "Test Anime Episode 1",
            artistName = "Anime Studio",
            albumTitle = "Season 1",
            durationMs = durationMs,
            isVideo = true
        )
    }

    @Test
    fun `detectIntroSegment returns default smart intro for media over 5 minutes`() {
        val longTrack = createTestTrack(durationMs = 24 * 60 * 1000L) // 24 mins
        val segment = OfflineSkipIntroDetector.detectIntroSegment(longTrack)

        assertEquals(0L, segment.startMs)
        assertEquals(85_000L, segment.endMs)
        assertEquals("Skip Intro", segment.label)
    }

    @Test
    fun `detectIntroSegment returns inactive segment for media under 5 minutes without chapters`() {
        val shortTrack = createTestTrack(durationMs = 3 * 60 * 1000L) // 3 mins
        val segment = OfflineSkipIntroDetector.detectIntroSegment(shortTrack)

        assertEquals(0L, segment.startMs)
        assertEquals(0L, segment.endMs)
        assertFalse(OfflineSkipIntroDetector.isSkipIntroActive(0L, segment))
    }

    @Test
    fun `isSkipIntroActive activates within intro window and deactivates after target`() {
        val segment = SkipIntroSegment(startMs = 0L, endMs = 85_000L, label = "Skip Intro")

        assertTrue(OfflineSkipIntroDetector.isSkipIntroActive(0L, segment))
        assertTrue(OfflineSkipIntroDetector.isSkipIntroActive(40_000L, segment))
        assertTrue(OfflineSkipIntroDetector.isSkipIntroActive(80_000L, segment))

        // Right at the end or past the end should be false
        assertFalse(OfflineSkipIntroDetector.isSkipIntroActive(85_000L, segment))
        assertFalse(OfflineSkipIntroDetector.isSkipIntroActive(90_000L, segment))
        assertFalse(OfflineSkipIntroDetector.isSkipIntroActive(500_000L, segment))
    }

    @Test
    fun `isSkipIntroActive handles non-zero start offsets`() {
        val segment = SkipIntroSegment(startMs = 90_000L, endMs = 175_000L, label = "Skip Opening")

        assertFalse(OfflineSkipIntroDetector.isSkipIntroActive(0L, segment))
        assertFalse(OfflineSkipIntroDetector.isSkipIntroActive(80_000L, segment))

        assertTrue(OfflineSkipIntroDetector.isSkipIntroActive(90_000L, segment))
        assertTrue(OfflineSkipIntroDetector.isSkipIntroActive(130_000L, segment))

        assertFalse(OfflineSkipIntroDetector.isSkipIntroActive(175_000L, segment))
        assertFalse(OfflineSkipIntroDetector.isSkipIntroActive(200_000L, segment))
    }

    @Test
    fun `getSkipTargetMs returns correct boundary`() {
        val segment = SkipIntroSegment(startMs = 10_000L, endMs = 95_000L, label = "Skip OP")
        assertEquals(95_000L, OfflineSkipIntroDetector.getSkipTargetMs(segment))
    }

    @Test
    fun `parseTimestampToMs correctly handles various time formats`() {
        assertEquals(85_000L, OfflineSkipIntroDetector.parseTimestampToMs("00:01:25.000"))
        assertEquals(85_000L, OfflineSkipIntroDetector.parseTimestampToMs("00:01:25,000"))
        assertEquals(85_000L, OfflineSkipIntroDetector.parseTimestampToMs("01:25"))
        assertEquals(3661_234L, OfflineSkipIntroDetector.parseTimestampToMs("01:01:01.234"))
    }

    @Test
    fun `parseChapterFile correctly extracts OGM chapters and matches intro`() {
        val tempFile = File.createTempFile("test_chapters", ".txt")
        tempFile.writeText(
            """
            CHAPTER01=00:00:00.000
            CHAPTER01NAME=Prologue
            CHAPTER02=00:01:15.000
            CHAPTER02NAME=Opening Theme
            CHAPTER03=00:02:45.000
            CHAPTER03NAME=Episode 1
            """.trimIndent()
        )

        try {
            val chapters = OfflineSkipIntroDetector.parseChapterFile(tempFile)
            assertEquals(3, chapters.size)
            assertEquals("Prologue", chapters[0].name)
            assertEquals(0L, chapters[0].startMs)
            assertEquals(75_000L, chapters[0].endMs)

            assertEquals("Opening Theme", chapters[1].name)
            assertEquals(75_000L, chapters[1].startMs)
            assertEquals(165_000L, chapters[1].endMs)

            val matched = OfflineSkipIntroDetector.findMatchingIntroChapter(chapters)
            assertTrue(matched != null)
            assertEquals("Opening Theme", matched?.name)
            assertEquals(75_000L, matched?.startMs)
            assertEquals(165_000L, matched?.endMs)
        } finally {
            tempFile.delete()
        }
    }
}
