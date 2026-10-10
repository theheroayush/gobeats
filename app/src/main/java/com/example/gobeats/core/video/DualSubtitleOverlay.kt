package com.example.gobeats.core.video

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gobeats.core.model.PlaybackUiState

// Color design tokens for dual subtitles
private val PrimaryAmberColor = Color(0xFFF59E0B) // Bold Amber/Yellow #F59E0B
private val SecondaryCyanWhiteColor = Color(0xFFE0F2FE) // Crisp White/Cyan #E0F2FE
private val PrimaryPillBackground = Color(0xE609090B) // High-contrast dark pill #09090B with 90% alpha
private val SecondaryPillBackground = Color(0xB318181B) // Subtle dark pill #18181B with 70% alpha
private val PrimaryBorderColor = Color(0x40F59E0B) // 25% subtle amber border

/**
 * Jetpack Compose Dual Subtitle Overlay component rendered on top of the video player.
 *
 * - Displays Primary Subtitle at bottom center in bold Amber/Yellow (#F59E0B) with high-contrast pill and shadow.
 * - Displays Secondary Subtitle stacked directly above Primary in crisp White/Cyan (#E0F2FE).
 * - Smoothly animates appearance, updates, and fading using [AnimatedVisibility] and [AnimatedContent].
 * - Responsive: dynamically offsets its position so it never overlaps the video scrubber or player controls.
 */
@Composable
fun DualSubtitleOverlay(
    state: PlaybackUiState,
    isControlsVisible: Boolean = false,
    modifier: Modifier = Modifier,
    customBottomPadding: Dp? = null
) {
    DualSubtitleOverlay(
        primaryText = state.activePrimarySubtitleText,
        secondaryText = state.activeSecondarySubtitleText,
        isControlsVisible = isControlsVisible,
        modifier = modifier,
        customBottomPadding = customBottomPadding
    )
}

/**
 * Direct text overload for [DualSubtitleOverlay].
 */
@Composable
fun DualSubtitleOverlay(
    primaryText: String?,
    secondaryText: String?,
    isControlsVisible: Boolean = false,
    modifier: Modifier = Modifier,
    customBottomPadding: Dp? = null
) {
    val cleanPrimary = primaryText?.trim()?.takeIf { it.isNotEmpty() }
    val cleanSecondary = secondaryText?.trim()?.takeIf { it.isNotEmpty() }
    val hasContent = cleanPrimary != null || cleanSecondary != null

    // Responsively lift subtitles above the scrubber bar when video player controls are visible
    val targetBottomPadding = customBottomPadding ?: if (isControlsVisible) {
        108.dp // High enough to avoid the scrub slider and duration readout
    } else {
        38.dp // Comfortable resting position above navigation bars
    }

    val animatedBottomPadding by animateDpAsState(
        targetValue = targetBottomPadding,
        animationSpec = tween(durationMillis = 220),
        label = "subtitleBottomPaddingAnim"
    )

    AnimatedVisibility(
        visible = hasContent,
        enter = fadeIn(animationSpec = tween(150)),
        exit = fadeOut(animationSpec = tween(150)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = animatedBottomPadding, start = 16.dp, end = 16.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.widthIn(max = 680.dp)
            ) {
                // Secondary Subtitle (Stacked directly ABOVE Primary)
                if (cleanSecondary != null) {
                    AnimatedContent(
                        targetState = cleanSecondary,
                        transitionSpec = {
                            fadeIn(tween(140)) togetherWith fadeOut(tween(140))
                        },
                        label = "secondarySubtitleTextAnim"
                    ) { targetText ->
                        SecondarySubtitlePill(text = targetText)
                    }
                }

                // Primary Subtitle (Bottom Center, prominent)
                if (cleanPrimary != null) {
                    AnimatedContent(
                        targetState = cleanPrimary,
                        transitionSpec = {
                            fadeIn(tween(140)) togetherWith fadeOut(tween(140))
                        },
                        label = "primarySubtitleTextAnim"
                    ) { targetText ->
                        PrimarySubtitlePill(text = targetText)
                    }
                }
            }
        }
    }
}

/**
 * Primary subtitle pill: Bold Amber/Yellow (#F59E0B), 18sp, black outline shadow, rounded dark pill.
 */
@Composable
private fun PrimarySubtitlePill(
    text: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = PrimaryPillBackground,
        border = BorderStroke(1.dp, PrimaryBorderColor),
        shadowElevation = 4.dp,
        modifier = modifier
    ) {
        Text(
            text = text,
            color = PrimaryAmberColor,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 24.sp,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(
                shadow = Shadow(
                    color = Color.Black,
                    offset = Offset(0f, 2f),
                    blurRadius = 4f
                )
            ),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        )
    }
}

/**
 * Secondary subtitle pill: Crisp White/Cyan (#E0F2FE), 14sp, regular/medium weight, subtle dark pill.
 */
@Composable
private fun SecondarySubtitlePill(
    text: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = SecondaryPillBackground,
        border = BorderStroke(0.5.dp, Color(0x33E0F2FE)),
        shadowElevation = 2.dp,
        modifier = modifier
    ) {
        Text(
            text = text,
            color = SecondaryCyanWhiteColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 19.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(
                shadow = Shadow(
                    color = Color.Black,
                    offset = Offset(0f, 1f),
                    blurRadius = 3f
                )
            ),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}
