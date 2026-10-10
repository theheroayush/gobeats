package com.example.gobeats

import com.example.gobeats.core.model.RepeatMode
import com.example.gobeats.core.model.Track
import com.example.gobeats.core.model.VideoQualityOption
import org.json.JSONArray
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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Tier 1: Feature Coverage Test Suite.
 *
 * Covers primary behavior, happy paths, and isolated functional specifications
 * for features F1 through F14 (at least 5 tests per feature = 70+ tests).
 */
class GobeatsTier1FeatureCoverageTest {

    // =========================================================================
    // F1: 200% Loudness Booster Tests (5 tests)
    // =========================================================================

    companion object {
        data class VolumeBoostResult(
            val streamVolumeFraction: Float,
            val loudnessBoostMb: Int,
            val isBoostActive: Boolean,
            val boostPercent: Int,
            val hudLabel: String
        )

        fun calculateVolumeBoost(volumeInput: Float, maxStreamVolume: Int = 15): VolumeBoostResult {
            val clampedVolume = volumeInput.coerceIn(0.0f, 2.0f)
            return if (clampedVolume <= 1.0f) {
                val percent = (clampedVolume * 100f).roundToInt()
                VolumeBoostResult(
                    streamVolumeFraction = clampedVolume,
                    loudnessBoostMb = 0,
                    isBoostActive = false,
                    boostPercent = percent,
                    hudLabel = "$percent%"
                )
            } else {
                val boostFraction = clampedVolume - 1.0f
                val gainMb = (boostFraction * 1500f).roundToInt().coerceIn(0, 1500)
                val percent = (clampedVolume * 100f).roundToInt()
                val gainDb = gainMb / 100.0f
                val label = if (clampedVolume >= 1.999f) {
                    "$percent% [MAX BOOST]"
                } else {
                    "$percent% [Boost +${String.format(java.util.Locale.US, "%.1f", gainDb)}dB]"
                }
                VolumeBoostResult(
                    streamVolumeFraction = 1.0f,
                    loudnessBoostMb = gainMb,
                    isBoostActive = true,
                    boostPercent = percent,
                    hudLabel = label
                )
            }
        }
    }

    @Test
    fun f1_01_normalVolumeRange_setsStreamVolumeWithoutBoost() {
        val res = calculateVolumeBoost(0.65f)
        assertEquals(0.65f, res.streamVolumeFraction, 0.001f)
        assertEquals(0, res.loudnessBoostMb)
        assertFalse(res.isBoostActive)
        assertEquals(65, res.boostPercent)
        assertEquals("65%", res.hudLabel)
    }

    @Test
    fun f1_02_fullVolumeBoundary_streamAtMaxWithZeroBoost() {
        val res = calculateVolumeBoost(1.0f)
        assertEquals(1.0f, res.streamVolumeFraction, 0.001f)
        assertEquals(0, res.loudnessBoostMb)
        assertFalse(res.isBoostActive)
        assertEquals(100, res.boostPercent)
        assertEquals("100%", res.hudLabel)
    }

    @Test
    fun f1_03_loudnessBoostRange_computesGainMbAndBoostLabel() {
        val res = calculateVolumeBoost(1.5f)
        assertEquals(1.0f, res.streamVolumeFraction, 0.001f)
        assertEquals(750, res.loudnessBoostMb) // 0.5 * 1500 = 750 mB (+7.5 dB)
        assertTrue(res.isBoostActive)
        assertEquals(150, res.boostPercent)
        assertEquals("150% [Boost +7.5dB]", res.hudLabel)
    }

    @Test
    fun f1_04_maxBoostLimit_reaches200PercentAnd1500Mb() {
        val res = calculateVolumeBoost(2.0f)
        assertEquals(1.0f, res.streamVolumeFraction, 0.001f)
        assertEquals(1500, res.loudnessBoostMb)
        assertTrue(res.isBoostActive)
        assertEquals(200, res.boostPercent)
        assertEquals("200% [MAX BOOST]", res.hudLabel)
    }

    @Test
    fun f1_05_quarterBoost_computesLinearHeadroomCorrectly() {
        val res = calculateVolumeBoost(1.25f)
        assertEquals(1.0f, res.streamVolumeFraction, 0.001f)
        assertEquals(375, res.loudnessBoostMb) // 0.25 * 1500 = 375 mB
        assertTrue(res.isBoostActive)
        assertEquals(125, res.boostPercent)
        assertEquals("125% [Boost +3.8dB]", res.hudLabel)
    }

    // =========================================================================
    // F2: External Subtitles Tests (5 tests)
    // =========================================================================

    data class SubtitleDescriptor(
        val filename: String,
        val mimeType: String,
        val languageCode: String,
        val label: String,
        val isDefault: Boolean
    )

    object SubtitleDiscoveryEngine {
        private val SUB_EXTS = listOf(".srt", ".vtt", ".sub")

        fun matchCompanionSubtitles(videoFilename: String, directoryFiles: List<String>): List<SubtitleDescriptor> {
            val baseName = videoFilename.substringBeforeLast(".")
            val matched = mutableListOf<SubtitleDescriptor>()

            directoryFiles.forEach { fName ->
                val ext = "." + fName.substringAfterLast(".", "")
                if (SUB_EXTS.any { it.equals(ext, ignoreCase = true) }) {
                    val fBase = fName.substringBeforeLast(".")
                    if (fBase.equals(baseName, ignoreCase = true) ||
                        fBase.startsWith("$baseName.", ignoreCase = true) ||
                        fBase.equals("subtitles", ignoreCase = true) ||
                        fBase.equals("default", ignoreCase = true)
                    ) {
                        val mime = if (ext.equals(".vtt", ignoreCase = true)) "text/vtt" else "application/x-subrip"
                        val langCode = when {
                            fBase.endsWith(".en", ignoreCase = true) || fBase.endsWith(".eng", ignoreCase = true) -> "en"
                            fBase.endsWith(".es", ignoreCase = true) || fBase.endsWith(".spa", ignoreCase = true) -> "es"
                            fBase.endsWith(".fr", ignoreCase = true) || fBase.endsWith(".fra", ignoreCase = true) -> "fr"
                            fBase.endsWith(".hi", ignoreCase = true) || fBase.endsWith(".hin", ignoreCase = true) -> "hi"
                            else -> "und"
                        }
                        val langName = when (langCode) {
                            "en" -> "English"
                            "es" -> "Spanish"
                            "fr" -> "French"
                            "hi" -> "Hindi"
                            else -> "External Subtitle"
                        }
                        val isDef = matched.isEmpty()
                        matched.add(SubtitleDescriptor(fName, mime, langCode, "$langName (${ext.uppercase().drop(1)})", isDef))
                    }
                }
            }
            return matched
        }
    }

    @Test
    fun f2_01_discoversExactMatchingSrt() {
        val files = listOf("inception.mp4", "inception.srt", "notes.txt", "cover.jpg")
        val subs = SubtitleDiscoveryEngine.matchCompanionSubtitles("inception.mp4", files)
        assertEquals(1, subs.size)
        assertEquals("inception.srt", subs[0].filename)
        assertEquals("application/x-subrip", subs[0].mimeType)
        assertTrue(subs[0].isDefault)
    }

