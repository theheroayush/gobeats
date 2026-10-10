# Handoff Report — Feature F11: Audiophile Gapless & Configurable Crossfade

**Agent:** `explorer_m3_2`  
**Milestone:** M3 (Audiophile Offline & Folder Browser)  
**Target Feature:** Feature F11 (Audiophile gapless playback transitions and configurable smooth 1-5s crossfade between queue items in PlaybackConnection)  
**Date:** 2026-10-06  

---

## 1. Observation

### 1.1 Existing Architecture & Codebase Status
1. **Media Pipeline & Session Service (`app/src/main/java/com/example/gobeats/core/media/GobeatsMediaSessionService.kt`)**:
   - Lines 56–68: Uses `DefaultRenderersFactory` with high-precision 32-bit float audio sink (`BitPerfectAudioEngine.createHighPrecisionAudioSink(context, enableFloatOutput = true, enableAudioTrackPlaybackParams = true)`).
   - Lines 71–79: Uses `DefaultLoadControl` with high-buffer duration settings:
     `minBufferMs = 50_000`, `maxBufferMs = 120_000`, `bufferForPlaybackMs = 1_500`, `bufferForPlaybackAfterRebufferMs = 3_000`.
   - Lines 80–93: Builds `ExoPlayer` with `C.AUDIO_CONTENT_TYPE_MUSIC`, `handleAudioFocus = true`, `setWakeMode(C.WAKE_MODE_LOCAL)`.
   - ExoPlayer natively pre-buffers subsequent queue items and automatically uses `GaplessInfoHolder` to strip encoder delay and padding samples (e.g., MP3 Xing/VBRI headers and MP4 `iTunSMPB` atoms), enabling true gapless transitions.
   - However, `GobeatsMediaSessionService` has no direct configuration or session command for toggling gapless behavior or driving crossfade curves.

2. **Playback Controller & State Management (`app/src/main/java/com/example/gobeats/core/media/PlaybackConnection.kt`)**:
   - Lines 483–488: Queues tracks via `player.setMediaItems(mediaItems, trackIndex, 0L)` and `player.prepare()`.
   - Lines 84–119: Implements `onMediaItemTransition(mediaItem, reason)`. On track transition, it recalculates ReplayGain (`val rgResult = eqMgr.computeTrackReplayGain(currentTrack)`) and sets volume to `rgResult.linearVolume`.
   - Lines 290–382: Implements `startPositionUpdates()`, polling player position every 250ms via `scope.launch`.
   - **Gap**: `PlaybackConnection` currently has **no crossfade implementation**:
     - No detection of crossfade window (`shouldTriggerCrossfade`).
     - No dynamic volume envelope ramping ($vol_{out} = \cos(p \cdot \frac{\pi}{2})$).
     - No smooth fade-in ramp on new track start ($vol_{in} = \sin(p \cdot \frac{\pi}{2})$).
     - No API to configure crossfade seconds (1–5s) or gapless mode.
     - `_playbackState` does not store `crossfadeDurationSec`, `gaplessPlayback`, or `isCrossfadeActive`.

3. **Domain Models (`app/src/main/java/com/example/gobeats/core/model/Models.kt`)**:
   - Lines 114–143: `PlaybackUiState` lacks properties for crossfade duration, gapless playback flag, and active crossfade state.

4. **Settings & Persistence (`app/src/main/java/com/example/gobeats/data/repository/Repositories.kt` & `feature/settings/SettingsScreen.kt`)**:
   - `AppSettings` (lines 263–270) has:
     ```kotlin
     val crossfadeDurationSec: Int = 0,
     val gaplessPlayback: Boolean = true
     ```
   - `SettingsRepository` (lines 283–293, 321–324) persists `crossfade_sec` and `gapless` in SharedPreferences:
     ```kotlin
     fun setPlaybackSettings(crossfadeSec: Int, gapless: Boolean) {
         prefs.edit().putInt("crossfade_sec", crossfadeSec).putBoolean("gapless", gapless).apply()
         _settings.value = _settings.value.copy(crossfadeDurationSec = crossfadeSec, gaplessPlayback = gapless)
     }
     ```
   - `SettingsScreen.kt` (lines 795–854) provides a dialog with a `Switch` for "Gapless Playback" and a `Slider` (0 to 12s) for "Crossfade Duration", calling `settingsRepository.setPlaybackSettings(...)`.
   - **Gap**: `SettingsRepository` is never observed by or connected to `PlaybackConnection.kt`.

