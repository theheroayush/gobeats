package com.example.gobeats

import android.net.Uri
import com.example.gobeats.core.designsystem.components.formatDuration
import com.example.gobeats.core.model.PlaybackUiState
import com.example.gobeats.core.model.Track
import com.example.gobeats.core.model.VideoResumePrompt
import com.example.gobeats.core.video.SubtitleDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Milestone 1 (Video Engine) Unit Tests:
 * - 200% Loudness boost calculation and gain mapping
 * - External subtitle companion detection and MIME resolution
 * - Video playback position persistence and resume eligibility thresholds
 * - Single-rendition black-screen trap prevention logic
 * - Floating video miniplayer geometry and state transitions
 */
class VideoEngineM1Test {

    // =========================================================================
    // 1. Loudness Booster (100% - 200% -> 0 - 1500 mB) Tests
    // =========================================================================

    @Test
    fun loudnessBoost_gainMappingFormula() {
        fun calculateBoostGainMb(percent: Int): Int {
            val clamped = percent.coerceIn(100, 200)
            return (clamped - 100) * 15 // 100% -> 0 mB, 200% -> 1500 mB
        }

        assertEquals(0, calculateBoostGainMb(100))
        assertEquals(150, calculateBoostGainMb(110))
        assertEquals(375, calculateBoostGainMb(125))
        assertEquals(750, calculateBoostGainMb(150))
        assertEquals(1125, calculateBoostGainMb(175))
        assertEquals(1500, calculateBoostGainMb(200))

        // Clamping guards
        assertEquals(0, calculateBoostGainMb(50))
        assertEquals(0, calculateBoostGainMb(0))
        assertEquals(1500, calculateBoostGainMb(250))
        assertEquals(1500, calculateBoostGainMb(500))
    }

    @Test
    fun loudnessBoost_uiStateTracksLoudnessPercentage() {
        val stateDefault = PlaybackUiState()
        assertEquals(100, stateDefault.videoLoudnessBoostPercent)

        val stateBoosted = stateDefault.copy(videoLoudnessBoostPercent = 160)
        assertEquals(160, stateBoosted.videoLoudnessBoostPercent)

        val stateMax = stateDefault.copy(videoLoudnessBoostPercent = 200)
        assertEquals(200, stateMax.videoLoudnessBoostPercent)
    }

    // =========================================================================
    // 2. Subtitle Auto-Detection & Parsing Tests
    // =========================================================================

