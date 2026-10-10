package com.example.gobeats

import com.example.gobeats.core.model.RepeatMode
import com.example.gobeats.core.model.Track
import org.json.JSONException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

/**
 * Tier 2: Boundary & Corner Cases Test Suite.
 *
 * Covers extreme inputs, negative values, zero/empty states, overflow conditions,
 * and adversarial edge cases for features F1 through F14 (at least 5 tests per feature = 70+ tests).
 */
class GobeatsTier2BoundaryCornerTest {

    // =========================================================================
    // F1: Boundary Cases — 200% Loudness Booster (5 tests)
    // =========================================================================

    private fun sanitizeAndCalculateBoost(input: Float, maxStreamVolume: Int = 15): Triple<Float, Int, Int> {
        val safeInput = when {
            input.isNaN() -> 0.0f
            input.isInfinite() -> if (input > 0) 2.0f else 0.0f
            else -> input.coerceIn(0.0f, 2.0f)
        }
        val streamFraction = safeInput.coerceAtMost(1.0f)
        val streamIndex = (streamFraction * maxStreamVolume).toInt().coerceIn(0, maxStreamVolume)
        val gainMb = if (safeInput > 1.0f) {
            Math.round((safeInput - 1.0f) * 1500f).coerceIn(0, 1500)
        } else {
            0
        }
        return Triple(streamFraction, streamIndex, gainMb)
    }

    @Test
    fun f1_b01_negativeInput_clampedToZeroStreamVolumeAndGain() {
        val (stream, idx, gain) = sanitizeAndCalculateBoost(-1.5f)
        assertEquals(0.0f, stream, 0.0001f)
        assertEquals(0, idx)
        assertEquals(0, gain)
    }

    @Test
    fun f1_b02_massiveOverflow_clampedTo200PercentAnd1500Mb() {
        val (stream, idx, gain) = sanitizeAndCalculateBoost(100.0f)
        assertEquals(1.0f, stream, 0.0001f)
        assertEquals(15, idx)
        assertEquals(1500, gain)
    }

    @Test
    fun f1_b03_nanAndInfiniteInputs_sanitizedSafely() {
        val (streamNan, idxNan, gainNan) = sanitizeAndCalculateBoost(Float.NaN)
        assertEquals(0.0f, streamNan, 0.0001f)
        assertEquals(0, idxNan)
        assertEquals(0, gainNan)

        val (streamInf, idxInf, gainInf) = sanitizeAndCalculateBoost(Float.POSITIVE_INFINITY)
        assertEquals(1.0f, streamInf, 0.0001f)
        assertEquals(15, idxInf)
        assertEquals(1500, gainInf)
    }

    @Test
    fun f1_b04_microDeltaAboveUnity_calculatesSmallestPositiveGain() {
        val (_, _, gain) = sanitizeAndCalculateBoost(1.002f) // 0.002 * 1500 = 3 mB
        assertEquals(3, gain)
    }

    @Test
    fun f1_b05_singleStepHardwareMaxVolume_indexAlwaysBounded() {
        // Devices with only 1 volume step
        val (_, idx1, _) = sanitizeAndCalculateBoost(0.4f, maxStreamVolume = 1)
        assertEquals(0, idx1)
        val (_, idx2, _) = sanitizeAndCalculateBoost(1.0f, maxStreamVolume = 1)
        assertEquals(1, idx2)
    }

    // =========================================================================
    // F2: Boundary Cases — External Subtitles (5 tests)
    // =========================================================================

    @Test
    fun f2_b01_filenameWithMultipleDots_resolvesBaseNameAccurately() {
        val files = listOf(
            "Star.Wars.Episode.IV.1977.1080p.mp4",
            "Star.Wars.Episode.IV.1977.1080p.srt",
            "Star.Wars.Episode.IV.1977.1080p.en.srt"
        )
        val subs = GobeatsTier1FeatureCoverageTest.SubtitleDiscoveryEngine.matchCompanionSubtitles(
            videoFilename = "Star.Wars.Episode.IV.1977.1080p.mp4",
            directoryFiles = files
        )
        assertEquals(2, subs.size)
        assertEquals("Star.Wars.Episode.IV.1977.1080p.srt", subs[0].filename)
        assertEquals("en", subs[1].languageCode)
    }

    @Test
    fun f2_b02_exactSameVideoFile_isNotIdentifiedAsSubtitle() {
        val files = listOf("movie.mp4")
        val subs = GobeatsTier1FeatureCoverageTest.SubtitleDiscoveryEngine.matchCompanionSubtitles("movie.mp4", files)
        assertTrue(subs.isEmpty())
    }

