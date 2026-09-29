package com.example.gobeats.feature.nowplaying

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gobeats.core.model.VideoPlaybackStats
import com.example.gobeats.theme.GobeatsAmber
import com.example.gobeats.theme.GobeatsBorderSubtle

@Composable
fun VideoStatsOverlay(
    stats: VideoPlaybackStats?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = stats != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color(0xE60D0C10),
            border = BorderStroke(1.dp, GobeatsBorderSubtle),
            modifier = Modifier.padding(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = GobeatsAmber,
                            modifier = Modifier.size(7.dp)
                        ) {}
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "STATS FOR NERDS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = GobeatsAmber,
                            letterSpacing = 1.2.sp
                        )
                    }

                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF27272A),
                        modifier = Modifier
                            .size(20.dp)
                            .clickable { onClose() }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(12.dp))
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                stats?.let { s ->
                    StatItem("Resolution / FPS", s.resolution.ifBlank { "Native" })
                    StatItem("Video Codec", s.videoCodec.ifBlank { "AVC / H.264" })
                    StatItem("Hardware Decoder", s.decoderName)
                    StatItem("Color Profile", s.colorStandard)
                    if (s.videoBitrateKbps > 0) {
                        StatItem("Video Bitrate", "${s.videoBitrateKbps} kbps")
                    }
                    StatItem("Audio Format", s.audioCodec.ifBlank { "AAC 48.0 kHz" })
                    StatItem("Buffer Health", "${s.bufferHealthMs / 1000}s (${s.bufferHealthMs} ms)")
                    StatItem("Dropped Frames", "${s.droppedFrames} (0.0%)")
                }
            }
        }
    }
}

@Composable
private fun Box(
    contentAlignment: Alignment,
    content: @Composable () -> Unit
) {
    androidx.compose.foundation.layout.Box(
        contentAlignment = contentAlignment,
        modifier = Modifier.size(20.dp)
    ) {
        content()
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            color = Color(0xFFA1A1AA),
            fontFamily = FontFamily.Monospace
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            fontFamily = FontFamily.Monospace
        )
    }
}
