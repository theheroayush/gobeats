package com.example.gobeats.core.audio

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.net.Uri
import com.example.gobeats.core.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class EqualizerBand(
    val index: Int,
    val centerFreqHz: Int,
    val levelMb: Int, // millibels, typically -1500 to +1500 (-15dB to +15dB)
    val minLevelMb: Int = -1200,
    val maxLevelMb: Int = 1200
)

data class EqualizerState(
    val isEnabled: Boolean = false,
    val currentPreset: String = "Studio Reference",
    val bands: List<EqualizerBand> = emptyList(),
    val bassBoostStrength: Int = 0, // 0 to 1000
    val virtualizerStrength: Int = 0, // 0 to 1000 (3D Spatializer)
    val isLoudnessEnabled: Boolean = true,
    val loudnessGainMb: Int = 250, // 0 to 800 mB (+2.5dB clean dynamic headroom)
    val activeAutoEqProfile: AutoEqProfile? = null,
    val replayGainMode: ReplayGainMode = ReplayGainMode.SMART,
    val replayGainPreAmpDb: Float = 0.0f,
    val replayGainPreventClipping: Boolean = true,
    val currentAppliedReplayGainDb: Float = 0.0f
)

class EqualizerManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("gobeats_equalizer", Context.MODE_PRIVATE)

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var currentSessionId: Int = 0

    private val _state = MutableStateFlow(loadInitialState())
    val state: StateFlow<EqualizerState> = _state.asStateFlow()

    companion object {
        val PRESETS = mapOf(
            "Studio Reference" to listOf(0, 0, 0, 0, 0),
            "Vocal Clarity" to listOf(-150, 0, 350, 250, 100),
            "Warm Analog" to listOf(250, 200, 50, 0, -100),
            "Deep Bass Punch" to listOf(500, 300, 0, 150, 200),
            "Electronic / EDM" to listOf(400, 200, -100, 250, 400),
            "Acoustic / Live" to listOf(150, 150, 200, 200, 250),
            "Flat" to listOf(0, 0, 0, 0, 0),
            "Custom" to listOf(0, 0, 0, 0, 0)
        )
    }

    private fun loadInitialState(): EqualizerState {
        val isEnabled = prefs.getBoolean("eq_enabled", false)
        val preset = prefs.getString("eq_preset", "Studio Reference") ?: "Studio Reference"
        val bass = prefs.getInt("eq_bass", 0)
        val virtualizer = prefs.getInt("eq_virtualizer", 0)
        val isLoudness = prefs.getBoolean("eq_loudness_enabled", true)
        val loudnessGain = prefs.getInt("eq_loudness_gain", 250)
        val autoEqId = prefs.getString("eq_autoeq_id", null)
        val rgModeStr = prefs.getString("rg_mode", ReplayGainMode.SMART.name) ?: ReplayGainMode.SMART.name
        val rgMode = try { ReplayGainMode.valueOf(rgModeStr) } catch (_: Exception) { ReplayGainMode.SMART }
        val rgPreAmp = prefs.getFloat("rg_preamp_db", 0.0f)
        val rgClip = prefs.getBoolean("rg_prevent_clipping", true)

        val defaultFreqs = listOf(60, 230, 910, 3600, 14000)
        val savedLevels = PRESETS[preset] ?: listOf(0, 0, 0, 0, 0)

        val bands = defaultFreqs.mapIndexed { idx, freq ->
            val level = prefs.getInt("eq_band_$idx", savedLevels.getOrElse(idx) { 0 })
            EqualizerBand(
                index = idx,
                centerFreqHz = freq,
                levelMb = level
            )
        }

        return EqualizerState(
            isEnabled = isEnabled,
            currentPreset = preset,
            bands = bands,
            bassBoostStrength = bass,
            virtualizerStrength = virtualizer,
            isLoudnessEnabled = isLoudness,
            loudnessGainMb = loudnessGain,
            activeAutoEqProfile = AutoEqCatalog.findById(autoEqId),
            replayGainMode = rgMode,
            replayGainPreAmpDb = rgPreAmp,
            replayGainPreventClipping = rgClip
        )
    }

    fun bindAudioSession(sessionId: Int) {
        if (sessionId <= 0 || sessionId == currentSessionId) return
        release()
        currentSessionId = sessionId

        try {
            equalizer = Equalizer(0, sessionId).apply {
                enabled = _state.value.isEnabled
                val numBands = numberOfBands.toInt()
                val minMb = bandLevelRange[0].toInt()
                val maxMb = bandLevelRange[1].toInt()

                val updatedBands = (0 until numBands).map { idx ->
                    val freq = getCenterFreq(idx.toShort()) / 1000
                    val saved = _state.value.bands.find { it.index == idx }?.levelMb ?: 0
                    val clamped = saved.coerceIn(minMb, maxMb).toShort()
                    setBandLevel(idx.toShort(), clamped)

                    EqualizerBand(
                        index = idx,
                        centerFreqHz = freq,
                        levelMb = clamped.toInt(),
                        minLevelMb = minMb,
                        maxLevelMb = maxMb
                    )
                }

                _state.value = _state.value.copy(bands = updatedBands)
            }

            bassBoost = BassBoost(0, sessionId).apply {
                enabled = _state.value.isEnabled
                if (strengthSupported) {
                    setStrength(_state.value.bassBoostStrength.toShort())
                }
            }

            virtualizer = Virtualizer(0, sessionId).apply {
                enabled = _state.value.isEnabled
                if (strengthSupported) {
                    setStrength(_state.value.virtualizerStrength.toShort())
                }
            }

            loudnessEnhancer = LoudnessEnhancer(sessionId).apply {
                enabled = _state.value.isLoudnessEnabled
                setTargetGain(_state.value.loudnessGainMb)
            }
        } catch (_: Exception) {
            // AudioEffect binding may fail on some emulators; state remains functional in UI
        }
    }

    fun setBandLevel(bandIndex: Int, levelMb: Int) {
        val current = _state.value
        val updated = current.bands.map { band ->
            if (band.index == bandIndex) {
                val clamped = levelMb.coerceIn(band.minLevelMb, band.maxLevelMb)
                try {
                    equalizer?.setBandLevel(bandIndex.toShort(), clamped.toShort())
                } catch (_: Exception) {}
                prefs.edit().putInt("eq_band_$bandIndex", clamped).apply()
                band.copy(levelMb = clamped)
            } else band
        }

        prefs.edit().putString("eq_preset", "Custom").remove("eq_autoeq_id").apply()
        _state.value = current.copy(bands = updated, currentPreset = "Custom", activeAutoEqProfile = null)
    }

    fun applyPreset(presetName: String) {
        val levels = PRESETS[presetName] ?: return
        val current = _state.value

        val updated = current.bands.mapIndexed { idx, band ->
            val level = levels.getOrElse(idx) { 0 }
            val clamped = level.coerceIn(band.minLevelMb, band.maxLevelMb)
            try {
                equalizer?.setBandLevel(idx.toShort(), clamped.toShort())
            } catch (_: Exception) {}
            prefs.edit().putInt("eq_band_$idx", clamped).apply()
            band.copy(levelMb = clamped)
        }

        prefs.edit().putString("eq_preset", presetName).remove("eq_autoeq_id").apply()
        _state.value = current.copy(bands = updated, currentPreset = presetName, activeAutoEqProfile = null)
    }

    fun applyAutoEqProfile(profile: AutoEqProfile?) {
        if (profile == null) {
            prefs.edit().remove("eq_autoeq_id").apply()
            applyPreset("Studio Reference")
            return
        }

        val current = _state.value
        val updatedBands = current.bands.mapIndexed { idx, band ->
            val targetMb = AutoEqCatalog.interpolateGainMb(profile, band.centerFreqHz)
            val clamped = targetMb.coerceIn(band.minLevelMb, band.maxLevelMb)
            try {
                equalizer?.setBandLevel(idx.toShort(), clamped.toShort())
            } catch (_: Exception) {}
            prefs.edit().putInt("eq_band_$idx", clamped).apply()
            band.copy(levelMb = clamped)
        }

        if (!current.isEnabled) {
            toggleEnabled(true)
        }
        setBassBoost(profile.recommendedBassBoost)
        setVirtualizer(profile.recommendedVirtualizer)

        val presetLabel = "AutoEQ: ${profile.model}"
        prefs.edit()
            .putString("eq_autoeq_id", profile.id)
            .putString("eq_preset", presetLabel)
            .apply()

        _state.value = _state.value.copy(
            isEnabled = true,
            bands = updatedBands,
            currentPreset = presetLabel,
            activeAutoEqProfile = profile,
            bassBoostStrength = profile.recommendedBassBoost,
            virtualizerStrength = profile.recommendedVirtualizer
        )
    }

    fun setReplayGainMode(mode: ReplayGainMode) {
        prefs.edit().putString("rg_mode", mode.name).apply()
        _state.value = _state.value.copy(replayGainMode = mode)
    }

    fun setReplayGainPreAmp(preAmpDb: Float) {
        val clamped = preAmpDb.coerceIn(-6.0f, 6.0f)
        prefs.edit().putFloat("rg_preamp_db", clamped).apply()
        _state.value = _state.value.copy(replayGainPreAmpDb = clamped)
    }

    fun setReplayGainPreventClipping(prevent: Boolean) {
        prefs.edit().putBoolean("rg_prevent_clipping", prevent).apply()
        _state.value = _state.value.copy(replayGainPreventClipping = prevent)
    }

    fun computeTrackReplayGain(track: Track?): ReplayGainResult {
        val current = _state.value
        if (track == null || current.replayGainMode == ReplayGainMode.OFF) {
            _state.value = current.copy(currentAppliedReplayGainDb = 0.0f)
            return ReplayGainResult(1.0f, 0, 0.0f)
        }

        val filePath = if (track.fileUri.startsWith("file://")) {
            Uri.parse(track.fileUri).path
        } else if (track.fileUri.startsWith("/")) {
            track.fileUri
        } else {
            null
        }

        val rgData = if (filePath != null) {
            ReplayGainProcessor.extractReplayGainFromFile(File(filePath))
        } else {
            ReplayGainData()
        }

        val fallbackDb = ReplayGainProcessor.estimateSmartFallbackGainDb(track.year, track.format)
        val result = ReplayGainProcessor.computeGainAdjustment(
            data = rgData,
            mode = current.replayGainMode,
            preAmpDb = current.replayGainPreAmpDb,
            preventClipping = current.replayGainPreventClipping,
            fallbackGainDb = fallbackDb
        )

        try {
            val totalLoudnessMb = (current.loudnessGainMb + result.loudnessBoostMb).coerceIn(0, 1000)
            loudnessEnhancer?.setTargetGain(totalLoudnessMb)
        } catch (_: Exception) {}

        _state.value = current.copy(currentAppliedReplayGainDb = result.appliedGainDb)
        return result
    }

    fun setBassBoost(strength: Int) {
        val clamped = strength.coerceIn(0, 1000)
        try {
            bassBoost?.let {
                if (it.strengthSupported) {
                    it.setStrength(clamped.toShort())
                }
            }
        } catch (_: Exception) {}
        prefs.edit().putInt("eq_bass", clamped).apply()
        _state.value = _state.value.copy(bassBoostStrength = clamped)
    }

    fun setVirtualizer(strength: Int) {
        val clamped = strength.coerceIn(0, 1000)
        try {
            virtualizer?.let {
                if (it.strengthSupported) {
                    it.setStrength(clamped.toShort())
                }
            }
        } catch (_: Exception) {}
        prefs.edit().putInt("eq_virtualizer", clamped).apply()
        _state.value = _state.value.copy(virtualizerStrength = clamped)
    }

    fun toggleEnabled(enabled: Boolean) {
        try {
            equalizer?.enabled = enabled
            bassBoost?.enabled = enabled
            virtualizer?.enabled = enabled
        } catch (_: Exception) {}
        prefs.edit().putBoolean("eq_enabled", enabled).apply()
        _state.value = _state.value.copy(isEnabled = enabled)
    }

    fun toggleLoudness(enabled: Boolean) {
        try {
            loudnessEnhancer?.enabled = enabled
        } catch (_: Exception) {}
        prefs.edit().putBoolean("eq_loudness_enabled", enabled).apply()
        _state.value = _state.value.copy(isLoudnessEnabled = enabled)
    }

    fun setLoudnessGain(gainMb: Int) {
        val clamped = gainMb.coerceIn(0, 800)
        try {
            loudnessEnhancer?.setTargetGain(clamped)
        } catch (_: Exception) {}
        prefs.edit().putInt("eq_loudness_gain", clamped).apply()
        _state.value = _state.value.copy(loudnessGainMb = clamped)
    }

    private var videoBoostGainMb: Int = 0

    fun setVideoLoudnessBoostGainMb(gainMb: Int) {
        val clamped = gainMb.coerceIn(0, 1500)
        videoBoostGainMb = clamped
        try {
            if (clamped > 0) {
                loudnessEnhancer?.enabled = true
                loudnessEnhancer?.setTargetGain(clamped)
            } else {
                if (_state.value.isLoudnessEnabled) {
                    loudnessEnhancer?.enabled = true
                    loudnessEnhancer?.setTargetGain(_state.value.loudnessGainMb)
                } else {
                    loudnessEnhancer?.enabled = false
                    loudnessEnhancer?.setTargetGain(0)
                }
            }
        } catch (_: Exception) {}
    }

    fun getVideoLoudnessBoostGainMb(): Int = videoBoostGainMb

    fun release() {
        try {
            equalizer?.release()
            bassBoost?.release()
            virtualizer?.release()
            loudnessEnhancer?.release()
        } catch (_: Exception) {}
        equalizer = null
        bassBoost = null
        virtualizer = null
        loudnessEnhancer = null
        currentSessionId = 0
    }
}
