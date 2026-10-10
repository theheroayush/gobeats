package com.example.gobeats.core.util

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed

/**
 * Safely pops the top destination from the navigation stack only if the stack size > 1.
 * Returns true if a destination was popped, false otherwise.
 * Prevents IndexOutOfBoundsException on rapid taps.
 */
fun <T> SnapshotStateList<T>.safePop(): Boolean {
    if (size > 1) {
        removeAt(lastIndex)
        return true
    }
    return false
}

/**
 * Safely pushes a destination onto the navigation stack, avoiding duplicate adjacent identical destinations.
 */
fun <T> SnapshotStateList<T>.safePush(item: T) {
    if (isEmpty() || lastOrNull() != item) {
        add(item)
    }
}

/**
 * Single-click debouncer modifier to prevent rapid double-tap bugs and accidental duplicate events.
 */
inline fun Modifier.clickableSingle(
    enabled: Boolean = true,
    debounceMs: Long = 350L,
    crossinline onClick: () -> Unit
): Modifier = composed {
    val multipleEventsCutter = remember { MultipleEventsCutter.get(debounceMs) }
    this.clickable(
        enabled = enabled,
        interactionSource = remember { MutableInteractionSource() },
        indication = androidx.compose.material3.ripple()
    ) {
        multipleEventsCutter.processEvent { onClick() }
    }
}

class MultipleEventsCutter private constructor(private val debounceMs: Long) {
    private val now: Long
        get() = System.currentTimeMillis()

    private var lastEventTimeMs: Long = 0

    fun processEvent(event: () -> Unit) {
        if (now - lastEventTimeMs >= debounceMs) {
            lastEventTimeMs = now
            event()
        }
    }

    companion object {
        fun get(debounceMs: Long = 350L): MultipleEventsCutter = MultipleEventsCutter(debounceMs)
    }
}
