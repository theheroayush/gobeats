package com.example.gobeats.feature.nowplaying

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gobeats.core.audio.SleepTimerManager
import com.example.gobeats.core.audio.SleepTimerState
import com.example.gobeats.data.repository.PlaybackRepository
import com.example.gobeats.theme.GobeatsAmber
import com.example.gobeats.theme.GobeatsBorderSubtle
import com.example.gobeats.theme.GobeatsSurfaceElevated
import com.example.gobeats.theme.GobeatsTextMuted
import com.example.gobeats.theme.GobeatsTextPrimary
import com.example.gobeats.theme.GobeatsTextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SleepTimerDialog(
    sleepTimerManager: SleepTimerManager,
    playbackRepository: PlaybackRepository,
    onDismiss: () -> Unit
) {
    val state by sleepTimerManager.state.collectAsState()
    val scope = rememberCoroutineScope()

    val presets = listOf(15, 30, 45, 60)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = GobeatsSurfaceElevated,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Bedtime,
                    contentDescription = null,
                    tint = GobeatsAmber,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Sleep Timer",
                    fontWeight = FontWeight.Bold,
                    color = GobeatsTextPrimary
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Status banner if running
                when (val current = state) {
                    is SleepTimerState.Running -> {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF1E1D24),
                            border = androidx.compose.foundation.BorderStroke(1.dp, GobeatsAmber.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 14.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Timer Active", fontSize = 12.sp, color = GobeatsTextMuted)
                                    Text(
                                        text = formatTime(current.remainingSeconds),
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = GobeatsAmber
                                    )
                                }
                                Button(
                                    onClick = {
                                        sleepTimerManager.cancelTimer {
                                            playbackRepository.setVolume(1.0f)
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2220)),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Text("Turn Off", color = Color(0xFFEF4444), fontSize = 12.sp)
                                }
                            }
                        }
                    }
                    is SleepTimerState.EndOfTrack -> {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF1E1D24),
                            border = androidx.compose.foundation.BorderStroke(1.dp, GobeatsAmber.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 14.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Timer Active", fontSize = 12.sp, color = GobeatsTextMuted)
                                    Text("End of Song", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = GobeatsAmber)
                                }
                                Button(
                                    onClick = {
                                        sleepTimerManager.cancelTimer {
                                            playbackRepository.setVolume(1.0f)
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2220)),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Text("Turn Off", color = Color(0xFFEF4444), fontSize = 12.sp)
                                }
                            }
                        }
                    }
                    else -> {}
                }

                Text(
                    text = "Select duration:",
                    fontSize = 13.sp,
                    color = GobeatsTextSecondary,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                presets.forEach { minutes ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                sleepTimerManager.startTimer(
                                    minutes = minutes,
                                    onFadeOut = {
                                        // 5 second smooth volume fadeout
                                        for (i in 5 downTo 1) {
                                            playbackRepository.setVolume(i / 5.0f)
                                            delay(1000)
                                        }
                                    },
                                    onExpire = {
                                        playbackRepository.togglePlayPause()
                                        playbackRepository.setVolume(1.0f)
                                    }
                                )
                                onDismiss()
                            }
                            .padding(vertical = 12.dp, horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "$minutes minutes",
                            fontSize = 15.sp,
                            color = GobeatsTextPrimary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // End of track option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            sleepTimerManager.startEndOfTrackTimer {
                                playbackRepository.togglePlayPause()
                            }
                            onDismiss()
                        }
                        .padding(vertical = 12.dp, horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "End of current song",
                        fontSize = 15.sp,
                        color = GobeatsTextPrimary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = GobeatsAmber)
            }
        }
    )
}

private fun formatTime(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format("%02d:%02d", m, s)
}
