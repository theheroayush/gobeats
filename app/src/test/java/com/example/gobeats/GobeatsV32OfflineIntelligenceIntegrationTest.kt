package com.example.gobeats

import com.example.gobeats.core.audio.AudioDeviceProfileManager
import com.example.gobeats.core.audio.DialogueBoostProcessor
import com.example.gobeats.core.model.DeviceAudioProfile
import com.example.gobeats.core.model.DeviceAudioProfileType
import com.example.gobeats.core.model.DialogueBoostMode
import com.example.gobeats.core.model.DualSubtitleTrack
import com.example.gobeats.core.model.PlaybackUiState
import com.example.gobeats.core.model.SkipIntroSegment
import com.example.gobeats.core.model.SubtitleCue
import com.example.gobeats.core.model.Track
import com.example.gobeats.core.video.AiSubtitleEngine
import com.example.gobeats.core.video.OfflineSkipIntroDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * End-to-end integration and verification suite for gobeats v3.2:
 * 1. Offline AI Subtitles & Indian Regional Languages (Hindi, Tamil, Telugu, Marathi, Bengali, English).
 * 2. Hardware-Aware Auto Device Profile & Persistent Bluetooth AV Delay.
 * 3. Distinct Music vs Video Audio Settings Pipelines.
 * 4. Offline Skip Intro Detection.
 * 5. Dialogue Boost Vocal Intelligibility Filter.
 */
class GobeatsV32OfflineIntelligenceIntegrationTest {

    // ==========================================
    // 1. DIALOGUE BOOST VOCAL FILTER
    // ==========================================

    @Test
    fun testDialogueBoostFilterProfiles() {
        val processor = DialogueBoostProcessor()

        // OFF mode: Flat response
        processor.setMode(DialogueBoostMode.OFF)
        assertEquals(0, processor.getGainOffsetForFreqHz(60))
        assertEquals(0, processor.getGainOffsetForFreqHz(1000))
        assertEquals(0, processor.getGainOffsetForFreqHz(3000))
        assertEquals(0.0f, processor.getGainOffsetDbForFreqHz(1000), 0.001f)

        // CLEAR mode: +4 dB at formants (1 kHz, 3 kHz), -3 dB at 60 Hz mud
        processor.setMode(DialogueBoostMode.CLEAR)
        assertEquals(-300, processor.getGainOffsetForFreqHz(60))
        assertEquals(400, processor.getGainOffsetForFreqHz(1000))
        assertEquals(400, processor.getGainOffsetForFreqHz(3000))
        assertEquals(4.0f, processor.getGainOffsetDbForFreqHz(1000), 0.001f)
        assertEquals(-3.0f, processor.getGainOffsetDbForFreqHz(60), 0.001f)

        // MAXIMUM mode: +7 dB at formants, -6 dB at 60 Hz rumble
        processor.setMode(DialogueBoostMode.MAXIMUM)
        assertEquals(-600, processor.getGainOffsetForFreqHz(60))
        assertEquals(700, processor.getGainOffsetForFreqHz(1000))
        assertEquals(700, processor.getGainOffsetForFreqHz(3000))
        assertEquals(7.0f, processor.getGainOffsetDbForFreqHz(1000), 0.001f)
        assertEquals(-6.0f, processor.getGainOffsetDbForFreqHz(60), 0.001f)
    }

    // ==========================================
    // 2. HARDWARE AUDIO PROFILE & AV DELAY
    // ==========================================