5. **Test Assertions**:
   - `app/src/test/java/com/example/gobeats/GobeatsTier1FeatureCoverageTest.kt` (lines 1061–1150):
     - Lines 1061–1086: Defines a local test `object CrossfadeEngine`:
       - `computeEqualPowerVolumes(progress: Float): Pair<Float, Float>`: Clamps progress $p \in [0.0, 1.0]$, returns $(\cos(p \cdot \frac{\pi}{2}), \sin(p \cdot \frac{\pi}{2}))$.
       - `shouldTriggerCrossfade(currentPosMs, durationMs, crossfadeSec, queueIndex, queueSize, repeatMode)`: Returns true if `remainingMs <= crossfadeWindowMs` and `remainingMs > 0`, and not last track unless `repeatMode == RepeatMode.ALL`.
     - Tests `f11_01`–`f11_05` verify:
       - `f11_01`: Equal power sum of squares: $vol_{out}^2 + vol_{in}^2 = 1.0$ at progress 0.0, 0.25, 0.5, 0.75, 1.0.
       - `f11_02`: Start (1.0, 0.0) and end (0.0, 1.0) points.
       - `f11_03`: Trigger inside window (3s remaining with 3s crossfade).
       - `f11_04`: Suppressed outside window (10s remaining with 3s crossfade).
       - `f11_05`: Last track suppressed with `RepeatMode.OFF`, triggered with `RepeatMode.ALL`.
   - `app/src/test/java/com/example/gobeats/GobeatsTier2BoundaryCornerTest.kt` (lines 576–639):
     - `f11_b01`: Track shorter than crossfade duration (2s track, 5s crossfade) triggers safely.
     - `f11_b02`: 0 seconds crossfade never triggers.
     - `f11_b03`: 1-track queue never triggers with `RepeatMode.OFF`.
     - `f11_b04`: Negative remaining time (overshot duration) never triggers.
     - `f11_b05`: Negative progress (-0.5f) clamps to 0.0f; oversized progress (2.5f) clamps to 1.0f.
   - `app/src/test/java/com/example/gobeats/GobeatsTier3CrossFeatureTest.kt`:
     - `cross_08` (lines 231–261): Folder instant queue triggers crossfade between consecutive tracks, suppresses on final track.
     - `cross_10` (lines 292–318): Relaunch from process death inside crossfade window immediately computes transition trigger.
   - **Baseline Test Run Result**:
     - Command: `.\gradlew.bat testDebugUnitTest`
     - Status: **BUILD SUCCESSFUL, 211 tests passed, 0 failures, 0 skipped**.

---

## 2. Logic Chain

1. **Test-First Evidence**:
   - The test suite (`GobeatsTier1FeatureCoverageTest`, `GobeatsTier2BoundaryCornerTest`, `GobeatsTier3CrossFeatureTest`) explicitly establishes the mathematical contract for crossfade via `computeEqualPowerVolumes` and `shouldTriggerCrossfade`.
   - Currently, `CrossfadeEngine` is defined solely inside the test file `GobeatsTier1FeatureCoverageTest.kt`.
   - Therefore, a production class `com.example.gobeats.core.audio.CrossfadeEngine` must be created in `app/src/main/java/com/example/gobeats/core/audio/CrossfadeEngine.kt`.
   - To maintain 100% backward compatibility with existing tests without modifying test signatures, `GobeatsTier1FeatureCoverageTest.CrossfadeEngine` should delegate to `com.example.gobeats.core.audio.CrossfadeEngine`.

