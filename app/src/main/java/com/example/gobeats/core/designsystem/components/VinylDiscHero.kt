package com.example.gobeats.core.designsystem.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.gobeats.theme.GobeatsAmber
import com.example.gobeats.theme.GobeatsAmberDark
import com.example.gobeats.theme.GobeatsAmberGlow
import com.example.gobeats.theme.GobeatsObsidian

/**
 * Vinyl Record Visualizer drawn with pure Compose Canvas.
 * Accurately reproduces the physical vinyl grooves, center audio waveform, and warm back-lighting glow from Mockup 1.
 */
@Composable
fun VinylDiscHero(
    modifier: Modifier = Modifier,
    size: Dp = 260.dp,
    isPlaying: Boolean = false,
    showGlow: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "vinylSpin")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 18000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "discAngle"
    )

    Box(
        modifier = modifier
            .size(size)
            .aspectRatio(1f),
        contentAlignment = Alignment.Center
    ) {
        // Ambient warm back-glow (reflecting amber sunrise)
        if (showGlow) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.toPx() / 2f, size.toPx() / 2f)
                val radius = size.toPx() / 1.8f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            GobeatsAmberGlow.copy(alpha = 0.45f),
                            GobeatsAmberGlow.copy(alpha = 0.15f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = radius
                    ),
                    center = center,
                    radius = radius
                )
            }
        }

        // The Vinyl Record Disc
        Canvas(
            modifier = Modifier
                .fillMaxSize(0.92f)
                .rotate(if (isPlaying) rotation else 0f)
                .shadow(16.dp, CircleShape, spotColor = GobeatsAmberDark, ambientColor = GobeatsObsidian)
        ) {
            val center = Offset(size.toPx() / 2f, size.toPx() / 2f)
            val outerRadius = size.toPx() * 0.44f

            // 1. Base Vinyl Polymer Body (Deep sheen gradient)
            drawCircle(
                brush = Brush.sweepGradient(
                    listOf(
                        Color(0xFF161618),
                        Color(0xFF28282E),
                        Color(0xFF141416),
                        Color(0xFF2C2C32),
                        Color(0xFF161618)
                    ),
                    center = center
                ),
                radius = outerRadius,
                center = center
            )

            // 2. Concentric Grooves (Simulating physical vinyl micro-grooves)
            val grooveCount = 22
            val minGrooveRadius = outerRadius * 0.42f
            val step = (outerRadius - minGrooveRadius) / grooveCount

            for (i in 0 until grooveCount) {
                val currentRadius = minGrooveRadius + (i * step)
                val alpha = if (i % 3 == 0) 0.35f else 0.18f
                drawCircle(
                    color = Color.White.copy(alpha = alpha),
                    radius = currentRadius,
                    center = center,
                    style = Stroke(width = if (i % 4 == 0) 1.2f else 0.6f)
                )
            }

            // 3. Center Label Area
            val labelRadius = outerRadius * 0.36f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF1C1C20), Color(0xFF121214)),
                    center = center,
                    radius = labelRadius
                ),
                radius = labelRadius,
                center = center
            )
            drawCircle(
                color = GobeatsAmber.copy(alpha = 0.7f),
                radius = labelRadius,
                center = center,
                style = Stroke(width = 1.5f)
            )

            // 4. Center Audio Waveform Bars (Gobeats Signature Icon)
            val barWidth = 3.5f
            val barGap = 6f
            val barHeights = listOf(14f, 26f, 38f, 26f, 14f)
            val totalWidth = (barHeights.size * barWidth) + ((barHeights.size - 1) * barGap)
            var startX = center.x - (totalWidth / 2f)

            for (height in barHeights) {
                drawRoundRect(
                    color = GobeatsAmber.copy(alpha = 0.9f),
                    topLeft = Offset(startX, center.y - (height / 2f)),
                    size = androidx.compose.ui.geometry.Size(barWidth, height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f, 2f)
                )
                startX += (barWidth + barGap)
            }

            // 5. Spindle hole
            drawCircle(
                color = GobeatsObsidian,
                radius = labelRadius * 0.12f,
                center = center
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.2f),
                radius = labelRadius * 0.12f,
                center = center,
                style = Stroke(width = 0.8f)
            )
        }
    }
}