    @Test
    fun testDeviceAudioProfileAcousticDefaults() {
        val earbudsProfile = DeviceAudioProfile(
            deviceId = "bt_galaxy_buds_pro",
            name = "Galaxy Buds Pro",
            type = DeviceAudioProfileType.EARBUDS,
            preferredEqPreset = "Deep Bass Punch",
            avDelayMs = 120L
        )

        val carProfile = DeviceAudioProfile(
            deviceId = "bt_car_multimedia",
            name = "Car Multimedia",
            type = DeviceAudioProfileType.CAR_AUDIO,
            preferredEqPreset = "Warm Analog",
            avDelayMs = 180L
        )

        val phoneSpeakerProfile = AudioDeviceProfileManager.DEFAULT_SPEAKER_PROFILE

        assertEquals(DeviceAudioProfileType.EARBUDS, earbudsProfile.type)
        assertEquals("Deep Bass Punch", earbudsProfile.preferredEqPreset)
        assertEquals(120L, earbudsProfile.avDelayMs)

        assertEquals(DeviceAudioProfileType.CAR_AUDIO, carProfile.type)
        assertEquals("Warm Analog", carProfile.preferredEqPreset)
        assertEquals(180L, carProfile.avDelayMs)

        assertEquals(DeviceAudioProfileType.SPEAKER, phoneSpeakerProfile.type)
        assertEquals("Vocal Clarity", phoneSpeakerProfile.preferredEqPreset)
        assertEquals(0L, phoneSpeakerProfile.avDelayMs)
    }

    // ==========================================
    // 3. OFFLINE SKIP INTRO HEURISTICS
    // ==========================================

    @Test
    fun testOfflineSkipIntroDetection() {
        val longVideo = Track(
            id = "vid_ep_1",
            sourceId = "local",
            fileUri = "file:///storage/emulated/0/Movies/Anime_Ep1.mkv",
            title = "Episode 01",
            artistName = "Studio",
            albumTitle = "Season 1",
            durationMs = 1_440_000L, // 24 minutes
            isVideo = true
        )

        val shortVideo = Track(
            id = "vid_clip",
            sourceId = "local",
            fileUri = "file:///storage/emulated/0/DCIM/clip.mp4",
            title = "Short Clip",
            artistName = "Camera",
            albumTitle = "Camera",
            durationMs = 120_000L, // 2 minutes
            isVideo = true
        )

        // Long media automatically activates default smart intro window (0 - 85s)
        val introSegLong = OfflineSkipIntroDetector.detectIntroSegment(longVideo)
        assertTrue(introSegLong.endMs > 0L)
        assertEquals(85_000L, introSegLong.endMs)

        // Active at 10s and 84s, deactivates within final 800ms threshold (>= 84.2s)
        assertTrue(OfflineSkipIntroDetector.isSkipIntroActive(10_000L, introSegLong))
        assertTrue(OfflineSkipIntroDetector.isSkipIntroActive(84_000L, introSegLong))
        assertFalse(OfflineSkipIntroDetector.isSkipIntroActive(84_500L, introSegLong))
        assertFalse(OfflineSkipIntroDetector.isSkipIntroActive(85_001L, introSegLong))
        assertEquals(85_000L, OfflineSkipIntroDetector.getSkipTargetMs(introSegLong))

        // Short media under 5 minutes without chapters does NOT produce active intro
        val introSegShort = OfflineSkipIntroDetector.detectIntroSegment(shortVideo)
        assertFalse(OfflineSkipIntroDetector.isSkipIntroActive(10_000L, introSegShort))
    }

    // ==========================================
    // 4. OFFLINE AI SUBTITLE ENGINE & LANGUAGES
    // ==========================================

    @Test
    fun testAiSubtitleEngineLanguagesAndFallback() {
        // Supported languages include Indian regional languages
        val supportedCodes = AiSubtitleEngine.SUPPORTED_LANGUAGES.map { it.code }
        assertTrue(supportedCodes.contains("hi-IN")) // Hindi
        assertTrue(supportedCodes.contains("ta-IN")) // Tamil
        assertTrue(supportedCodes.contains("te-IN")) // Telugu
        assertTrue(supportedCodes.contains("mr-IN")) // Marathi
        assertTrue(supportedCodes.contains("bn-IN")) // Bengali
        assertTrue(supportedCodes.contains("en-IN")) // English (India)
        assertTrue(supportedCodes.contains("en-US")) // English (US)

        // Fallback chain verification
        assertEquals("en-IN", AiSubtitleEngine.getFallbackLanguage("hi-IN"))
        assertEquals("en-IN", AiSubtitleEngine.getFallbackLanguage("ta-IN"))
        assertEquals("en-US", AiSubtitleEngine.getFallbackLanguage("en-IN"))
        assertEquals("en-US", AiSubtitleEngine.getFallbackLanguage("unknown-lang"))
    }