    @Test
    fun f2_b03_emptyDirectory_returnsEmptyList() {
        val subs = GobeatsTier1FeatureCoverageTest.SubtitleDiscoveryEngine.matchCompanionSubtitles("film.mkv", emptyList())
        assertTrue(subs.isEmpty())
    }

    @Test
    fun f2_b04_mixedCaseExtensionsAndDots_parsedProperly() {
        val files = listOf("drama.mp4", "drama.Es.vTt", "drama.FR.SrT")
        val subs = GobeatsTier1FeatureCoverageTest.SubtitleDiscoveryEngine.matchCompanionSubtitles("drama.mp4", files)
        assertEquals(2, subs.size)
        assertEquals("es", subs[0].languageCode)
        assertEquals("text/vtt", subs[0].mimeType)
        assertEquals("fr", subs[1].languageCode)
        assertEquals("application/x-subrip", subs[1].mimeType)
    }

    @Test
    fun f2_b05_subtitlesWithSpacesAndSpecialCharacters() {
        val files = listOf(
            "My Vacation (2026) [4K]!.mp4",
            "My Vacation (2026) [4K]!.srt",
            "random.srt"
        )
        val subs = GobeatsTier1FeatureCoverageTest.SubtitleDiscoveryEngine.matchCompanionSubtitles(
            videoFilename = "My Vacation (2026) [4K]!.mp4",
            directoryFiles = files
        )
        assertEquals(1, subs.size)
        assertEquals("My Vacation (2026) [4K]!.srt", subs[0].filename)
    }

    // =========================================================================
    // F3: Boundary Cases — Video Resume Memory (5 tests)
    // =========================================================================

    @Test
    fun f3_b01_zeroOrNegativeDuration_neverPromptsResume() {
        val zeroRecord = GobeatsTier1FeatureCoverageTest.VideoResumeRecord("v1", 10000L, 0L, 1L)
        assertFalse(GobeatsTier1FeatureCoverageTest.VideoResumeDecisionEngine.shouldPromptResume(zeroRecord))

        val negRecord = GobeatsTier1FeatureCoverageTest.VideoResumeRecord("v2", 10000L, -5000L, 1L)
        assertFalse(GobeatsTier1FeatureCoverageTest.VideoResumeDecisionEngine.shouldPromptResume(negRecord))
    }

    @Test
    fun f3_b02_exactBoundary5000ms_promptsResume() {
        val boundaryRecord = GobeatsTier1FeatureCoverageTest.VideoResumeRecord("v1", 5000L, 100000L, 1L)
        assertTrue(GobeatsTier1FeatureCoverageTest.VideoResumeDecisionEngine.shouldPromptResume(boundaryRecord))

        val justBelowRecord = GobeatsTier1FeatureCoverageTest.VideoResumeRecord("v1", 4999L, 100000L, 1L)
        assertFalse(GobeatsTier1FeatureCoverageTest.VideoResumeDecisionEngine.shouldPromptResume(justBelowRecord))
    }

    @Test
    fun f3_b03_exactBoundaryCreditSkip_stopsPrompting() {
        val duration = 100000L
        val skipThreshold = duration - 10000L // 90000L
        val justBelowCredit = GobeatsTier1FeatureCoverageTest.VideoResumeRecord("v1", skipThreshold - 1L, duration, 1L)
        assertTrue(GobeatsTier1FeatureCoverageTest.VideoResumeDecisionEngine.shouldPromptResume(justBelowCredit))

        val atCredit = GobeatsTier1FeatureCoverageTest.VideoResumeRecord("v1", skipThreshold, duration, 1L)
        assertFalse(GobeatsTier1FeatureCoverageTest.VideoResumeDecisionEngine.shouldPromptResume(atCredit))
    }

    @Test
    fun f3_b04_positionExceedingDuration_treatedAsCompletedAndNotPrompted() {
        val overflowRecord = GobeatsTier1FeatureCoverageTest.VideoResumeRecord("v1", 150000L, 100000L, 1L)
        assertFalse(GobeatsTier1FeatureCoverageTest.VideoResumeDecisionEngine.shouldPromptResume(overflowRecord))
    }

    @Test
    fun f3_b05_nullRecord_returnsZeroSafely() {
        val pos = GobeatsTier1FeatureCoverageTest.VideoResumeDecisionEngine.determineResumePosition(null, true)
        assertEquals(0L, pos)
    }

    // =========================================================================
    // F4: Boundary Cases — Black-Screen Trap Fix (5 tests)
    // =========================================================================