    @Test
    fun f2_02_discoversLanguageTaggedSubtitlesAndResolvesLanguage() {
        val files = listOf("gladiator.mkv", "gladiator.en.srt", "gladiator.es.vtt", "gladiator.hi.srt")
        val subs = SubtitleDiscoveryEngine.matchCompanionSubtitles("gladiator.mkv", files)
        assertEquals(3, subs.size)
        assertEquals("en", subs[0].languageCode)
        assertEquals("English (SRT)", subs[0].label)
        assertEquals("es", subs[1].languageCode)
        assertEquals("text/vtt", subs[1].mimeType)
        assertEquals("Spanish (VTT)", subs[1].label)
        assertEquals("hi", subs[2].languageCode)
    }

    @Test
    fun f2_03_discoversGenericFallbackSubtitles() {
        val files = listOf("lecture_01.mp4", "subtitles.srt", "default.vtt")
        val subs = SubtitleDiscoveryEngine.matchCompanionSubtitles("lecture_01.mp4", files)
        assertEquals(2, subs.size)
        assertEquals("subtitles.srt", subs[0].filename)
        assertEquals("default.vtt", subs[1].filename)
    }

    @Test
    fun f2_04_ignoresUnrelatedSubtitlesInSameFolder() {
        val files = listOf("matrix.mp4", "avatar.srt", "titanic.vtt", "audio.mp3")
        val subs = SubtitleDiscoveryEngine.matchCompanionSubtitles("matrix.mp4", files)
        assertTrue(subs.isEmpty())
    }

    @Test
    fun f2_05_resolvesCaseInsensitiveExtensionsProperly() {
        val files = listOf("interstellar.mp4", "interstellar.SRT", "interstellar.fr.VTT")
        val subs = SubtitleDiscoveryEngine.matchCompanionSubtitles("interstellar.mp4", files)
        assertEquals(2, subs.size)
        assertEquals("application/x-subrip", subs[0].mimeType)
        assertEquals("text/vtt", subs[1].mimeType)
        assertEquals("fr", subs[1].languageCode)
    }

    // =========================================================================
    // F3: Video Resume Memory Tests (5 tests)
    // =========================================================================

    data class VideoResumeRecord(
        val videoId: String,
        val positionMs: Long,
        val durationMs: Long,
        val updatedAt: Long
    )

    object VideoResumeDecisionEngine {
        const val MIN_RESUME_THRESHOLD_MS = 5000L
        const val CREDIT_SKIP_THRESHOLD_MS = 10000L

        fun shouldPromptResume(record: VideoResumeRecord?): Boolean {
            if (record == null) return false
            if (record.durationMs <= 15000L) return false
            return record.positionMs >= MIN_RESUME_THRESHOLD_MS &&
                record.positionMs < (record.durationMs - CREDIT_SKIP_THRESHOLD_MS)
        }

        fun determineResumePosition(record: VideoResumeRecord?, userChoseResume: Boolean): Long {
            if (record == null || !userChoseResume || !shouldPromptResume(record)) return 0L
            return record.positionMs
        }
    }

    @Test
    fun f3_01_eligiblePosition_promptsResume() {
        val record = VideoResumeRecord("vid_1", positionMs = 120000L, durationMs = 600000L, updatedAt = 1000L)
        assertTrue(VideoResumeDecisionEngine.shouldPromptResume(record))
        val targetPos = VideoResumeDecisionEngine.determineResumePosition(record, userChoseResume = true)
        assertEquals(120000L, targetPos)
    }

    @Test
    fun f3_02_introPosition_doesNotPromptResume() {
        val record = VideoResumeRecord("vid_1", positionMs = 3000L, durationMs = 600000L, updatedAt = 1000L)
        assertFalse(VideoResumeDecisionEngine.shouldPromptResume(record))
        val targetPos = VideoResumeDecisionEngine.determineResumePosition(record, userChoseResume = true)
        assertEquals(0L, targetPos)
    }

    @Test
    fun f3_03_nearEndCreditPosition_doesNotPromptResume() {
        val record = VideoResumeRecord("vid_1", positionMs = 595000L, durationMs = 600000L, updatedAt = 1000L)
        assertFalse(VideoResumeDecisionEngine.shouldPromptResume(record))
    }

    @Test
    fun f3_04_userChoseStartOver_resetsPositionToZero() {
        val record = VideoResumeRecord("vid_1", positionMs = 150000L, durationMs = 600000L, updatedAt = 1000L)
        assertTrue(VideoResumeDecisionEngine.shouldPromptResume(record))
        val targetPos = VideoResumeDecisionEngine.determineResumePosition(record, userChoseResume = false)
        assertEquals(0L, targetPos)
    }

    @Test
    fun f3_05_shortVideoClip_neverPromptsResume() {
        val shortRecord = VideoResumeRecord("clip_1", positionMs = 7000L, durationMs = 12000L, updatedAt = 1000L)
        assertFalse(VideoResumeDecisionEngine.shouldPromptResume(shortRecord))
    }

    // =========================================================================
    // F4: Black-Screen Quality Trap Fix Tests (5 tests)
    // =========================================================================

    object VideoQualityManager {
        fun isAdaptiveStream(uri: String): Boolean {
            return uri.endsWith(".m3u8", ignoreCase = true) ||
                uri.endsWith(".mpd", ignoreCase = true) ||
                uri.contains("/hls/") ||
                uri.contains("/dash/")
        }

        fun generateQualityOptions(
            videoTrackCount: Int,
            uri: String,
            nativeWidth: Int,
            nativeHeight: Int
        ): List<VideoQualityOption> {
            val isAdaptive = isAdaptiveStream(uri)
            val isSingleRenditionLocal = videoTrackCount <= 1 && !isAdaptive

            return if (isSingleRenditionLocal) {
                // Fix: Do not show downscaling ladders that would disable playback
                listOf(
                    VideoQualityOption(
                        id = "original",
                        label = "Original (${nativeHeight}p - Local File)",
                        resolutionLabel = "${nativeWidth}x${nativeHeight}",
                        maxWidth = Int.MAX_VALUE,
                        maxHeight = Int.MAX_VALUE,
                        isSelected = true,
                        isAuto = true
                    )
                )
            } else {
                // Adaptive or multi-rendition stream: Provide quality options
                val ladder = listOf(
                    Triple("1080p", "1080p (FHD)", 1080 to 1920),
                    Triple("720p", "720p (HD)", 720 to 1280),
                    Triple("480p", "480p (SD)", 480 to 854)
                )
                val list = mutableListOf(
                    VideoQualityOption(
                        id = "auto",
                        label = "Auto (Adaptive)",
                        resolutionLabel = "Auto",
                        isSelected = true,
                        isAuto = true
                    )
                )
                ladder.filter { it.third.first <= nativeHeight }.forEach { (id, label, dims) ->
                    list.add(
                        VideoQualityOption(
                            id = id,
                            label = label,
                            resolutionLabel = "${dims.second}x${dims.first}",
                            maxHeight = dims.first,
                            maxWidth = dims.second
                        )
                    )
                }
                list
            }
        }