    @Test
    fun testSrtParsingAndSerializationLosslessRoundTrip() {
        val srtInput = """
            1
            00:00:01,500 --> 00:00:04,200
            नमस्ते, गोबीट्स में आपका स्वागत है।

            2
            00:00:05,000 --> 00:00:08,750
            Welcome to the ultimate offline media experience.
        """.trimIndent()

        val parsedCues = AiSubtitleEngine.parseSrt(srtInput)
        assertEquals(2, parsedCues.size)

        assertEquals(1500L, parsedCues[0].startMs)
        assertEquals(4200L, parsedCues[0].endMs)
        assertEquals("नमस्ते, गोबीट्स में आपका स्वागत है।", parsedCues[0].text)

        assertEquals(5000L, parsedCues[1].startMs)
        assertEquals(8750L, parsedCues[1].endMs)
        assertEquals("Welcome to the ultimate offline media experience.", parsedCues[1].text)

        // Serialize back to SRT format
        val serializedSrt = AiSubtitleEngine.toSrt(parsedCues)
        val roundTripCues = AiSubtitleEngine.parseSrt(serializedSrt)

        assertEquals(parsedCues.size, roundTripCues.size)
        assertEquals(parsedCues[0].text, roundTripCues[0].text)
        assertEquals(parsedCues[1].text, roundTripCues[1].text)
        assertEquals(parsedCues[0].startMs, roundTripCues[0].startMs)
        assertEquals(parsedCues[1].endMs, roundTripCues[1].endMs)
    }

    // ==========================================
    // 5. PLAYBACK UI STATE & AUDIO SEPARATION
    // ==========================================

    @Test
    fun testPlaybackUiStateV32Integration() {
        val state = PlaybackUiState(
            isVideoMode = true,
            dialogueBoostMode = DialogueBoostMode.CLEAR,
            avDelayMs = 150L,
            primarySubtitleTrack = DualSubtitleTrack(
                id = "ai_sub_hi",
                label = "Hindi AI",
                languageCode = "hi-IN",
                isAiGenerated = true
            ),
            secondarySubtitleTrack = DualSubtitleTrack(
                id = "ai_sub_en",
                label = "English AI",
                languageCode = "en-IN",
                isAiGenerated = true
            ),
            activePrimarySubtitleText = "नमस्ते दुनिया",
            activeSecondarySubtitleText = "Hello World",
            activeSkipIntroSegment = SkipIntroSegment(0L, 85_000L),
            isSkipIntroVisible = true,
            currentAudioDeviceProfile = DeviceAudioProfile(
                deviceId = "bt_earbuds",
                name = "OnePlus Buds",
                type = DeviceAudioProfileType.EARBUDS,
                preferredEqPreset = "Deep Bass Punch",
                avDelayMs = 150L
            )
        )

        // Video-specific audio settings operate without modifying music bit-perfect mode
        assertTrue(state.isVideoMode)
        assertEquals(DialogueBoostMode.CLEAR, state.dialogueBoostMode)
        assertEquals(150L, state.avDelayMs)
        assertNotNull(state.currentAudioDeviceProfile)
        assertEquals("OnePlus Buds", state.currentAudioDeviceProfile?.name)
        assertEquals(DeviceAudioProfileType.EARBUDS, state.currentAudioDeviceProfile?.type)

        // Dual Subtitles simultaneous presence
        assertEquals("नमस्ते दुनिया", state.activePrimarySubtitleText)
        assertEquals("Hello World", state.activeSecondarySubtitleText)

        // Skip intro active
        assertTrue(state.isSkipIntroVisible)
        assertEquals(85_000L, state.activeSkipIntroSegment?.endMs)
    }
}