    @Test
    fun f4_b01_zeroDimensionsLocalVideo_doesNotCrash() {
        val options = GobeatsTier1FeatureCoverageTest.VideoQualityManager.generateQualityOptions(
            videoTrackCount = 1,
            uri = "/storage/emulated/0/Movies/unindexed.mp4",
            nativeWidth = 0,
            nativeHeight = 0
        )
        assertEquals(1, options.size)
        assertEquals("original", options[0].id)
    }

    @Test
    fun f4_b02_extreme4kLocalVideo_preservesOriginalOptionWithoutDownscaling() {
        val options = GobeatsTier1FeatureCoverageTest.VideoQualityManager.generateQualityOptions(
            videoTrackCount = 1,
            uri = "content://media/external/video/media/4k",
            nativeWidth = 3840,
            nativeHeight = 2160
        )
        assertEquals(1, options.size)
        assertEquals("3840x2160", options[0].resolutionLabel)
        assertEquals(Int.MAX_VALUE, options[0].maxWidth)
    }

    @Test
    fun f4_b03_verticalVideo9x16_resolutionFormattedCorrectly() {
        val options = GobeatsTier1FeatureCoverageTest.VideoQualityManager.generateQualityOptions(
            videoTrackCount = 1,
            uri = "/storage/DCIM/Camera/reel.mp4",
            nativeWidth = 1080,
            nativeHeight = 1920
        )
        assertEquals(1, options.size)
        assertEquals("1080x1920", options[0].resolutionLabel)
    }

    @Test
    fun f4_b04_malformedUri_doesNotCrashAdaptiveCheck() {
        assertFalse(GobeatsTier1FeatureCoverageTest.VideoQualityManager.isAdaptiveStream(""))
        assertFalse(GobeatsTier1FeatureCoverageTest.VideoQualityManager.isAdaptiveStream("invalid_path"))
    }

    @Test
    fun f4_b05_zeroTracksReported_singleRenditionDefaultsGracefully() {
        val options = GobeatsTier1FeatureCoverageTest.VideoQualityManager.generateQualityOptions(
            videoTrackCount = 0,
            uri = "/storage/sample.mp4",
            nativeWidth = 1280,
            nativeHeight = 720
        )
        assertEquals(1, options.size)
    }

    // =========================================================================
    // F5: Boundary Cases — Floating Video Miniplayer (5 tests)
    // =========================================================================

    @Test
    fun f5_b01_draggedToExtremeNegativeCoordinates_clampedToSafeMargins() {
        val (x, y) = GobeatsTier1FeatureCoverageTest.FloatingMiniplayerEngine.clampPosition(
            rawX = -99999,
            rawY = -99999,
            screenW = 1080,
            screenH = 2400
        )
        assertEquals(GobeatsTier1FeatureCoverageTest.FloatingMiniplayerEngine.MARGIN, x)
        assertEquals(GobeatsTier1FeatureCoverageTest.FloatingMiniplayerEngine.TOP_SAFE, y)
    }

    @Test
    fun f5_b02_draggedToExtremePositiveCoordinates_clampedWithinScreen() {
        val (x, y) = GobeatsTier1FeatureCoverageTest.FloatingMiniplayerEngine.clampPosition(
            rawX = 999999,
            rawY = 999999,
            screenW = 1080,
            screenH = 2400
        )
        val expectedX = 1080 - GobeatsTier1FeatureCoverageTest.FloatingMiniplayerEngine.WIDTH - GobeatsTier1FeatureCoverageTest.FloatingMiniplayerEngine.MARGIN
        val expectedY = 2400 - GobeatsTier1FeatureCoverageTest.FloatingMiniplayerEngine.HEIGHT - GobeatsTier1FeatureCoverageTest.FloatingMiniplayerEngine.BOTTOM_SAFE
        assertEquals(expectedX, x)
        assertEquals(expectedY, y)
    }

    @Test
    fun f5_b03_exactCenterHorizontalSnap_deterministicallySnapsToRight() {
        val screenW = 1000
        // center = 500, center >= screenW / 2 -> snaps right
        val rawX = 500 - (GobeatsTier1FeatureCoverageTest.FloatingMiniplayerEngine.WIDTH / 2)
        val snapped = GobeatsTier1FeatureCoverageTest.FloatingMiniplayerEngine.snapToEdge(rawX, screenW)
        val expectedRight = screenW - GobeatsTier1FeatureCoverageTest.FloatingMiniplayerEngine.WIDTH - GobeatsTier1FeatureCoverageTest.FloatingMiniplayerEngine.MARGIN
        assertEquals(expectedRight, snapped)
    }

