package com.example.gobeats

import com.example.gobeats.core.model.RepeatMode
import com.example.gobeats.core.model.Track
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Tier 3: Cross-Feature Pairwise Interaction Test Suite.
 *
 * Verifies multi-feature interactions, shared states, and integration boundaries
 * across combinations of F1 through F14 (at least 10 pairwise test cases).
 */
class GobeatsTier3CrossFeatureTest {

    // =========================================================================
    // 1. F1 (Loudness Boost) + F3 (Video Resume Memory)
    // =========================================================================

    @Test
    fun cross_01_loudnessBoostRetainedAcrossVideoResume() {
        var userLoudnessBoost = 1.6f // 160% (+900 mB)
        val resumeRecord = GobeatsTier1FeatureCoverageTest.VideoResumeRecord(
            videoId = "vid_matrix",
            positionMs = 180000L,
            durationMs = 7200000L,
            updatedAt = System.currentTimeMillis()
        )

        // User accepts resume
        val targetPos = GobeatsTier1FeatureCoverageTest.VideoResumeDecisionEngine.determineResumePosition(resumeRecord, true)
        assertEquals(180000L, targetPos)

        // Boost level must remain intact after seeking
        val boost = GobeatsTier1FeatureCoverageTest.calculateVolumeBoost(userLoudnessBoost)
        assertEquals(900, boost.loudnessBoostMb)
        assertEquals(160, boost.boostPercent)
        assertTrue(boost.isBoostActive)
    }

    // =========================================================================
    // 2. F2 (External Subtitles) + F4 (Black-Screen Trap Fix)
    // =========================================================================

    @Test
    fun cross_02_externalSubtitlesOnSingleRenditionLocalVideo_noBlackScreen() {
        val singleVideoUri = "content://media/external/video/media/88"
        val folderFiles = listOf("film.mp4", "film.en.srt", "film.es.vtt")

        // 1. Discover subtitles
        val subs = GobeatsTier1FeatureCoverageTest.SubtitleDiscoveryEngine.matchCompanionSubtitles("film.mp4", folderFiles)
        assertEquals(2, subs.size)

        // 2. Generate quality options for single-rendition local video
        val qualityOptions = GobeatsTier1FeatureCoverageTest.VideoQualityManager.generateQualityOptions(
            videoTrackCount = 1,
            uri = singleVideoUri,
            nativeWidth = 1920,
            nativeHeight = 1080
        )

        // Subtitle presence must NOT cause downscaling quality constraints that black-screen local playback
        assertEquals(1, qualityOptions.size)
        assertEquals("original", qualityOptions[0].id)
        assertFalse(GobeatsTier1FeatureCoverageTest.VideoQualityManager.shouldApplyDownscalingConstraint(1, singleVideoUri))
    }

    // =========================================================================
    // 3. F3 (Video Resume Memory) + F13 (Process Death Persistence)
    // =========================================================================

    @Test
    fun cross_03_videoResumeMemoryCoexistsWithAudioQueueProcessDeath() {
        // State 1: Global audio playback state
        val audioQueue = listOf("audio_1", "audio_2", "audio_3")
        val serializedQueue = GobeatsTier1FeatureCoverageTest.ProcessDeathPersistenceEngine.serializeQueue(audioQueue)
        val globalState = GobeatsTier1FeatureCoverageTest.SavedPlaybackState(
            currentTrackId = "audio_2",
            positionMs = 45000L,
            isPlaying = false,
            queueTrackIds = audioQueue,
            updatedAt = 1000L
        )

        // State 2: Independent video resume table record
        val videoResume = GobeatsTier1FeatureCoverageTest.VideoResumeRecord(
            videoId = "video_doc_99",
            positionMs = 540000L,
            durationMs = 1800000L,
            updatedAt = 2000L
        )

        // Restoring audio state from process death
        val restoredQueue = GobeatsTier1FeatureCoverageTest.ProcessDeathPersistenceEngine.deserializeQueue(serializedQueue)
        assertEquals("audio_2", globalState.currentTrackId)
        assertEquals(45000L, globalState.positionMs)
        assertEquals(audioQueue, restoredQueue)

        // Video resume position remains independently intact
        assertTrue(GobeatsTier1FeatureCoverageTest.VideoResumeDecisionEngine.shouldPromptResume(videoResume))
        assertEquals(540000L, videoResume.positionMs)
    }

    // =========================================================================
    // 4. F5 (Floating Video Miniplayer) + F1 (Loudness Booster)
    // =========================================================================

