package com.example.gobeats.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val GobeatsColorScheme = darkColorScheme(
    primary = GobeatsAmber,
    onPrimary = GobeatsObsidian,
    primaryContainer = GobeatsAmberGlow,
    onPrimaryContainer = GobeatsAmber,
    secondary = GobeatsButtonPrimaryBg,
    onSecondary = GobeatsButtonPrimaryText,
    background = GobeatsObsidian,
    onBackground = GobeatsTextPrimary,
    surface = GobeatsSurface,
    onSurface = GobeatsTextPrimary,
    surfaceVariant = GobeatsSurfaceElevated,
    onSurfaceVariant = GobeatsTextSecondary,
    outline = GobeatsBorderSubtle,
    outlineVariant = GobeatsBorderFocused,
    error = GobeatsError,
    onError = GobeatsTextPrimary
)

@Composable
fun GobeatsTheme(
    darkTheme: Boolean = true, // gobeats defaults to its signature dark obsidian aesthetic
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = GobeatsObsidian.toArgb()
                window.navigationBarColor = GobeatsObsidian.toArgb()
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = false
                controller.isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = GobeatsColorScheme,
        typography = GobeatsTypography,
        content = content
    )
}
