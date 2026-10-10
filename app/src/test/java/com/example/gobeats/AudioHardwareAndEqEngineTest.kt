package com.example.gobeats

import com.example.gobeats.core.audio.AudioDeviceProfileManager
import com.example.gobeats.core.audio.DialogueBoostProcessor
import com.example.gobeats.core.model.DeviceAudioProfile
import com.example.gobeats.core.model.DeviceAudioProfileType
import com.example.gobeats.core.model.DialogueBoostMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioHardwareAndEqEngineTest {

    @Test
    fun audioDeviceProfileManager_defaultPresetsMatchAcousticArchetypes() {
        val manager = AudioDeviceProfileManager.DEFAULT_SPEAKER_PROFILE
        assertEquals(DeviceAudioProfileType.SPEAKER, manager.type)
        assertEquals("Vocal Clarity", manager.preferredEqPreset)
        assertEquals(0L, manager.avDelayMs)
    }

    @Test
    fun dialogueBoostProcessor_modeOffProvidesFlatResponse() {
        val processor = DialogueBoostProcessor()
        processor.setMode(DialogueBoostMode.OFF)

        assertEquals(DialogueBoostMode.OFF, processor.mode.value)
        assertEquals(0, processor.getGainOffsetForFreqHz(60))
        assertEquals(0, processor.getGainOffsetForFreqHz(1000))
        assertEquals(0, processor.getGainOffsetForFreqHz(3000))
        assertEquals(0, processor.getGainOffsetForFreqHz(230))
        assertEquals(0, processor.getGainOffsetForFreqHz(14000))

        assertEquals(0.0f, processor.getGainOffsetDbForFreqHz(60), 0.001f)
        assertEquals(0.0f, processor.getGainOffsetDbForFreqHz(1000), 0.001f)
        assertEquals(0.0f, processor.getGainOffsetDbForFreqHz(3000), 0.001f)
    }

    @Test
    fun dialogueBoostProcessor_modeClearAppliesExpectedGains() {
        val processor = DialogueBoostProcessor()
        processor.setMode(DialogueBoostMode.CLEAR)

        assertEquals(DialogueBoostMode.CLEAR, processor.mode.value)

        // 60 Hz mud cut: -3 dB (-300 mB)
        assertEquals(-300, processor.getGainOffsetForFreqHz(60))
        assertEquals(-3.0f, processor.getGainOffsetDbForFreqHz(60), 0.001f)

        // 1 kHz vocal presence boost: +4 dB (+400 mB)
        assertEquals(400, processor.getGainOffsetForFreqHz(1000))
        assertEquals(4.0f, processor.getGainOffsetDbForFreqHz(1000), 0.001f)

        // 3 kHz speech intelligibility / sibilance boost: +4 dB (+400 mB)
        assertEquals(400, processor.getGainOffsetForFreqHz(3000))
        assertEquals(4.0f, processor.getGainOffsetDbForFreqHz(3000), 0.001f)

        // Untargeted frequencies should remain uncolored (0 dB)
        assertEquals(0, processor.getGainOffsetForFreqHz(230))
        assertEquals(0, processor.getGainOffsetForFreqHz(14000))

        val offsets = processor.getGainOffsets()
        assertEquals(-300, offsets[DialogueBoostProcessor.FREQ_RUMBLE_CUT_HZ])
        assertEquals(400, offsets[DialogueBoostProcessor.FREQ_VOCAL_BODY_HZ])
        assertEquals(400, offsets[DialogueBoostProcessor.FREQ_VOCAL_CLARITY_HZ])
    }

    @Test
    fun dialogueBoostProcessor_modeMaximumAppliesExpectedGains() {
        val processor = DialogueBoostProcessor()
        processor.setMode(DialogueBoostMode.MAXIMUM)

        assertEquals(DialogueBoostMode.MAXIMUM, processor.mode.value)

        // 60 Hz deep rumble cut: -6 dB (-600 mB)
        assertEquals(-600, processor.getGainOffsetForFreqHz(60))
        assertEquals(-6.0f, processor.getGainOffsetDbForFreqHz(60), 0.001f)

        // 1 kHz vocal punch: +7 dB (+700 mB)
        assertEquals(700, processor.getGainOffsetForFreqHz(1000))
        assertEquals(7.0f, processor.getGainOffsetDbForFreqHz(1000), 0.001f)

        // 3 kHz maximum articulation: +7 dB (+700 mB)
        assertEquals(700, processor.getGainOffsetForFreqHz(3000))
        assertEquals(7.0f, processor.getGainOffsetDbForFreqHz(3000), 0.001f)

        // Untargeted frequencies remain at 0 mB
        assertEquals(0, processor.getGainOffsetForFreqHz(230))
        assertEquals(0, processor.getGainOffsetForFreqHz(14000))

        val offsets = processor.getGainOffsets()
        assertEquals(-600, offsets[DialogueBoostProcessor.FREQ_RUMBLE_CUT_HZ])
        assertEquals(700, offsets[DialogueBoostProcessor.FREQ_VOCAL_BODY_HZ])
        assertEquals(700, offsets[DialogueBoostProcessor.FREQ_VOCAL_CLARITY_HZ])
    }

    @Test
    fun dialogueBoostProcessor_releaseCompletesSafely() {
        val processor = DialogueBoostProcessor()
        processor.setMode(DialogueBoostMode.CLEAR)
        processor.release()
        // Processor state is preserved and release executes without throwing
        assertEquals(DialogueBoostMode.CLEAR, processor.mode.value)
    }

    @Test
    fun deviceAudioProfile_modelIntegrity() {
        val profile = DeviceAudioProfile(
            deviceId = "bt_airpods_pro",
            name = "AirPods Pro",
            type = DeviceAudioProfileType.EARBUDS,
            preferredEqPreset = "Deep Bass Punch",
            avDelayMs = 150L,
            updatedAt = 123456789L
        )

        assertEquals("bt_airpods_pro", profile.deviceId)
        assertEquals("AirPods Pro", profile.name)
        assertEquals(DeviceAudioProfileType.EARBUDS, profile.type)
        assertEquals("Deep Bass Punch", profile.preferredEqPreset)
        assertEquals(150L, profile.avDelayMs)
        assertEquals(123456789L, profile.updatedAt)
    }
}