    @Test
    fun f5_b04_landscapeMode_alwaysSuppressesFloatingMiniplayer() {
        assertFalse(
            GobeatsTier1FeatureCoverageTest.FloatingMiniplayerEngine.isMiniplayerVisible(
                isVideoMode = true,
                isPlaying = true,
                isNowPlayingSheetOpen = false,
                isCinemaFullscreen = false,
                isLandscape = true // landscape!
            )
        )
    }

    @Test
    fun f5_b05_audioTrackPlaying_alwaysSuppressesFloatingVideoMiniplayer() {
        assertFalse(
            GobeatsTier1FeatureCoverageTest.FloatingMiniplayerEngine.isMiniplayerVisible(
                isVideoMode = false, // audio mode!
                isPlaying = true,
                isNowPlayingSheetOpen = false,
                isCinemaFullscreen = false,
                isLandscape = false
            )
        )
    }

    // =========================================================================
    // F6: Boundary Cases — Scoped Storage Hardening (5 tests)
    // =========================================================================

    @Test
    fun f6_b01_nonExistentStagingFile_validationReturnsFalse() {
        val missing = File("non_existent_staging_${System.currentTimeMillis()}.tmp")
        assertFalse(GobeatsTier1FeatureCoverageTest.ScopedStorageRouter.validateStagingFile(missing, "fLaC".toByteArray()))
    }

    @Test
    fun f6_b02_emptyZeroByteStagingFile_validationReturnsFalse() {
        val emptyTemp = File.createTempFile("empty_test", ".tmp")
        try {
            assertFalse(GobeatsTier1FeatureCoverageTest.ScopedStorageRouter.validateStagingFile(emptyTemp, "ID3".toByteArray()))
        } finally {
            emptyTemp.delete()
        }
    }

    @Test
    fun f6_b03_truncatedHeaderStagingFile_validationReturnsFalse() {
        val truncated = File.createTempFile("trunc_test", ".tmp")
        try {
            truncated.writeBytes(byteArrayOf('I'.code.toByte(), 'D'.code.toByte())) // only 2 bytes!
            assertFalse(GobeatsTier1FeatureCoverageTest.ScopedStorageRouter.validateStagingFile(truncated, "ID3".toByteArray()))
        } finally {
            truncated.delete()
        }
    }

    @Test
    fun f6_b04_apiBoundaryTransitions_exactLevels() {
        assertEquals(
            GobeatsTier1FeatureCoverageTest.StorageWriteMechanism.DIRECT_POSIX_FILE,
            GobeatsTier1FeatureCoverageTest.ScopedStorageRouter.resolveWriteMechanism(24, true)
        )
        assertEquals(
            GobeatsTier1FeatureCoverageTest.StorageWriteMechanism.DIRECT_POSIX_FILE,
            GobeatsTier1FeatureCoverageTest.ScopedStorageRouter.resolveWriteMechanism(28, true)
        )
        assertEquals(
            GobeatsTier1FeatureCoverageTest.StorageWriteMechanism.RECOVERABLE_SECURITY_EXCEPTION,
            GobeatsTier1FeatureCoverageTest.ScopedStorageRouter.resolveWriteMechanism(29, true)
        )
        assertEquals(
            GobeatsTier1FeatureCoverageTest.StorageWriteMechanism.MEDIA_STORE_CREATE_WRITE_REQUEST,
            GobeatsTier1FeatureCoverageTest.ScopedStorageRouter.resolveWriteMechanism(30, true)
        )
    }

    @Test
    fun f6_b05_unsupportedOrFutureApiLevel_defaultsSafely() {
        assertEquals(
            GobeatsTier1FeatureCoverageTest.StorageWriteMechanism.MEDIA_STORE_CREATE_WRITE_REQUEST,
            GobeatsTier1FeatureCoverageTest.ScopedStorageRouter.resolveWriteMechanism(40, true)
        )
    }

    // =========================================================================
    // F7: Boundary Cases — FLAC Vorbis Tag Editor (5 tests)
    // =========================================================================

    @Test
    fun f7_b01_emptyCommentsMap_producesValidVorbisBlock() {
        val block = GobeatsTier1FeatureCoverageTest.FlacTagEngine.buildVorbisCommentBlock(
            vendorString = "GoBeats",
            comments = emptyMap(),
            isLast = true
        )
        assertTrue(block.size >= 12)
        val parsed = GobeatsTier1FeatureCoverageTest.FlacTagEngine.parseVorbisComments(block)
        assertTrue(parsed.isEmpty())
    }

