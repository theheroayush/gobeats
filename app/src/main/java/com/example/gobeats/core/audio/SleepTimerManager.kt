package com.example.gobeats.core.audio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed class SleepTimerState {
    data object Inactive : SleepTimerState()
    data class Running(val remainingSeconds: Long, val totalSeconds: Long) : SleepTimerState()
    data object EndOfTrack : SleepTimerState()
}

class SleepTimerManager {

    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private var timerJob: Job? = null

    private val _state = MutableStateFlow<SleepTimerState>(SleepTimerState.Inactive)
    val state: StateFlow<SleepTimerState> = _state.asStateFlow()

    private var endOfTrackAction: (() -> Unit)? = null

    fun startTimer(
        minutes: Int,
        onFadeOut: (suspend () -> Unit)? = null,
        onExpire: () -> Unit
    ) {
        timerJob?.cancel()
        endOfTrackAction = null

        val totalSeconds = (minutes * 60).toLong()
        _state.value = SleepTimerState.Running(remainingSeconds = totalSeconds, totalSeconds = totalSeconds)

        timerJob = scope.launch {
            var currentSeconds = totalSeconds
            var fadeOutTriggered = false

            while (isActive && currentSeconds > 0) {
                delay(1000)
                currentSeconds--
                _state.value = SleepTimerState.Running(remainingSeconds = currentSeconds, totalSeconds = totalSeconds)

                // Trigger gentle volume attenuation when 5 seconds remain
                if (currentSeconds <= 5 && !fadeOutTriggered) {
                    fadeOutTriggered = true
                    onFadeOut?.invoke()
                }
            }

            if (isActive) {
                _state.value = SleepTimerState.Inactive
                onExpire()
            }
        }
    }

    fun startEndOfTrackTimer(onExpire: () -> Unit) {
        timerJob?.cancel()
        endOfTrackAction = onExpire
        _state.value = SleepTimerState.EndOfTrack
    }

    fun notifyTrackEnded() {
        if (_state.value is SleepTimerState.EndOfTrack) {
            val action = endOfTrackAction
            cancelTimer()
            action?.invoke()
        }
    }

    fun cancelTimer(onCancel: (() -> Unit)? = null) {
        timerJob?.cancel()
        timerJob = null
        endOfTrackAction = null
        _state.value = SleepTimerState.Inactive
        onCancel?.invoke()
    }
}
