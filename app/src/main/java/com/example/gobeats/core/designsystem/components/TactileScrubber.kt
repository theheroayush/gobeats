package com.example.gobeats.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gobeats.theme.GobeatsAmber
import com.example.gobeats.theme.GobeatsBorderSubtle
import com.example.gobeats.theme.GobeatsTextMuted

@Composable
fun TactileScrubber(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(0f) }

    val safeDuration = if (durationMs > 0) durationMs else 1L
    val currentFraction = if (isDragging) dragPosition else (positionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)

    Column(modifier = modifier.fillMaxWidth()) {
        Slider(
            value = currentFraction,
            onValueChange = {
                isDragging = true
                dragPosition = it
            },
            onValueChangeFinished = {
                val targetMs = (dragPosition * safeDuration).toLong()
                onSeek(targetMs)
                isDragging = false
            },
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = GobeatsAmber,
                inactiveTrackColor = GobeatsBorderSubtle
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val displayMs = if (isDragging) (dragPosition * safeDuration).toLong() else positionMs
            Text(
                text = formatDuration(displayMs),
                fontSize = 12.sp,
                color = GobeatsTextMuted
            )
            val remainingMs = (safeDuration - displayMs).coerceAtLeast(0L)
            Text(
                text = "-${formatDuration(remainingMs)}",
                fontSize = 12.sp,
                color = GobeatsTextMuted
            )
        }
    }
}

fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}
