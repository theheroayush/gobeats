package com.example.gobeats.feature.nowplaying

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.example.gobeats.core.model.PlaybackUiState
import com.example.gobeats.theme.GobeatsAmber
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Draggable in-app floating video miniplayer with magnetic edge snapping,
 * play/pause and close controls, and tap-to-expand.
 */
@OptIn(UnstableApi::class)
@Composable
fun FloatingVideoMiniplayer(
    player: Player?,
    state: PlaybackUiState,
    onExpand: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val cardWidthDp = 208.dp
    val cardHeightDp = 117.dp // 16:9 ratio
    val marginDp = 16.dp
    val topMarginDp = 56.dp
    val bottomMarginDp = 92.dp // Sits safely above the navigation bar

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val parentWidthPx = with(density) { maxWidth.toPx() }
        val parentHeightPx = with(density) { maxHeight.toPx() }
        val cardWidthPx = with(density) { cardWidthDp.toPx() }
        val cardHeightPx = with(density) { cardHeightDp.toPx() }
        val marginPx = with(density) { marginDp.toPx() }
        val topMarginPx = with(density) { topMarginDp.toPx() }
        val bottomMarginPx = with(density) { bottomMarginDp.toPx() }

        val defaultTargetX = (parentWidthPx - cardWidthPx - marginPx).coerceAtLeast(0f)
        val defaultTargetY = (parentHeightPx - cardHeightPx - bottomMarginPx).coerceAtLeast(0f)

        val offsetX = remember { Animatable(defaultTargetX) }
        val offsetY = remember { Animatable(defaultTargetY) }

        LaunchedEffect(parentWidthPx, parentHeightPx) {
            val clampedX = offsetX.value.coerceIn(
                marginPx,
                (parentWidthPx - cardWidthPx - marginPx).coerceAtLeast(marginPx)
            )
            val clampedY = offsetY.value.coerceIn(
                topMarginPx,
                (parentHeightPx - cardHeightPx - bottomMarginPx).coerceAtLeast(topMarginPx)
            )
            offsetX.snapTo(clampedX)
            offsetY.snapTo(clampedY)
        }

        Surface(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), offsetY.value.roundToInt()) }
                .size(cardWidthDp, cardHeightDp)
                .shadow(12.dp, RoundedCornerShape(14.dp))
                .clip(RoundedCornerShape(14.dp))
                .pointerInput(parentWidthPx, parentHeightPx) {
                    detectDragGestures(
                        onDragEnd = {
                            val snapTargetX = if (offsetX.value + cardWidthPx / 2f < parentWidthPx / 2f) {
                                marginPx
                            } else {
                                (parentWidthPx - cardWidthPx - marginPx).coerceAtLeast(marginPx)
                            }
                            scope.launch {
                                offsetX.animateTo(
                                    snapTargetX,
                                    spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)
                                )
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val newX = (offsetX.value + dragAmount.x).coerceIn(
                                marginPx,
                                (parentWidthPx - cardWidthPx - marginPx).coerceAtLeast(marginPx)
                            )
                            val newY = (offsetY.value + dragAmount.y).coerceIn(
                                topMarginPx,
                                (parentHeightPx - cardHeightPx - bottomMarginPx).coerceAtLeast(topMarginPx)
                            )
                            scope.launch {
                                offsetX.snapTo(newX)
                                offsetY.snapTo(newY)
                            }
                        }
                    )
                },
            shape = RoundedCornerShape(14.dp),
            color = Color.Black,
            border = BorderStroke(1.5.dp, GobeatsAmber.copy(alpha = 0.7f))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
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
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Gradient scrim overlay with tap-to-expand
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xCC09080E),
                                    Color(0x3309080E),
                                    Color(0xCC09080E)
                                )
                            )
                        )
                        .clickable { onExpand() }
                )

                // Top Bar: Track title + Close button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .align(Alignment.TopCenter),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = state.currentTrack?.title ?: "Video",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Surface(
                        shape = CircleShape,
                        color = Color(0x99000000),
                        modifier = Modifier.size(24.dp)
                    ) {
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Miniplayer",
                                tint = Color.White,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }

                // Center: Play / Pause toggle
                Surface(
                    shape = CircleShape,
                    color = Color(0xB3121118),
                    border = BorderStroke(1.dp, GobeatsAmber.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .size(36.dp)
                        .align(Alignment.Center)
                ) {
                    IconButton(
                        onClick = onTogglePlayPause,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (state.isPlaying) "Pause" else "Play",
                            tint = GobeatsAmber,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Bottom Left: Expand icon
                Surface(
                    shape = CircleShape,
                    color = Color(0x99000000),
                    modifier = Modifier
                        .size(22.dp)
                        .align(Alignment.BottomStart)
                        .padding(start = 6.dp, bottom = 6.dp)
                ) {
                    IconButton(
                        onClick = onExpand,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fullscreen,
                            contentDescription = "Expand Video",
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }

                // Bottom: Realtime progress bar
                val progress = if (state.durationMs > 0) {
                    (state.currentPositionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)
                } else 0f

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.5.dp)
                        .align(Alignment.BottomCenter)
                        .background(Color(0x33FFFFFF))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .fillMaxHeight()
                            .background(GobeatsAmber)
                    )
                }
            }
        }
    }
}
