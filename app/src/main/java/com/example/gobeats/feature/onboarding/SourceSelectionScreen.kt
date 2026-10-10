package com.example.gobeats.feature.onboarding

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gobeats.core.designsystem.components.GobeatsPrimaryButton
import com.example.gobeats.core.designsystem.components.OnboardingProgressBar
import com.example.gobeats.core.designsystem.components.SourceOptionCard
import com.example.gobeats.core.sync.GobeatsSyncServer
import com.example.gobeats.core.sync.GobeatsSyncService
import com.example.gobeats.theme.GobeatsAmber
import com.example.gobeats.theme.GobeatsAmberGlow
import com.example.gobeats.theme.GobeatsBorderSubtle
import com.example.gobeats.theme.GobeatsCharcoal
import com.example.gobeats.theme.GobeatsObsidian
import com.example.gobeats.theme.GobeatsSurface
import com.example.gobeats.theme.GobeatsSurfaceElevated
import com.example.gobeats.theme.GobeatsTextMuted
import com.example.gobeats.theme.GobeatsTextPrimary
import com.example.gobeats.theme.GobeatsTextSecondary

/**
 * Screen S02: Source Selection Screen
 * 1:1 Pixel-Perfect translation of Mockup 2 with responsive mobile compatibility:
 * - 4-stage segmented top indicator
 * - "Where's your music?"
 * - Interactive source cards (This phone, Connect computer, Personal server, Streaming)
 * - Mobile-friendly PC sync card with live IP/URL, 1-tap Copy, Share, PIN, and Wi-Fi state
 * - Trust banner "Your music stays yours"
 * - Primary dynamic CTA adapting to chosen source
 */
@Composable
fun SourceSelectionScreen(
    syncServer: GobeatsSyncServer? = null,
    onContinue: (selectedSource: String) -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit
) {
    val context = LocalContext.current
    var selectedSource by remember { mutableStateOf("phone") }

    val syncState by syncServer?.state?.collectAsState()
        ?: remember { mutableStateOf(com.example.gobeats.core.sync.SyncServerState()) }

    val rawIp = syncServer?.getLocalIpAddress() ?: "127.0.0.1"
    val isWifiConnected = rawIp != "127.0.0.1"
    val serverUrl = if (isWifiConnected) "http://$rawIp:42100" else "http://192.168.1.100:42100"

    // Automatically ensure sync service runs when computer is selected
    LaunchedEffect(selectedSource) {
        if (selectedSource == "computer") {
            GobeatsSyncService.start(context)
        }
    }

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

                // 4-Stage Segmented Progress Bar (Stage 3 Active)
                OnboardingProgressBar(currentStep = 3)

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

            // Dynamic Mobile-Friendly Computer Sync Details Card
            AnimatedVisibility(visible = selectedSource == "computer") {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 4.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF14131B),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GobeatsAmber.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Wi-Fi Status Header
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isWifiConnected) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(Color(0xFF34D399), CircleShape)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Wi-Fi Connected • Ready to Sync",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF34D399)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.WifiOff,
                                    contentDescription = null,
                                    tint = GobeatsAmber,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Connect phone & PC to same Wi-Fi",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = GobeatsAmber
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "Open this address in any browser on your computer:",
                            fontSize = 12.sp,
                            color = GobeatsTextSecondary,
                            lineHeight = 16.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // URL Box with Copy and Share
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = GobeatsObsidian,
                            border = androidx.compose.foundation.BorderStroke(1.dp, GobeatsBorderSubtle),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = serverUrl,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = GobeatsAmber,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )

                                IconButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("gobeats Connect URL", serverUrl))
                                        Toast.makeText(context, "URL copied to clipboard", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy Link",
                                        tint = GobeatsTextPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        val sendIntent = Intent().apply {
                                            action = Intent.ACTION_SEND
                                            putExtra(
                                                Intent.EXTRA_TEXT,
                                                "gobeats PC Sync URL: $serverUrl\nPairing PIN: ${syncState.pinCode}"
                                            )
                                            type = "text/plain"
                                        }
                                        val shareIntent = Intent.createChooser(sendIntent, "Send Link to PC")
                                        context.startActivity(shareIntent)
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = "Share to PC",
                                        tint = GobeatsAmber,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Pairing PIN Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Pairing PIN:",
                                fontSize = 12.sp,
                                color = GobeatsTextMuted
                            )
                            Text(
                                text = syncState.pinCode,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = GobeatsTextPrimary,
                                letterSpacing = 2.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "💡 Tip: Drag & drop your folders, songs, or playlists into the web browser. They will sync instantly to your phone.",
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = GobeatsTextMuted
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            SourceOptionCard(
                title = "Connect a personal server",
                description = "Access your music from Jellyfin, Navidrome, Plex, or other compatible servers.",
                icon = Icons.Default.Dns,
                isSelected = selectedSource == "server",
                onClick = { selectedSource = "server" }
            )

            AnimatedVisibility(visible = selectedSource == "server") {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = GobeatsSurfaceElevated
                ) {
                    Text(
                        text = "You can link your Jellyfin or Navidrome server credentials anytime from Settings > Cloud & Media Sources.",
                        fontSize = 12.sp,
                        color = GobeatsTextSecondary,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            SourceOptionCard(
                title = "Streaming integrations",
                description = "Connect optional streaming services for discovery and more.",
                icon = Icons.Default.Cloud,
                isSelected = selectedSource == "streaming",
                onClick = { selectedSource = "streaming" }
            )

            AnimatedVisibility(visible = selectedSource == "streaming") {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = GobeatsSurfaceElevated
                ) {
                    Text(
                        text = "Streaming integrations and internet radios can be linked in Settings > Streaming Integrations.",
                        fontSize = 12.sp,
                        color = GobeatsTextSecondary,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

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

            // Dynamic Primary Button based on selected source
            val buttonText = when (selectedSource) {
                "computer" -> "Continue with PC Sync"
                "phone" -> "Continue to Scan Storage"
                else -> "Continue"
            }

            GobeatsPrimaryButton(
                text = buttonText,
                onClick = { onContinue(selectedSource) }
            )

            if (selectedSource == "computer") {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Also scan device storage folders",
                    fontSize = 12.sp,
                    color = GobeatsAmber,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clickable { onContinue("phone") }
                        .padding(6.dp)
                )
            }

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