2. **Runtime Volume Enveloping in PlaybackConnection**:
   - `PlaybackConnection` already runs `startPositionUpdates()` polling every 250ms.
   - In `startPositionUpdates()`, when `controller` is playing an audio track (not video mode):
     - If `crossfadeDurationSec > 0`:
       - Evaluate `CrossfadeEngine.shouldTriggerCrossfade(...)`.
       - If `true`: compute normalized progress $p = \frac{\text{crossfadeWindowMs} - \text{remainingMs}}{\text{crossfadeWindowMs}}$, then compute $(vol_{out}, vol_{in}) = \text{CrossfadeEngine.computeEqualPowerVolumes}(p)$.
       - Scale player volume: `player.volume = (baseVolume * volOut).coerceIn(0f, 1f)`.
       - Set `isCrossfadeActive = true` in `_playbackState`.
       - When the track transitions naturally to the next queue item via `onMediaItemTransition`:
         - Initiate a smooth fade-in ramp: start the incoming track at `player.volume = 0f`, then launch a coroutine ramp over `fadeDurationMs = min(crossfadeDurationSec * 1000L, 2500L)` using $vol_{in} = \sin(p \cdot \frac{\pi}{2})$ up to `baseVolume`.
       - If `false` (outside window or user seeks back): restore `player.volume = baseVolume` and set `isCrossfadeActive = false`.
   - If `crossfadeDurationSec == 0`:
     - If `gaplessPlayback == true`: bit-perfect gapless mode is maintained. ExoPlayer transitions instantaneously without volume alterations or pauses.
     - If `gaplessPlayback == false`: on track transition, insert a brief inter-track gap (e.g., 250ms pause/mute) before resuming.

3. **Settings Synchronization**:
   - `PlaybackConnection` receives `context` in constructor. It can read and observe `SettingsRepository(context)`:
     - On init, initialize `crossfadeDurationSec = settingsRepo.settings.value.crossfadeDurationSec` and `gaplessPlayback = settingsRepo.settings.value.gaplessPlayback`.
     - Collect `settingsRepo.settings` in a background coroutine in `PlaybackConnection` to react instantly when user changes settings in `SettingsScreen`.
     - Expose `setCrossfadeDuration(seconds: Int)` and `setGaplessPlayback(enabled: Boolean)` on `PlaybackConnection` and `PlaybackRepository`, persisting to `SettingsRepository`.
   - Update `PlaybackUiState` with:
     - `val crossfadeDurationSec: Int = 0`
     - `val gaplessPlayback: Boolean = true`
     - `val isCrossfadeActive: Boolean = false`

4. **ExoPlayer Audio Configuration in GobeatsMediaSessionService**:
   - Ensure `MediaSession.ConnectionResult` supports `Player.COMMAND_SET_VOLUME` and `Player.COMMAND_GET_VOLUME`.
   - Verify `BitPerfectAudioEngine` and `DefaultAudioSink` gapless buffer settings are preserved.

---

## 3. Caveats

1. **Single-ExoPlayer Overlapping Audio Constraint**:
   - Standard Media3 `ExoPlayer` decodes one media period at a time to the hardware `AudioSink`. It does not simultaneously decode two separate audio decoders into a single PCM mixer in a single player instance without a custom multi-track audio renderer.
   - The industry-standard approach for ExoPlayer crossfade in Android audio players (and the explicit specification in `TEST_INFRA.md` §1.2 & §5.1) is **equal-power volume envelope ramping ($vol_{out} = \cos(\dots), vol_{in} = \sin(\dots)$) across queue boundaries**. This avoids dual-player resource contention, hardware DAC lockouts, and DSP session detachment.
2. **Video Mode Exclusion**:
   - Crossfade volume ducking must only apply to audio tracks (`!isVideoMode`), never to videos.
3. **ReplayGain Interplay**:
   - The base volume is determined by `eqMgr.computeTrackReplayGain(track).linearVolume`. Crossfade multiplies this base volume rather than assuming hardcoded `1.0f`.
4. **Manual User Actions**:
   - When the user manually presses Next, Previous, or Seeks, any active fade-in or fade-out ramp must be canceled immediately, resetting volume to `baseVolume`.

---

## 4. Conclusion & Implementation Plan

### 4.1 Required File Modifications & Additions