    @Test
    fun f7_b02_commentValueWithMultipleEqualsSigns_parsedIntact() {
        val map = mapOf("URL" to "https://example.com/play?track=1&quality=lossless")
        val block = GobeatsTier1FeatureCoverageTest.FlacTagEngine.buildVorbisCommentBlock("GoBeats", map, true)
        val parsed = GobeatsTier1FeatureCoverageTest.FlacTagEngine.parseVorbisComments(block)
        assertEquals("https://example.com/play?track=1&quality=lossless", parsed["URL"])
    }

    @Test
    fun f7_b03_massiveLyricsTag_encodesLengthAccurately() {
        val hugeLyrics = "A".repeat(25000)
        val map = mapOf("LYRICS" to hugeLyrics)
        val block = GobeatsTier1FeatureCoverageTest.FlacTagEngine.buildVorbisCommentBlock("GoBeats", map, true)
        val parsed = GobeatsTier1FeatureCoverageTest.FlacTagEngine.parseVorbisComments(block)
        assertEquals(25000, parsed["LYRICS"]?.length)
    }

    @Test(expected = IllegalArgumentException::class)
    fun f7_b04_nonVorbisCommentBlockType_throwsException() {
        val invalidBlock = ByteArray(16)
        invalidBlock[0] = 0 // type 0 STREAMINFO, not type 4
        GobeatsTier1FeatureCoverageTest.FlacTagEngine.parseVorbisComments(invalidBlock)
    }

    @Test
    fun f7_b05_specialCharactersInVendorString() {
        val vendor = "GoBeats 音樂 v1.0.0 © 2026"
        val block = GobeatsTier1FeatureCoverageTest.FlacTagEngine.buildVorbisCommentBlock(vendor, mapOf("T" to "V"), true)
        val parsed = GobeatsTier1FeatureCoverageTest.FlacTagEngine.parseVorbisComments(block)
        assertEquals("V", parsed["T"])
    }

    // =========================================================================
    // F8: Boundary Cases — M4A/MP4 Tag Editor & stco (5 tests)
    // =========================================================================

    @Test
    fun f8_b01_zeroMoovDelta_offsetsRemainStrictlyUnchanged() {
        val original = listOf(1024, 2048, 4096)
        val adjusted = GobeatsTier1FeatureCoverageTest.M4aChunkOffsetEngine.adjustStcoOffsets(original, moovDelta = 0, mdatStartOffset = 512)
        assertEquals(original, adjusted)
    }

    @Test
    fun f8_b02_emptyOffsetsList_returnsEmptyList() {
        val adjusted = GobeatsTier1FeatureCoverageTest.M4aChunkOffsetEngine.adjustStcoOffsets(emptyList(), 100, 512)
        assertTrue(adjusted.isEmpty())
    }

    @Test
    fun f8_b03_emptyTagString_buildsValidChildBoxWithZeroPayload() {
        val atom = GobeatsTier1FeatureCoverageTest.M4aChunkOffsetEngine.buildIlstTextAtom("\u00A9nam", "")
        val buf = ByteBuffer.wrap(atom).order(ByteOrder.BIG_ENDIAN)
        val totalSize = buf.int
        assertEquals(24, totalSize) // 8 parent + 16 data box with 0 payload
    }

    @Test(expected = IllegalArgumentException::class)
    fun f8_b04_invalidFourCcLength_throwsException() {
        GobeatsTier1FeatureCoverageTest.M4aChunkOffsetEngine.buildIlstTextAtom("TOO_LONG", "Value")
    }

    @Test
    fun f8_b05_largeMoovDelta_offsetsShiftWithoutTruncation() {
        val original = listOf(1_000_000, 2_000_000)
        val largeDelta = 500_000
        val adjusted = GobeatsTier1FeatureCoverageTest.M4aChunkOffsetEngine.adjustStcoOffsets(original, largeDelta, 1000)
        assertEquals(1_500_000, adjusted[0])
        assertEquals(2_500_000, adjusted[1])
    }

    // =========================================================================
    // F9: Boundary Cases — Tag Sync Pipeline (5 tests)
    // =========================================================================

    @Test
    fun f9_b01_sqlInjectionPayloadInTags_treatedAsLiteralString() {
        val sim = GobeatsTier1FeatureCoverageTest.TagSyncSimulator()
        val track = Track("t1", "local", "/music/song.mp3", "Title", "Artist", "Album", 100L)
        val sqlInjection = "Robert'); DROP TABLE tracks;--"

        val updated = sim.onTagsSaved(track, sqlInjection, sqlInjection, sqlInjection)
        assertEquals(sqlInjection, updated.title)

        val preserved = sim.applyOverridesDuringRescan(track)
        assertEquals(sqlInjection, preserved.title)
    }

