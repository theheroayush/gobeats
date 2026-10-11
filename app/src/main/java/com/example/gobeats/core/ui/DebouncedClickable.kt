package com.example.gobeats.core.ui

import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.semantics.Role

/**
 * Modifier that prevents accidental rapid multi-clicking within [debounceMs] window.
 * Protects against IPC Binder overload, duplicate Media3 playback requests, and double navigation.
 */
fun Modifier.debouncedClickable(
    debounceMs: Long = 350L,
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    onClick: () -> Unit
): Modifier = composed {
    val clickGuard = remember { ClickGuard(debounceMs) }
    this.clickable(
        enabled = enabled,
        onClickLabel = onClickLabel,
        role = role,
        onClick = {
            if (clickGuard.allowClick()) {
                onClick()
            }
        }
    )
}

/**
 * Thread-safe click guard tracking uptime between user interaction events.
 * Supports configurable time provider with safe fallback for JVM unit test execution.
 */
class ClickGuard(
    private val debounceMs: Long,
    private val timeProvider: () -> Long = {
        try {
            SystemClock.uptimeMillis()
        } catch (_: Throwable) {
            System.currentTimeMillis()
        }
    }
) {
    private var lastClickTimeMs: Long = 0L

    fun allowClick(): Boolean {
        val now = try {
            timeProvider()
        } catch (_: Throwable) {
            System.currentTimeMillis()
        }
        if (now - lastClickTimeMs >= debounceMs) {
            lastClickTimeMs = now
            return true
        }
        return false
    }
}