| File | Action | Purpose |
|---|---|---|
| `app/src/main/java/com/example/gobeats/core/audio/CrossfadeEngine.kt` | **CREATE** | Production crossfade engine: equal-power curve, linear curve, trigger predicate, progress math. |
| `app/src/main/java/com/example/gobeats/core/model/Models.kt` | **MODIFY** | Add `crossfadeDurationSec`, `gaplessPlayback`, `isCrossfadeActive` to `PlaybackUiState`. |
| `app/src/main/java/com/example/gobeats/core/media/PlaybackConnection.kt` | **MODIFY** | Implement crossfade volume envelope, fade-in ramp on transition, settings observation, and public APIs. |
| `app/src/main/java/com/example/gobeats/data/repository/Repositories.kt` | **MODIFY** | Add `setCrossfadeDuration` and `setGaplessPlayback` to `PlaybackRepository`. |
| `app/src/main/java/com/example/gobeats/core/media/GobeatsMediaSessionService.kt` | **MODIFY** | Ensure `COMMAND_SET_VOLUME` and `COMMAND_GET_VOLUME` in player commands. |
| `app/src/test/java/com/example/gobeats/GobeatsTier1FeatureCoverageTest.kt` | **MODIFY** | Forward `CrossfadeEngine` methods to `com.example.gobeats.core.audio.CrossfadeEngine` to maintain 100% test compatibility. |

---

### 4.2 Exact Implementation Specs

#### Step 1: Create `CrossfadeEngine.kt`
Path: `app/src/main/java/com/example/gobeats/core/audio/CrossfadeEngine.kt`
```kotlin
package com.example.gobeats.core.audio

import com.example.gobeats.core.model.RepeatMode
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Audiophile Crossfade and Transition Engine for GoBeats.
 * Computes equal-power (constant loudness) volume curves and transition boundaries.
 */
object CrossfadeEngine {

    /**
     * Computes the (volOut, volIn) equal-power volume pair for a given transition progress [0.0..1.0].
     * Equal power property: volOut^2 + volIn^2 == 1.0 (prevents center-point volume dip).
     */
    fun computeEqualPowerVolumes(progress: Float): Pair<Float, Float> {
        val p = progress.coerceIn(0.0f, 1.0f)
        val rad = (p * (PI / 2.0)).toFloat()
        val volOut = cos(rad)
        val volIn = sin(rad)
        return volOut to volIn
    }

    /**
     * Computes linear volume pair for linear crossfade curve: volOut = 1 - p, volIn = p.
     */
    fun computeLinearVolumes(progress: Float): Pair<Float, Float> {
        val p = progress.coerceIn(0.0f, 1.0f)
        return (1.0f - p) to p
    }

    /**
     * Determines whether crossfade volume envelope should be triggered for the current track.
     */
    fun shouldTriggerCrossfade(
        currentPosMs: Long,
        durationMs: Long,
        crossfadeSec: Int,
        queueIndex: Int,
        queueSize: Int,
        repeatMode: RepeatMode
    ): Boolean {
        if (crossfadeSec <= 0) return false
        val remainingMs = durationMs - currentPosMs
        val crossfadeWindowMs = crossfadeSec * 1000L
        if (remainingMs > crossfadeWindowMs || remainingMs <= 0) return false

        val isLastTrack = queueIndex >= queueSize - 1
        return !isLastTrack || repeatMode == RepeatMode.ALL
    }

    /**
     * Calculates the normalized progress (0.0f to 1.0f) of the crossfade transition.
     */
    fun calculateProgress(
        currentPosMs: Long,
        durationMs: Long,
        crossfadeSec: Int
    ): Float {
        if (crossfadeSec <= 0) return 0.0f
        val remainingMs = (durationMs - currentPosMs).coerceAtLeast(0L)
        val windowMs = crossfadeSec * 1000L
        if (windowMs <= 0L) return 0.0f
        val elapsedInWindow = (windowMs - remainingMs).coerceIn(0L, windowMs)
        return (elapsedInWindow.toFloat() / windowMs.toFloat()).coerceIn(0.0f, 1.0f)
    }
}
```

#### Step 2: Update `PlaybackUiState` in `Models.kt`
Add properties with defaults:
```kotlin
    val crossfadeDurationSec: Int = 0,
    val gaplessPlayback: Boolean = true,
    val isCrossfadeActive: Boolean = false
```