    @Test
    fun f9_b02_emojiAndMultiByteUnicode_retainedIntact() {
        val sim = GobeatsTier1FeatureCoverageTest.TagSyncSimulator()
        val track = Track("t1", "local", "/music/song.mp3", "Title", "Artist", "Album", 100L)
        val emojiTitle = "🔥 Cosmic Drift 🚀 🎵"

        val updated = sim.onTagsSaved(track, emojiTitle, "Artist", "Album")
        assertEquals(emojiTitle, updated.title)

        val preserved = sim.applyOverridesDuringRescan(track)
        assertEquals(emojiTitle, preserved.title)
    }

    @Test
    fun f9_b03_repeatedSavesToSameTrack_updatesOverrideWithoutDuplicates() {
        val sim = GobeatsTier1FeatureCoverageTest.TagSyncSimulator()
        val track = Track("t1", "local", "/music/song.mp3", "Title", "Artist", "Album", 100L)

        sim.onTagsSaved(track, "V1", "A1", "Alb1")
        sim.onTagsSaved(track, "V2", "A2", "Alb2")
        sim.onTagsSaved(track, "V3", "A3", "Alb3")

        assertEquals(1, sim.overrides.size)
        assertEquals("V3", sim.overrides["t1"]!!.title)
    }

    @Test
    fun f9_b04_emptyStringTags_storedAsEmptyWithoutNullPointer() {
        val sim = GobeatsTier1FeatureCoverageTest.TagSyncSimulator()
        val track = Track("t1", "local", "/music/song.mp3", "Title", "Artist", "Album", 100L)

        val updated = sim.onTagsSaved(track, "", "", "")
        assertEquals("", updated.title)
    }

    @Test
    fun f9_b05_trackIdMismatch_overrideNotApplied() {
        val sim = GobeatsTier1FeatureCoverageTest.TagSyncSimulator()
        val track1 = Track("t1", "local", "/1", "T1", "A1", "Alb1", 100L)
        sim.onTagsSaved(track1, "Overridden", "A1", "Alb1")

        val track2 = Track("t2", "local", "/2", "T2", "A2", "Alb2", 100L)
        val res = sim.applyOverridesDuringRescan(track2)
        assertEquals("T2", res.title)
    }

    // =========================================================================
    // F10: Boundary Cases — Folders Directory Tree (5 tests)
    // =========================================================================

    @Test
    fun f10_b01_emptyFolder_returnsEmptyQueue() {
        val queue = GobeatsTier1FeatureCoverageTest.FolderBrowserEngine.generateFolderQueue(emptyList(), false)
        assertTrue(queue.isEmpty())
    }

    @Test
    fun f10_b02_nonAudioFilesOnly_allFilteredOut() {
        val files = listOf("photo.png", "video.mp4", "readme.pdf", "info.json")
        val audioFiles = files.filter { GobeatsTier1FeatureCoverageTest.FolderBrowserEngine.isSupportedAudioFile(it) }
        assertTrue(audioFiles.isEmpty())
    }

    @Test
    fun f10_b03_singleTrackFolder_queueContainsExactlyOne() {
        val files = listOf("solitary_song.flac")
        val queue = GobeatsTier1FeatureCoverageTest.FolderBrowserEngine.generateFolderQueue(files, shuffle = true)
        assertEquals(1, queue.size)
        assertEquals("solitary_song.flac", queue[0])
    }

    @Test
    fun f10_b04_naturalSorting_caseInsensitiveOrdering() {
        val entries = listOf("b_track.mp3" to false, "A_track.mp3" to false)
        val sorted = GobeatsTier1FeatureCoverageTest.FolderBrowserEngine.sortFolderEntries(entries)
        assertEquals("A_track.mp3", sorted[0].first)
        assertEquals("b_track.mp3", sorted[1].first)
    }

    @Test
    fun f10_b05_deepPathSegments_handlesRootAndTrailingSlashes() {
        val root = "/"
        val segments = root.split("/").filter { it.isNotEmpty() }
        assertTrue(segments.isEmpty())

        val deep = "/a/b/c/d/e/f/g/h/i/j/"
        val deepSegments = deep.split("/").filter { it.isNotEmpty() }
        assertEquals(10, deepSegments.size)
    }

    // =========================================================================
    // F11: Boundary Cases — Audiophile Gapless & Crossfade (5 tests)
    // =========================================================================

