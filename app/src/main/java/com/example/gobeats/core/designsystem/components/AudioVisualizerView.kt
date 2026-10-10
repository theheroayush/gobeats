package com.example.gobeats.core.designsystem.components

import android.media.audiofx.Visualizer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.gobeats.theme.GobeatsAmber
import kotlin.math.abs
import kotlin.math.sin

/**
 * 60 FPS Real-time Audio Visualizer with dual-mode architecture:
 * 1. Hardware FFT Engine: Attaches to active audioSessionId via Android AudioFX Visualizer.
 * 2. Procedural Beat-Decay Physics Fallback: Dynamically driven by harmonic wave oscillation
 *    and gravity peak-hold physics, guaranteeing 60fps smoothness with zero crashes when
 *    permissions are absent or on emulators.
 */
@Composable
fun AudioVisualizerView(
    isPlaying: Boolean,
    audioSessionId: Int = 0,
    primaryColor: Color = GobeatsAmber,
    secondaryColor: Color = Color(0xFFFFC107),
    barCount: Int = 32,
    modifier: Modifier = Modifier,
    height: Dp = 36.dp
) {
    val barAmplitudes = remember(barCount) {
        mutableStateListOf<Float>().apply {
            repeat(barCount) { add(0.08f) }
        }
    }
    val peakAmplitudes = remember(barCount) {
        mutableStateListOf<Float>().apply {
            repeat(barCount) { add(0.08f) }
        }
    }

    var hardwareVisualizerActive by remember { mutableStateOf(false) }

    // Hardware Visualizer Lifecycle
    DisposableEffect(audioSessionId, isPlaying) {
        var visualizer: Visualizer? = null
        if (audioSessionId > 0 && isPlaying) {
            try {
                visualizer = Visualizer(audioSessionId).apply {
                    captureSize = Visualizer.getCaptureSizeRange()[0]
                    setDataCaptureListener(
                        object : Visualizer.OnDataCaptureListener {
                            override fun onWaveFormDataCapture(
                                viz: Visualizer?,
                                waveform: ByteArray?,
                                samplingRate: Int
                            ) {}

                            override fun onFftDataCapture(
                                viz: Visualizer?,
                                fft: ByteArray?,
                                samplingRate: Int
                            ) {
                                if (fft != null && fft.isNotEmpty()) {
                                    val binSize = fft.size / (barCount * 2)
                                    for (i in 0 until barCount) {
                                        val idx = (i * binSize * 2).coerceIn(0, fft.size - 2)
                                        val r = fft[idx].toInt()
                                        val im = fft[idx + 1].toInt()
                                        val magnitude = kotlin.math.hypot(r.toDouble(), im.toDouble()).toFloat() / 128f
                                        val current = barAmplitudes.getOrElse(i) { 0f }
                                        val nextVal = (current * 0.4f + magnitude.coerceIn(0.05f, 1f) * 0.6f)
                                        barAmplitudes[i] = nextVal
                                        if (nextVal > peakAmplitudes[i]) {
                                            peakAmplitudes[i] = nextVal
                                        }
                                    }
                                    hardwareVisualizerActive = true
                                }
                            }
                        },
                        Visualizer.getMaxCaptureRate() / 2,
                        false,
                        true
                    )
                    enabled = true
                }
            } catch (_: Exception) {
                // If Visualizer creation fails (missing permission, OEM quirk, emulator), gracefully fallback
                visualizer = null
                hardwareVisualizerActive = false
            }
        }

        onDispose {
            try {
                visualizer?.enabled = false
                visualizer?.release()
            } catch (_: Exception) {}
            hardwareVisualizerActive = false
        }
    }

    // 60 FPS Procedural Simulation & Gravity Peak Decay Loop
    LaunchedEffect(isPlaying, hardwareVisualizerActive) {
        var lastTimeNanos = 0L
        var phase = 0f

        while (true) {
            withFrameNanos { frameTimeNanos ->
                if (lastTimeNanos > 0L) {
                    val dt = (frameTimeNanos - lastTimeNanos) / 1_000_000_000f
                    phase += dt * 5.5f

                    for (i in 0 until barCount) {
                        val current = barAmplitudes.getOrElse(i) { 0.05f }
                        val currentPeak = peakAmplitudes.getOrElse(i) { 0.05f }

                        if (!hardwareVisualizerActive) {
                            if (isPlaying) {
                                // Multi-octave synthetic music spectrum
                                val octave1 = sin(phase * 1.8f + i * 0.28f) * 0.35f
                                val octave2 = sin(phase * 3.2f + i * 0.52f) * 0.25f
                                val octave3 = sin(phase * 0.9f - i * 0.15f) * 0.20f
                                val baseline = 0.12f
                                val target = (baseline + abs(octave1 + octave2 + octave3)).coerceIn(0.06f, 0.96f)

                                // Smooth rise and gradual decay
                                val smoothed = if (target > current) {
                                    current + (target - current) * 0.45f
                                } else {
                                    current * 0.91f + target * 0.09f
                                }
                                barAmplitudes[i] = smoothed

                                // Peak hold physics
                                if (smoothed >= currentPeak) {
                                    peakAmplitudes[i] = smoothed
                                } else {
                                    peakAmplitudes[i] = (currentPeak - 0.015f).coerceAtLeast(smoothed)
                                }
                            } else {
                                // Smoothly fade to idle bars when paused
                                barAmplitudes[i] = (current * 0.85f).coerceAtLeast(0.04f)
                                peakAmplitudes[i] = (currentPeak * 0.85f).coerceAtLeast(0.04f)
                            }
                        } else {
                            // Hardware mode peak decay
                            if (current < currentPeak) {
                                peakAmplitudes[i] = (currentPeak - 0.02f).coerceAtLeast(current)
                            }
                        }
                    }
                }
                lastTimeNanos = frameTimeNanos
            }
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        val totalWidth = size.width
        val canvasHeight = size.height
        val barGap = 2.5.dp.toPx()
        val totalGaps = (barCount - 1) * barGap
        val barWidth = ((totalWidth - totalGaps) / barCount).coerceAtLeast(2.dp.toPx())
        val cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)

        for (i in 0 until barCount) {
            val amp = barAmplitudes.getOrElse(i) { 0.05f }.coerceIn(0.04f, 1.0f)
            val peak = peakAmplitudes.getOrElse(i) { 0.05f }.coerceIn(0.04f, 1.0f)

            val barHeight = (canvasHeight * amp).coerceAtLeast(barWidth)
            val left = i * (barWidth + barGap)
            val top = canvasHeight - barHeight

            // Bar Gradient
            val gradientBrush = Brush.verticalGradient(
                colors = listOf(
                    secondaryColor,
                    primaryColor.copy(alpha = 0.85f)
                ),
                startY = top,
                endY = canvasHeight
            )

            // Draw rounded bar
            drawRoundRect(
                brush = gradientBrush,
                topLeft = Offset(left, top),
                size = Size(barWidth, barHeight),
                cornerRadius = cornerRadius
            )

            // Draw floating peak dot
            if (peak > amp + 0.04f) {
                val peakY = (canvasHeight - (canvasHeight * peak)).coerceAtLeast(1.dp.toPx())
                drawCircle(
                    color = secondaryColor.copy(alpha = 0.9f),
                    radius = barWidth / 2.2f,
                    center = Offset(left + barWidth / 2f, peakY)
                )
            }
        }
    }
}
