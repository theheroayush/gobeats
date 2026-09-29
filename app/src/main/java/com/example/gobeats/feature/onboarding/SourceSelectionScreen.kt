package com.example.gobeats.feature.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gobeats.core.designsystem.components.GobeatsPrimaryButton
import com.example.gobeats.core.designsystem.components.SourceOptionCard
import com.example.gobeats.theme.GobeatsAmber
import com.example.gobeats.theme.GobeatsAmberGlow
import com.example.gobeats.theme.GobeatsBorderSubtle
import com.example.gobeats.theme.GobeatsCharcoal
import com.example.gobeats.theme.GobeatsObsidian
import com.example.gobeats.theme.GobeatsSurface
import com.example.gobeats.theme.GobeatsTextMuted
import com.example.gobeats.theme.GobeatsTextPrimary
import com.example.gobeats.theme.GobeatsTextSecondary

/**
 * Screen S02: Source Selection Screen
 * 1:1 Pixel-Perfect translation of Mockup 2:
 * - 4-stage segmented top indicator
 * - "Where's your music?"
 * - Interactive source cards (This phone, Connect computer, Personal server, Streaming)
 * - Trust banner "Your music stays yours"
 * - Primary "Continue ->" CTA
 * - Bottom vinyl disc curve peeking upward
 */
@Composable
fun SourceSelectionScreen(
    onContinue: () -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit
) {
    var selectedSource by remember { mutableStateOf("phone") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        GobeatsObsidian,
                        GobeatsCharcoal,
                        GobeatsObsidian
                    )
                )
            )
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Navigation Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = GobeatsTextPrimary,
                    modifier = Modifier
                        .size(24.dp)
                        .clickable { onBack() }
                )

                // 4-Stage Segmented Progress Bar
                Row(
                    modifier = Modifier.width(110.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .background(Color.White, RoundedCornerShape(2.dp))
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .background(GobeatsBorderSubtle, RoundedCornerShape(2.dp))
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .background(GobeatsBorderSubtle, RoundedCornerShape(2.dp))
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .background(GobeatsBorderSubtle, RoundedCornerShape(2.dp))
                    )
                }

                Text(
                    text = "Skip",
                    fontSize = 14.sp,
                    color = GobeatsTextMuted,
                    modifier = Modifier
                        .clickable { onSkip() }
                        .padding(4.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Subtitle Brand
            Text(
                text = "gobeats",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = GobeatsTextPrimary,
                letterSpacing = (-0.3).sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Header Title
            Text(
                text = "Where's your music?",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = GobeatsTextPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Choose your music sources. You can add more\nlater in Settings.",
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = GobeatsTextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Source Cards
            SourceOptionCard(
                title = "This phone",
                description = "Scan music from your Android device (storage, SD card, etc.).",
                icon = Icons.Default.Folder,
                isSelected = selectedSource == "phone",
                onClick = { selectedSource = "phone" }
            )

            Spacer(modifier = Modifier.height(12.dp))

            SourceOptionCard(
                title = "Connect a computer",
                description = "Wirelessly transfer music from your Windows PC with gobeats Connect.",
                icon = Icons.Default.Laptop,
                badgeText = "Recommended",
                isSelected = selectedSource == "computer",
                onClick = { selectedSource = "computer" }
            )

            Spacer(modifier = Modifier.height(12.dp))

            SourceOptionCard(
                title = "Connect a personal server",
                description = "Access your music from Jellyfin, Navidrome, Plex, or other compatible servers.",
                icon = Icons.Default.Dns,
                isSelected = selectedSource == "server",
                onClick = { selectedSource = "server" }
            )

            Spacer(modifier = Modifier.height(12.dp))

            SourceOptionCard(
                title = "Streaming integrations",
                description = "Connect optional streaming services for discovery and more.",
                icon = Icons.Default.Cloud,
                isSelected = selectedSource == "streaming",
                onClick = { selectedSource = "streaming" }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Trust / Privacy Banner Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = GobeatsSurface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Privacy",
                        tint = GobeatsTextSecondary,
                        modifier = Modifier
                            .size(20.dp)
                            .padding(top = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Your music stays yours",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = GobeatsTextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "We only access the music sources you choose. No account is required for local playback, and your data stays on your device unless you enable a service.",
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = GobeatsTextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Primary Button
            GobeatsPrimaryButton(
                text = "Continue",
                onClick = onContinue
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Your music. Your rules.",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = GobeatsTextMuted,
                letterSpacing = 1.2.sp
            )

            Spacer(modifier = Modifier.height(12.dp))
        }

        // Bottom vinyl curve peeking in from below (reproducing Mockup 2 subtle visual motif)
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .align(Alignment.BottomCenter)
        ) {
            val center = Offset(size.width / 2f, size.height + 140f)
            val radius = 170f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(GobeatsAmberGlow.copy(alpha = 0.25f), Color.Transparent),
                    center = center,
                    radius = radius
                ),
                center = center,
                radius = radius
            )
            drawCircle(
                color = GobeatsAmber.copy(alpha = 0.35f),
                center = center,
                radius = radius,
                style = Stroke(width = 1.5f)
            )
        }
    }
}