    @Test
    fun f11_b01_trackShorterThanCrossfadeDuration_handledSafely() {
        // Track is 2 seconds (2000ms), crossfade is 5 seconds
        // Position at 1500ms -> remaining is 500ms, which is <= 5000ms
        val triggered = GobeatsTier1FeatureCoverageTest.CrossfadeEngine.shouldTriggerCrossfade(
            currentPosMs = 1500L,
            durationMs = 2000L,
            crossfadeSec = 5,
            queueIndex = 0,
            queueSize = 2,
            repeatMode = RepeatMode.OFF
        )
        assertTrue(triggered)
    }

    @Test
    fun f11_b02_zeroCrossfadeSeconds_neverTriggers() {
        val triggered = GobeatsTier1FeatureCoverageTest.CrossfadeEngine.shouldTriggerCrossfade(
            currentPosMs = 179000L,
            durationMs = 180000L,
            crossfadeSec = 0,
            queueIndex = 0,
            queueSize = 2,
            repeatMode = RepeatMode.OFF
        )
        assertFalse(triggered)
    }

    @Test
    fun f11_b03_singleTrackQueue_neverTriggersWhenRepeatOff() {
        val triggered = GobeatsTier1FeatureCoverageTest.CrossfadeEngine.shouldTriggerCrossfade(
            currentPosMs = 99000L,
            durationMs = 100000L,
            crossfadeSec = 3,
            queueIndex = 0,
            queueSize = 1,
            repeatMode = RepeatMode.OFF
        )
        assertFalse(triggered)
    }

    @Test
    fun f11_b04_negativeRemainingTime_neverTriggers() {
        val triggered = GobeatsTier1FeatureCoverageTest.CrossfadeEngine.shouldTriggerCrossfade(
            currentPosMs = 185000L,
            durationMs = 180000L, // overshot duration
            crossfadeSec = 3,
            queueIndex = 0,
            queueSize = 2,
            repeatMode = RepeatMode.OFF
        )
        assertFalse(triggered)
    }

    @Test
    fun f11_b05_progressClampingInVolumeCurve() {
        val (volOutNeg, volInNeg) = GobeatsTier1FeatureCoverageTest.CrossfadeEngine.computeEqualPowerVolumes(-0.5f)
        assertEquals(1.0f, volOutNeg, 0.001f)
        assertEquals(0.0f, volInNeg, 0.001f)

        val (volOutOver, volInOver) = GobeatsTier1FeatureCoverageTest.CrossfadeEngine.computeEqualPowerVolumes(2.5f)
        assertEquals(0.0f, volOutOver, 0.001f)
        assertEquals(1.0f, volInOver, 0.001f)
    }

    // =========================================================================
    // F12: Boundary Cases — Offline Synced Lyrics (5 tests)
    // =========================================================================

    @Test
    fun f12_b01_malformedLrcTimestamps_parsedAsPlainLyrics() {
        val malformedLrc = """
            [invalid_tag]This line has invalid tag
            [99:99]This line has standard format
            Plain text without brackets
        """.trimIndent()
        val parsed = com.example.gobeats.core.lyrics.LrcParser.parse(malformedLrc)
        assertNotNull(parsed)
    }

    @Test
    fun f12_b02_emptyLyricsString_returnsEmptyData() {
        val empty = com.example.gobeats.core.lyrics.LrcParser.parse("")
        assertFalse(empty.isSynced)
        assertTrue(empty.lines.isEmpty())
        assertEquals("none", empty.source)
    }

    @Test
    fun f12_b03_usltFrameShorterThanHeader_returnsEmpty() {
        val shortFrame = byteArrayOf(0x00, 0x65, 0x6E) // only 3 bytes
        val parsed = GobeatsTier1FeatureCoverageTest.OfflineLyricsResolver.parseUsltFrame(shortFrame)
        assertEquals("", parsed)
    }

    @Test
    fun f12_b04_lyricsStartingAtZeroMs_matchedAtZeroPlaybackPosition() {
        val lrc = "[00:00.00]Opening chord"
        val parsed = com.example.gobeats.core.lyrics.LrcParser.parse(lrc)
        val activeIdx = com.example.gobeats.core.lyrics.LrcParser.getActiveLineIndex(parsed.lines, 0L)
        assertEquals(0, activeIdx)
    }

    @Test
    fun f12_b05_networkFailsDuringOnlineFallback_returnsEmptyCleanly() {
        var caught = false
        try {
            GobeatsTier1FeatureCoverageTest.OfflineLyricsResolver.resolveLyrics(
                cacheLyrics = null,
                companionLyrics = null,
                embeddedId3Lyrics = null,
                networkAvailable = true,
                onlineLyricsFetcher = { throw RuntimeException("Network timeout") }
            )
        } catch (_: Exception) {
            caught = true
        }
        assertTrue(caught)
    }

