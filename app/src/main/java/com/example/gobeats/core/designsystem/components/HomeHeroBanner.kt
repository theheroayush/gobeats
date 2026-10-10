package com.example.gobeats.core.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gobeats.theme.GobeatsAmber
import com.example.gobeats.theme.GobeatsAmberGlow
import com.example.gobeats.theme.GobeatsBorderSubtle
import com.example.gobeats.theme.GobeatsTextMuted
import com.example.gobeats.theme.GobeatsTextPrimary
import com.example.gobeats.theme.GobeatsTextSecondary
import java.util.Calendar

@Composable
fun HomeHeroBanner(
    userName: String = "",
    modifier: Modifier = Modifier
) {
    val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greetingPrefix = when (currentHour) {
        in 4..11 -> "GOOD MORNING"
        in 12..17 -> "GOOD AFTERNOON"
        else -> "GOOD EVENING"
    }
    val greeting = if (userName.isNotBlank()) "$greetingPrefix, ${userName.uppercase()}" else greetingPrefix

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFF141417),
        border = androidx.compose.foundation.BorderStroke(1.dp, GobeatsBorderSubtle),
        modifier = modifier
            .fillMaxWidth()
            .height(160.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Background Canvas: Atmospheric Sunset Dunes with glowing celestial sun
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                // Sun glow
                val sunCenter = Offset(w * 0.68f, h * 0.38f)
                val sunRadius = 38f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFFFD89E),
                            Color(0xFFE5A93C).copy(alpha = 0.85f),
                            GobeatsAmberGlow.copy(alpha = 0.35f),
                            Color.Transparent
                        ),
                        center = sunCenter,
                        radius = sunRadius * 2.8f
                    ),
                    center = sunCenter,
                    radius = sunRadius * 2.8f
                )
                drawCircle(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFFFFE8C2), Color(0xFFE5A93C)),
                        startY = sunCenter.y - sunRadius,
                        endY = sunCenter.y + sunRadius
                    ),
                    center = sunCenter,
                    radius = sunRadius
                )

                // Foreground Dune 1
                val dune1 = Path().apply {
                    moveTo(0f, h * 0.65f)
                    cubicTo(w * 0.3f, h * 0.5f, w * 0.6f, h * 0.72f, w, h * 0.45f)
                    lineTo(w, h)
                    lineTo(0f, h)
                    close()
                }
                drawPath(
                    path = dune1,
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF222026), Color(0xFF131316)),
                        startY = h * 0.45f,
                        endY = h
                    )
                )

                // Foreground Dune 2
                val dune2 = Path().apply {
                    moveTo(0f, h * 0.78f)
                    cubicTo(w * 0.4f, h * 0.85f, w * 0.75f, h * 0.62f, w, h * 0.75f)
                    lineTo(w, h)
                    lineTo(0f, h)
                    close()
                }
                drawPath(
                    path = dune2,
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF1B1A20), Color(0xFF0F0E11)),
                        startY = h * 0.6f,
                        endY = h
                    )
                )
            }

            // Foreground typography content
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 22.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = greeting,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = GobeatsAmber,
                        letterSpacing = 1.6.sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Music makes\na brighter day",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 28.sp,
                        color = GobeatsTextPrimary
                    )
                }

                // Philosophy quote
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Listen.\nExplore.\nBe you.",
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        fontWeight = FontWeight.Normal,
                        color = GobeatsTextSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.End
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .width(28.dp)
                            .height(1.5.dp)
                            .background(GobeatsAmber)
                    )
                }
            }
        }
    }
}