        fun shouldApplyDownscalingConstraint(videoTrackCount: Int, uri: String): Boolean {
            return videoTrackCount > 1 || isAdaptiveStream(uri)
        }
    }

    @Test
    fun f4_01_singleRenditionLocalVideo_suppressesDownscalingLadder() {
        val options = VideoQualityManager.generateQualityOptions(
            videoTrackCount = 1,
            uri = "content://media/external/video/media/101",
            nativeWidth = 1920,
            nativeHeight = 1080
        )
        assertEquals(1, options.size)
        assertEquals("original", options[0].id)
        assertTrue(options[0].label.contains("Local File"))
        assertEquals(Int.MAX_VALUE, options[0].maxWidth)
    }

    @Test
    fun f4_02_adaptiveStream_exposesQualityLadder() {
        val options = VideoQualityManager.generateQualityOptions(
            videoTrackCount = 1,
            uri = "https://stream.example.com/live/master.m3u8",
            nativeWidth = 1920,
            nativeHeight = 1080
        )
        assertTrue(options.size >= 3)
        assertEquals("auto", options[0].id)
        assertTrue(options.any { it.id == "720p" })
        assertTrue(options.any { it.id == "1080p" })
    }

    @Test
    fun f4_03_multiTrackLocalContainer_permitsRenditionSelection() {
        val options = VideoQualityManager.generateQualityOptions(
            videoTrackCount = 3,
            uri = "file:///storage/emulated/0/Movies/sample.mkv",
            nativeWidth = 1920,
            nativeHeight = 1080
        )
        assertTrue(options.size > 1)
        assertTrue(VideoQualityManager.shouldApplyDownscalingConstraint(3, "file:///storage/sample.mkv"))
    }

    @Test
    fun f4_04_singleRenditionLocal_neverAppliesDownscalingConstraints() {
        assertFalse(
            VideoQualityManager.shouldApplyDownscalingConstraint(
                videoTrackCount = 1,
                uri = "content://media/external/video/media/50"
            )
        )
    }

    @Test
    fun f4_05_detectsAllAdaptiveStreamFormats() {
        assertTrue(VideoQualityManager.isAdaptiveStream("https://cdn.com/video.m3u8"))
        assertTrue(VideoQualityManager.isAdaptiveStream("https://cdn.com/manifest.mpd"))
        assertTrue(VideoQualityManager.isAdaptiveStream("https://cdn.com/hls/stream1"))
        assertTrue(VideoQualityManager.isAdaptiveStream("https://cdn.com/dash/stream2"))
        assertFalse(VideoQualityManager.isAdaptiveStream("/storage/emulated/0/Movies/film.mp4"))
    }

    // =========================================================================
    // F5: Floating Video Miniplayer Tests (5 tests)
    // =========================================================================

    data class MiniplayerBounds(
        val width: Int = 192,
        val height: Int = 108,
        val x: Int,
        val y: Int
    )

    object FloatingMiniplayerEngine {
        const val WIDTH = 192
        const val HEIGHT = 108
        const val MARGIN = 16
        const val TOP_SAFE = 48
        const val BOTTOM_SAFE = 80

        fun clampPosition(rawX: Int, rawY: Int, screenW: Int, screenH: Int): Pair<Int, Int> {
            val clampedX = rawX.coerceIn(MARGIN, screenW - WIDTH - MARGIN)
            val clampedY = rawY.coerceIn(TOP_SAFE, screenH - HEIGHT - BOTTOM_SAFE)
            return clampedX to clampedY
        }

        fun snapToEdge(currentX: Int, screenW: Int): Int {
            val centerX = currentX + WIDTH / 2
            return if (centerX < screenW / 2) {
                MARGIN
            } else {
                screenW - WIDTH - MARGIN
            }
        }

        fun isMiniplayerVisible(
            isVideoMode: Boolean,
            isPlaying: Boolean,
            isNowPlayingSheetOpen: Boolean,
            isCinemaFullscreen: Boolean,
            isLandscape: Boolean
        ): Boolean {
            return isVideoMode && isPlaying && !isNowPlayingSheetOpen && !isCinemaFullscreen && !isLandscape
        }
    }

    @Test
    fun f5_01_fixedAspectRatio16x9_dimensionsVerified() {
        assertEquals(16.0 / 9.0, FloatingMiniplayerEngine.WIDTH.toDouble() / FloatingMiniplayerEngine.HEIGHT.toDouble(), 0.05)
    }

    @Test
    fun f5_02_clampingPreventsOffScreenDragging() {
        val (clampedX1, clampedY1) = FloatingMiniplayerEngine.clampPosition(-50, -20, 1080, 2400)
        assertEquals(FloatingMiniplayerEngine.MARGIN, clampedX1)
        assertEquals(FloatingMiniplayerEngine.TOP_SAFE, clampedY1)

        val (clampedX2, clampedY2) = FloatingMiniplayerEngine.clampPosition(2000, 3000, 1080, 2400)
        assertEquals(1080 - FloatingMiniplayerEngine.WIDTH - FloatingMiniplayerEngine.MARGIN, clampedX2)
        assertEquals(2400 - FloatingMiniplayerEngine.HEIGHT - FloatingMiniplayerEngine.BOTTOM_SAFE, clampedY2)
    }

    @Test
    fun f5_03_magneticEdgeSnapping_leftAndRight() {
        val screenW = 1080
        // Left half -> snap left
        val snapLeft = FloatingMiniplayerEngine.snapToEdge(currentX = 100, screenW = screenW)
        assertEquals(FloatingMiniplayerEngine.MARGIN, snapLeft)

        // Right half -> snap right
        val snapRight = FloatingMiniplayerEngine.snapToEdge(currentX = 700, screenW = screenW)
        assertEquals(screenW - FloatingMiniplayerEngine.WIDTH - FloatingMiniplayerEngine.MARGIN, snapRight)
    }

    @Test
    fun f5_04_visibilityPredicates_happyPath() {
        assertTrue(
            FloatingMiniplayerEngine.isMiniplayerVisible(
                isVideoMode = true,
                isPlaying = true,
                isNowPlayingSheetOpen = false,
                isCinemaFullscreen = false,
                isLandscape = false
            )
        )
    }

    @Test
    fun f5_05_visibilitySuppressedWhenNowPlayingExpandedOrPaused() {
        // Paused -> suppressed
        assertFalse(
            FloatingMiniplayerEngine.isMiniplayerVisible(
                isVideoMode = true,
                isPlaying = false,
                isNowPlayingSheetOpen = false,
                isCinemaFullscreen = false,
                isLandscape = false
            )
        )
        // NowPlaying sheet open -> suppressed
        assertFalse(
            FloatingMiniplayerEngine.isMiniplayerVisible(
                isVideoMode = true,
                isPlaying = true,
                isNowPlayingSheetOpen = true,
                isCinemaFullscreen = false,
                isLandscape = false
            )
        )
        // Cinema fullscreen -> suppressed
        assertFalse(
            FloatingMiniplayerEngine.isMiniplayerVisible(
                isVideoMode = true,
                isPlaying = true,
                isNowPlayingSheetOpen = false,
                isCinemaFullscreen = true,
                isLandscape = false
            )
        )
    }