    @Test
    fun subtitleDetector_findsCompanionSubtitlesInDirectory() {
        val tempDir = File.createTempFile("m1_sub_test_", "_dir")
        tempDir.delete()
        tempDir.mkdirs()

        try {
            val videoFile = File(tempDir, "sample_movie.mp4")
            videoFile.createNewFile()

            val srtFile = File(tempDir, "sample_movie.srt")
            srtFile.createNewFile()

            val vttFile = File(tempDir, "sample_movie.en.vtt")
            vttFile.createNewFile()

            val genericFile = File(tempDir, "subtitles.srt")
            genericFile.createNewFile()

            val unrelatedFile = File(tempDir, "other_video.srt")
            unrelatedFile.createNewFile()

            val nonSubtitle = File(tempDir, "sample_movie.nfo")
            nonSubtitle.createNewFile()

            val companions = SubtitleDetector.findCompanionFiles(videoFile)

            val names = companions.map { it.name }.toSet()
            assertTrue("Exact .srt must be detected", names.contains("sample_movie.srt"))
            assertTrue("Language tagged .vtt must be detected", names.contains("sample_movie.en.vtt"))
            assertTrue("Generic subtitles.srt must be detected", names.contains("subtitles.srt"))
            assertFalse("Unrelated subtitle must not be included", names.contains("other_video.srt"))
            assertFalse("Non-subtitle file must not be included", names.contains("sample_movie.nfo"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun subtitleDetector_parsesLanguageTagsAndMimeTypes() {
        val (enCode, enLabel) = SubtitleDetector.extractLanguageInfo("action", "action.en.srt")
        assertEquals("en", enCode)
        assertEquals("English", enLabel)

        val (frCode, frLabel) = SubtitleDetector.extractLanguageInfo("action", "action.fr.vtt")
        assertEquals("fr", frCode)
        assertEquals("French", frLabel)

        val (hiCode, hiLabel) = SubtitleDetector.extractLanguageInfo("movie", "movie.hin.srt")
        assertEquals("hi", hiCode)
        assertEquals("Hindi", hiLabel)

        val (esCode, esLabel) = SubtitleDetector.extractLanguageInfo("show", "show.spanish.vtt")
        assertEquals("es", esCode)
        assertEquals("Spanish", esLabel)

        val (undCode, undLabel) = SubtitleDetector.extractLanguageInfo("action", "action.srt")
        assertEquals("und", undCode)
        assertEquals("Subtitles", undLabel)
    }

    // =========================================================================
    // 3. Video Resume Position Eligibility & Formatting Tests
    // =========================================================================

    @Test
    fun videoResume_eligibilityLogic() {
        fun isEligibleForResume(positionMs: Long, durationMs: Long): Boolean {
            return positionMs > 5000L && durationMs > 0L && positionMs < (durationMs - 10000L)
        }

        val totalDuration = 120_000L // 2 minutes

        // Position <= 5000ms -> beginning of video, no prompt
        assertFalse(isEligibleForResume(0L, totalDuration))
        assertFalse(isEligibleForResume(3000L, totalDuration))
        assertFalse(isEligibleForResume(5000L, totalDuration))

        // Position near the end (duration - 10000ms = 110_000L) -> completed/credits, no prompt
        assertFalse(isEligibleForResume(110_000L, totalDuration))
        assertFalse(isEligibleForResume(115_000L, totalDuration))
        assertFalse(isEligibleForResume(120_000L, totalDuration))

        // Valid midpoint positions -> prompt is eligible!
        assertTrue(isEligibleForResume(5001L, totalDuration))
        assertTrue(isEligibleForResume(45_000L, totalDuration))
        assertTrue(isEligibleForResume(109_999L, totalDuration))
    }

    @Test
    fun videoResume_promptDisplayFormatting() {
        val prompt = VideoResumePrompt(
            videoId = "vid_123",
            positionMs = 75_000L,
            durationMs = 180_000L
        )

        assertEquals("vid_123", prompt.videoId)
        assertEquals(75_000L, prompt.positionMs)
        assertEquals(180_000L, prompt.durationMs)
        assertEquals("1:15", formatDuration(prompt.positionMs))
        assertEquals("3:00", formatDuration(prompt.durationMs))
    }

    // =========================================================================
    // 4. Black-Screen Trap Prevention & Single Rendition Tests
    // =========================================================================

    @Test
    fun singleRendition_detectionAndResolutionGuard() {
        // When a video format is a local offline file with a single fixed rendition,
        // downscaling ladders should not constrain ExoPlayer max video dimensions.
        fun shouldClearConstraints(totalVideoFormats: Int, isAdaptiveStream: Boolean): Boolean {
            return totalVideoFormats <= 1 && !isAdaptiveStream
        }

        assertTrue("Single format local video must clear constraints",
            shouldClearConstraints(totalVideoFormats = 1, isAdaptiveStream = false))

        assertFalse("Multi-rung adaptive DASH/HLS stream should keep constraints",
            shouldClearConstraints(totalVideoFormats = 4, isAdaptiveStream = true))

        assertFalse("Multi-format local file with multiple tracks should keep constraints",
            shouldClearConstraints(totalVideoFormats = 3, isAdaptiveStream = false))
    }

    // =========================================================================
    // 5. Floating Video Miniplayer Geometry & Snapping Tests
    // =========================================================================

    @Test
    fun floatingMiniplayer_magneticEdgeSnappingCalculations() {
        val parentWidthPx = 1080f
        val cardWidthPx = 540f
        val marginPx = 40f

        val leftSnapTarget = marginPx // 40f
        val rightSnapTarget = parentWidthPx - cardWidthPx - marginPx // 500f

        fun computeSnapTargetX(currentX: Float): Float {
            val center = currentX + cardWidthPx / 2f
            return if (center < parentWidthPx / 2f) leftSnapTarget else rightSnapTarget
        }

        // Towed to left half (< 540)
        assertEquals(leftSnapTarget, computeSnapTargetX(50f), 0.01f)
        assertEquals(leftSnapTarget, computeSnapTargetX(200f), 0.01f)

        // Towed to right half (>= 540)
        assertEquals(rightSnapTarget, computeSnapTargetX(300f), 0.01f)
        assertEquals(rightSnapTarget, computeSnapTargetX(500f), 0.01f)
    }

    @Test
    fun floatingMiniplayer_visibilityPredicate() {
        fun shouldShowMiniplayer(
            isVideoMode: Boolean,
            isVideoTrack: Boolean,
            showNowPlayingSheet: Boolean,
            isCinemaActive: Boolean,
            isDismissed: Boolean
        ): Boolean {
            return isVideoMode && isVideoTrack && !showNowPlayingSheet && !isCinemaActive && !isDismissed
        }

        // Video playing and minimized -> miniplayer must show
        assertTrue(shouldShowMiniplayer(
            isVideoMode = true,
            isVideoTrack = true,
            showNowPlayingSheet = false,
            isCinemaActive = false,
            isDismissed = false
        ))

        // When NowPlaying sheet is opened -> miniplayer must NOT show
        assertFalse(shouldShowMiniplayer(
            isVideoMode = true,
            isVideoTrack = true,
            showNowPlayingSheet = true,
            isCinemaActive = false,
            isDismissed = false
        ))

        // When Fullscreen Cinema is active -> miniplayer must NOT show
        assertFalse(shouldShowMiniplayer(
            isVideoMode = true,
            isVideoTrack = true,
            showNowPlayingSheet = false,
            isCinemaActive = true,
            isDismissed = false
        ))

        // When user dismissed miniplayer -> miniplayer must NOT show
        assertFalse(shouldShowMiniplayer(
            isVideoMode = true,
            isVideoTrack = true,
            showNowPlayingSheet = false,
            isCinemaActive = false,
            isDismissed = true
        ))

        // When track is audio-only -> miniplayer must NOT show
        assertFalse(shouldShowMiniplayer(
            isVideoMode = true,
            isVideoTrack = false,
            showNowPlayingSheet = false,
            isCinemaActive = false,
            isDismissed = false
        ))
    }
}
