package com.example.gobeats.feature.library

import android.app.Activity
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.example.gobeats.core.media.tag.ScopedStorageRouter
import com.example.gobeats.core.media.tag.StorageWriteMechanism
import java.io.File
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.gobeats.core.designsystem.components.GobeatsPrimaryButton
import com.example.gobeats.core.designsystem.components.GobeatsSecondaryButton
import com.example.gobeats.core.media.TagEditPayload
import com.example.gobeats.core.model.Track
import com.example.gobeats.theme.GobeatsAmber
import com.example.gobeats.theme.GobeatsBorderFocused
import com.example.gobeats.theme.GobeatsBorderSubtle
import com.example.gobeats.theme.GobeatsError
import com.example.gobeats.theme.GobeatsObsidian
import com.example.gobeats.theme.GobeatsSurface
import com.example.gobeats.theme.GobeatsSurfaceElevated
import com.example.gobeats.theme.GobeatsTextMuted
import com.example.gobeats.theme.GobeatsTextPrimary
import com.example.gobeats.theme.GobeatsTextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagEditorBottomSheet(
    track: Track,
    onDismiss: () -> Unit,
    onSave: (TagEditPayload) -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf(track.title) }
    var artist by remember { mutableStateOf(track.artistName) }
    var album by remember { mutableStateOf(track.albumTitle) }
    var yearText by remember { mutableStateOf(track.year?.takeIf { it > 0 }?.toString() ?: "") }
    var genreText by remember { mutableStateOf(track.genre ?: "") }
    var trackNumberText by remember { mutableStateOf(track.trackNumber.takeIf { it > 0 }?.toString() ?: "") }

    var currentArtworkUri by remember { mutableStateOf(track.artworkUri) }
    var selectedArtworkBytes by remember { mutableStateOf<ByteArray?>(null) }
    var isArtworkRemoved by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val bytes = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    } catch (_: Exception) {
                        null
                    }
                }
                if (bytes != null && bytes.isNotEmpty()) {
                    selectedArtworkBytes = bytes
                    currentArtworkUri = uri.toString()
                    isArtworkRemoved = false
                }
            }
        }
    }

    var pendingPayload by remember { mutableStateOf<TagEditPayload?>(null) }

    val writePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            pendingPayload?.let { payload ->
                onSave(payload)
                pendingPayload = null
            }
        } else {
            isSaving = false
            pendingPayload = null
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = GobeatsSurfaceElevated,
        dragHandle = { BottomSheetDefaults.DragHandle(color = GobeatsBorderFocused) },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Edit Track Tags",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = GobeatsTextPrimary
                    )
                    Text(
                        text = track.title,
                        fontSize = 12.sp,
                        color = GobeatsTextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = GobeatsTextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Artwork Section
            Box(
                modifier = Modifier
                    .size(130.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(GobeatsSurface)
                    .border(1.dp, GobeatsBorderSubtle, RoundedCornerShape(16.dp))
                    .clickable { photoPickerLauncher.launch("image/*") },
                contentAlignment = Alignment.Center
            ) {
                if (!isArtworkRemoved && !currentArtworkUri.isNullOrBlank()) {
                    AsyncImage(
                        model = currentArtworkUri,
                        contentDescription = "Cover Art Preview",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = GobeatsAmber,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Add Artwork",
                            fontSize = 11.sp,
                            color = GobeatsTextMuted,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Edit Overlay Badge
                Surface(
                    shape = CircleShape,
                    color = GobeatsObsidian.copy(alpha = 0.8f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = "Pick Image",
                            tint = GobeatsAmber,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Remove Artwork Option
            if (!isArtworkRemoved && (!currentArtworkUri.isNullOrBlank() || selectedArtworkBytes != null)) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            isArtworkRemoved = true
                            currentArtworkUri = null
                            selectedArtworkBytes = null
                        }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Remove Artwork",
                        tint = GobeatsError,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Remove Artwork",
                        fontSize = 12.sp,
                        color = GobeatsError,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Metadata Form Fields
            TagEditorTextField(
                label = "Title",
                value = title,
                onValueChange = { title = it }
            )

            Spacer(modifier = Modifier.height(12.dp))

            TagEditorTextField(
                label = "Artist",
                value = artist,
                onValueChange = { artist = it }
            )

            Spacer(modifier = Modifier.height(12.dp))

            TagEditorTextField(
                label = "Album",
                value = album,
                onValueChange = { album = it }
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.weight(1f)) {
                    TagEditorTextField(
                        label = "Year",
                        value = yearText,
                        onValueChange = { yearText = it.filter { ch -> ch.isDigit() }.take(4) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Box(modifier = Modifier.weight(1f)) {
                    TagEditorTextField(
                        label = "Track #",
                        value = trackNumberText,
                        onValueChange = { trackNumberText = it.filter { ch -> ch.isDigit() }.take(4) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            TagEditorTextField(
                label = "Genre",
                value = genreText,
                onValueChange = { genreText = it }
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Action Buttons
            if (isSaving) {
                CircularProgressIndicator(
                    color = GobeatsAmber,
                    modifier = Modifier.size(36.dp)
                )
            } else {
                GobeatsPrimaryButton(
                    text = "Save Changes",
                    onClick = {
                        isSaving = true
                        val finalArtworkUri = if (isArtworkRemoved) null else currentArtworkUri
                        val payload = TagEditPayload(
                            trackId = track.id,
                            title = title.ifBlank { track.title },
                            artist = artist.ifBlank { track.artistName },
                            album = album.ifBlank { track.albumTitle },
                            year = yearText.toIntOrNull(),
                            genre = genreText.takeIf { it.isNotBlank() },
                            trackNumber = trackNumberText.toIntOrNull(),
                            artworkUri = finalArtworkUri,
                            newArtworkBytes = selectedArtworkBytes,
                            removeArtwork = isArtworkRemoved
                        )

                        // Check Scoped Storage write permission on Android 11+
                        val filePath = when {
                            track.fileUri.startsWith("file://") -> Uri.parse(track.fileUri).path
                            track.fileUri.startsWith("/") -> track.fileUri
                            else -> null
                        }
                        val isPublic = ScopedStorageRouter.isPublicStorage(context, filePath ?: track.fileUri)
                        val mechanism = ScopedStorageRouter.resolveWriteMechanism(Build.VERSION.SDK_INT, isPublic)
                        val canDirectWrite = filePath?.let { File(it) }?.let { it.exists() && it.canWrite() } ?: false

                        if (mechanism == StorageWriteMechanism.MEDIA_STORE_CREATE_WRITE_REQUEST && !canDirectWrite) {
                            val contentUri = when {
                                track.fileUri.startsWith("content://") -> Uri.parse(track.fileUri)
                                filePath != null -> ScopedStorageRouter.getContentUriForFilePath(context, filePath)
                                else -> null
                            }
                            val intentSender = contentUri?.let { ScopedStorageRouter.requestWritePermissionIntentSender(context, it) }
                            if (intentSender != null) {
                                pendingPayload = payload
                                writePermissionLauncher.launch(
                                    IntentSenderRequest.Builder(intentSender).build()
                                )
                                return@GobeatsPrimaryButton
                            }
                        }

                        onSave(payload)
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                GobeatsSecondaryButton(
                    text = "Cancel",
                    onClick = onDismiss
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TagEditorTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 13.sp) },
        singleLine = true,
        keyboardOptions = keyboardOptions,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = GobeatsTextPrimary,
            unfocusedTextColor = GobeatsTextPrimary,
            focusedContainerColor = GobeatsSurface,
            unfocusedContainerColor = GobeatsSurface,
            focusedBorderColor = GobeatsAmber,
            unfocusedBorderColor = GobeatsBorderSubtle,
            focusedLabelColor = GobeatsAmber,
            unfocusedLabelColor = GobeatsTextMuted,
            cursorColor = GobeatsAmber
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth()
    )
}
