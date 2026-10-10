package com.example.gobeats.core.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.gobeats.theme.GobeatsAmber
import com.example.gobeats.theme.GobeatsObsidian
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ArtworkPaletteColors(
    val dominant: Color = Color(0xFF261E17),
    val vibrant: Color = GobeatsAmber,
    val darkMuted: Color = Color(0xFF141210),
    val lightVibrant: Color = Color(0xFFFDE68A),
    val backgroundBase: Color = GobeatsObsidian,
    val meshColors: List<Color> = listOf(
        Color(0xFF2E2015),
        Color(0xFF181514),
        GobeatsObsidian
    )
)

object ArtworkPaletteExtractor {

    private val cache = LruCache<String, ArtworkPaletteColors>(120)

    val DEFAULT_PALETTE = ArtworkPaletteColors()

    suspend fun extractColors(context: Context, artworkUri: String?): ArtworkPaletteColors = withContext(Dispatchers.IO) {
        if (artworkUri.isNullOrBlank()) {
            return@withContext DEFAULT_PALETTE
        }

        cache.get(artworkUri)?.let { return@withContext it }

        var bitmap: Bitmap? = null

        // 1. Try Coil ImageLoader with downsampled software bitmap (128x128 for ultra-fast quantization)
        try {
            val request = ImageRequest.Builder(context)
                .data(artworkUri)
                .size(128, 128)
                .allowHardware(false) // Hardware bitmaps cannot be read by Palette directly
                .build()
            val result = context.imageLoader.execute(request)
            if (result is SuccessResult) {
                bitmap = (result.drawable as? BitmapDrawable)?.bitmap
            }
        } catch (_: Exception) {}

        // 2. Direct ContentResolver fallback if Coil was unavailable or failed
        if (bitmap == null) {
            try {
                val parsedUri = Uri.parse(artworkUri)
                val options = BitmapFactory.Options().apply {
                    inSampleSize = 4
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
                if (artworkUri.startsWith("content://") || artworkUri.startsWith("android.resource://")) {
                    context.contentResolver.openInputStream(parsedUri)?.use { stream ->
                        bitmap = BitmapFactory.decodeStream(stream, null, options)
                    }
                } else if (artworkUri.startsWith("file://") || artworkUri.startsWith("/")) {
                    val path = if (artworkUri.startsWith("file://")) parsedUri.path else artworkUri
                    if (path != null) {
                        bitmap = BitmapFactory.decodeFile(path, options)
                    }
                }
            } catch (_: Exception) {}
        }

        val finalBitmap = bitmap ?: run {
            cache.put(artworkUri, DEFAULT_PALETTE)
            return@withContext DEFAULT_PALETTE
        }

        // Generate Palette with maximum 16 color quantization for lightning speed (<5ms)
        val palette = try {
            Palette.from(finalBitmap).maximumColorCount(16).generate()
        } catch (_: Exception) {
            null
        }

        if (palette == null) {
            cache.put(artworkUri, DEFAULT_PALETTE)
            return@withContext DEFAULT_PALETTE
        }

        val dominantInt = palette.getDominantColor(0xFF261E17.toInt())
        val vibrantInt = palette.getVibrantColor(0xFFF59E0B.toInt())
        val darkMutedInt = palette.getDarkMutedColor(0xFF141210.toInt())
        val lightVibrantInt = palette.getLightVibrantColor(0xFFFDE68A.toInt())

        val dominant = Color(dominantInt)
        val vibrant = Color(vibrantInt)
        val darkMuted = Color(darkMutedInt)
        val lightVibrant = Color(lightVibrantInt)

        // Generate 3 gradient stops for mesh background: subtle deep dominant, dark muted, and Obsidian base
        val meshColors = listOf(
            dominant.copy(alpha = 0.55f),
            darkMuted.copy(alpha = 0.40f),
            GobeatsObsidian
        )

        val result = ArtworkPaletteColors(
            dominant = dominant,
            vibrant = vibrant,
            darkMuted = darkMuted,
            lightVibrant = lightVibrant,
            backgroundBase = GobeatsObsidian,
            meshColors = meshColors
        )

        cache.put(artworkUri, result)
        result
    }
}

@Composable
fun rememberArtworkPalette(context: Context, artworkUri: String?): State<ArtworkPaletteColors> {
    val state = remember(artworkUri) { mutableStateOf(ArtworkPaletteExtractor.DEFAULT_PALETTE) }

    LaunchedEffect(artworkUri) {
        val extracted = ArtworkPaletteExtractor.extractColors(context, artworkUri)
        state.value = extracted
    }

    return state
}
