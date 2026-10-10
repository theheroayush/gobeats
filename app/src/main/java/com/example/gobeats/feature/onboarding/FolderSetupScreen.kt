package com.example.gobeats.feature.onboarding

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.gobeats.core.designsystem.components.GobeatsPrimaryButton
import com.example.gobeats.core.designsystem.components.GobeatsSecondaryButton
import com.example.gobeats.core.designsystem.components.OnboardingProgressBar
import com.example.gobeats.data.repository.LibraryRepository
import com.example.gobeats.theme.GobeatsAmber
import com.example.gobeats.theme.GobeatsObsidian
import com.example.gobeats.theme.GobeatsSurface
import com.example.gobeats.theme.GobeatsTextMuted
import com.example.gobeats.theme.GobeatsTextPrimary
import com.example.gobeats.theme.GobeatsTextSecondary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun FolderSetupScreen(
    libraryRepository: LibraryRepository,
    onComplete: () -> Unit,
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current

    val requiredPermissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.READ_MEDIA_VIDEO
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    val hasPermission = remember {
        requiredPermissions.any { perm ->
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun startBackgroundScanAndEnterApp() {
        // Kick off asynchronous media scan in background without blocking the UI
        CoroutineScope(Dispatchers.IO).launch {
            libraryRepository.scanMediaStore().collect {}
        }
        onComplete()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val grantedAny = results.values.any { it }
        if (grantedAny) {
            startBackgroundScanAndEnterApp()
        } else {
            // Still admit the user so they can explore the app and connect to PC or server
            onComplete()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GobeatsObsidian)
            .statusBarsPadding()
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
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

                // 4-Stage Segmented Progress Bar (Stage 4 Active)
                OnboardingProgressBar(currentStep = 4)

                Text(
                    text = "Skip",
                    fontSize = 14.sp,
                    color = GobeatsTextMuted,
                    modifier = Modifier
                        .clickable { onComplete() }
                        .padding(4.dp)
                )
            }

            // Center Content
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .background(GobeatsSurface, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.LibraryMusic,
                        contentDescription = "Library",
                        tint = GobeatsAmber,
                        modifier = Modifier.size(44.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Ready to build your library",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = GobeatsTextPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "gobeats will index your device's music and video files quietly in the background so you can start listening immediately without waiting.",
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = GobeatsTextSecondary,
                    textAlign = TextAlign.Center
                )
            }

            // Bottom Buttons
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (hasPermission) {
                    GobeatsPrimaryButton(
                        text = "Enter Library",
                        onClick = { startBackgroundScanAndEnterApp() }
                    )
                } else {
                    GobeatsPrimaryButton(
                        text = "Grant Access & Start Listening",
                        onClick = { permissionLauncher.launch(requiredPermissions) }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    GobeatsSecondaryButton(
                        text = "Explore App First",
                        onClick = { onComplete() }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Step 4 of 4 • Storage Access",
                    fontSize = 11.sp,
                    color = GobeatsTextMuted
                )
            }
        }
    }
}