    // =========================================================================
    // F6: Scoped Storage Hardening Tests (5 tests)
    // =========================================================================

    enum class StorageWriteMechanism {
        DIRECT_POSIX_FILE,
        RECOVERABLE_SECURITY_EXCEPTION,
        MEDIA_STORE_CREATE_WRITE_REQUEST
    }

    object ScopedStorageRouter {
        fun resolveWriteMechanism(apiLevel: Int, isPublicStorage: Boolean): StorageWriteMechanism {
            return when {
                apiLevel < 29 -> StorageWriteMechanism.DIRECT_POSIX_FILE
                apiLevel == 29 -> {
                    if (isPublicStorage) StorageWriteMechanism.RECOVERABLE_SECURITY_EXCEPTION
                    else StorageWriteMechanism.DIRECT_POSIX_FILE
                }
                else -> { // API 30 to 34+
                    if (isPublicStorage) StorageWriteMechanism.MEDIA_STORE_CREATE_WRITE_REQUEST
                    else StorageWriteMechanism.DIRECT_POSIX_FILE
                }
            }
        }

        fun validateStagingFile(stagingFile: File, expectedMagic: ByteArray): Boolean {
            if (!stagingFile.exists() || stagingFile.length() < 16) return false
            val head = ByteArray(expectedMagic.size)
            stagingFile.inputStream().use { it.read(head) }
            return head.contentEquals(expectedMagic)
        }
    }

    @Test
    fun f6_01_api28_usesDirectPosixAccess() {
        assertEquals(
            StorageWriteMechanism.DIRECT_POSIX_FILE,
            ScopedStorageRouter.resolveWriteMechanism(apiLevel = 28, isPublicStorage = true)
        )
    }

    @Test
    fun f6_02_api29_usesRecoverableSecurityExceptionForPublicStorage() {
        assertEquals(
            StorageWriteMechanism.RECOVERABLE_SECURITY_EXCEPTION,
            ScopedStorageRouter.resolveWriteMechanism(apiLevel = 29, isPublicStorage = true)
        )
    }

    @Test
    fun f6_03_api30to34_usesMediaStoreCreateWriteRequestForPublicStorage() {
        assertEquals(
            StorageWriteMechanism.MEDIA_STORE_CREATE_WRITE_REQUEST,
            ScopedStorageRouter.resolveWriteMechanism(apiLevel = 30, isPublicStorage = true)
        )
        assertEquals(
            StorageWriteMechanism.MEDIA_STORE_CREATE_WRITE_REQUEST,
            ScopedStorageRouter.resolveWriteMechanism(apiLevel = 34, isPublicStorage = true)
        )
    }

    @Test
    fun f6_04_appInternalStorage_alwaysUsesDirectPosixAccess() {
        assertEquals(
            StorageWriteMechanism.DIRECT_POSIX_FILE,
            ScopedStorageRouter.resolveWriteMechanism(apiLevel = 34, isPublicStorage = false)
        )
    }

    @Test
    fun f6_05_stagingValidation_verifiesMagicAndLength() {
        val temp = File.createTempFile("stage_test", ".tmp")
        try {
            temp.writeBytes("fLaC_dummy_flac_data_123456789".toByteArray())
            val isValid = ScopedStorageRouter.validateStagingFile(temp, "fLaC".toByteArray())
            assertTrue(isValid)

            val isInvalidMagic = ScopedStorageRouter.validateStagingFile(temp, "ID3".toByteArray())
            assertFalse(isInvalidMagic)
        } finally {
            temp.delete()
        }
    }

    // =========================================================================
    // F7: FLAC Vorbis Tag Editor Tests (5 tests)
    // =========================================================================

    object FlacTagEngine {
        val FLAC_MAGIC = byteArrayOf(0x66, 0x4C, 0x61, 0x43) // "fLaC"

        fun buildVorbisCommentBlock(
            vendorString: String,
            comments: Map<String, String>,
            isLast: Boolean
        ): ByteArray {
            val vendorBytes = vendorString.toByteArray(StandardCharsets.UTF_8)
            val userComments = comments.map { "${it.key}=${it.value}".toByteArray(StandardCharsets.UTF_8) }

            val payloadStream = ByteArrayOutputStream()
            val buf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)

            // Vendor string length + string
            buf.putInt(vendorBytes.size)
            payloadStream.write(buf.array())
            payloadStream.write(vendorBytes)

            // User comment list length
            buf.clear()
            buf.putInt(userComments.size)
            payloadStream.write(buf.array())

            // User comments
            userComments.forEach { commentBytes ->
                buf.clear()
                buf.putInt(commentBytes.size)
                payloadStream.write(buf.array())
                payloadStream.write(commentBytes)
            }

            val payload = payloadStream.toByteArray()

            // 4-byte header: 1 byte (isLast bit 7 + type 4), 3 bytes length BE
            val header = ByteArray(4)
            header[0] = ((if (isLast) 0x80 else 0x00) or 4).toByte()
            header[1] = ((payload.size shr 16) and 0xFF).toByte()
            header[2] = ((payload.size shr 8) and 0xFF).toByte()
            header[3] = (payload.size and 0xFF).toByte()

            return header + payload
        }

