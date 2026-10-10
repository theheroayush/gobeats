package com.example.gobeats.feature.nowplaying

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import android.view.HapticFeedbackConstants
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gobeats.core.designsystem.components.formatDuration
import com.example.gobeats.theme.GobeatsAmber
import com.example.gobeats.theme.GobeatsBorderSubtle
import kotlinx.coroutines.delay
import kotlin.math.abs

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@Composable
fun VideoGestureOverlay(
    currentPositionMs: Long,
    durationMs: Long,
    isTouchLocked: Boolean = false,
    isFullscreenCinema: Boolean = false,
    is2xSpeedActive: Boolean = false,
    videoLoudnessBoostPercent: Int = 100,
    onSeek: (Long) -> Unit,
    onDoubleTapLeft: () -> Unit,
    onDoubleTapRight: () -> Unit,
    onSingleTap: () -> Unit,
    onStart2xSpeed: () -> Unit = {},
    onStop2xSpeed: () -> Unit = {},
    onPinchZoomIn: () -> Unit = {},
    onPinchZoomOut: () -> Unit = {},
    onSwipeUpFullscreen: () -> Unit = {},
    onSwipeDownExitFullscreen: () -> Unit = {},
    onUnlockTouch: () -> Unit = {},
    onVideoLoudnessBoostChange: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val activity = remember(context) { context.findActivity() }
    val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }

    var showBrightnessHud by remember { mutableStateOf(false) }
    var brightnessLevel by remember { mutableFloatStateOf(0.5f) }

    var showVolumeHud by remember { mutableStateOf(false) }
    var volumeLevel by remember { mutableFloatStateOf(0.5f) }

    var showSeekHud by remember { mutableStateOf(false) }
    var seekTargetMs by remember { mutableLongStateOf(0L) }
    var seekDeltaMs by remember { mutableLongStateOf(0L) }

    // Multi-tap Seek Accumulation state
    var doubleTapSide by remember { mutableStateOf<String?>(null) } // "LEFT" or "RIGHT"
    var accumulatedSeconds by remember { mutableIntStateOf(10) }
    var lastTapTimeMs by remember { mutableLongStateOf(0L) }

    // Floating Zoom Notification Pill
    var zoomFeedbackText by remember { mutableStateOf<String?>(null) }

    // Touch Lock Hint
    var showLockedHint by remember { mutableStateOf(false) }

    // Ripple wave expansion animation
    val rippleScale = remember { Animatable(0f) }

    // Initialize brightness and volume on load
    LaunchedEffect(activity, videoLoudnessBoostPercent) {
        activity?.window?.attributes?.let { attrs ->
            brightnessLevel = if (attrs.screenBrightness >= 0f) attrs.screenBrightness else 0.5f
        }
        audioManager?.let { am ->
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat()
            val cur = am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
            val baseVol = (cur / max.coerceAtLeast(1f)).coerceIn(0f, 1f)
            volumeLevel = if (videoLoudnessBoostPercent > 100) {
                (videoLoudnessBoostPercent / 100f).coerceIn(1f, 2f)
            } else {
                baseVol
            }
        }
    }

    // Reset window brightness override on dispose
    DisposableEffect(activity) {
        onDispose {
            activity?.window?.let { window ->
                val params = window.attributes
                params.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                window.attributes = params
            }
        }
    }

    // Auto-dismiss HUDs
    LaunchedEffect(showBrightnessHud) {
        if (showBrightnessHud) {
            delay(1800)
            showBrightnessHud = false
        }
    }
    LaunchedEffect(showVolumeHud) {
        if (showVolumeHud) {
            delay(1800)
            showVolumeHud = false
        }
    }
    LaunchedEffect(doubleTapSide, accumulatedSeconds) {
        if (doubleTapSide != null) {
            rippleScale.snapTo(0f)
            rippleScale.animateTo(1f, animationSpec = tween(550))
            delay(250)
            doubleTapSide = null
            accumulatedSeconds = 10
        }
    }
    LaunchedEffect(zoomFeedbackText) {
        if (zoomFeedbackText != null) {
            delay(1500)
            zoomFeedbackText = null
        }
    }
    LaunchedEffect(showLockedHint) {
        if (showLockedHint) {
            delay(2800)
            showLockedHint = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            // Transform Gestures: Pinch to zoom
            .pointerInput(isTouchLocked) {
                if (isTouchLocked) return@pointerInput
                detectTransformGestures { _, _, zoom, _ ->
                    if (zoom > 1.12f) {
                        onPinchZoomIn()
                        zoomFeedbackText = "Zoomed to fill"
                    } else if (zoom < 0.88f) {
                        onPinchZoomOut()
                        zoomFeedbackText = "Original aspect ratio"
                    }
                }
            }
            // Tap & Long-Press (2x Speed Preview) Gestures
            .pointerInput(isTouchLocked, currentPositionMs, durationMs) {
                detectTapGestures(
                    onTap = {
                        if (isTouchLocked) {
                            showLockedHint = true
                        } else {
                            onSingleTap()
                        }
                    },
                    onLongPress = {
                        if (!isTouchLocked) {
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            onStart2xSpeed()
                        }
                    },
                    onPress = {
                        val released = tryAwaitRelease()
                        if (released && is2xSpeedActive) {
                            onStop2xSpeed()
                        }
                    },
                    onDoubleTap = { offset ->
                        if (isTouchLocked) {
                            showLockedHint = true
                            return@detectTapGestures
                        }
                        val now = System.currentTimeMillis()
                        val isLeft = offset.x < size.width / 2
                        val side = if (isLeft) "LEFT" else "RIGHT"

                        if (doubleTapSide == side && (now - lastTapTimeMs) < 800L) {
                            accumulatedSeconds += 10
                        } else {
                            accumulatedSeconds = 10
                        }
                        lastTapTimeMs = now
                        doubleTapSide = side

                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)

                        if (isLeft) {
                            val newPos = (currentPositionMs - accumulatedSeconds * 1000L).coerceAtLeast(0L)
                            onSeek(newPos)
                        } else {
                            val newPos = (currentPositionMs + accumulatedSeconds * 1000L).coerceAtMost(durationMs)
                            onSeek(newPos)
                        }
                    }
                )
            }
            // Drag Gestures: Seek, Volume, Brightness, Swipe up/down fullscreen
            .pointerInput(isTouchLocked, currentPositionMs, durationMs, isFullscreenCinema) {
                if (isTouchLocked) return@pointerInput
                var totalDragX = 0f
                var totalDragY = 0f
                var isHorizontalDrag = false
                var isVerticalDrag = false
                var dragStartedOnLeft = false

                detectDragGestures(
                    onDragStart = { offset ->
                        totalDragX = 0f
                        totalDragY = 0f
                        isHorizontalDrag = false
                        isVerticalDrag = false
                        dragStartedOnLeft = offset.x < size.width / 2
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        totalDragX += dragAmount.x
                        totalDragY += dragAmount.y

                        if (!isHorizontalDrag && !isVerticalDrag) {
                            if (abs(totalDragX) > abs(totalDragY) && abs(totalDragX) > 16f) {
                                isHorizontalDrag = true
                            } else if (abs(totalDragY) > 16f) {
                                isVerticalDrag = true
                            }
                        }

                        if (isHorizontalDrag && durationMs > 0L) {
                            showSeekHud = true
                            val deltaFraction = totalDragX / size.width.coerceAtLeast(1)
                            val delta = (deltaFraction * 90000L).toLong()
                            seekDeltaMs = delta
                            seekTargetMs = (currentPositionMs + delta).coerceIn(0L, durationMs)
                        } else if (isVerticalDrag) {
                            val dragDelta = -dragAmount.y / (size.height * 0.75f)

                            if (dragStartedOnLeft) {
                                showBrightnessHud = true
                                val newBrightness = (brightnessLevel + dragDelta).coerceIn(0.01f, 1f)
                                brightnessLevel = newBrightness
                                activity?.window?.let { window ->
                                    val params = window.attributes
                                    params.screenBrightness = newBrightness
                                    window.attributes = params
                                }
                            } else {
                                showVolumeHud = true
                                audioManager?.let { am ->
                                    val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                    val oldVol = volumeLevel
                                    val newVol = (volumeLevel + dragDelta).coerceIn(0f, 2.0f)
                                    volumeLevel = newVol

                                    // Haptic feedback tick when crossing the 100% booster boundary
                                    if ((oldVol <= 1.0f && newVol > 1.0f) || (oldVol > 1.0f && newVol <= 1.0f)) {
                                        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                    }

                                    if (newVol <= 1.0f) {
                                        val index = (newVol * max).toInt()
                                        am.setStreamVolume(AudioManager.STREAM_MUSIC, index, 0)
                                        onVideoLoudnessBoostChange(100)
                                    } else {
                                        // Hold hardware stream volume at max
                                        am.setStreamVolume(AudioManager.STREAM_MUSIC, max, 0)
                                        val boostPercent = (newVol * 100).toInt().coerceIn(100, 200)
                                        onVideoLoudnessBoostChange(boostPercent)
                                    }
                                }
                            }
                        }
                    },
                    onDragEnd = {
                        if (isHorizontalDrag && showSeekHud) {
                            onSeek(seekTargetMs)
                            showSeekHud = false
                        }
                        // Swipe UP to enter cinema, Swipe DOWN to exit cinema
                        if (isVerticalDrag && abs(totalDragY) > 90f) {
                            if (totalDragY < -90f && !isFullscreenCinema) {
                                onSwipeUpFullscreen()
                            } else if (totalDragY > 90f && isFullscreenCinema) {
                                onSwipeDownExitFullscreen()
                            }
                        }
                    },
                    onDragCancel = {
                        showSeekHud = false
                    }
                )
            }
    ) {
        // Multi-Tap Expanding Ripple & Double-Tap Feedback
        if (doubleTapSide == "LEFT") {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.5f)
                    .align(Alignment.CenterStart)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(GobeatsAmber.copy(alpha = 0.28f * (1f - rippleScale.value)), Color.Transparent),
                            center = Offset(0f, size.height / 2),
                            radius = size.width * (0.4f + 0.8f * rippleScale.value)
                        )
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = Color(0xCC000000),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GobeatsAmber.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(start = 24.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.FastRewind, contentDescription = null, tint = GobeatsAmber, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.height(2.dp))
                        Text("-$accumulatedSeconds sec", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }

        if (doubleTapSide == "RIGHT") {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.5f)
                    .align(Alignment.CenterEnd)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(GobeatsAmber.copy(alpha = 0.28f * (1f - rippleScale.value)), Color.Transparent),
                            center = Offset(size.width, size.height / 2),
                            radius = size.width * (0.4f + 0.8f * rippleScale.value)
                        )
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = Color(0xCC000000),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GobeatsAmber.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(end = 24.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.FastForward, contentDescription = null, tint = GobeatsAmber, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.height(2.dp))
                        Text("+$accumulatedSeconds sec", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }

        // YouTube-style Floating 2x Speed Preview Badge
        AnimatedVisibility(
            visible = is2xSpeedActive,
            enter = fadeIn() + scaleIn(initialScale = 0.85f),
            exit = fadeOut() + scaleOut(targetScale = 0.85f),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 18.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xE60A0A0C),
                border = androidx.compose.foundation.BorderStroke(1.dp, GobeatsAmber)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        tint = GobeatsAmber,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "2.0x >",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        // Floating Pinch Zoom Notification Pill
        AnimatedVisibility(
            visible = zoomFeedbackText != null,
            enter = fadeIn() + scaleIn(initialScale = 0.9f),
            exit = fadeOut() + scaleOut(targetScale = 0.9f),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 20.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xCC000000),
                border = androidx.compose.foundation.BorderStroke(1.dp, GobeatsBorderSubtle)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.ZoomIn, contentDescription = null, tint = GobeatsAmber, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = zoomFeedbackText ?: "",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
        }

        // Touch Lock Overlay & Floating Unlock Pill
        AnimatedVisibility(
            visible = isTouchLocked && showLockedHint,
            enter = fadeIn() + scaleIn(initialScale = 0.9f),
            exit = fadeOut() + scaleOut(targetScale = 0.9f),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0xF2121118),
                border = androidx.compose.foundation.BorderStroke(1.dp, GobeatsAmber),
                modifier = Modifier.clickable { onUnlockTouch() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = GobeatsAmber, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Screen Locked", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Tap to unlock controls", fontSize = 11.sp, color = GobeatsAmber)
                    }
                }
            }
        }

        // Left HUD: Screen Brightness
        AnimatedVisibility(
            visible = showBrightnessHud,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color(0xE6121118),
                border = androidx.compose.foundation.BorderStroke(1.dp, GobeatsBorderSubtle)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Canvas(modifier = Modifier.size(20.dp)) {
                        val center = Offset(size.width / 2, size.height / 2)
                        drawCircle(color = GobeatsAmber, radius = size.width * 0.28f)
                        val rayCount = 8
                        for (i in 0 until rayCount) {
                            val angle = (i * (360.0 / rayCount) * (Math.PI / 180.0)).toFloat()
                            val startX = center.x + kotlin.math.cos(angle) * (size.width * 0.38f)
                            val startY = center.y + kotlin.math.sin(angle) * (size.height * 0.38f)
                            val endX = center.x + kotlin.math.cos(angle) * (size.width * 0.48f)
                            val endY = center.y + kotlin.math.sin(angle) * (size.height * 0.48f)
                            drawLine(
                                color = GobeatsAmber,
                                start = Offset(startX, startY),
                                end = Offset(endX, endY),
                                strokeWidth = 2f,
                                cap = StrokeCap.Round
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Box(
                        modifier = Modifier
                            .width(6.dp)
                            .height(80.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0xFF27272A)),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(brightnessLevel)
                                .clip(RoundedCornerShape(3.dp))
                                .background(GobeatsAmber)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${(brightnessLevel * 100).toInt()}%",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        // Right HUD: Media Volume
        AnimatedVisibility(
            visible = showVolumeHud,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color(0xE6121118),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (volumeLevel > 1.0f) Color(0xFFFF5722) else GobeatsBorderSubtle)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        tint = if (volumeLevel > 1.0f) Color(0xFFFF5722) else GobeatsAmber,
                        modifier = Modifier.size(20.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Box(
                        modifier = Modifier
                            .width(6.dp)
                            .height(80.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0xFF27272A)),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        val barHeightFraction = (volumeLevel / 2f).coerceIn(0f, 1f)
                        val isBoosted = volumeLevel > 1.0f
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(barHeightFraction)
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    if (isBoosted) {
                                        Brush.verticalGradient(
                                            listOf(Color(0xFFFF3D00), GobeatsAmber)
                                        )
                                    } else {
                                        Brush.verticalGradient(
                                            listOf(GobeatsAmber, GobeatsAmber)
                                        )
                                    }
                                )
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    val pct = (volumeLevel * 100).toInt()
                    val textLabel = if (volumeLevel > 1.0f) "$pct%\n[BOOST]" else "$pct%"
                    Text(
                        text = textLabel,
                        fontSize = if (volumeLevel > 1.0f) 8.sp else 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (volumeLevel > 1.0f) Color(0xFFFF7043) else Color.White,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }

        // Center HUD: Fast Scrubbing / Seek Preview
        AnimatedVisibility(
            visible = showSeekHud,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xF00A0A0C),
                border = androidx.compose.foundation.BorderStroke(1.dp, GobeatsBorderSubtle)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = formatDuration(seekTargetMs),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = GobeatsAmber
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "/ ${formatDuration(durationMs)}",
                            fontSize = 13.sp,
                            color = Color(0xFFA1A1AA)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    val deltaSign = if (seekDeltaMs >= 0) "+" else "-"
                    val absDelta = abs(seekDeltaMs)
                    Text(
                        text = "[$deltaSign${formatDuration(absDelta)}]",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (seekDeltaMs >= 0) Color(0xFF34D399) else Color(0xFFF87171)
                    )
                }
            }
        }
    }
}
