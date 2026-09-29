package com.example.gobeats.core.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gobeats.core.model.Track
import com.example.gobeats.theme.GobeatsAmber
import com.example.gobeats.theme.GobeatsAmberGlow
import com.example.gobeats.theme.GobeatsBorderSubtle
import com.example.gobeats.theme.GobeatsObsidian
import com.example.gobeats.theme.GobeatsSurface
import com.example.gobeats.theme.GobeatsSurfaceElevated
import com.example.gobeats.theme.GobeatsTextPrimary
import kotlinx.coroutines.delay

@Composable
fun ArtworkCarousel(
    currentTrack: Track,
    previousTrack: Track?,
    nextTrack: Track?,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    onRewind10s: () -> Unit = {},
    onFastForward10s: () -> Unit = {},
    onArtworkClick: () -> Unit = {}
) {
    var dragAccumulator by remember { mutableFloatStateOf(0f) }
    var showRewindIndicator by remember { mutableStateOf(false) }
    var showForwardIndicator by remember { mutableStateOf(false) }

    LaunchedEffect(showRewindIndicator) {
        if (showRewindIndicator) {
            delay(650)
            showRewindIndicator = false
        }
    }

    LaunchedEffect(showForwardIndicator) {
        if (showForwardIndicator) {
            delay(650)
            showForwardIndicator = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(290.dp)
            .pointerInput(currentTrack.id) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { _, dragAmount ->
                        dragAccumulator += dragAmount
                    },
                    onDragEnd = {
                        if (dragAccumulator > 60f) {
                            onPrevious()
                        } else if (dragAccumulator < -60f) {
                            onNext()
                        }
                        dragAccumulator = 0f
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Left Peeking Previous Track Card
        if (previousTrack != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(44.dp)
                    .height(240.dp)
                    .clip(RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp))
                    .background(Color(0xFF16151A))
                    .clickable { onPrevious() }
            ) {
                // Subtle mountain silhouette texture
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(
                        brush = Brush.verticalGradient(
                            listOf(Color(0xFF282430), Color(0xFF100F14))
                        )
                    )
                }
            }
        }

        // Center Hero Artwork Card (Matching Mountain Sunrise from Mockup 3)
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF18171E),
            border = androidx.compose.foundation.BorderStroke(1.dp, GobeatsBorderSubtle),
            modifier = Modifier
                .size(280.dp)
                .shadow(24.dp, RoundedCornerShape(24.dp), spotColor = GobeatsAmberGlow, ambientColor = GobeatsObsidian)
                .pointerInput(currentTrack.id) {
                    detectTapGestures(
                        onTap = { onArtworkClick() },
                        onDoubleTap = { offset ->
                            if (offset.x < size.width / 2) {
                                showRewindIndicator = true
                                onRewind10s()
                            } else {
                                showForwardIndicator = true
                                onFastForward10s()
                            }
                        }
                    )
                }
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Generative Alpine Mountain Sunset with Crescent Moon
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    // Deep atmospheric twilight sky
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF1E2638), // Midnight blue
                                Color(0xFF4C3652), // Dusky rose
                                Color(0xFF8A535A), // Sunset peach
                                Color(0xFF452D3E)
                            )
                        )
                    )

                    // Moon
                    val moonCenter = Offset(w * 0.72f, h * 0.22f)
                    drawCircle(
                        color = Color(0xFFFFF0D4),
                        radius = 12f,
                        center = moonCenter
                    )

                    // Mountain Peak Layer 1 (Back snowy ridges)
                    val backRidge = Path().apply {
                        moveTo(0f, h * 0.55f)
                        lineTo(w * 0.25f, h * 0.40f)
                        lineTo(w * 0.45f, h * 0.32f)
                        lineTo(w * 0.65f, h * 0.48f)
                        lineTo(w * 0.85f, h * 0.36f)
                        lineTo(w, h * 0.52f)
                        lineTo(w, h)
                        lineTo(0f, h)
                        close()
                    }
                    drawPath(
                        path = backRidge,
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xFFE8D3D6), Color(0xFF4B344B)),
                            startY = h * 0.32f,
                            endY = h
                        )
                    )

                    // Mountain Peak Layer 2 (Center sharp sunlit alpine ridge)
                    val mainRidge = Path().apply {
                        moveTo(w * 0.1f, h * 0.7f)
                        lineTo(w * 0.42f, h * 0.28f) // High peak
                        lineTo(w * 0.68f, h * 0.55f)
                        lineTo(w * 0.95f, h * 0.38f)
                        lineTo(w, h * 0.45f)
                        lineTo(w, h)
                        lineTo(0f, h)
                        close()
                    }
                    drawPath(
                        path = mainRidge,
                        brush = Brush.linearGradient(
                            colors = listOf(Color(0xFFFFF6ED), Color(0xFFE08D70), Color(0xFF261928)),
                            start = Offset(w * 0.42f, h * 0.28f),
                            end = Offset(w * 0.8f, h)
                        )
                    )

                    // Foreground Shadow Ridge
                    val frontRidge = Path().apply {
                        moveTo(0f, h * 0.72f)
                        lineTo(w * 0.35f, h * 0.52f)
                        lineTo(w * 0.62f, h * 0.85f)
                        lineTo(w, h * 0.65f)
                        lineTo(w, h)
                        lineTo(0f, h)
                        close()
                    }
                    drawPath(
                        path = frontRidge,
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xFF2F2432), Color(0xFF131016)),
                            startY = h * 0.52f,
                            endY = h
                        )
                    )
                }

                // Album Track Title Overlay on artwork (matching Karan Aujla art style)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "HIGHER\nTHAN\nYESTERDAY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.4.sp,
                        lineHeight = 15.sp,
                        color = Color.White.copy(alpha = 0.85f)
                    )

                    Text(
                        text = "A BRIGHTER\nTOMORROW",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.8.sp,
                        lineHeight = 11.sp,
                        color = Color.White.copy(alpha = 0.75f),
                        modifier = Modifier.align(Alignment.End),
                        textAlign = androidx.compose.ui.text.style.TextAlign.End
                    )
                }

                // Rewind 10s Feedback Overlay
                if (showRewindIndicator) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Surface(
                            shape = RoundedCornerShape(topEnd = 30.dp, bottomEnd = 30.dp),
                            color = GobeatsAmber.copy(alpha = 0.85f),
                            modifier = Modifier.padding(start = 16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.FastRewind, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("-10s", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }

                // Fast Forward 10s Feedback Overlay
                if (showForwardIndicator) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Surface(
                            shape = RoundedCornerShape(topStart = 30.dp, bottomStart = 30.dp),
                            color = GobeatsAmber.copy(alpha = 0.85f),
                            modifier = Modifier.padding(end = 16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("+10s", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.Default.FastForward, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }

        // Right Peeking Next Track Card
        if (nextTrack != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(44.dp)
                    .height(240.dp)
                    .clip(RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp))
                    .background(Color(0xFF16151A))
                    .clickable { onNext() }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(
                        brush = Brush.verticalGradient(
                            listOf(Color(0xFF24222B), Color(0xFF0F0E13))
                        )
                    )
                }
            }
        }
    }
}
