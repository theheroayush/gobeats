package com.example.gobeats.feature.onboarding

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.example.gobeats.data.repository.LibraryRepository
import com.example.gobeats.theme.GobeatsAmber
import com.example.gobeats.theme.GobeatsObsidian
import com.example.gobeats.theme.GobeatsSurface
import com.example.gobeats.theme.GobeatsTextMuted
import com.example.gobeats.theme.GobeatsTextPrimary
import com.example.gobeats.theme.GobeatsTextSecondary
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable
fun FolderSetupScreen(
    libraryRepository: LibraryRepository,
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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

    var hasPermission by remember {
        mutableStateOf(
            requiredPermissions.any { perm ->
                ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    var isScanning by remember { mutableStateOf(false) }
    var scannedCount by remember { mutableIntStateOf(0) }
    var currentFile by remember { mutableStateOf("") }
    var scanFinished by remember { mutableStateOf(false) }

    fun startScanning() {
        isScanning = true
        scope.launch {
            libraryRepository.scanMediaStore().collectLatest { progress ->
                scannedCount = progress.scannedCount
                currentFile = progress.currentFile
                if (progress.isComplete) {
                    isScanning = false
                    scanFinished = true
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val grantedAny = results.values.any { it }
        hasPermission = grantedAny
        if (grantedAny) {
            startScanning()
        }
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission && !isScanning && !scanFinished) {
            startScanning()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GobeatsObsidian)
            .statusBarsPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(GobeatsSurface, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isScanning) {
                    CircularProgressIndicator(
                        color = GobeatsAmber,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(44.dp)
                    )
                } else if (scanFinished) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Ready",
                        tint = GobeatsAmber,
                        modifier = Modifier.size(48.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = "Folder",
                        tint = GobeatsAmber,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = if (scanFinished) "Your library is ready"
                       else if (isScanning) "Scanning your music..."
                       else "Enable music access",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = GobeatsTextPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = if (scanFinished) "Found $scannedCount tracks on this device."
                       else if (isScanning) "Indexing: $currentFile"
                       else "gobeats needs permission to index and play the audio files stored on your phone.",
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = GobeatsTextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(28.dp))

            if (isScanning) {
                LinearProgressIndicator(
                    color = GobeatsAmber,
                    trackColor = GobeatsSurface,
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(6.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "$scannedCount tracks found",
                    fontSize = 13.sp,
                    color = GobeatsTextMuted
                )
            } else if (scanFinished) {
                GobeatsPrimaryButton(
                    text = "Start Listening",
                    onClick = onComplete
                )
            } else {
                GobeatsPrimaryButton(
                    text = "Grant Access & Scan",
                    onClick = { permissionLauncher.launch(requiredPermissions) }
                )
                Spacer(modifier = Modifier.height(12.dp))
                GobeatsSecondaryButton(
                    text = "Skip for Now",
                    onClick = {
                        onComplete()
                    }
                )
            }
        }
    }
}