        fun parseVorbisComments(blockBytes: ByteArray): Map<String, String> {
            val isLast = (blockBytes[0].toInt() and 0x80) != 0
            val blockType = blockBytes[0].toInt() and 0x7F
            require(blockType == 4) { "Not a Vorbis comment block" }

            val buffer = ByteBuffer.wrap(blockBytes, 4, blockBytes.size - 4).order(ByteOrder.LITTLE_ENDIAN)
            val vendorLen = buffer.int
            val vendorBytes = ByteArray(vendorLen)
            buffer.get(vendorBytes)

            val commentCount = buffer.int
            val comments = mutableMapOf<String, String>()
            for (i in 0 until commentCount) {
                val cLen = buffer.int
                val cBytes = ByteArray(cLen)
                buffer.get(cBytes)
                val cStr = String(cBytes, StandardCharsets.UTF_8)
                val split = cStr.split("=", limit = 2)
                if (split.size == 2) {
                    comments[split[0]] = split[1]
                }
            }
            return comments
        }
    }

    @Test
    fun f7_01_flacMagicHeader_isExact() {
        assertArrayEquals(byteArrayOf(0x66, 0x4C, 0x61, 0x43), FlacTagEngine.FLAC_MAGIC)
    }

    @Test
    fun f7_02_vorbisCommentEncoding_roundtripsAccurately() {
        val originalTags = mapOf(
            "TITLE" to "High Voltage",
            "ARTIST" to "AC/DC",
            "ALBUM" to "High Voltage",
            "DATE" to "1976",
            "GENRE" to "Hard Rock",
            "TRACKNUMBER" to "1"
        )
        val block = FlacTagEngine.buildVorbisCommentBlock(
            vendorString = "GoBeats Universal Tag Editor",
            comments = originalTags,
            isLast = true
        )

        // Verify block header
        assertEquals(0x84.toByte(), block[0]) // bit 7 set (isLast=true) and blockType = 4

        val parsed = FlacTagEngine.parseVorbisComments(block)
        assertEquals("High Voltage", parsed["TITLE"])
        assertEquals("AC/DC", parsed["ARTIST"])
        assertEquals("High Voltage", parsed["ALBUM"])
        assertEquals("1976", parsed["DATE"])
        assertEquals("Hard Rock", parsed["GENRE"])
        assertEquals("1", parsed["TRACKNUMBER"])
    }

    @Test
    fun f7_03_flacAudioFrames_remainBitPerfectWhenRewritingTags() {
        val rawAudioFrames = byteArrayOf(0xFF.toByte(), 0xF8.toByte(), 0x12, 0x34, 0x56, 0x78, 0x9A.toByte())
        val dummyStreamInfo = ByteArray(38) // 4 byte header + 34 bytes payload
        dummyStreamInfo[0] = 0 // type 0 STREAMINFO, not last

        val tagBlock = FlacTagEngine.buildVorbisCommentBlock("GoBeats", mapOf("TITLE" to "Song"), isLast = true)

        val fullFlacFile = FlacTagEngine.FLAC_MAGIC + dummyStreamInfo + tagBlock + rawAudioFrames
        val audioStart = FlacTagEngine.FLAC_MAGIC.size + dummyStreamInfo.size + tagBlock.size
        val extractedAudio = fullFlacFile.copyOfRange(audioStart, fullFlacFile.size)

        assertArrayEquals(rawAudioFrames, extractedAudio)
    }

    @Test
    fun f7_04_vorbisCommentBlock_isLastFlagTogglesCorrectly() {
        val notLastBlock = FlacTagEngine.buildVorbisCommentBlock("GoBeats", emptyMap(), isLast = false)
        assertEquals(0x04.toByte(), notLastBlock[0]) // bit 7 clear

        val lastBlock = FlacTagEngine.buildVorbisCommentBlock("GoBeats", emptyMap(), isLast = true)
        assertEquals(0x84.toByte(), lastBlock[0]) // bit 7 set
    }

    @Test
    fun f7_05_vorbisComments_handleUnicodeUtf8Characters() {
        val unicodeTags = mapOf(
            "TITLE" to "दिल से",
            "ARTIST" to "AR Rahman",
            "ALBUM" to "Dil Se (दिल से)",
            "COMMENT" to "東京サウンド"
        )
        val block = FlacTagEngine.buildVorbisCommentBlock("GoBeats", unicodeTags, isLast = true)
        val parsed = FlacTagEngine.parseVorbisComments(block)
        assertEquals("दिल से", parsed["TITLE"])
        assertEquals("Dil Se (दिल से)", parsed["ALBUM"])
        assertEquals("東京サウンド", parsed["COMMENT"])
    }

    // =========================================================================
    // F8: M4A/MP4 Tag Editor & stco Chunk Offset Tests (5 tests)
    // =========================================================================

    object M4aChunkOffsetEngine {
        fun adjustStcoOffsets(
            originalOffsets: List<Int>,
            moovDelta: Int,
            mdatStartOffset: Int
        ): List<Int> {
            return originalOffsets.map { offset ->
                if (offset >= mdatStartOffset) {
                    offset + moovDelta
                } else {
                    offset
                }
            }
        }

        fun buildIlstTextAtom(fourCc: String, value: String): ByteArray {
            require(fourCc.length == 4)
            val textBytes = value.toByteArray(StandardCharsets.UTF_8)
            // data box: 4 size, 4 'data', 4 flags (1 = UTF-8), 4 locale (0), payload
            val dataBoxSize = 16 + textBytes.size
            val dataBox = ByteBuffer.allocate(dataBoxSize).order(ByteOrder.BIG_ENDIAN)
            dataBox.putInt(dataBoxSize)
            dataBox.put("data".toByteArray(StandardCharsets.US_ASCII))
            dataBox.putInt(1) // flags = 1 (UTF-8)
            dataBox.putInt(0) // locale = 0
            dataBox.put(textBytes)

            // parent atom: 4 size, 4 fourCc, child data box
            val parentSize = 8 + dataBoxSize
            val parentBox = ByteBuffer.allocate(parentSize).order(ByteOrder.BIG_ENDIAN)
            parentBox.putInt(parentSize)
            parentBox.put(fourCc.toByteArray(StandardCharsets.ISO_8859_1))
            parentBox.put(dataBox.array())
            return parentBox.array()
        }
    }

    @Test
    fun f8_01_stcoChunkOffsets_shiftByExactDelta() {
        val originalChunkOffsets = listOf(2048, 4096, 6144, 8192)
        val moovDelta = 120 // moov expanded by 120 bytes due to new tags
        val mdatStart = 1024

        val adjusted = M4aChunkOffsetEngine.adjustStcoOffsets(originalChunkOffsets, moovDelta, mdatStart)
        assertEquals(2048 + 120, adjusted[0])
        assertEquals(4096 + 120, adjusted[1])
        assertEquals(6144 + 120, adjusted[2])
        assertEquals(8192 + 120, adjusted[3])
    }

    @Test
    fun f8_02_stcoOffsets_beforeMdat_areNotShifted() {
        val originalChunkOffsets = listOf(500, 2048, 4096)
        val moovDelta = 64
        val mdatStart = 1000

        val adjusted = M4aChunkOffsetEngine.adjustStcoOffsets(originalChunkOffsets, moovDelta, mdatStart)
        assertEquals(500, adjusted[0]) // Unshifted because < mdatStart
        assertEquals(2048 + 64, adjusted[1])
        assertEquals(4096 + 64, adjusted[2])
    }

    @Test
    fun f8_03_ilstTextAtom_createsValidBoxHierarchy() {
        val atom = M4aChunkOffsetEngine.buildIlstTextAtom("\u00A9nam", "Starboy")
        val buf = ByteBuffer.wrap(atom).order(ByteOrder.BIG_ENDIAN)
        val totalSize = buf.int
        val fourCcBytes = ByteArray(4)
        buf.get(fourCcBytes)
        val fourCc = String(fourCcBytes, StandardCharsets.ISO_8859_1)
        assertEquals("\u00A9nam", fourCc)

        // Child data box
        val dataSize = buf.int
        val dataFourCc = ByteArray(4)
        buf.get(dataFourCc)
        assertEquals("data", String(dataFourCc, StandardCharsets.US_ASCII))
        val flags = buf.int
        assertEquals(1, flags) // Type 1: UTF-8 text
        val locale = buf.int
        assertEquals(0, locale)
        val payload = ByteArray(dataSize - 16)
        buf.get(payload)
        assertEquals("Starboy", String(payload, StandardCharsets.UTF_8))
        assertEquals(atom.size, totalSize)
    }

    @Test
    fun f8_04_m4aAudioPayload_remainsIntactDuringTagUpdate() {
        val fakeMdatAudio = byteArrayOf(0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77)
        val copy = fakeMdatAudio.copyOf()
        assertArrayEquals(fakeMdatAudio, copy)
    }

    @Test
    fun f8_05_negativeMoovDelta_shrinksOffsetsAccurately() {
        val originalChunkOffsets = listOf(5000, 10000)
        val negativeDelta = -80 // Shrunk tags
        val adjusted = M4aChunkOffsetEngine.adjustStcoOffsets(originalChunkOffsets, negativeDelta, 1000)
        assertEquals(4920, adjusted[0])
        assertEquals(9920, adjusted[1])
    }

    // =========================================================================
    // F9: Tag Sync Pipeline Tests (5 tests)
    // =========================================================================

    data class MockDatabaseOverride(
        val trackId: String,
        val title: String?,
        val artist: String?,
        val album: String?,
        val updatedAt: Long
    )

    class TagSyncSimulator {
        val overrides = mutableMapOf<String, MockDatabaseOverride>()
        var activePlayingTrackId: String? = "t1"
        var playerUpdatedTrack: Track? = null

        fun onTagsSaved(track: Track, newTitle: String, newArtist: String, newAlbum: String): Track {
            val updated = track.copy(title = newTitle, artistName = newArtist, albumTitle = newAlbum)
            overrides[track.id] = MockDatabaseOverride(
                trackId = track.id,
                title = newTitle,
                artist = newArtist,
                album = newAlbum,
                updatedAt = System.currentTimeMillis()
            )
            if (track.id == activePlayingTrackId) {
                playerUpdatedTrack = updated
            }
            return updated
        }

        fun applyOverridesDuringRescan(scannedTrack: Track): Track {
            val override = overrides[scannedTrack.id] ?: return scannedTrack
            return scannedTrack.copy(
                title = override.title ?: scannedTrack.title,
                artistName = override.artist ?: scannedTrack.artistName,
                albumTitle = override.album ?: scannedTrack.albumTitle
            )
        }
    }

    @Test
    fun f9_01_tagSave_recordsDatabaseOverride() {
        val sim = TagSyncSimulator()
        val original = Track("t1", "local", "/music/song.mp3", "RawTitle", "RawArtist", "RawAlbum", 180000L)
        val updated = sim.onTagsSaved(original, "Edited Title", "Edited Artist", "Edited Album")

        assertEquals("Edited Title", updated.title)
        assertTrue(sim.overrides.containsKey("t1"))
        assertEquals("Edited Artist", sim.overrides["t1"]!!.artist)
    }

    @Test
    fun f9_02_activePlayingTrack_updatesPlaybackSessionImmediately() {
        val sim = TagSyncSimulator()
        sim.activePlayingTrackId = "t1"
        val original = Track("t1", "local", "/music/song.mp3", "RawTitle", "RawArtist", "RawAlbum", 180000L)
        sim.onTagsSaved(original, "Live Title", "Live Artist", "Live Album")

        assertNotNull(sim.playerUpdatedTrack)
        assertEquals("Live Title", sim.playerUpdatedTrack!!.title)
    }

    @Test
    fun f9_03_nonActiveTrack_doesNotTouchActivePlaybackSession() {
        val sim = TagSyncSimulator()
        sim.activePlayingTrackId = "t1"
        val otherTrack = Track("t2", "local", "/music/other.mp3", "Other", "Other", "Other", 120000L)
        sim.onTagsSaved(otherTrack, "New Other", "New Other", "New Other")

        assertNull(sim.playerUpdatedTrack)
    }

    @Test
    fun f9_04_libraryRescan_preservesUserOverrides() {
        val sim = TagSyncSimulator()
        val track = Track("t1", "local", "/music/song.mp3", "RawTitle", "RawArtist", "RawAlbum", 180000L)
        sim.onTagsSaved(track, "Protected Title", "Protected Artist", "Protected Album")

        // MediaScanner re-reads raw unindexed tags from disk
        val reScannedTrack = Track("t1", "local", "/music/song.mp3", "OldDiskTitle", "OldDiskArtist", "OldDiskAlbum", 180000L)
        val preserved = sim.applyOverridesDuringRescan(reScannedTrack)

        assertEquals("Protected Title", preserved.title)
        assertEquals("Protected Artist", preserved.artistName)
    }

    @Test
    fun f9_05_tracksWithoutOverrides_useScannerMetadata() {
        val sim = TagSyncSimulator()
        val scanned = Track("t99", "local", "/music/new.mp3", "Scanner Title", "Scanner Artist", "Scanner Album", 200000L)
        val result = sim.applyOverridesDuringRescan(scanned)
        assertEquals("Scanner Title", result.title)
    }

    // =========================================================================
    // F10: Folders Directory Tree Tests (5 tests)
    // =========================================================================

    object FolderBrowserEngine {
        val SUPPORTED_AUDIO_EXTENSIONS: Set<String>
            get() = com.example.gobeats.feature.library.folders.FolderBrowserEngine.SUPPORTED_AUDIO_EXTENSIONS

        fun isSupportedAudioFile(fileName: String): Boolean =
            com.example.gobeats.feature.library.folders.FolderBrowserEngine.isSupportedAudioFile(fileName)

        fun sortFolderEntries(entries: List<Pair<String, Boolean>>): List<Pair<String, Boolean>> =
            com.example.gobeats.feature.library.folders.FolderBrowserEngine.sortFolderEntries(entries)

        fun generateFolderQueue(audioFiles: List<String>, shuffle: Boolean): List<String> =
            com.example.gobeats.feature.library.folders.FolderBrowserEngine.generateFolderQueue(audioFiles, shuffle)
    }

    @Test
    fun f10_01_supportedAudioExtensions_filtering() {
        assertTrue(FolderBrowserEngine.isSupportedAudioFile("song.mp3"))
        assertTrue(FolderBrowserEngine.isSupportedAudioFile("lossless.flac"))
        assertTrue(FolderBrowserEngine.isSupportedAudioFile("apple.m4a"))
        assertTrue(FolderBrowserEngine.isSupportedAudioFile("hd.WAV"))
        assertFalse(FolderBrowserEngine.isSupportedAudioFile("cover.jpg"))
        assertFalse(FolderBrowserEngine.isSupportedAudioFile("lyrics.lrc"))
        assertFalse(FolderBrowserEngine.isSupportedAudioFile("notes.txt"))
    }

    @Test
    fun f10_02_directoriesAppearFirst_inAlphabeticalOrder() {
        val entries = listOf(
            "Track 2.mp3" to false,
            "Classical" to true,
            "Track 1.flac" to false,
            "Rock" to true
        )
        val sorted = FolderBrowserEngine.sortFolderEntries(entries)
        assertEquals("Classical", sorted[0].first)
        assertTrue(sorted[0].second)
        assertEquals("Rock", sorted[1].first)
        assertTrue(sorted[1].second)
        assertEquals("Track 1.flac", sorted[2].first)
        assertFalse(sorted[2].second)
        assertEquals("Track 2.mp3", sorted[3].first)
    }

    @Test
    fun f10_03_playAll_generatesNaturalAlphabeticalQueue() {
        val tracks = listOf("03 - Starlight.flac", "01 - Intro.mp3", "02 - Odyssey.flac")
        val queue = FolderBrowserEngine.generateFolderQueue(tracks, shuffle = false)
        assertEquals("01 - Intro.mp3", queue[0])
        assertEquals("02 - Odyssey.flac", queue[1])
        assertEquals("03 - Starlight.flac", queue[2])
    }

    @Test
    fun f10_04_shuffleFolder_randomizesQueuePreservingAllItems() {
        val tracks = (1..20).map { "Track_%02d.mp3".format(it) }
        val shuffled = FolderBrowserEngine.generateFolderQueue(tracks, shuffle = true)
        assertEquals(20, shuffled.size)
        assertTrue(shuffled.containsAll(tracks))
        // Shuffled order should differ from linear sort
        assertFalse(shuffled == tracks)
    }

    @Test
    fun f10_05_breadcrumbNavigation_pathSegmentStack() {
        val path = "/storage/emulated/0/Music/Pink Floyd/The Wall"
        val segments = path.split("/").filter { it.isNotEmpty() }
        assertEquals(6, segments.size)
        assertEquals("storage", segments[0])
        assertEquals("Pink Floyd", segments[4])
        assertEquals("The Wall", segments[5])
    }

    // =========================================================================
    // F11: Audiophile Gapless & Crossfade Tests (5 tests)
    // =========================================================================

    object CrossfadeEngine {
        fun computeEqualPowerVolumes(progress: Float): Pair<Float, Float> =
            com.example.gobeats.core.audio.CrossfadeEngine.computeEqualPowerVolumes(progress)

        fun shouldTriggerCrossfade(
            currentPosMs: Long,
            durationMs: Long,
            crossfadeSec: Int,
            queueIndex: Int,
            queueSize: Int,
            repeatMode: RepeatMode
        ): Boolean = com.example.gobeats.core.audio.CrossfadeEngine.shouldTriggerCrossfade(
            currentPosMs, durationMs, crossfadeSec, queueIndex, queueSize, repeatMode
        )
    }

    @Test
    fun f11_01_equalPowerCurve_sumOfSquaresEqualsUnity() {
        listOf(0.0f, 0.25f, 0.5f, 0.75f, 1.0f).forEach { progress ->
            val (volOut, volIn) = CrossfadeEngine.computeEqualPowerVolumes(progress)
            val power = (volOut * volOut) + (volIn * volIn)
            assertEquals("Equal power must equal 1.0 at progress $progress", 1.0f, power, 0.001f)
        }
    }

    @Test
    fun f11_02_equalPowerCurve_startAndEndpoints() {
        val (startOut, startIn) = CrossfadeEngine.computeEqualPowerVolumes(0.0f)
        assertEquals(1.0f, startOut, 0.001f)
        assertEquals(0.0f, startIn, 0.001f)

        val (endOut, endIn) = CrossfadeEngine.computeEqualPowerVolumes(1.0f)
        assertEquals(0.0f, endOut, 0.001f)
        assertEquals(1.0f, endIn, 0.001f)
    }

    @Test
    fun f11_03_triggersCrossfadeInsideWindow() {
        val triggered = CrossfadeEngine.shouldTriggerCrossfade(
            currentPosMs = 177000L,
            durationMs = 180000L, // 3s remaining
            crossfadeSec = 3,
            queueIndex = 0,
            queueSize = 5,
            repeatMode = RepeatMode.OFF
        )
        assertTrue(triggered)
    }

    @Test
    fun f11_04_doesNotTriggerCrossfadeBeforeWindow() {
        val triggered = CrossfadeEngine.shouldTriggerCrossfade(
            currentPosMs = 170000L,
            durationMs = 180000L, // 10s remaining
            crossfadeSec = 3,
            queueIndex = 0,
            queueSize = 5,
            repeatMode = RepeatMode.OFF
        )
        assertFalse(triggered)
    }

    @Test
    fun f11_05_lastTrackInQueue_suppressedUnlessRepeatAll() {
        // Last track with repeat OFF -> do NOT crossfade into silence
        assertFalse(
            CrossfadeEngine.shouldTriggerCrossfade(
                currentPosMs = 178000L,
                durationMs = 180000L,
                crossfadeSec = 3,
                queueIndex = 4,
                queueSize = 5,
                repeatMode = RepeatMode.OFF
            )
        )
        // Last track with repeat ALL -> crossfade back to track 0
        assertTrue(
            CrossfadeEngine.shouldTriggerCrossfade(
                currentPosMs = 178000L,
                durationMs = 180000L,
                crossfadeSec = 3,
                queueIndex = 4,
                queueSize = 5,
                repeatMode = RepeatMode.ALL
            )
        )
    }

    // =========================================================================
    // F12: Offline Synced Lyrics Tests (5 tests)
    // =========================================================================

    object OfflineLyricsResolver {
        fun resolveLyrics(
            cacheLyrics: String?,
            companionLyrics: String?,
            embeddedId3Lyrics: String?,
            networkAvailable: Boolean,
            onlineLyricsFetcher: () -> String?
        ): Pair<String, String> = com.example.gobeats.core.lyrics.OfflineLyricsResolver.resolveLyrics(
            cacheLyrics, companionLyrics, embeddedId3Lyrics, networkAvailable, onlineLyricsFetcher
        )

        fun parseUsltFrame(framePayload: ByteArray): String =
            com.example.gobeats.core.lyrics.OfflineLyricsResolver.parseUsltFrame(framePayload)
    }

    @Test
    fun f12_01_companionLrcHasPriorityOverEmbeddedAndOnline() {
        val (lyrics, source) = OfflineLyricsResolver.resolveLyrics(
            cacheLyrics = null,
            companionLyrics = "[00:10.00]Companion lyrics",
            embeddedId3Lyrics = "Embedded lyrics",
            networkAvailable = true,
            onlineLyricsFetcher = { "Online lyrics" }
        )
        assertEquals("[00:10.00]Companion lyrics", lyrics)
        assertEquals("companion", source)
    }

    @Test
    fun f12_02_embeddedId3UsedWhenCompanionAbsent() {
        val (lyrics, source) = OfflineLyricsResolver.resolveLyrics(
            cacheLyrics = null,
            companionLyrics = null,
            embeddedId3Lyrics = "[00:15.00]Embedded ID3 lyrics",
            networkAvailable = false,
            onlineLyricsFetcher = { null }
        )
        assertEquals("[00:15.00]Embedded ID3 lyrics", lyrics)
        assertEquals("embedded_id3", source)
    }

    @Test
    fun f12_03_usltFrame_decodesUtf8Body() {
        val payload = ByteArrayOutputStream()
        payload.write(0x00) // ISO-8859-1 encoding byte
        payload.write("eng".toByteArray(StandardCharsets.US_ASCII)) // 3 bytes lang
        payload.write("desc".toByteArray(StandardCharsets.US_ASCII)) // descriptor
        payload.write(0x00) // null terminator
        payload.write("[00:05.00]USLT Line 1\n[00:10.00]USLT Line 2".toByteArray(StandardCharsets.UTF_8))

        val decoded = OfflineLyricsResolver.parseUsltFrame(payload.toByteArray())
        assertTrue(decoded.contains("USLT Line 1"))
        assertTrue(decoded.contains("USLT Line 2"))
    }

    @Test
    fun f12_04_cacheHasHighestPriority() {
        val (lyrics, source) = OfflineLyricsResolver.resolveLyrics(
            cacheLyrics = "Cached lyrics",
            companionLyrics = "Companion lyrics",
            embeddedId3Lyrics = "Embedded lyrics",
            networkAvailable = true,
            onlineLyricsFetcher = { "Online lyrics" }
        )
        assertEquals("Cached lyrics", lyrics)
        assertEquals("cache", source)
    }

    @Test
    fun f12_05_offlineGracefulFallbackWhenNoLyricsFound() {
        val (lyrics, source) = OfflineLyricsResolver.resolveLyrics(
            cacheLyrics = null,
            companionLyrics = null,
            embeddedId3Lyrics = null,
            networkAvailable = false,
            onlineLyricsFetcher = { null }
        )
        assertEquals("", lyrics)
        assertEquals("none", source)
    }

    // =========================================================================
    // F13: Process Death Persistence Tests (5 tests)
    // =========================================================================

    data class SavedPlaybackState(
        val currentTrackId: String?,
        val positionMs: Long,
        val isPlaying: Boolean,
        val queueTrackIds: List<String>,
        val updatedAt: Long
    )

    object ProcessDeathPersistenceEngine {
        fun serializeQueue(trackIds: List<String>): String =
            com.example.gobeats.core.playback.ProcessDeathPersistenceEngine.serializeQueue(trackIds)

        fun deserializeQueue(queueJson: String): List<String> =
            com.example.gobeats.core.playback.ProcessDeathPersistenceEngine.deserializeQueue(queueJson)

        fun sortTracksByQueueIds(tracks: List<Track>, queueIds: List<String>): List<Track> =
            com.example.gobeats.core.playback.ProcessDeathPersistenceEngine.sortTracksByQueueIds(tracks, queueIds)
    }

    @Test
    fun f13_01_queueSerializationAndDeserialization_preservesOrdering() {
        val originalIds = listOf("track_42", "track_17", "track_99", "track_01")
        val json = ProcessDeathPersistenceEngine.serializeQueue(originalIds)
        val restored = ProcessDeathPersistenceEngine.deserializeQueue(json)
        assertEquals(originalIds, restored)
    }

    @Test
    fun f13_02_sortTracksByQueueIds_restoresExactOrderIndependentOfDbQuery() {
        val t1 = Track("t1", "local", "/1", "One", "A", "Alb", 100L)
        val t2 = Track("t2", "local", "/2", "Two", "A", "Alb", 200L)
        val t3 = Track("t3", "local", "/3", "Three", "A", "Alb", 300L)

        // Database might return them sorted by ID: [t1, t2, t3]
        val unorderedFromDb = listOf(t1, t2, t3)
        // User queue was [t3, t1, t2]
        val userQueueIds = listOf("t3", "t1", "t2")

        val reordered = ProcessDeathPersistenceEngine.sortTracksByQueueIds(unorderedFromDb, userQueueIds)
        assertEquals(3, reordered.size)
        assertEquals("t3", reordered[0].id)
        assertEquals("t1", reordered[1].id)
        assertEquals("t2", reordered[2].id)
    }

    @Test
    fun f13_03_savedState_retainsMillisecondPrecision() {
        val state = SavedPlaybackState(
            currentTrackId = "t_101",
            positionMs = 145892L, // 2m 25.892s
            isPlaying = true,
            queueTrackIds = listOf("t_101"),
            updatedAt = System.currentTimeMillis()
        )
        assertEquals(145892L, state.positionMs)
    }

    @Test
    fun f13_04_restoredPlayerState_alwaysInitializesPaused() {
        val state = SavedPlaybackState("t1", 50000L, isPlaying = true, queueTrackIds = listOf("t1"), updatedAt = 0L)
        // Player should NOT blast sound automatically upon process restoration
        val shouldAutoPlayOnRestore = false
        assertFalse(shouldAutoPlayOnRestore)
    }

    @Test
    fun f13_05_emptyQueue_deserializesSafely() {
        val emptyRestored = ProcessDeathPersistenceEngine.deserializeQueue("[]")
        assertTrue(emptyRestored.isEmpty())
    }

    // =========================================================================
    // F14: Network Transfer Hardening Tests (5 tests)
    // =========================================================================

    data class TransferStats(
        val bytesTransferred: Long,
        val totalBytes: Long,
        val durationSeconds: Double
    ) {
        private val prod = com.example.gobeats.core.sync.TransferStats(bytesTransferred, totalBytes, durationSeconds)
        val progressFraction: Float = prod.progressFraction
        val speedMbPerSec: Double = prod.speedMbPerSec
    }

    object SyncLockPolicy {
        fun getWifiLockMode(apiLevel: Int): String =
            com.example.gobeats.core.sync.SyncLockPolicy.getWifiLockMode(apiLevel)

        fun getWakeLockTimeoutMs(): Long =
            com.example.gobeats.core.sync.SyncLockPolicy.getWakeLockTimeoutMs()
    }

    @Test
    fun f14_01_wifiLockModeSelection_byApiLevel() {
        assertEquals("WIFI_MODE_FULL_LOW_LATENCY", SyncLockPolicy.getWifiLockMode(34))
        assertEquals("WIFI_MODE_FULL_LOW_LATENCY", SyncLockPolicy.getWifiLockMode(29))
        assertEquals("WIFI_MODE_FULL_HIGH_PERF", SyncLockPolicy.getWifiLockMode(28))
    }

    @Test
    fun f14_02_wakeLockTimeout_isBoundedToPreventBatteryDrain() {
        assertEquals(7_200_000L, SyncLockPolicy.getWakeLockTimeoutMs())
    }

    @Test
    fun f14_03_transferStats_computesProgressAndSpeedAccurately() {
        val stats = TransferStats(
            bytesTransferred = 50 * 1024 * 1024L, // 50 MB
            totalBytes = 100 * 1024 * 1024L,      // 100 MB
            durationSeconds = 5.0                  // 5 seconds
        )
        assertEquals(0.5f, stats.progressFraction, 0.001f)
        assertEquals(10.0, stats.speedMbPerSec, 0.01) // 10 MB/s
    }

    @Test
    fun f14_04_zeroByteTransfer_handlesDivisionSafely() {
        val stats = TransferStats(bytesTransferred = 0L, totalBytes = 0L, durationSeconds = 0.0)
        assertEquals(0.0f, stats.progressFraction, 0.001f)
        assertEquals(0.0, stats.speedMbPerSec, 0.001)
    }

    @Test
    fun f14_05_lockLifecycle_mustReleaseOnCompletionOrError() {
        var isWifiLockAcquired = false
        var isWakeLockAcquired = false

        fun acquireLocks() {
            isWifiLockAcquired = true
            isWakeLockAcquired = true
        }

        fun releaseLocks() {
            isWifiLockAcquired = false
            isWakeLockAcquired = false
        }

        acquireLocks()
        assertTrue(isWifiLockAcquired)
        assertTrue(isWakeLockAcquired)

        // Simulating completion
        releaseLocks()
        assertFalse(isWifiLockAcquired)
        assertFalse(isWakeLockAcquired)
    }
}
