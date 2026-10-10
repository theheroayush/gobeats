package com.example.gobeats.feature.onboarding

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CompassCalibration
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gobeats.core.designsystem.components.GobeatsPrimaryButton
import com.example.gobeats.core.designsystem.components.GobeatsSecondaryButton
import com.example.gobeats.core.designsystem.components.VinylDiscHero
import com.example.gobeats.theme.GobeatsBorderSubtle
import com.example.gobeats.theme.GobeatsCharcoal
import com.example.gobeats.theme.GobeatsObsidian
import com.example.gobeats.theme.GobeatsSurface
import com.example.gobeats.theme.GobeatsSurfaceElevated
import com.example.gobeats.theme.GobeatsTextMuted
import com.example.gobeats.theme.GobeatsTextPrimary
import com.example.gobeats.theme.GobeatsTextSecondary

/**
 * Screen S01: Onboarding Welcome Screen
 * 1:1 Pixel-Perfect translation of Mockup 1:
 * - "gobeats" wordmark + "YOUR MUSIC. YOUR RULES."
 * - Top-right "Skip"
 * - Hyper-realistic VinylDiscHero with ambient amber glow
 * - 4 circular feature pills
 * - Bone-white Primary Pill Button ("Get started ->")
 * - Secondary dark button ("I'll explore first")
 * - Footer: "Free. Open source. Built for music lovers."
 */
@Composable
fun WelcomeScreen(
    onGetStarted: () -> Unit,
    onExploreFirst: () -> Unit,
    onSkip: () -> Unit
) {
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
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header: Wordmark & Skip
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    com.example.gobeats.core.designsystem.components.OnboardingProgressBar(
                        currentStep = 1,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    Text(
                        text = "gobeats",
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        color = GobeatsTextPrimary,
                        letterSpacing = (-0.5).sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "YOUR MUSIC. YOUR RULES.",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = GobeatsTextMuted,
                        letterSpacing = 1.8.sp
                    )
                }

                Text(
                    text = "Skip",
                    fontSize = 14.sp,
                    color = GobeatsTextMuted,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .clickable { onSkip() }
                        .padding(8.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Vinyl Record Hero
            VinylDiscHero(
                size = 230.dp,
                isPlaying = true,
                showGlow = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Statement & Subtitle
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "A home for all your music",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = GobeatsTextPrimary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Local files, your servers, and more.\nBeautifully organized. Always yours.",
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = GobeatsTextSecondary,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 4 Circular Feature Items
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                FeaturePillItem(
                    icon = Icons.Default.MusicNote,
                    title = "Play\nanything",
                    subtitle = "All your music\nsources"
                )
                FeaturePillItem(
                    icon = Icons.Default.Favorite,
                    title = "Make it\nyours",
                    subtitle = "Playlists, favorites\nand more"
                )
                FeaturePillItem(
                    icon = Icons.Default.Devices,
                    title = "Stay in\nsync",
                    subtitle = "PC to Android\nwireless transfer"
                )
                FeaturePillItem(
                    icon = Icons.Default.CompassCalibration,
                    title = "Discover\nmore",
                    subtitle = "Optional streaming\nintegrations"
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Action Buttons
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                GobeatsPrimaryButton(
                    text = "Get started",
                    onClick = onGetStarted
                )

                Spacer(modifier = Modifier.height(12.dp))

                GobeatsSecondaryButton(
                    text = "I'll explore first",
                    onClick = onExploreFirst
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Free. Open source. Built for music lovers.",
                    fontSize = 12.sp,
                    color = GobeatsTextMuted,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun FeaturePillItem(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(76.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(GobeatsSurfaceElevated, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = GobeatsTextPrimary,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = title,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = GobeatsTextPrimary,
            textAlign = TextAlign.Center,
            lineHeight = 14.sp
        )

        Spacer(modifier = Modifier.height(3.dp))

        Text(
            text = subtitle,
            fontSize = 9.sp,
            color = GobeatsTextMuted,
            textAlign = TextAlign.Center,
            lineHeight = 12.sp
        )
    }
}
