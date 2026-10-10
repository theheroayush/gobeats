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

        // 1. Enable the target alias FIRST so the launcher always sees an active component
        try {
            pm.setComponentEnabledSetting(
                ComponentName(pkg, targetAlias),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
        } catch (_: Exception) {}

        // 2. Disable all other aliases AFTER target alias is active
        allAliases.filter { it != targetAlias }.forEach { aliasName ->
            try {
                pm.setComponentEnabledSetting(
                    ComponentName(pkg, aliasName),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            } catch (_: Exception) {}
        }
    }
}
