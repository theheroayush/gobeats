package com.example.gobeats.core.audio

import android.content.Context
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.os.Build
import android.util.Log
import com.example.gobeats.core.model.DialogueBoostMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Intelligent acoustic dialogue and vocal intelligibility processor for gobeats.
 *
 * Focuses on isolating human vocal formants (primary energy band: 300 Hz - 3.4 kHz)
 * from overwhelming background musical scores, engine rumble, or explosive sound effects.
 *
 * Supported DialogueBoostMode profiles:
 * - OFF: Normal audio balance (0 dB gain offset).
 * - CLEAR: +4 dB at 1 kHz and 3 kHz, -3 dB at 60 Hz (cuts low-end mud/rumble).
 * - MAXIMUM: +7 dB at 1 kHz and 3 kHz, -6 dB at 60 Hz (maximum dialogue presence and clarity).
 *
 * Can bind directly to ExoPlayer's audio session ID using hardware DynamicsProcessing (API 28+)
 * or standalone Equalizer, or seamlessly manage frequency filter gain offsets for EqualizerManager.
 */
class DialogueBoostProcessor(
    private val context: Context? = null,
    private val equalizerManager: EqualizerManager? = null
) {

    companion object {
        private const val TAG = "DialogueBoostProcessor"
        private const val PREFS_NAME = "gobeats_audio"
        private const val KEY_MODE = "dialogue_boost_mode"

        // Center frequencies targeted by vocal formant acoustics
        const val FREQ_RUMBLE_CUT_HZ = 60
        const val FREQ_VOCAL_BODY_HZ = 1000
        const val FREQ_VOCAL_CLARITY_HZ = 3000

        private fun logI(tag: String, msg: String) {
            try { Log.i(tag, msg) } catch (_: Throwable) {}
        }
        private fun logD(tag: String, msg: String) {
            try { Log.d(tag, msg) } catch (_: Throwable) {}
        }
        private fun logW(tag: String, msg: String, tr: Throwable? = null) {
            try { if (tr != null) Log.w(tag, msg, tr) else Log.w(tag, msg) } catch (_: Throwable) {}
        }
    }

    private val prefs = try {
        context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    } catch (_: Throwable) {
        null
    }

    private val _mode = MutableStateFlow(loadInitialMode())
    val mode: StateFlow<DialogueBoostMode> = _mode.asStateFlow()

    private var currentSessionId: Int = 0
    private var dynamicsProcessing: DynamicsProcessing? = null
    private var standaloneEqualizer: Equalizer? = null

    init {
        // Synchronize initial offsets with EqualizerManager if present
        syncWithEqualizerManager(_mode.value)
    }

    private fun loadInitialMode(): DialogueBoostMode {
        val savedName = try {
            prefs?.getString(KEY_MODE, DialogueBoostMode.OFF.name) ?: DialogueBoostMode.OFF.name
        } catch (_: Throwable) {
            DialogueBoostMode.OFF.name
        }
        return try {
            DialogueBoostMode.valueOf(savedName)
        } catch (_: Exception) {
            DialogueBoostMode.OFF
        }
    }

    /**
     * Returns the gain offset in millibels (mB, where 1 dB = 100 mB) for a given center frequency.
     */
    fun getGainOffsetForFreqHz(freqHz: Int, targetMode: DialogueBoostMode = _mode.value): Int {
        return when (targetMode) {
            DialogueBoostMode.OFF -> 0
            DialogueBoostMode.CLEAR -> {
                when {
                    freqHz <= 120 -> -300 // -3 dB cut at 60 Hz (cuts mud & boominess)
                    freqHz in 600..1800 -> 400 // +4 dB boost at 1 kHz (core vocal resonance)
                    freqHz in 1801..5000 -> 400 // +4 dB boost at 3 kHz (speech consonant intelligibility)
                    else -> 0
                }
            }
            DialogueBoostMode.MAXIMUM -> {
                when {
                    freqHz <= 120 -> -600 // -6 dB cut at 60 Hz (deep rumble attenuation)
                    freqHz in 600..1800 -> 700 // +7 dB boost at 1 kHz (maximum vocal punch)
                    freqHz in 1801..5000 -> 700 // +7 dB boost at 3 kHz (maximum articulation)
                    else -> 0
                }
            }
        }
    }

    /**
     * Returns the gain offset in decibels (dB) for a given center frequency.
     */
    fun getGainOffsetDbForFreqHz(freqHz: Int, targetMode: DialogueBoostMode = _mode.value): Float {
        return getGainOffsetForFreqHz(freqHz, targetMode) / 100.0f
    }

    /**
     * Returns the active gain offsets map in millibels for key dialogue frequencies.
     */
    fun getGainOffsets(targetMode: DialogueBoostMode = _mode.value): Map<Int, Int> {
        return mapOf(
            FREQ_RUMBLE_CUT_HZ to getGainOffsetForFreqHz(FREQ_RUMBLE_CUT_HZ, targetMode),
            FREQ_VOCAL_BODY_HZ to getGainOffsetForFreqHz(FREQ_VOCAL_BODY_HZ, targetMode),
            FREQ_VOCAL_CLARITY_HZ to getGainOffsetForFreqHz(FREQ_VOCAL_CLARITY_HZ, targetMode)
        )
    }

    /**
     * Updates the dialogue boost mode, reconfigures active DSP effects,
     * updates EqualizerManager offsets, and persists preference.
     */
    fun setMode(newMode: DialogueBoostMode) {
        if (_mode.value == newMode) return
        _mode.value = newMode
        try {
            prefs?.edit()?.putString(KEY_MODE, newMode.name)?.apply()
        } catch (_: Throwable) {}

        // 1. Update EqualizerManager gain offsets
        syncWithEqualizerManager(newMode)

        // 2. Update hardware DSP session effects
        applyModeToHardwareEffects(newMode)

        logI(TAG, "Dialogue boost mode set to: $newMode")
    }

    /**
     * Binds processor to ExoPlayer's audio session ID.
     * Attempts DynamicsProcessing (API 28+), falling back to standalone Equalizer if unavailable.
     */
    fun bindAudioSession(sessionId: Int) {
        if (sessionId <= 0 || sessionId == currentSessionId) return
        releaseAudioEffects()
        currentSessionId = sessionId

        var boundSuccessfully = false

        // Attempt DynamicsProcessing on Android 9+ (API 28+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val channelCount = 2
                val preEqBandCount = 3
                val builder = DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                    channelCount,
                    true, // preEqInUse
                    preEqBandCount, // preEqBandCount
                    false, // mbcInUse
                    0, // mbcBandCount
                    false, // postEqInUse
                    0, // postEqBandCount
                    true // limiterInUse
                )

                val preEq = DynamicsProcessing.Eq(true, true, preEqBandCount).apply {
                    setBand(0, DynamicsProcessing.EqBand(true, FREQ_RUMBLE_CUT_HZ.toFloat(), getGainOffsetDbForFreqHz(FREQ_RUMBLE_CUT_HZ)))
                    setBand(1, DynamicsProcessing.EqBand(true, FREQ_VOCAL_BODY_HZ.toFloat(), getGainOffsetDbForFreqHz(FREQ_VOCAL_BODY_HZ)))
                    setBand(2, DynamicsProcessing.EqBand(true, FREQ_VOCAL_CLARITY_HZ.toFloat(), getGainOffsetDbForFreqHz(FREQ_VOCAL_CLARITY_HZ)))
                }

                builder.setPreEqAllChannelsTo(preEq)
                val config = builder.build()

                dynamicsProcessing = DynamicsProcessing(0, sessionId, config).apply {
                    enabled = _mode.value != DialogueBoostMode.OFF
                }
                boundSuccessfully = true
                logD(TAG, "Successfully bound DynamicsProcessing to audio session $sessionId")
            } catch (e: Throwable) {
                logW(TAG, "DynamicsProcessing initialization failed on session $sessionId, falling back", e)
                dynamicsProcessing = null
            }
        }

        // Fallback: standalone Equalizer effect if DynamicsProcessing is absent and EqualizerManager is not handling bands
        if (!boundSuccessfully && equalizerManager == null) {
            try {
                standaloneEqualizer = Equalizer(10, sessionId).apply {
                    enabled = _mode.value != DialogueBoostMode.OFF
                    applyEqualizerBands(this, _mode.value)
                }
                logD(TAG, "Successfully bound standalone Equalizer to audio session $sessionId")
            } catch (e: Throwable) {
                logW(TAG, "Standalone Equalizer binding failed on session $sessionId", e)
                standaloneEqualizer = null
            }
        }

        // If EqualizerManager is present, make sure it reflects the current mode
        syncWithEqualizerManager(_mode.value)
    }

    private fun applyModeToHardwareEffects(targetMode: DialogueBoostMode) {
        val isEnabled = targetMode != DialogueBoostMode.OFF

        // Update DynamicsProcessing
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            dynamicsProcessing?.let { dp ->
                try {
                    dp.enabled = isEnabled
                    if (isEnabled) {
                        val preEq = DynamicsProcessing.Eq(true, true, 3).apply {
                            setBand(0, DynamicsProcessing.EqBand(true, FREQ_RUMBLE_CUT_HZ.toFloat(), getGainOffsetDbForFreqHz(FREQ_RUMBLE_CUT_HZ, targetMode)))
                            setBand(1, DynamicsProcessing.EqBand(true, FREQ_VOCAL_BODY_HZ.toFloat(), getGainOffsetDbForFreqHz(FREQ_VOCAL_BODY_HZ, targetMode)))
                            setBand(2, DynamicsProcessing.EqBand(true, FREQ_VOCAL_CLARITY_HZ.toFloat(), getGainOffsetDbForFreqHz(FREQ_VOCAL_CLARITY_HZ, targetMode)))
                        }
                        dp.setPreEqAllChannelsTo(preEq)
                    }
                } catch (e: Throwable) {
                    logW(TAG, "Failed to apply mode to DynamicsProcessing", e)
                }
            }
        }

        // Update standalone Equalizer
        standaloneEqualizer?.let { eq ->
            try {
                eq.enabled = isEnabled
                if (isEnabled) {
                    applyEqualizerBands(eq, targetMode)
                }
            } catch (e: Throwable) {
                logW(TAG, "Failed to apply mode to standalone Equalizer", e)
            }
        }
    }

    private fun applyEqualizerBands(eq: Equalizer, targetMode: DialogueBoostMode) {
        try {
            val numBands = eq.numberOfBands.toInt()
            val minMb = eq.bandLevelRange[0].toInt()
            val maxMb = eq.bandLevelRange[1].toInt()

            for (idx in 0 until numBands) {
                val centerFreqHz = eq.getCenterFreq(idx.toShort()) / 1000
                val offsetMb = getGainOffsetForFreqHz(centerFreqHz, targetMode)
                val clamped = offsetMb.coerceIn(minMb, maxMb).toShort()
                eq.setBandLevel(idx.toShort(), clamped)
            }
        } catch (e: Throwable) {
            logW(TAG, "Error applying bands to Equalizer", e)
        }
    }

    private fun syncWithEqualizerManager(targetMode: DialogueBoostMode) {
        equalizerManager?.let { eqMgr ->
            try {
                val bands = eqMgr.state.value.bands
                val offsets = bands.associate { band ->
                    band.index to getGainOffsetForFreqHz(band.centerFreqHz, targetMode)
                }
                eqMgr.setDialogueBoostOffsets(offsets)
            } catch (e: Throwable) {
                logW(TAG, "Error syncing dialogue boost offsets with EqualizerManager", e)
            }
        }
    }

    private fun releaseAudioEffects() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                dynamicsProcessing?.release()
            } catch (_: Throwable) {}
            dynamicsProcessing = null
        }
        try {
            standaloneEqualizer?.release()
        } catch (_: Throwable) {}
        standaloneEqualizer = null
    }

    /**
     * Releases active audio effect sessions and clears hardware bindings.
     */
    fun release() {
        releaseAudioEffects()
        currentSessionId = 0
    }
}