#### Step 3: Enhance `PlaybackConnection.kt`
1. **Properties & State**:
   ```kotlin
   private var baseTrackVolume: Float = 1.0f
   private var fadeInJob: Job? = null
   private var settingsRepo: com.example.gobeats.data.repository.SettingsRepository? = null
   ```
2. **Init Settings Observer**:
   ```kotlin
   init {
       initController()
       try {
           settingsRepo = com.example.gobeats.data.repository.SettingsRepository(context)
           settingsRepo?.let { repo ->
               _playbackState.update {
                   it.copy(
                       crossfadeDurationSec = repo.settings.value.crossfadeDurationSec,
                       gaplessPlayback = repo.settings.value.gaplessPlayback
                   )
               }
               scope.launch {
                   repo.settings.collect { appSettings ->
                       _playbackState.update {
                           it.copy(
                               crossfadeDurationSec = appSettings.crossfadeDurationSec,
                               gaplessPlayback = appSettings.gaplessPlayback
                           )
                       }
                   }
               }
           }
       } catch (_: Exception) {}
   }
   ```
3. **Volume Envelope Handling in `startPositionUpdates()`**:
   In the position polling loop (every 250ms), when not in video mode:
   ```kotlin
   if (!_playbackState.value.isVideoMode) {
       val crossfadeSec = _playbackState.value.crossfadeDurationSec
       val queueIdx = _playbackState.value.queueIndex
       val qSize = currentQueue.size
       val repMode = _playbackState.value.repeatMode
       val pos = player.currentPosition
       val dur = player.duration

       val shouldFade = CrossfadeEngine.shouldTriggerCrossfade(
           currentPosMs = pos,
           durationMs = dur,
           crossfadeSec = crossfadeSec,
           queueIndex = queueIdx,
           queueSize = qSize,
           repeatMode = repMode
       )

       if (shouldFade && fadeInJob?.isActive != true) {
           val progress = CrossfadeEngine.calculateProgress(pos, dur, crossfadeSec)
           val (volOut, _) = CrossfadeEngine.computeEqualPowerVolumes(progress)
           player.volume = (baseTrackVolume * volOut).coerceIn(0f, 1f)
           if (!_playbackState.value.isCrossfadeActive) {
               _playbackState.update { it.copy(isCrossfadeActive = true) }
           }
       } else if (!shouldFade && fadeInJob?.isActive != true && _playbackState.value.isCrossfadeActive) {
           player.volume = baseTrackVolume
           _playbackState.update { it.copy(isCrossfadeActive = false) }
       }
   }
   ```
4. **Transition Handling in `onMediaItemTransition()`**:
   ```kotlin
   fadeInJob?.cancel()
   val crossfadeSec = _playbackState.value.crossfadeDurationSec
   val gapless = _playbackState.value.gaplessPlayback

   // ReplayGain / Base Volume computation
   scope.launch(Dispatchers.IO) {
       dbHelper.recordTrackPlayed(currentTrack.id)
       val eqMgr = (context.applicationContext as? com.example.gobeats.GobeatsApplication)?.equalizerManager
       val targetVolume = if (eqMgr != null) {
           eqMgr.computeTrackReplayGain(currentTrack).linearVolume
       } else {
           1.0f
       }
       baseTrackVolume = targetVolume

       scope.launch(Dispatchers.Main) {
           if (crossfadeSec > 0 && !currentTrack.isVideo) {
               // Smooth Fade-In ramp
               startFadeInRamp(targetVolume, crossfadeSec)
           } else {
               controller?.volume = targetVolume
               _playbackState.update { it.copy(isCrossfadeActive = false) }
           }
       }
   }
   ```
   With `startFadeInRamp`:
   ```kotlin
   private fun startFadeInRamp(targetVolume: Float, crossfadeSec: Int) {
       fadeInJob?.cancel()
       val player = controller ?: return
       player.volume = 0.0f
       _playbackState.update { it.copy(isCrossfadeActive = true) }

       fadeInJob = scope.launch {
           val rampDurationMs = (crossfadeSec * 1000L).coerceAtMost(2500L)
           val stepIntervalMs = 50L
           val totalSteps = (rampDurationMs / stepIntervalMs).toInt().coerceAtLeast(1)

           for (step in 1..totalSteps) {
               delay(stepIntervalMs)
               if (!isActive) break
               val progress = step.toFloat() / totalSteps.toFloat()
               val (_, volIn) = CrossfadeEngine.computeEqualPowerVolumes(progress)
               player.volume = (targetVolume * volIn).coerceIn(0f, 1f)
           }
           player.volume = targetVolume
           _playbackState.update { it.copy(isCrossfadeActive = false) }
       }
   }
   ```
