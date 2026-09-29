package com.example.gobeats.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gobeats.theme.GobeatsSurfaceElevated
import com.example.gobeats.theme.GobeatsTextMuted
import com.example.gobeats.theme.GobeatsTextPrimary

@Composable
fun QualityBadgesRow(
    format: String = "FLAC",
    isLossless: Boolean = true,
    isHiRes: Boolean = true,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Format Pill (FLAC / MP3)
        Box(
            modifier = Modifier
                .background(GobeatsSurfaceElevated, RoundedCornerShape(percent = 50))
                .padding(horizontal = 10.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = format.uppercase(),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = GobeatsTextPrimary
            )
        }

        // Lossless Pill
        if (isLossless) {
            Box(
                modifier = Modifier
                    .background(Color(0xFF2C1929), RoundedCornerShape(percent = 50))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Lossless",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFE879F9)
                )
            }
        }

        // Hi-Res Pill with waveform
        if (isHiRes) {
            Box(
                modifier = Modifier
                    .background(GobeatsSurfaceElevated, RoundedCornerShape(percent = 50))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = GobeatsTextMuted,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Hi-Res",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = GobeatsTextPrimary
                    )
                }
            }
        }
    }
}
