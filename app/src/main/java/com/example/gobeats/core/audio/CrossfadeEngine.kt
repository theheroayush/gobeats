package com.example.gobeats.core.audio

import com.example.gobeats.core.model.RepeatMode
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Audiophile Crossfade and Transition Engine for GoBeats.
 * Computes equal-power (constant perceived loudness) volume curves,
 * linear curves, transition boundaries, and progress metrics.
 */
object CrossfadeEngine {

    /**
     * Computes the (volOut, volIn) equal-power volume pair for a given transition progress [0.0..1.0].
     * Equal power property: volOut^2 + volIn^2 == 1.0 (prevents center-point volume dip).
     */
    fun computeEqualPowerVolumes(progress: Float): Pair<Float, Float> {
        val p = progress.coerceIn(0.0f, 1.0f)
        val rad = (p * (PI / 2.0)).toFloat()
        val volOut = cos(rad)
        val volIn = sin(rad)
        return volOut to volIn
    }

    /**
     * Computes linear volume pair for linear crossfade curve: volOut = 1 - p, volIn = p.
     */
    fun computeLinearVolumes(progress: Float): Pair<Float, Float> {
        val p = progress.coerceIn(0.0f, 1.0f)
        return (1.0f - p) to p
    }

    /**
     * Determines whether crossfade volume envelope should be triggered for the current track.
     */
    fun shouldTriggerCrossfade(
        currentPosMs: Long,
        durationMs: Long,
        crossfadeSec: Int,
        queueIndex: Int,
        queueSize: Int,
        repeatMode: RepeatMode
    ): Boolean {
        if (crossfadeSec <= 0) return false
        val remainingMs = durationMs - currentPosMs
        val crossfadeWindowMs = crossfadeSec * 1000L
        if (remainingMs > crossfadeWindowMs || remainingMs <= 0) return false

        val isLastTrack = queueIndex >= queueSize - 1
        return !isLastTrack || repeatMode == RepeatMode.ALL
    }

    /**
     * Calculates the normalized progress (0.0f to 1.0f) of the crossfade transition.
     */
    fun calculateProgress(
        currentPosMs: Long,
        durationMs: Long,
        crossfadeSec: Int
    ): Float {
        if (crossfadeSec <= 0) return 0.0f
        val remainingMs = (durationMs - currentPosMs).coerceAtLeast(0L)
        val windowMs = crossfadeSec * 1000L
        if (windowMs <= 0L) return 0.0f
        val elapsedInWindow = (windowMs - remainingMs).coerceIn(0L, windowMs)
        return (elapsedInWindow.toFloat() / windowMs.toFloat()).coerceIn(0.0f, 1.0f)
    }
}
