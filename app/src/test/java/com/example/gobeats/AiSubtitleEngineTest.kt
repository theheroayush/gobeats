package com.example.gobeats

import com.example.gobeats.core.model.SubtitleCue
import com.example.gobeats.core.video.AiSubtitleEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiSubtitleEngineTest {

    @Test
    fun `parseSrt correctly parses standard SubRip format`() {
        val srtContent = """
            1
            00:00:01,000 --> 00:00:04,500
            नमस्ते और गोबीट्स में आपका स्वागत है।

            2
            00:00:05,250 --> 00:00:08,750
            High fidelity offline speech recognition.
        """.trimIndent()

        val cues = AiSubtitleEngine.parseSrt(srtContent)
        assertEquals(2, cues.size)

        assertEquals(1000L, cues[0].startMs)
        assertEquals(4500L, cues[0].endMs)
        assertEquals("नमस्ते और गोबीट्स में आपका स्वागत है।", cues[0].text)

        assertEquals(5250L, cues[1].startMs)
        assertEquals(8750L, cues[1].endMs)
        assertEquals("High fidelity offline speech recognition.", cues[1].text)
    }

    @Test
    fun `parseSrt handles dot separators and strips HTML tags`() {
        val srtWithHtml = """
            1
            00:01:10.100 --> 00:01:15.500
            <font color="#ffff00"><b>तमिल और तेलुगु उपशीर्षक</b></font>
        """.trimIndent()

        val cues = AiSubtitleEngine.parseSrt(srtWithHtml)
        assertEquals(1, cues.size)
        assertEquals(70_100L, cues[0].startMs)
        assertEquals(75_500L, cues[0].endMs)
        assertEquals("तमिल और तेलुगु उपशीर्षक", cues[0].text)
    }

    @Test
    fun `toSrt and parseSrt have lossless round-trip fidelity`() {
        val originalCues = listOf(
            SubtitleCue(startMs = 1200L, endMs = 4500L, text = "नमस्ते भारत"),
            SubtitleCue(startMs = 5000L, endMs = 8200L, text = "வணக்கம் தமிழ்நாடு"),
            SubtitleCue(startMs = 9000L, endMs = 12500L, text = "Welcome to Gobeats Studio")
        )

        val serializedSrt = AiSubtitleEngine.toSrt(originalCues)
        assertTrue(serializedSrt.contains("00:00:01,200 --> 00:00:04,500"))
        assertTrue(serializedSrt.contains("00:00:05,000 --> 00:00:08,200"))
        assertTrue(serializedSrt.contains("00:00:09,000 --> 00:00:12,500"))

        val parsedBack = AiSubtitleEngine.parseSrt(serializedSrt)
        assertEquals(originalCues.size, parsedBack.size)

        for (i in originalCues.indices) {
            assertEquals(originalCues[i].startMs, parsedBack[i].startMs)
            assertEquals(originalCues[i].endMs, parsedBack[i].endMs)
            assertEquals(originalCues[i].text, parsedBack[i].text)
        }
    }

    @Test
    fun `getFallbackLanguage provides expected regional fallbacks`() {
        // Indian languages fallback to en-IN
        assertEquals("en-IN", AiSubtitleEngine.getFallbackLanguage("hi-IN"))
        assertEquals("en-IN", AiSubtitleEngine.getFallbackLanguage("ta-IN"))
        assertEquals("en-IN", AiSubtitleEngine.getFallbackLanguage("te-IN"))
        assertEquals("en-IN", AiSubtitleEngine.getFallbackLanguage("mr-IN"))
        assertEquals("en-IN", AiSubtitleEngine.getFallbackLanguage("bn-IN"))

        // en-IN falls back to en-US
        assertEquals("en-US", AiSubtitleEngine.getFallbackLanguage("en-IN"))
        assertEquals("en-US", AiSubtitleEngine.getFallbackLanguage("en-US"))
        assertEquals("en-US", AiSubtitleEngine.getFallbackLanguage("fr-FR"))
    }

    @Test
    fun `buildTimedCues orders segments and caps durations cleanly`() {
        val segments = listOf(
            0L to "पहला वाक्य",
            2000L to "दूसरा वाक्य",
            6000L to "तीसरा वाक्य"
        )

        val cues = AiSubtitleEngine.buildTimedCues(segments, defaultDurationMs = 3000L)
        assertEquals(3, cues.size)

        assertEquals(0L, cues[0].startMs)
        assertEquals(2000L, cues[0].endMs) // capped by next start

        assertEquals(2000L, cues[1].startMs)
        assertEquals(5000L, cues[1].endMs) // 2000 + 3000

        assertEquals(6000L, cues[2].startMs)
        assertEquals(9000L, cues[2].endMs) // 6000 + 3000
    }
}
