package com.example.gobeats.core.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

object AppIconManager {

    const val STYLE_NEON_PURPLE = "neon_purple"
    const val STYLE_WHITE_MINIMAL = "white_minimal"
    const val STYLE_SUNSET = "sunset"
    const val STYLE_MOUNTAIN = "mountain"

    val ALL_STYLES = listOf(
        STYLE_NEON_PURPLE,
        STYLE_WHITE_MINIMAL,
        STYLE_SUNSET,
        STYLE_MOUNTAIN
    )

    fun setAppIcon(context: Context, style: String) {
        val pm = context.packageManager
        val pkg = context.packageName

        val targetAlias = when (style) {
            STYLE_WHITE_MINIMAL -> "$pkg.MainActivityWhiteMinimal"
            STYLE_SUNSET -> "$pkg.MainActivitySunset"
            STYLE_MOUNTAIN -> "$pkg.MainActivityMountain"
            else -> "$pkg.MainActivityNeonPurple"
        }

        val allAliases = listOf(
            "$pkg.MainActivityNeonPurple",
            "$pkg.MainActivityWhiteMinimal",
            "$pkg.MainActivitySunset",
            "$pkg.MainActivityMountain"
        )

        allAliases.forEach { aliasName ->
            val newState = if (aliasName == targetAlias) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            try {
                pm.setComponentEnabledSetting(
                    ComponentName(pkg, aliasName),
                    newState,
                    PackageManager.DONT_KILL_APP
                )
            } catch (_: Exception) {}
        }
    }
}