5. **Add Control APIs**:
   ```kotlin
   fun setCrossfadeDuration(seconds: Int) {
       val clamped = seconds.coerceIn(0, 12)
       _playbackState.update { it.copy(crossfadeDurationSec = clamped) }
       settingsRepo?.setPlaybackSettings(clamped, _playbackState.value.gaplessPlayback)
   }

   fun setGaplessPlayback(enabled: Boolean) {
       _playbackState.update { it.copy(gaplessPlayback = enabled) }
       settingsRepo?.setPlaybackSettings(_playbackState.value.crossfadeDurationSec, enabled)
   }
   ```

#### Step 4: Update `PlaybackRepository` in `Repositories.kt`
Expose:
```kotlin
    fun setCrossfadeDuration(seconds: Int) {
        playbackConnection.setCrossfadeDuration(seconds)
    }

    fun setGaplessPlayback(enabled: Boolean) {
        playbackConnection.setGaplessPlayback(enabled)
    }
```

#### Step 5: Delegate `CrossfadeEngine` in `GobeatsTier1FeatureCoverageTest.kt`
Update lines 1061–1086 of `GobeatsTier1FeatureCoverageTest.kt`:
```kotlin
    object CrossfadeEngine {
        fun computeEqualPowerVolumes(progress: Float): Pair<Float, Float> =
            com.example.gobeats.core.audio.CrossfadeEngine.computeEqualPowerVolumes(progress)

        fun shouldTriggerCrossfade(
            currentPosMs: Long,
            durationMs: Long,
            crossfadeSec: Int,
            queueIndex: Int,
            queueSize: Int,
            repeatMode: RepeatMode
        ): Boolean = com.example.gobeats.core.audio.CrossfadeEngine.shouldTriggerCrossfade(
            currentPosMs, durationMs, crossfadeSec, queueIndex, queueSize, repeatMode
        )
    }
```
This guarantees that tests test the production code while retaining identical method signatures.

---

## 5. Verification Method

To independently verify the implementation:

1. **Compile Verification**:
   ```powershell
   .\gradlew.bat compileDebugKotlin
   ```
   Must compile with 0 errors.

2. **Run F11 Isolated Tests**:
   ```powershell
   .\gradlew.bat testDebugUnitTest --tests "com.example.gobeats.GobeatsTier1FeatureCoverageTest.f11*"
   ```
   All 5 tests (`f11_01` to `f11_05`) must pass cleanly.

3. **Run F11 Boundary Tests**:
   ```powershell
   .\gradlew.bat testDebugUnitTest --tests "com.example.gobeats.GobeatsTier2BoundaryCornerTest.f11*"
   ```
   All 5 boundary tests (`f11_b01` to `f11_b05`) must pass cleanly.

4. **Run Cross-Feature Tests**:
   ```powershell
   .\gradlew.bat testDebugUnitTest --tests "com.example.gobeats.GobeatsTier3CrossFeatureTest.cross_08*"
   .\gradlew.bat testDebugUnitTest --tests "com.example.gobeats.GobeatsTier3CrossFeatureTest.cross_10*"
   ```
   Must pass cleanly.

5. **Full Unit Test Suite Regression**:
   ```powershell
   .\gradlew.bat testDebugUnitTest
   ```
   All 211+ tests across all 4 tiers and regression baseline must pass with 100% success rate.

6. **Invalidation Conditions**:
   - Any modification that changes the equal power sum-of-squares $(vol_{out}^2 + vol_{in}^2 \ne 1.0)$ will invalidate `f11_01`.
   - Modifying `shouldTriggerCrossfade` to trigger when `remainingMs <= 0` or on the final track with `RepeatMode.OFF` will invalidate `f11_05`, `f11_b03`, and `f11_b04`.
   - Applying crossfade volume ducking in video mode will degrade video audio playback.
