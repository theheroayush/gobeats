package com.example.gobeats.feature.nowplaying

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ClosedCaptionDisabled
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.gobeats.core.designsystem.components.formatDuration
import com.example.gobeats.core.model.PlaybackUiState
import com.example.gobeats.core.model.VideoAspectMode
import com.example.gobeats.theme.GobeatsAmber
import com.example.gobeats.theme.GobeatsBorderSubtle
import kotlinx.coroutines.delay

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@OptIn(UnstableApi::class)
@Composable
fun FullscreenVideoPlayer(
    player: Player?,
    state: PlaybackUiState,
    onSeek: (Long) -> Unit,
    onTogglePlayPause: () -> Unit,
    onRewind10s: () -> Unit,
    onFastForward10s: () -> Unit,
    onCycleAspectMode: () -> Unit,
    onExitFullscreen: () -> Unit,
    onOpenSettings: () -> Unit,
    onStart2xSpeed: () -> Unit,
    onStop2xSpeed: () -> Unit,
    onPinchZoomIn: () -> Unit,
    onPinchZoomOut: () -> Unit,
    onSetTouchLocked: (Boolean) -> Unit,
    onToggleSubtitles: () -> Unit,
    onCloseStats: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    var showControlsOverlay by remember { mutableStateOf(false) }

    // Immersive Mode: Hide system status bar and navigation bar in cinema mode
    DisposableEffect(activity) {
        activity?.let { act ->
            val window = act.window
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            activity?.let { act ->
                val window = act.window
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Auto-dim controls overlay after 3.2 seconds
    LaunchedEffect(showControlsOverlay) {
        if (showControlsOverlay) {
            delay(3200)
            showControlsOverlay = false
        }
    }

    val resizeMode = when (state.videoAspectMode) {
        VideoAspectMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        VideoAspectMode.FILL_CROP -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        VideoAspectMode.STRETCH -> AspectRatioFrameLayout.RESIZE_MODE_FILL
        VideoAspectMode.RATIO_16_9 -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
    }

    val aspectLabel = when (state.videoAspectMode) {
        VideoAspectMode.FIT -> "Fit"
        VideoAspectMode.FILL_CROP -> "Fill"
        VideoAspectMode.STRETCH -> "Stretch"
        VideoAspectMode.RATIO_16_9 -> "16:9"
    }

    val currentTrack = state.currentTrack

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        val playerView = remember {
            PlayerView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                useController = false
                setBackgroundColor(android.graphics.Color.BLACK)
                setShutterBackgroundColor(android.graphics.Color.BLACK)
                keepScreenOn = true
            }
        }

        DisposableEffect(player) {
            playerView.player = player
            onDispose {
                playerView.player = null
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

        // YouTube Gestures Overlay (2x speed, multi-tap seek, pinch zoom, touch lock)
        VideoGestureOverlay(
            currentPositionMs = state.currentPositionMs,
            durationMs = state.durationMs,
            isTouchLocked = state.isTouchLocked,
            isFullscreenCinema = true,
            is2xSpeedActive = state.is2xSpeedPreviewActive,
            onSeek = onSeek,
            onDoubleTapLeft = onRewind10s,
            onDoubleTapRight = onFastForward10s,
            onSingleTap = {
                if (!state.isTouchLocked) {
                    showControlsOverlay = !showControlsOverlay
                }
            },
            onStart2xSpeed = onStart2xSpeed,
            onStop2xSpeed = onStop2xSpeed,
            onPinchZoomIn = onPinchZoomIn,
            onPinchZoomOut = onPinchZoomOut,
            onSwipeDownExitFullscreen = onExitFullscreen,
            onUnlockTouch = { onSetTouchLocked(false) },
            modifier = Modifier.fillMaxSize()
        )

        // Top Cinema Action Bar
        AnimatedVisibility(
            visible = showControlsOverlay && !state.isTouchLocked,
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
                        Brush.verticalGradient(listOf(Color(0xE6000000), Color.Transparent))
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back / Minimize Button & Title
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onExitFullscreen) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Exit Fullscreen",
                                tint = Color.White
                            )
                        }

                        Spacer(Modifier.width(8.dp))

                        Column {
                            Text(
                                text = currentTrack?.title ?: "Video",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            currentTrack?.artistName?.takeIf { it.isNotBlank() }?.let { artist ->
                                Text(
                                    text = artist,
                                    fontSize = 11.sp,
                                    color = Color(0xFFA1A1AA),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Action Icons: Quality Pill, CC, Settings, Lock
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Quality Badge Pill
                        val activeQuality = state.videoQualityOptions.find { it.isSelected }?.id?.uppercase()
                            ?: state.videoQualityOptions.find { it.isAuto }?.label?.substringAfter("(")?.substringBefore(")")
                            ?: "AUTO"
                        Surface(
                            modifier = Modifier.clickable { onOpenSettings() },
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0x9927272A),
                            border = BorderStroke(1.dp, GobeatsBorderSubtle)
                        ) {
                            Text(
                                text = activeQuality,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = GobeatsAmber,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        Spacer(Modifier.width(8.dp))

                        // CC (Subtitles) toggle
                        IconButton(onClick = onToggleSubtitles) {
                            Icon(
                                imageVector = if (state.selectedSubtitleTrackIndex != -1) Icons.Default.ClosedCaption else Icons.Default.ClosedCaptionDisabled,
                                contentDescription = "Subtitles",
                                tint = if (state.selectedSubtitleTrackIndex != -1) GobeatsAmber else Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Settings Gear
                        IconButton(onClick = onOpenSettings) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Lock Screen Button
                        IconButton(onClick = {
                            onSetTouchLocked(true)
                            showControlsOverlay = false
                        }) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Lock Screen",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        // Center Playback Controls (Play/Pause, Rewind, Fast Forward)
        AnimatedVisibility(
            visible = showControlsOverlay && !state.isTouchLocked,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(36.dp)
            ) {
                // Rewind 10s
                Surface(
                    shape = CircleShape,
                    color = Color(0x99000000),
                    border = BorderStroke(1.dp, GobeatsBorderSubtle),
                    modifier = Modifier
                        .size(52.dp)
                        .clickable { onRewind10s() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.FastRewind, contentDescription = "Rewind 10s", tint = Color.White, modifier = Modifier.size(26.dp))
                    }
                }

                // Play / Pause
                Surface(
                    shape = CircleShape,
                    color = GobeatsAmber,
                    modifier = Modifier
                        .size(68.dp)
                        .clickable { onTogglePlayPause() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (state.isPlaying) "Pause" else "Play",
                            tint = Color.Black,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                // Fast Forward 10s
                Surface(
                    shape = CircleShape,
                    color = Color(0x99000000),
                    border = BorderStroke(1.dp, GobeatsBorderSubtle),
                    modifier = Modifier
                        .size(52.dp)
                        .clickable { onFastForward10s() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.FastForward, contentDescription = "Fast Forward 10s", tint = Color.White, modifier = Modifier.size(26.dp))
                    }
                }
            }
        }

        // Bottom Cinema Scrub Bar & Aspect Ratio
        AnimatedVisibility(
            visible = showControlsOverlay && !state.isTouchLocked,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000)))
                    )
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Precision Scrubber Slider
                    var isUserDraggingSlider by remember { mutableStateOf(false) }
                    var sliderDragFraction by remember { mutableFloatStateOf(0f) }

                    val activeProgress = if (isUserDraggingSlider) {
                        sliderDragFraction
                    } else if (state.durationMs > 0) {
                        (state.currentPositionMs.toFloat() / state.durationMs).coerceIn(0f, 1f)
                    } else 0f

                    Slider(
                        value = activeProgress,
                        onValueChange = { frac ->
                            isUserDraggingSlider = true
                            sliderDragFraction = frac
                        },
                        onValueChangeFinished = {
                            val targetMs = (sliderDragFraction * state.durationMs).toLong()
                            onSeek(targetMs)
                            isUserDraggingSlider = false
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = GobeatsAmber,
                            activeTrackColor = GobeatsAmber,
                            inactiveTrackColor = Color(0x55555555)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Time Readout & Aspect Ratio / Fullscreen Exit
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${formatDuration(state.currentPositionMs)} / ${formatDuration(state.durationMs)}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Aspect Ratio Pill
                            Surface(
                                modifier = Modifier.clickable { onCycleAspectMode() },
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0x9927272A),
                                border = BorderStroke(1.dp, GobeatsBorderSubtle)
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
                                        text = aspectLabel,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }

                            Spacer(Modifier.width(10.dp))

                            // Exit Fullscreen Button
                            IconButton(onClick = onExitFullscreen) {
                                Icon(
                                    imageVector = Icons.Default.FullscreenExit,
                                    contentDescription = "Exit Fullscreen",
                                    tint = GobeatsAmber,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Live "Stats for Nerds" Overlay
        if (state.showStatsForNerds) {
            VideoStatsOverlay(
                stats = state.videoStats,
                onClose = onCloseStats,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(top = 16.dp, start = 16.dp)
            )
        }
    }
}