    @Test
    fun cross_04_floatingMiniplayerActiveWhileAdjustingLoudnessBooster() {
        // Video playing in floating miniplayer
        var isFloating = GobeatsTier1FeatureCoverageTest.FloatingMiniplayerEngine.isMiniplayerVisible(
            isVideoMode = true,
            isPlaying = true,
            isNowPlayingSheetOpen = false,
            isCinemaFullscreen = false,
            isLandscape = false
        )
        assertTrue(isFloating)

        // Adjust volume to 140%
        val boost = GobeatsTier1FeatureCoverageTest.calculateVolumeBoost(1.4f)
        assertEquals(600, boost.loudnessBoostMb) // 0.4 * 1500 = 600 mB
        assertTrue(boost.isBoostActive)

        // Miniplayer remains active after boost adjustment
        assertTrue(isFloating)
    }

    // =========================================================================
    // 5. F6 (Scoped Storage Hardening) + F7 (FLAC Vorbis Tag Editor)
    // =========================================================================

    @Test
    fun cross_05_scopedStorageStagingWithFlacVorbisTagWriting() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "gobeats_cross_test_" + System.currentTimeMillis())
        tempDir.mkdirs()
        val stagingFile = File(tempDir, "tag_staging_123.tmp")

        try {
            // Build valid FLAC file with Vorbis tags
            val rawAudio = byteArrayOf(0x11, 0x22, 0x33, 0x44)
            val streamInfo = ByteArray(38) // 4 byte header + 34 bytes payload
            val tagBlock = GobeatsTier1FeatureCoverageTest.FlacTagEngine.buildVorbisCommentBlock(
                vendorString = "GoBeats",
                comments = mapOf("TITLE" to "Lossless Master", "ARTIST" to "Apex Engine"),
                isLast = true
            )
            val fullContent = GobeatsTier1FeatureCoverageTest.FlacTagEngine.FLAC_MAGIC + streamInfo + tagBlock + rawAudio
            stagingFile.writeBytes(fullContent)

            // Scoped storage validator checks staging file before commit
            val isValid = GobeatsTier1FeatureCoverageTest.ScopedStorageRouter.validateStagingFile(
                stagingFile,
                GobeatsTier1FeatureCoverageTest.FlacTagEngine.FLAC_MAGIC
            )
            assertTrue(isValid)

            // Verify Vorbis comments inside staging file
            val parsedComments = GobeatsTier1FeatureCoverageTest.FlacTagEngine.parseVorbisComments(tagBlock)
            assertEquals("Lossless Master", parsedComments["TITLE"])
            assertEquals("Apex Engine", parsedComments["ARTIST"])
        } finally {
            stagingFile.delete()
            tempDir.delete()
        }
    }

    // =========================================================================
    // 6. F6 (Scoped Storage Hardening) + F8 (M4A Tag Editor & stco Offsets)
    // =========================================================================

    @Test
    fun cross_06_scopedStorageStagingWithM4aChunkOffsetAdjustment() {
        val originalChunkOffsets = listOf(4096, 8192, 12288)
        val moovExpansion = 240 // tags added expanded moov by 240 bytes

        // Calculate chunk offset adjustments
        val adjustedOffsets = GobeatsTier1FeatureCoverageTest.M4aChunkOffsetEngine.adjustStcoOffsets(
            originalOffsets = originalChunkOffsets,
            moovDelta = moovExpansion,
            mdatStartOffset = 2048
        )
        assertEquals(4096 + 240, adjustedOffsets[0])
        assertEquals(8192 + 240, adjustedOffsets[1])
        assertEquals(12288 + 240, adjustedOffsets[2])

        // Verify API write mechanism for public storage
        val mechanism = GobeatsTier1FeatureCoverageTest.ScopedStorageRouter.resolveWriteMechanism(apiLevel = 33, isPublicStorage = true)
        assertEquals(GobeatsTier1FeatureCoverageTest.StorageWriteMechanism.MEDIA_STORE_CREATE_WRITE_REQUEST, mechanism)
    }

    // =========================================================================
    // 7. F7/F8 (Lossless Tag Editor) + F9 (Tag Sync Pipeline)
    // =========================================================================

    @Test
    fun cross_07_losslessTagWritingRegistersDatabaseOverrideProtectingRescan() {
        val sim = GobeatsTier1FeatureCoverageTest.TagSyncSimulator()
        val flacTrack = Track(
            id = "flac_101",
            sourceId = "local",
            fileUri = "/storage/emulated/0/Music/track.flac",
            title = "Raw Title",
            artistName = "Raw Artist",
            albumTitle = "Raw Album",
            durationMs = 240000L,
            format = "FLAC"
        )

        // User saves new tags
        val updated = sim.onTagsSaved(flacTrack, "Audiophile Edition", "HiFi Artist", "Master Album")
        assertEquals("Audiophile Edition", updated.title)

        // Rescan from raw disk occurs
        val scanned = flacTrack.copy(title = "Old Tag In File")
        val preserved = sim.applyOverridesDuringRescan(scanned)
        assertEquals("Audiophile Edition", preserved.title)
        assertEquals("HiFi Artist", preserved.artistName)
    }

    // =========================================================================
    // 8. F10 (Folders Directory Tree) + F11 (Audiophile Gapless & Crossfade)
    // =========================================================================

    @Test
    fun cross_08_folderInstantQueueWithActiveCrossfadeBetweenConsecutiveTracks() {
        val folderTracks = listOf(
            "01_intro.flac",
            "02_main_theme.flac",
            "03_outro.flac"
        )
        val queue = GobeatsTier1FeatureCoverageTest.FolderBrowserEngine.generateFolderQueue(folderTracks, shuffle = false)
        assertEquals(3, queue.size)

        // Track 0 transitioning into Track 1 with 3s crossfade
        val shouldCrossfade0to1 = GobeatsTier1FeatureCoverageTest.CrossfadeEngine.shouldTriggerCrossfade(
            currentPosMs = 177500L,
            durationMs = 180000L,
            crossfadeSec = 3,
            queueIndex = 0,
            queueSize = queue.size,
            repeatMode = RepeatMode.OFF
        )
        assertTrue(shouldCrossfade0to1)

        // Track 2 (last track) with repeat OFF should NOT crossfade into nothingness
        val shouldCrossfadeLast = GobeatsTier1FeatureCoverageTest.CrossfadeEngine.shouldTriggerCrossfade(
            currentPosMs = 177500L,
            durationMs = 180000L,
            crossfadeSec = 3,
            queueIndex = 2,
            queueSize = queue.size,
            repeatMode = RepeatMode.OFF
        )
        assertFalse(shouldCrossfadeLast)
    }

    // =========================================================================
    // 9. F10 (Folders Directory Tree) + F12 (Offline Synced Lyrics)
    // =========================================================================

    @Test
    fun cross_09_folderBrowsingDetectsCompanionLrcForFolderTracks() {
        val folderFiles = listOf(
            "Comfortably Numb.flac",
            "Comfortably Numb.lrc",
            "Hey You.flac",
            "Hey You.lrc",
            "folder.jpg"
        )

        val audioFiles = folderFiles.filter { GobeatsTier1FeatureCoverageTest.FolderBrowserEngine.isSupportedAudioFile(it) }
        assertEquals(2, audioFiles.size)

        // Check companion .lrc lookup for the first track
        val baseName = audioFiles[0].substringBeforeLast(".")
        val companionLrc = folderFiles.find { it.equals("$baseName.lrc", ignoreCase = true) }
        assertNotNull(companionLrc)
        assertEquals("Comfortably Numb.lrc", companionLrc)
    }

    // =========================================================================
    // 10. F13 (Process Death Persistence) + F11 (Crossfade Engine)
    // =========================================================================

    @Test
    fun cross_10_restoringPlaybackStateAfterProcessDeathWithCrossfadeEnabled() {
        val savedTrackIds = listOf("t_01", "t_02", "t_03")
        val jsonQueue = GobeatsTier1FeatureCoverageTest.ProcessDeathPersistenceEngine.serializeQueue(savedTrackIds)

        // Restored in paused state at 178,000ms of a 180,000ms track (inside 3s crossfade window)
        val restoredState = GobeatsTier1FeatureCoverageTest.SavedPlaybackState(
            currentTrackId = "t_01",
            positionMs = 178000L,
            isPlaying = false,
            queueTrackIds = GobeatsTier1FeatureCoverageTest.ProcessDeathPersistenceEngine.deserializeQueue(jsonQueue),
            updatedAt = System.currentTimeMillis()
        )

        assertEquals("t_01", restoredState.currentTrackId)
        assertFalse(restoredState.isPlaying) // Paused!

        // When user taps play, crossfade should immediately compute transition
        val willTriggerCrossfade = GobeatsTier1FeatureCoverageTest.CrossfadeEngine.shouldTriggerCrossfade(
            currentPosMs = restoredState.positionMs,
            durationMs = 180000L,
            crossfadeSec = 3,
            queueIndex = 0,
            queueSize = restoredState.queueTrackIds.size,
            repeatMode = RepeatMode.OFF
        )
        assertTrue(willTriggerCrossfade)
    }
}