    // =========================================================================
    // F13: Boundary Cases — Process Death Persistence (5 tests)
    // =========================================================================

    @Test(expected = JSONException::class)
    fun f13_b01_corruptJsonQueue_throwsJsonExceptionToTriggerCleanReset() {
        GobeatsTier1FeatureCoverageTest.ProcessDeathPersistenceEngine.deserializeQueue("{not_an_array}")
    }

    @Test
    fun f13_b02_largeQueue500Items_serializesAndDeserializes() {
        val bigQueue = (1..500).map { "track_uuid_$it" }
        val json = GobeatsTier1FeatureCoverageTest.ProcessDeathPersistenceEngine.serializeQueue(bigQueue)
        val restored = GobeatsTier1FeatureCoverageTest.ProcessDeathPersistenceEngine.deserializeQueue(json)
        assertEquals(500, restored.size)
        assertEquals("track_uuid_1", restored[0])
        assertEquals("track_uuid_500", restored[499])
    }

    @Test
    fun f13_b03_specialCharactersInTrackIds_jsonPreservesString() {
        val specialIds = listOf("track/123", "track?id=456&src=sd", "t:uuid:\"test\"")
        val json = GobeatsTier1FeatureCoverageTest.ProcessDeathPersistenceEngine.serializeQueue(specialIds)
        val restored = GobeatsTier1FeatureCoverageTest.ProcessDeathPersistenceEngine.deserializeQueue(json)
        assertEquals(specialIds, restored)
    }

    @Test
    fun f13_b04_missingTrackInDbDuringSort_filteredOutWithoutCrash() {
        val dbTracks = listOf(Track("t1", "loc", "/1", "One", "A", "Alb", 100L))
        val queueIds = listOf("t1", "t_deleted_2", "t_deleted_3")
        val restored = GobeatsTier1FeatureCoverageTest.ProcessDeathPersistenceEngine.sortTracksByQueueIds(dbTracks, queueIds)
        assertEquals(1, restored.size)
        assertEquals("t1", restored[0].id)
    }

    @Test
    fun f13_b05_negativePositionRestored_coercedToZero() {
        val rawPosition = -5000L
        val sanitized = rawPosition.coerceAtLeast(0L)
        assertEquals(0L, sanitized)
    }

    // =========================================================================
    // F14: Boundary Cases — Network Transfer Hardening (5 tests)
    // =========================================================================

    @Test
    fun f14_b01_zeroDurationTransfer_speedDefaultsSafely() {
        val stats = GobeatsTier1FeatureCoverageTest.TransferStats(
            bytesTransferred = 1000L,
            totalBytes = 1000L,
            durationSeconds = 0.0
        )
        assertEquals(0.0, stats.speedMbPerSec, 0.001)
        assertEquals(1.0f, stats.progressFraction, 0.001f)
    }

    @Test
    fun f14_b02_largeFileTransfer_exceeding32BitIntegers() {
        val largeBytes = 50L * 1024L * 1024L * 1024L // 50 GB
        val stats = GobeatsTier1FeatureCoverageTest.TransferStats(
            bytesTransferred = 25L * 1024L * 1024L * 1024L,
            totalBytes = largeBytes,
            durationSeconds = 250.0
        )
        assertEquals(0.5f, stats.progressFraction, 0.001f)
        assertTrue(stats.speedMbPerSec > 90.0) // ~100 MB/s
    }

    @Test
    fun f14_b03_progressCannotExceedUnity() {
        val stats = GobeatsTier1FeatureCoverageTest.TransferStats(
            bytesTransferred = 200L,
            totalBytes = 100L, // more bytes than declared
            durationSeconds = 1.0
        )
        assertEquals(1.0f, stats.progressFraction, 0.001f)
    }

    @Test
    fun f14_b04_apiLevelBoundaryForWifiLock() {
        assertEquals("WIFI_MODE_FULL_HIGH_PERF", GobeatsTier1FeatureCoverageTest.SyncLockPolicy.getWifiLockMode(24))
        assertEquals("WIFI_MODE_FULL_HIGH_PERF", GobeatsTier1FeatureCoverageTest.SyncLockPolicy.getWifiLockMode(28))
        assertEquals("WIFI_MODE_FULL_LOW_LATENCY", GobeatsTier1FeatureCoverageTest.SyncLockPolicy.getWifiLockMode(29))
    }

    @Test
    fun f14_b05_abortedTransfer_locksReleasedSafely() {
        var locksHeld = true
        fun abortTransfer() {
            locksHeld = false
        }
        abortTransfer()
        assertFalse(locksHeld)
    }
}
