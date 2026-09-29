package com.example.gobeats.core.audio

import java.io.File
import java.io.FileInputStream
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

enum class ReplayGainMode(val displayName: String, val description: String) {
    OFF("Off", "Original unadjusted file volume"),
    TRACK("Track Gain", "Normalizes every track to uniform loudness (Best for Shuffle & Playlists)"),
    ALBUM("Album Gain", "Preserves relative volume dynamics across an album"),
    SMART("Smart Leveling", "Uses ReplayGain tags with intelligent mastering-era fallback")
}

data class ReplayGainData(
    val trackGainDb: Float? = null,
    val albumGainDb: Float? = null,
    val trackPeak: Float? = null,
    val albumPeak: Float? = null
) {
    val hasTags: Boolean
        get() = trackGainDb != null || albumGainDb != null
}

data class ReplayGainResult(
    val linearVolume: Float,
    val loudnessBoostMb: Int,
    val appliedGainDb: Float
)

object ReplayGainProcessor {

    private val GAIN_REGEX = Regex("""([+-]?\d+(?:\.\d+)?)""")

    /**
     * Parses a ReplayGain string such as "-6.45 dB" or "+1.80 dB" into a Float in dB.
     */
    fun parseGainDb(raw: String?): Float? {
        if (raw.isNullOrBlank()) return null
        val match = GAIN_REGEX.find(raw.trim()) ?: return null
        return match.groupValues[1].toFloatOrNull()?.coerceIn(-30.0f, 30.0f)
    }

    /**
     * Parses a peak amplitude value such as "0.988553" or "1.054200".
     */
    fun parsePeak(raw: String?): Float? {
        if (raw.isNullOrBlank()) return null
        val match = GAIN_REGEX.find(raw.trim()) ?: return null
        val value = match.groupValues[1].toFloatOrNull() ?: return null
        return if (value > 0f) value.coerceIn(0.01f, 4.0f) else null
    }

    /**
     * Extracts ReplayGain tags from raw header bytes (ID3v2 TXXX or Vorbis/FLAC comments).
     */
    fun extractReplayGainFromBytes(bytes: ByteArray): ReplayGainData {
        if (bytes.isEmpty()) return ReplayGainData()

        // Normalize null bytes and UTF-16 zero padding into spaces so ASCII keys & values remain contiguous
        val sb = StringBuilder(bytes.size)
        for (b in bytes) {
            val c = b.toInt() and 0xFF
            if (c in 32..126) {
                sb.append(c.toChar())
            } else if (c == 0) {
                // Skip UTF-16 BOM / null interleaving if between printable characters, or add separator
                sb.append(' ')
            }
        }
        // Collapse multiple spaces for clean key-value matching, also build a stripped version for UTF-16
        val asciiStripped = buildString(bytes.size) {
            for (b in bytes) {
                val c = b.toInt() and 0xFF
                if (c in 32..126) append(c.toChar())
            }
        }

        val trackGain = findTagValue(asciiStripped, "replaygain_track_gain")
            ?.let { parseGainDb(it) }
        val albumGain = findTagValue(asciiStripped, "replaygain_album_gain")
            ?.let { parseGainDb(it) }
        val trackPeak = findTagValue(asciiStripped, "replaygain_track_peak")
            ?.let { parsePeak(it) }
        val albumPeak = findTagValue(asciiStripped, "replaygain_album_peak")
            ?.let { parsePeak(it) }

        return ReplayGainData(
            trackGainDb = trackGain,
            albumGainDb = albumGain,
            trackPeak = trackPeak,
            albumPeak = albumPeak
        )
    }

    private fun findTagValue(content: String, key: String): String? {
        val index = content.indexOf(key, ignoreCase = true)
        if (index == -1) return null
        val start = (index + key.length).coerceAtMost(content.length)
        val end = (start + 24).coerceAtMost(content.length)
        val window = content.substring(start, end).trimStart('=', ':', ' ')
        return window
    }

