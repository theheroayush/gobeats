package com.example.gobeats.feature.nowplaying

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.example.gobeats.core.designsystem.components.formatDuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.gobeats.core.model.PlaybackUiState
import com.example.gobeats.core.model.VideoAspectMode
import com.example.gobeats.core.model.VideoPlaybackStats
import com.example.gobeats.core.video.DualSubtitleOverlay
import com.example.gobeats.core.video.OfflineSkipIntroDetector
import com.example.gobeats.core.video.SkipIntroPill
import com.example.gobeats.theme.GobeatsAmber
import com.example.gobeats.theme.GobeatsBorderSubtle
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerSurface(
    player: Player?,
    videoWidth: Int = 0,
    videoHeight: Int = 0,
    aspectMode: VideoAspectMode = VideoAspectMode.FIT,
    currentPositionMs: Long = 0L,
    durationMs: Long = 0L,
    isTouchLocked: Boolean = false,
    is2xSpeedActive: Boolean = false,
    showStatsForNerds: Boolean = false,
    videoStats: VideoPlaybackStats? = null,
    modifier: Modifier = Modifier,
    onSeek: (Long) -> Unit = {},
    onCycleAspectMode: () -> Unit = {},
    onToggleFullscreen: () -> Unit = {},
    onEnterPiP: () -> Unit = {},
    onOpenAudioSubtitles: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onDoubleTapLeft: () -> Unit = {},
    onDoubleTapRight: () -> Unit = {},
    onStart2xSpeed: () -> Unit = {},
    onStop2xSpeed: () -> Unit = {},
    onPinchZoomIn: () -> Unit = {},
    onPinchZoomOut: () -> Unit = {},
    onSetTouchLocked: (Boolean) -> Unit = {},
    onCloseStats: () -> Unit = {},
    videoLoudnessBoostPercent: Int = 100,
    resumePrompt: com.example.gobeats.core.model.VideoResumePrompt? = null,
    playbackState: PlaybackUiState? = null,
    onVideoLoudnessBoostChange: (Int) -> Unit = {},
    onResumeSavedPosition: () -> Unit = {},
    onDismissResumePrompt: () -> Unit = {},
    onSkipIntro: () -> Unit = {}
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(20.dp)
    var showControlsOverlay by remember { mutableStateOf(false) }

    // Auto-dim controls overlay after 3.2 seconds
    LaunchedEffect(showControlsOverlay) {
        if (showControlsOverlay) {
            delay(3200)
            showControlsOverlay = false
        }
    }

    // Determine target container aspect ratio based on native video dimensions
    val computedAspectRatio = remember(videoWidth, videoHeight, aspectMode) {
        when (aspectMode) {
            VideoAspectMode.RATIO_16_9 -> 16f / 9f
            VideoAspectMode.FILL_CROP -> if (videoWidth > 0 && videoHeight > 0) {
                (videoWidth.toFloat() / videoHeight).coerceIn(0.75f, 1.85f)
            } else {
                16f / 9f
            }
            VideoAspectMode.STRETCH, VideoAspectMode.FIT -> if (videoWidth > 0 && videoHeight > 0) {
                (videoWidth.toFloat() / videoHeight).coerceIn(0.75f, 1.85f)
            } else {
                16f / 9f
            }
        }
    }

    val resizeMode = when (aspectMode) {
        VideoAspectMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        VideoAspectMode.FILL_CROP -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        VideoAspectMode.STRETCH -> AspectRatioFrameLayout.RESIZE_MODE_FILL
        VideoAspectMode.RATIO_16_9 -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
    }

    val aspectModeLabel = when (aspectMode) {
        VideoAspectMode.FIT -> "Fit"
        VideoAspectMode.FILL_CROP -> "Fill"
        VideoAspectMode.STRETCH -> "Stretch"
        VideoAspectMode.RATIO_16_9 -> "16:9"
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(computedAspectRatio)
            .clip(shape)
            .background(Color.Black)
            .border(1.dp, GobeatsBorderSubtle, shape),
        contentAlignment = Alignment.Center
    ) {
        val playerView = remember {
            val view = android.view.LayoutInflater.from(context).inflate(
                com.example.gobeats.R.layout.view_media3_texture_player,
                null
            ) as PlayerView
            view.apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                useController = false
                setShutterBackgroundColor(android.graphics.Color.BLACK)
                keepScreenOn = true
            }
        }

        DisposableEffect(player) {
            playerView.player = player
            onDispose {
                if (playerView.player == player && player?.isPlaying != true) {
                    playerView.player = null
                }
            }
        }

        AndroidView(
            factory = { playerView },
            update = { view ->
                if (view.player != player) {
                    view.player = player
                }
                view.resizeMode = resizeMode
            },
            modifier = Modifier.fillMaxSize()
        )

        // YouTube Touch & Gesture Overlay
        VideoGestureOverlay(
            currentPositionMs = currentPositionMs,
            durationMs = durationMs,
            isTouchLocked = isTouchLocked,
            isFullscreenCinema = false,
            is2xSpeedActive = is2xSpeedActive,
            videoLoudnessBoostPercent = videoLoudnessBoostPercent,
            onSeek = onSeek,
            onDoubleTapLeft = onDoubleTapLeft,
            onDoubleTapRight = onDoubleTapRight,
            onSingleTap = {
                if (!isTouchLocked) {
                    showControlsOverlay = !showControlsOverlay
                }
            },
            onStart2xSpeed = onStart2xSpeed,
            onStop2xSpeed = onStop2xSpeed,
            onPinchZoomIn = onPinchZoomIn,
            onPinchZoomOut = onPinchZoomOut,
            onSwipeUpFullscreen = onToggleFullscreen,
            onUnlockTouch = { onSetTouchLocked(false) },
            onVideoLoudnessBoostChange = onVideoLoudnessBoostChange,
            modifier = Modifier.fillMaxSize()
        )

        // Top Video Action Bar (HUD Overlay on single tap)
        AnimatedVisibility(
            visible = showControlsOverlay && !isTouchLocked,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xCC000000), Color.Transparent)
                        )
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Aspect Mode Pill
                    Surface(
                        modifier = Modifier.clickable { onCycleAspectMode() },
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0x9927272A),
                        border = androidx.compose.foundation.BorderStroke(1.dp, GobeatsBorderSubtle)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.AspectRatio,
                                contentDescription = null,
                                tint = GobeatsAmber,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = aspectModeLabel,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    // Action Icons: Subtitle, Settings, PiP, Fullscreen
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Settings Gear
                        Surface(
                            modifier = Modifier.clickable { onOpenSettings() },
                            shape = CircleShape,
                            color = Color(0x9927272A)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Video Settings",
                                tint = Color.White,
                                modifier = Modifier
                                    .padding(6.dp)
                                    .size(16.dp)
                            )
                        }

                        Spacer(Modifier.width(8.dp))

                        // Subtitle/Audio selector
                        Surface(
                            modifier = Modifier.clickable { onOpenAudioSubtitles() },
                            shape = CircleShape,
                            color = Color(0x9927272A)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChatBubbleOutline,
                                contentDescription = "Audio & Subtitles",
                                tint = Color.White,
                                modifier = Modifier
                                    .padding(6.dp)
                                    .size(16.dp)
                            )
                        }

                        Spacer(Modifier.width(8.dp))

                        // PiP button
                        Surface(
                            modifier = Modifier.clickable { onEnterPiP() },
                            shape = CircleShape,
                            color = Color(0x9927272A)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PictureInPicture,
                                contentDescription = "Picture in Picture",
                                tint = Color.White,
                                modifier = Modifier
                                    .padding(6.dp)
                                    .size(16.dp)
                            )
                        }

                        Spacer(Modifier.width(8.dp))

                        // Fullscreen cinema mode button
                        Surface(
                            modifier = Modifier.clickable { onToggleFullscreen() },
                            shape = CircleShape,
                            color = Color(0x9927272A)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fullscreen,
                                contentDescription = "Fullscreen Cinema",
                                tint = GobeatsAmber,
                                modifier = Modifier
                                    .padding(6.dp)
                                    .size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // Resume Playback Prompt Banner Overlay
        resumePrompt?.let { prompt ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xF2121118),
                    border = BorderStroke(1.dp, GobeatsAmber.copy(alpha = 0.8f)),
                    shadowElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Resume from ${formatDuration(prompt.positionMs)}?",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.width(10.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = GobeatsAmber,
                            modifier = Modifier.clickable { onResumeSavedPosition() }
                        ) {
                            Text(
                                text = "Resume",
                                color = Color.Black,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        IconButton(
                            onClick = onDismissResumePrompt,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = Color(0xFFA1A1AA),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }

        // Dual Subtitle Overlay
        if (playbackState != null) {
            DualSubtitleOverlay(
                state = playbackState,
                isControlsVisible = showControlsOverlay && !isTouchLocked,
                modifier = Modifier.align(Alignment.BottomCenter)
            )

            // Skip Intro Action Pill
            playbackState.activeSkipIntroSegment?.let { introSeg ->
                SkipIntroPill(
                    visible = playbackState.isSkipIntroVisible && !isTouchLocked,
                    segment = introSeg,
                    onSkip = {
                        onSeek(OfflineSkipIntroDetector.getSkipTargetMs(introSeg))
                        onSkipIntro()
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = if (showControlsOverlay) 64.dp else 16.dp)
                )
            }
        }

        // Live Stats for Nerds Overlay
        if (showStatsForNerds) {
            VideoStatsOverlay(
                stats = videoStats,
                onClose = onCloseStats,
                modifier = Modifier.align(Alignment.TopStart)
            )
        }
    }
}