    /**
     * Reads up to the first 64KB of an audio file to inspect embedded ReplayGain metadata.
     */
    fun extractReplayGainFromFile(file: File): ReplayGainData {
        if (!file.exists() || !file.canRead()) return ReplayGainData()
        return try {
            FileInputStream(file).use { fis ->
                val maxRead = minOf(file.length(), 65536L).toInt()
                if (maxRead <= 0) return ReplayGainData()
                val buffer = ByteArray(maxRead)
                val read = fis.read(buffer)
                if (read > 0) {
                    extractReplayGainFromBytes(if (read == maxRead) buffer else buffer.copyOf(read))
                } else {
                    ReplayGainData()
                }
            }
        } catch (_: Exception) {
            ReplayGainData()
        }
    }

    /**
     * Estimates target gain adjustment when a track lacks embedded ReplayGain tags.
     * Accounts for mastering era (Loudness War 2000-2015 vs pre-1990 dynamic masters vs modern streaming).
     */
    fun estimateSmartFallbackGainDb(year: Int?, format: String = "MP3"): Float {
        val baseByYear = when {
            year == null || year <= 0 -> -1.2f
            year < 1985 -> +2.4f     // Pre-digital / early CD masters (-16 to -18 LUFS)
            year in 1985..1994 -> +1.5f // Dynamic CD era (-14 to -16 LUFS)
            year in 1995..1999 -> -0.5f // Early compression era
            year in 2000..2014 -> -2.8f // Peak Loudness War era (-6 to -8 LUFS)
            else -> -1.4f            // Modern streaming era (-10 to -12 LUFS)
        }
        // High-res lossless formats often retain slightly more dynamic headroom
        val formatOffset = if (format.equals("FLAC", ignoreCase = true) || format.equals("WAV", ignoreCase = true)) {
            +0.4f
        } else {
            0.0f
        }
        return (baseByYear + formatOffset).coerceIn(-6.0f, 6.0f)
    }

    /**
     * Computes the exact linear volume multiplier [0.05..1.0] and LoudnessEnhancer millibel boost [0..800]
     * for the given ReplayGain data, mode, pre-amp, and peak-clipping protection.
     */
    fun computeGainAdjustment(
        data: ReplayGainData,
        mode: ReplayGainMode,
        preAmpDb: Float = 0.0f,
        preventClipping: Boolean = true,
        fallbackGainDb: Float = 0.0f
    ): ReplayGainResult {
        if (mode == ReplayGainMode.OFF) {
            return ReplayGainResult(
                linearVolume = 1.0f,
                loudnessBoostMb = 0,
                appliedGainDb = 0.0f
            )
        }

        val rawGainDb = when (mode) {
            ReplayGainMode.OFF -> 0.0f
            ReplayGainMode.TRACK -> data.trackGainDb ?: data.albumGainDb ?: fallbackGainDb
            ReplayGainMode.ALBUM -> data.albumGainDb ?: data.trackGainDb ?: fallbackGainDb
            ReplayGainMode.SMART -> data.trackGainDb ?: data.albumGainDb ?: fallbackGainDb
        }

        var targetDb = (rawGainDb + preAmpDb).coerceIn(-18.0f, 12.0f)
        var linearRatio = 10.0.pow(targetDb.toDouble() / 20.0).toFloat()

        if (preventClipping) {
            val relevantPeak = when (mode) {
                ReplayGainMode.ALBUM -> data.albumPeak ?: data.trackPeak
                else -> data.trackPeak ?: data.albumPeak
            } ?: 1.0f

            if (relevantPeak > 0f && linearRatio * relevantPeak > 1.0f) {
                linearRatio = 1.0f / relevantPeak
                targetDb = (20.0 * log10(linearRatio.toDouble())).toFloat()
            }
        }

        return if (targetDb <= 0.0f) {
            // Attenuation: scale player volume smoothly between 0.05f and 1.0f
            ReplayGainResult(
                linearVolume = linearRatio.coerceIn(0.05f, 1.0f),
                loudnessBoostMb = 0,
                appliedGainDb = targetDb
            )
        } else {
            // Positive gain: keep player volume at 1.0f and apply hardware LoudnessEnhancer boost in millibels (1 dB = 100 mB)
            val boostMb = (targetDb * 100.0f).roundToInt().coerceIn(0, 800)
            ReplayGainResult(
                linearVolume = 1.0f,
                loudnessBoostMb = boostMb,
                appliedGainDb = boostMb / 100.0f
            )
        }
    }
}
